package top.katton.engine

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmCompiledModuleInMemoryImpl
import org.objectweb.asm.ClassReader
import top.katton.api.ClientScriptEntrypoint
import top.katton.api.LOGGER
import top.katton.api.ServerScriptEntrypoint
import top.katton.util.Event
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.enableScriptsInstancesSharing
import kotlin.script.experimental.api.importScripts
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.JvmScriptCompiler

/**
 * ScriptEngine compiles and executes Kotlin script sources.
 */
object ScriptEngine {

    private val compiler = JvmScriptCompiler()

    // Base compilation configuration for all scripts.
    // Includes classpath dependencies and default imports commonly used by scripts.
    private val baseConfig = ScriptCompilationConfiguration {
        jvm {
            dependenciesFromCurrentContext(wholeClasspath = true)
        }
        defaultImports(
            ClientScriptEntrypoint::class.qualifiedName!!,
            ServerScriptEntrypoint::class.qualifiedName!!
        )
    }

    // Evaluation configuration: enable sharing of script instances if needed.
    private val evaluationConfig = ScriptEvaluationConfiguration {
        enableScriptsInstancesSharing()
    }

    @JvmStatic
    fun compileAndExecuteAll(scriptPath: Collection<String>, environment: ScriptEnvironment) {
        val dummyScript = "".toScriptSource()
        LOGGER.info("Compiling and executing ${scriptPath.size} ${environment.name.lowercase()} scripts")
        runBlocking {
            val compileResult = compiler(dummyScript, ScriptCompilationConfiguration(baseConfig) {
                importScripts(scriptPath.map { File(it).toScriptSource() })
            })
            logCompileResult(compileResult)

            when (compileResult) {
                is ResultWithDiagnostics.Success -> {
                    val executionResult = execute(compileResult.value, environment)
                    logExecutionResult(executionResult)
                }

                is ResultWithDiagnostics.Failure -> {
                    return@runBlocking
                }
            }
        }
    }

    private fun logCompileResult(compileResult: ResultWithDiagnostics<CompiledScript>) {
        val compileReports = compileResult.reports.filter { it.severity >= ScriptDiagnostic.Severity.INFO }
        if (compileReports.isNotEmpty()) {
            LOGGER.info(compileReports.joinToString("\n"))
        }

        when (compileResult) {
            is ResultWithDiagnostics.Success -> {
                LOGGER.info("Compile succeeded, start executing scripts")
            }

            is ResultWithDiagnostics.Failure -> {
                LOGGER.error("Compile failed.")
            }
        }
    }

    private fun logExecutionResult(executionResult: ResultWithDiagnostics<EvaluationResult>) {
        when (executionResult) {
            is ResultWithDiagnostics.Success -> {
                val summary = (executionResult.value.returnValue as? ResultValue.Value)
                    ?.value as? Map<*, *>

                if (summary != null) {
                    val successCount = summary["successCount"]
                    val failureCount = summary["failureCount"]
                    val totalAttempted = summary["totalAttempted"]
                    val errorMessages = summary["errorMessages"] as? List<*>

                    LOGGER.info(
                        "Execution finished: total=$totalAttempted, success=$successCount, failure=$failureCount"
                    )

                    if (!errorMessages.isNullOrEmpty()) {
                        LOGGER.warn("Execution errors:\n${errorMessages.joinToString("\n")}")
                    }
                } else {
                    LOGGER.info("Execution finished, but no execution summary was returned")
                }
            }

            is ResultWithDiagnostics.Failure -> {
                LOGGER.error("Execution failed: ${executionResult.reports.joinToString("\n")}")
            }
        }
    }

    private suspend fun execute(
        script: CompiledScript,
        environment: ScriptEnvironment
    ): ResultWithDiagnostics<EvaluationResult> {
        val module = (script as KJvmCompiledScript).getCompiledModule() as KJvmCompiledModuleInMemoryImpl
        val outputFiles = module.compilerOutputFiles

        val rootClass = when (val res = script.getClass(evaluationConfig)) {
            is ResultWithDiagnostics.Success -> res.value
            is ResultWithDiagnostics.Failure -> return res
        }

        val loader = rootClass.java.classLoader
        val rootName = rootClass.qualifiedName
        var successCount = 0
        var failureCount = 0
        val errorMessages = mutableListOf<String>()

        for ((path, bytes) in outputFiles) {
            if (!path.endsWith(".class") || path.contains("$")) continue

            val internalName = ClassReader(bytes).className
            val fqcn = internalName.replace('/', '.')
            if (fqcn == rootName) continue

            runCatching {
                val clazz = Class.forName(fqcn, false, loader)
                val entrypoints = clazz.declaredMethods.filter { method ->
                    Modifier.isStatic(method.modifiers) &&
                        method.annotationClassNames().contains(environment.annotationClassName)
                }

                for (method in entrypoints) {
                    if (method.parameterCount != 0) {
                        failureCount++
                        errorMessages += "$fqcn.${method.name}: ${environment.annotationClassName.substringAfterLast('.')} functions must not declare parameters"
                        continue
                    }

                    Event.withScriptOwner(fqcn) {
                        method.isAccessible = true
                        method.invoke(null)
                    }
                    successCount++
                }
            }.onFailure {
                failureCount++
                errorMessages += "$fqcn: ${it.message ?: "Unknown error"}"
                LOGGER.warn("Failed to execute ${environment.name.lowercase()} script entrypoints from class: $fqcn", it)
            }
        }

        val executionSummary = mapOf(
            "successCount" to successCount,
            "failureCount" to failureCount,
            "totalAttempted" to (successCount + failureCount),
            "errorMessages" to errorMessages
        )

        return ResultWithDiagnostics.Success(
            EvaluationResult(
                returnValue = ResultValue.Value(
                    name = "executionSummary",
                    value = executionSummary,
                    type = "Map<String, Any>",
                    scriptClass = rootClass,
                    scriptInstance = executionSummary
                ),
                configuration = evaluationConfig
            )
        )
    }

    private fun Method.annotationClassNames(): Set<String> {
        return annotations.mapNotNull { it.annotationClass.qualifiedName }.toSet()
    }
}
