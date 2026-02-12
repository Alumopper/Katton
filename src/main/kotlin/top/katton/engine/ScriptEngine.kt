package top.katton.engine

import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
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
//        refineConfiguration {
//            onAnnotations(Import::class) { context: ScriptConfigurationRefinementContext ->
//                val annotations = context.collectedData?.get(ScriptCollectedData.foundAnnotations)
//                    ?.filter { it.annotationClass == Import::class }
//                    ?: return@onAnnotations context.compilationConfiguration.asSuccess()
//
//                val sources = annotations.flatMap { (it as Import).paths.asList() }
//                    .mapNotNull { path ->
//                        File(path).takeIf { it.exists() }?.toScriptSource()
//                }
//
//                ScriptCompilationConfiguration(context.compilationConfiguration) {
//                    if (sources.isNotEmpty()) {
//                        importScripts.append(sources)
//                    }
//                }.asSuccess()
//            }
//        }
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

        val source = sourceCode.toScriptSource(name)

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
            // Load the class using the evaluation configuration; return any failure immediately.
            when (val res = compiled.getClass(evaluationConfig)) {
                is ResultWithDiagnostics.Success -> res.value
                is ResultWithDiagnostics.Failure -> return res
            }
        }

        return try {
            // Try to find a Kotlin constructor (may have parameter metadata).
            val ctor = scriptClass.constructors.firstOrNull()
            
            val instance = if (ctor != null) {
                // Prefer calling the Kotlin constructor without parameters.
                ctor.call()
            } else {
                // Fallback to Java reflection: get the first Java constructor and call it.
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