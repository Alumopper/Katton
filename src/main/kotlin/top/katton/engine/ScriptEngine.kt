package top.katton.engine

import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmCompiledModuleInMemoryImpl
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import top.katton.util.Event
import java.util.concurrent.ConcurrentHashMap
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Opcodes
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.Collections
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

/**
 * ScriptEngine compiles and executes Kotlin script sources.
 *
 * It keeps caches for compiled scripts and loaded script classes to avoid
 * repeated compilation and class loading, improving execution performance.
 */
object ScriptEngine {

    data class CompileRequest(
        val name: String,
        val sourceCode: String,
        var imported: Collection<String> = emptySet(),
        val sourceName: String = name
    )

    private data class ClassImportability(
        val isObjectClass: Boolean,
        val isCompanionClass: Boolean
    )

    private val compiler = JvmScriptCompiler()

    // Cache mapping script name -> (sourceHash, CompiledScript)
    // The hash ensures we only reuse the compiled artifact when the source is unchanged.
    private val compiledCache = ConcurrentHashMap<String, Pair<Int, KattonScript>>()

    private val dependencyCache = ConcurrentHashMap<String, ConcurrentLinkedQueue<KattonScript>>()

    val scriptPackages: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    fun resetDependencyIndex() {
        dependencyCache.clear()
        scriptPackages.clear()
    }

    fun resetAllCachesForReload() {
        resetDependencyIndex()
        compiledCache.clear()
        classCache.clear()
    }

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
    suspend fun compile(
        name: String,
        sourceCode: String,
        imported: Collection<KattonScript> = emptySet(),
        sourceName: String = name,
        forceRecompile: Boolean = false
    ): ResultWithDiagnostics<KattonScript>{
        // Calculate the hash of the source to detect changes.
        val sourceHash = sourceCode.hashCode()

        if (!forceRecompile) {
            // Check cache: only reuse compiled script when both name and source hash match.
            compiledCache[name]?.let { (cachedHash, compiled) ->
                if (cachedHash == sourceHash) {
                    //add dependencies to the cache
                    for (exported in compiled.exported){
                        dependencyCache.computeIfAbsent(exported){
                            ConcurrentLinkedQueue()
                        }.add(compiled)
                    }
                    return ResultWithDiagnostics.Success(compiled)
                }
            }
        }

        val source = sourceCode.toScriptSource(sourceName)
        val safeImported = sanitizeImportedScripts(name, imported)

        val normalResult = compileWithConfiguration(
            source = source,
            imported = safeImported,
            compatibilityMode = false
        )

        val finalResult = when (normalResult) {
            is ResultWithDiagnostics.Success -> normalResult
            is ResultWithDiagnostics.Failure -> {
                val hasTopologicalSortCycle = normalResult.reports.any {
                    it.message.contains("Cannot compute a topological sort", ignoreCase = true)
                }
                if (!hasTopologicalSortCycle) {
                    normalResult
                } else {
                    val retried = compileWithConfiguration(
                        source = source,
                        imported = safeImported,
                        compatibilityMode = true
                    )
                    when (retried) {
                        is ResultWithDiagnostics.Success -> retried
                        is ResultWithDiagnostics.Failure -> {
                            val importedNames = if (safeImported.isEmpty()) "<none>" else safeImported.joinToString { it.identifier }
                            ResultWithDiagnostics.Failure(
                                retried.reports + ScriptDiagnostic(
                                    -1,
                                    message = "Script dependency context for '$name': imported scripts = [$importedNames]",
                                    severity = ScriptDiagnostic.Severity.ERROR
                                ) + ScriptDiagnostic(
                                    -1,
                                    message = "Katton retry strategy: compatibility compile mode enabled after topological sort cycle.",
                                    severity = ScriptDiagnostic.Severity.WARNING
                                )
                            )
                        }
                    }
                }
            }
        }

        return finalResult.onSuccess { compiled ->

            val kattonScript = KattonScript(
                script = compiled as KJvmCompiledScript,
                identifier = name,
                sourceCode = source,
                dependencies = safeImported.toSet(),
            )

            // Scan public members from compiled bytes (No class loading)
            processExportedMembers(kattonScript)

            // Save compiled script together with its source hash for future reuse.
            compiledCache[name] = sourceHash to kattonScript

            ResultWithDiagnostics.Success(kattonScript)
        }
    }

    private suspend fun compileWithConfiguration(
        source: SourceCode,
        imported: Collection<KattonScript>,
        compatibilityMode: Boolean
    ): ResultWithDiagnostics<CompiledScript> {
        return compiler(source, ScriptCompilationConfiguration(baseConfig) {
            importScripts(imported.map { it.sourceCode })
            if (compatibilityMode) {
                compilerOptions.append("-jvm-target", "21")
                compilerOptions.append("-Xbackend-threads=1")
            }
        })
    }

