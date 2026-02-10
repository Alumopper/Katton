package top.katton.engine

import net.fabricmc.loader.api.FabricLoader
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.enableScriptsInstancesSharing
import kotlin.script.experimental.api.onSuccess
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.JvmScriptCompiler

/**
 * ScriptEngine compiles and executes Kotlin script sources.
 *
 * It keeps caches for compiled scripts and loaded script classes to avoid
 * repeated compilation and class loading, improving execution performance.
 */
class ScriptEngine {

    private val compiler = JvmScriptCompiler()

    // Cache mapping script name -> (sourceHash, CompiledScript)
    // The hash ensures we only reuse the compiled artifact when the source is unchanged.
    private val compiledCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, CompiledScript>>()

    // Shared scripting host (currently unused but kept for potential future use)
    private val sharedHost = BasicJvmScriptingHost()

    // Base compilation configuration for all scripts.
    // Includes classpath dependencies and default imports commonly used by scripts.
    private val baseConfig = ScriptCompilationConfiguration {
        jvm {
            dependenciesFromCurrentContext(wholeClasspath = true)
        }
    }

    // Evaluation configuration: enable sharing of script instances if needed.
    private val evaluationConfig = ScriptEvaluationConfiguration {
        enableScriptsInstancesSharing()
    }

    /**
     * Compile the provided script source into a CompiledScript.
     *
     * This method uses an internal cache keyed by name + source hash to avoid
     * recompilation when the same source is passed again.
     *
     * @param name A stable name or id for the script (used as cache key and source name).
     * @param sourceCode The Kotlin script source text.
     * @return ResultWithDiagnostics wrapping the compiled script or diagnostics on failure.
     */
    suspend fun compile(name: String, sourceCode: String): ResultWithDiagnostics<CompiledScript>{
        // Calculate the hash of the source to detect changes.
        val sourceHash = sourceCode.hashCode()
        
        // Check cache: only reuse compiled script when both name and source hash match.
        compiledCache[name]?.let { (cachedHash, compiled) ->
            if (cachedHash == sourceHash) {
                return ResultWithDiagnostics.Success(compiled)
            }
        }

        // Remap imports to support "Named" class names in scripts running on Intermediary/Obfuscated runtimes.
        val remappedSourceCode = ScriptRemapper.remap(sourceCode)
        val source = remappedSourceCode.toScriptSource(name)

        return compiler(source, baseConfig).onSuccess { compiled ->
            // Save compiled script together with its source hash for future reuse.
            compiledCache[name] = sourceHash to compiled
            ResultWithDiagnostics.Success(compiled)
        }
    }

    /**
     * Updates the cache by removing scripts that are no longer present in the provided set.
     * This ensures deleted scripts are removed from memory.
     *
     * @param validScriptNames The set of script names that are currently valid/loaded.
     */
    fun updateCache(validScriptNames: Set<String>) {
        val keysToRemove = compiledCache.keys.filter { it !in validScriptNames }
        keysToRemove.forEach { name ->
            compiledCache.remove(name)?.let { (_, compiledScript) ->
                classCache.remove(compiledScript)
            }
        }
    }

    // Cache mapping CompiledScript -> loaded KClass to avoid repeated getClass operations.
    private val classCache = java.util.concurrent.ConcurrentHashMap<CompiledScript, kotlin.reflect.KClass<*>>()

