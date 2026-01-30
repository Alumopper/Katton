package top.kts4mc.engine

import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import kotlin.reflect.full.createType
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptCompiler
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.enableScriptsInstancesSharing
import kotlin.script.experimental.api.onFailure
import kotlin.script.experimental.api.onSuccess
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.JvmScriptCompiler

class ScriptEngine {

    private val compiler = JvmScriptCompiler()

    private val compiledCache = mutableMapOf<String, Pair<Int, CompiledScript>>()

    private val sharedHost = BasicJvmScriptingHost()

    private val baseConfig = ScriptCompilationConfiguration {
        jvm {
            dependenciesFromCurrentContext(wholeClasspath = true)
        }
        providedProperties(
            "server" to MinecraftServer::class,
            "source" to CommandSourceStack::class
        )
    }

    private val evaluationConfig = ScriptEvaluationConfiguration {
        enableScriptsInstancesSharing()
    }

    suspend fun compile(name: String, sourceCode: String, providedProps: Map<String, kotlin.reflect.KType> = emptyMap()): ResultWithDiagnostics<CompiledScript>{
        // 计算源码哈希
        val sourceHash = sourceCode.hashCode()
        
        // 检查缓存：名称和源码哈希都匹配才使用缓存
        compiledCache[name]?.let { (cachedHash, compiled) ->
            if (cachedHash == sourceHash) {
                return ResultWithDiagnostics.Success(compiled)
            }
        }

        val source = sourceCode.toScriptSource(name)

        val configWithProvided = ScriptCompilationConfiguration(baseConfig) {
            providedProperties(*providedProps.toList().toTypedArray())
        }
        return compiler(source, configWithProvided).onSuccess { compiled ->
            // 缓存时保存源码哈希
            compiledCache[name] = sourceHash to compiled
            ResultWithDiagnostics.Success(compiled)
        }
    }

    private val classCache = java.util.concurrent.ConcurrentHashMap<CompiledScript, kotlin.reflect.KClass<*>>()

    suspend fun execute(
        compiled: CompiledScript,
        bindings: Map<String, Any?> = emptyMap()
    ): ResultWithDiagnostics<EvaluationResult> {
        // 使用缓存的 Class 避免每次 getClass 重复加载
        val scriptClass = classCache.getOrPut(compiled) {
            // 使用基础配置加载类，不包含具体 bindings 数据，保证配置一致性以命中内部缓存
            when (val res = compiled.getClass(evaluationConfig)) {
                is ResultWithDiagnostics.Success -> res.value
                is ResultWithDiagnostics.Failure -> return res
            }
        }

        return try {
            // 尝试获取 Kotlin 构造函数
            val ctor = scriptClass.constructors.firstOrNull()
            
            val instance = if (ctor != null) {
                // 匹配构造参数
                val args = ctor.parameters.map { param ->
                    bindings[param.name]
                }.toTypedArray()
                ctor.call(*args)
            } else {
                // 回退到 Java 反射 (处理 Kotlin 反射不可用的情况)
                // 假设参数顺序严格遵循 providedProperties 的定义顺序: server, source
                val javaCtor = scriptClass.java.constructors.firstOrNull()
                    ?: throw IllegalStateException("No constructor found for script class ${scriptClass.simpleName}")
                
                val args = arrayOf(bindings["server"], bindings["source"])
                javaCtor.newInstance(*args)
            }

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