    private fun sanitizeImportedScripts(
        targetScriptName: String,
        imported: Collection<KattonScript>
    ): List<KattonScript> {
        if (imported.isEmpty()) return emptyList()

        val uniqueImports = imported.distinctBy { it.identifier }
        return uniqueImports.filterNot { dependency ->
            dependency.identifier == targetScriptName || dependency.dependsOn(targetScriptName)
        }
    }

    private fun KattonScript.dependsOn(targetScriptName: String): Boolean {
        val visited = HashSet<String>()
        fun dfs(script: KattonScript): Boolean {
            if (!visited.add(script.identifier)) return false
            if (script.identifier == targetScriptName) return true
            return script.dependencies.any { dfs(it) }
        }
        return dfs(this)
    }

    suspend fun compileBatch(
        requests: Collection<CompileRequest>,
        forceRecompile: Boolean = false
    ): Map<String, ResultWithDiagnostics<KattonScript>> {
        if (requests.isEmpty()) return emptyMap()

        val pending = LinkedHashMap<String, CompileRequest>()
        requests.forEach {
            pending[it.name] = it
            it.imported = it.imported.filter(::isScriptImport)
        }

        val results = LinkedHashMap<String, ResultWithDiagnostics<KattonScript>>()
        var forcedRelease = false

        while (pending.isNotEmpty()) {
            var progress = false
            val iterator = pending.entries.iterator()

            while (iterator.hasNext()) {
                val (name, request) = iterator.next()
                val resolved = resolvedScriptImports(request.imported)
                if (resolved.size != request.imported.size && !forcedRelease) {
                    continue
                }

                val result = compile(
                    name = request.name,
                    sourceCode = request.sourceCode,
                    imported = resolved,
                    sourceName = request.sourceName,
                    forceRecompile = forceRecompile
                )
                results[name] = result
                iterator.remove()
                progress = true
            }

            if (!progress) {
                // No dependency can be resolved anymore: treat unresolved imports as non-script dependencies.
                forcedRelease = true
            }
        }

        return results
    }

    private fun isScriptImport(identifier: String): Boolean {
        return scriptPackages.any { pkg -> identifier.startsWith("$pkg.") }
    }

    private fun resolvedScriptImports(imported: Collection<String>): Collection<KattonScript> {
        if (imported.isEmpty()) return emptySet()
        return imported
            .flatMap { dependencyCache[it] ?: emptyList() }
    }

    private fun addExport(identifier: String, script: KattonScript) {
        script.exported.add(identifier)
        val providers = dependencyCache.computeIfAbsent(identifier){
            ConcurrentLinkedQueue()
        }
        providers.add(script)
    }

    private fun processExportedMembers(script: KattonScript) {
        // Get compiled bytes
        val outputFiles =
            (script.script.getCompiledModule() as? KJvmCompiledModuleInMemoryImpl)?.compilerOutputFiles ?: emptyMap()

        // the package of the script
        // empty if the script is in the default package
        val scriptInfo = getScriptInfo(outputFiles)
        script.scriptPackage = scriptInfo.scriptPackage
        script.scriptMainClassInternalName = scriptInfo.scriptMainClassInternalName

        // ignore if the script is in default package
        if (scriptInfo.scriptPackage == null) {
            return
        }

        outputFiles.forEach { (path, bytes) ->
            if (path.endsWith(".class")) {
                processClassByte(bytes, script)
            }
        }
    }