    /**
     * Execute a compiled script and return its evaluation result.
     *
     * The engine will load the script's class (cached when possible) and attempt to
     * instantiate it. Instantiation prefers a no-arg Kotlin constructor, falling back
     * to Java reflection when necessary.
     *
     * @param compiled The compiled script to execute.
     * @return ResultWithDiagnostics containing EvaluationResult or diagnostics on failure.
     */
    suspend fun execute(
        compiled: CompiledScript
    ): ResultWithDiagnostics<EvaluationResult> {
        // Use cached class to avoid repeated getClass() calls for the same compiled script.
        val scriptClass = classCache.getOrPut(compiled) {
            // Try to load using standard mechanism, but if we suspect bytecode mismatch (Named vs Intermediary),
            // we should try our custom reloader directly if configured.
            // For now, let's inject the Remapping loader logic here.
            
            // First, try standard loading. If it matches environment, good.
            // But since the user asked for bytecode remapping, we should attempt it if possible.
            // Optimization: Only try remapping if the compilation produced 'Named' references that differ from runtime.
            // We'll unconditionally attempt remapping if the helper is available, or use a flag.
            
            try {
                // Determine the parent ClassLoader. Use context classloader or dependencies' loader.
                val parentLoader = Thread.currentThread().contextClassLoader 
                    ?: this.javaClass.classLoader
                
                // Remap bytecode from Named -> Intermediary and load
                val remappedLoader = try {
                     RuntimeRemapper.remapAndLoad(compiled, parentLoader)
                } catch (e: Throwable) {
                     // If remapping fails (e.g. types not compatible), fall back to standard load
                     // e.g. maybe compiled script is already Intermediary
                     null
                }

                if (remappedLoader != null) {
                    // Try to load the main script class from our new loader
                    // We need to know the class name. 
                    // CompiledScript doesn't easily expose the main class name without `getClass`.
                    // But we can infer it or ask `compiled` for properties.
                    // KJvmCompiledScript has `scriptRawName` or we can scan the remapped classes.
                    
                    // Fallback to standard getClass(config) which uses the config's classloader?
                    // We can supply our remapped loader to the config!
                    
                    val configWithLoader = ScriptEvaluationConfiguration(evaluationConfig) {
                        jvm {
                            baseClassLoader(remappedLoader)
                        }
                    }
                    val res = compiled.getClass(configWithLoader)
                    if (res is ResultWithDiagnostics.Success) {
                        res.value
                    } else {
                        // fallback
                        (compiled.getClass(evaluationConfig) as? ResultWithDiagnostics.Success)?.value 
                            ?: throw IllegalStateException("Could not load script class")
                    }
                } else {
                     val res = compiled.getClass(evaluationConfig)
                     if (res is ResultWithDiagnostics.Failure) return res
                     (res as ResultWithDiagnostics.Success).value
                }
            } catch (e: Throwable) {
                 val res = compiled.getClass(evaluationConfig)
                 if (res is ResultWithDiagnostics.Failure) return res
                 (res as ResultWithDiagnostics.Success).value
            }
        }

        return try {
            // Try to find a Kotlin constructor (may have parameter metadata).
            val ctor = scriptClass.constructors.firstOrNull()
            
            val instance = if (ctor != null) {
                // Prefer calling the Kotlin constructor without parameters.
                ctor.call()
            } else {
                // Fallback to Java reflection: obtain the first Java constructor and call it.
                // This handles environments where Kotlin reflection metadata is not available.
                val javaCtor = scriptClass.java.constructors.firstOrNull()
                    ?: throw IllegalStateException("No constructor found for script class ${scriptClass.simpleName}")
                javaCtor.newInstance()
            }

            // Wrap the created instance as the script evaluation result.
            ResultWithDiagnostics.Success(
                EvaluationResult(
                    returnValue = kotlin.script.experimental.api.ResultValue.Value(
                        name = "scriptResult",
                        value = instance, 
                        type = "Any",
                        scriptClass = scriptClass,
                        scriptInstance = instance
                    ),
                    configuration = evaluationConfig
                )
            )
        } catch (e: Throwable) {
            // Convert exception into a ScriptDiagnostic to return as failure diagnostics.
            ResultWithDiagnostics.Failure(
               listOf(
                   ScriptDiagnostic(
                       -1,
                       message =  e.message ?: "Execution Error",
                       exception = e
                   )
               )
            )
        }
    }

}