    private fun processClassByte(
        bytes: ByteArray,
        script: KattonScript
    ) {
        val reader = ClassReader(bytes)
        val internalName = reader.className
        val isScriptMain = internalName == script.scriptMainClassInternalName
        val importability = getClassImportability(reader)

        // Process class defined inside script.
        // For example, we need to transform MainClass$Test into package.Test
        if (!isScriptMain && script.scriptMainClassInternalName.isNotEmpty()
            && internalName.startsWith(script.scriptMainClassInternalName)
        ) {
            //Get the name relative to the main class, such as $Test or $Test$Sub
            val relativeName = internalName.removePrefix(script.scriptMainClassInternalName)
            // remove the first $ and replace the rest $ with . to get the simple name
            // $Test -> Test
            // $Test$QwQ -> Test.QwQ
            val simpleName = relativeName.substring(1).replace('$', '.')

            // Filter out anonymous classes which start with a digit
            if (simpleName.isNotEmpty() && !simpleName[0].isDigit()) {
                val fqn = "${script.scriptPackage}.$simpleName"

                // Only consider public classes as exports
                if ((reader.access and Opcodes.ACC_PUBLIC) != 0) {
                    addExport(fqn, script)
                }
            }
        }

        if(!(importability.isObjectClass || importability.isCompanionClass || isScriptMain)) return

        // Process public members of all class.
        val visitor = object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(
                access: Int,
                methodName: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor? {
                if ((access and Opcodes.ACC_PUBLIC) != 0 &&
                    (access and Opcodes.ACC_SYNTHETIC) == 0 &&
                    !methodName.startsWith("<") && !methodName.contains("$")
                ) {
                    if (methodName != "main") {
                        val fqn = "${script.scriptPackage}.$methodName"
                        addExport(fqn, script)
                    }
                }
                return null
            }

            override fun visitField(
                access: Int,
                fieldName: String,
                descriptor: String,
                signature: String?,
                value: Any?
            ): FieldVisitor? {
                if ((access and Opcodes.ACC_PUBLIC) != 0 &&
                    (access and Opcodes.ACC_SYNTHETIC) == 0 &&
                    !fieldName.contains("$") &&
                    fieldName != "INSTANCE" &&
                    fieldName != "Companion"
                ) {
                    val fqn = "${script.scriptPackage}.$fieldName"
                    addExport(fqn, script)
                }
                return null
            }
        }
        reader.accept(
            visitor,
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
        )
    }

    private data class ScriptInfo(val scriptPackage: String?, val scriptMainClassInternalName: String)

    private fun getScriptInfo(outputFiles: Map<String, ByteArray>): ScriptInfo {

        var scriptMainClassInternalName = ""
        var scriptPackage: String? = null

        //find the main class of the scripts (not containing $ and end with .class)
        for ((path, bytes) in outputFiles) {
            if (path.endsWith(".class") && !path.contains("$")) {
                val reader = ClassReader(bytes)
                val className = reader.className // the main class of the scripts
                scriptMainClassInternalName = className
                val lastSlash = className.lastIndexOf('/')
                scriptPackage = if (lastSlash != -1) {
                    className.substring(0, lastSlash).replace('/', '.')
                }else{
                    null
                }
                break
            }
        }

        return ScriptInfo(scriptPackage, scriptMainClassInternalName)
    }

    private fun getClassImportability(reader: ClassReader): ClassImportability {
        var hasInstanceField = false

        reader.accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitField(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                value: Any?
            ): FieldVisitor? {
                if (name == "INSTANCE" &&
                    (access and Opcodes.ACC_STATIC) != 0 &&
                    (access and Opcodes.ACC_FINAL) != 0
                ) {
                    hasInstanceField = true
                }
                return null
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

        return ClassImportability(
            isObjectClass = hasInstanceField,
            isCompanionClass = reader.className.endsWith("\$Companion")
        )
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
    private val classCache = ConcurrentHashMap<KattonScript, kotlin.reflect.KClass<*>>()

    /**
     * Execute a compiled script and return its evaluation result.
     *
     * The engine will load the script's class (cached when possible) and attempt to
     * instantiate it. Instantiation prefers a no-arg Kotlin constructor, falling back
     * to Java reflection when necessary.
     *
     * @param script The compiled script to execute.
     * @return ResultWithDiagnostics containing EvaluationResult or diagnostics on failure.
     */
    @JvmStatic
    suspend fun execute(
        script: KattonScript,
        scriptOwner: String? = null
    ): ResultWithDiagnostics<EvaluationResult> {
        if (scriptOwner != null) {
            Event.clearHandlersByOwner(scriptOwner)
        }

        // Use cached class to avoid repeated getClass() calls for the same compiled script.
        val scriptClass = classCache.getOrPut(script) {
            // Load the class using the evaluation configuration; return any failure immediately.
            when (val res = script.script.getClass(evaluationConfig)) {
                is ResultWithDiagnostics.Success -> res.value
                is ResultWithDiagnostics.Failure -> return res
            }
        }

        return try {
            // Try to find a Kotlin constructor (may have parameter metadata).
            val ctor = scriptClass.constructors.firstOrNull()
            
            val instance = Event.withScriptOwner(scriptOwner) {
                if (ctor != null) {
                    // Prefer calling the Kotlin constructor without parameters.
                    ctor.call()
                } else {
                    // Fallback to Java reflection: get the first Java constructor and call it.
                    // This handles environments where Kotlin reflection metadata is not available.
                    val javaCtor = scriptClass.java.constructors.firstOrNull()
                        ?: throw IllegalStateException("No constructor found for script class ${scriptClass.simpleName}")
                    javaCtor.newInstance()
                }
            }

            // Wrap the created instance as the script evaluation result.
            ResultWithDiagnostics.Success(
                EvaluationResult(
                    returnValue = ResultValue.Value(
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