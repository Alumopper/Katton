package top.kts4mc.stub

import org.objectweb.asm.*
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * Minecraft JAR 存根生成器
 * 扫描 JAR 包中的所有类，生成完整的 Kotlin 存根文件
 */
object JarStubGenerator {

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 2) {
            println("Usage: JarStubGenerator <input-jar> <output-jar>")
            return
        }

        val inputJar = File(args[0])
        val outputJar = File(args[1])

        generateStubs(inputJar, outputJar)
    }

    /**
     * 生成存根 JAR
     * @param inputJar 输入的 Minecraft JAR 文件
     * @param outputJar 输出的存根 JAR 文件
     */
    fun generateStubs(inputJar: File, outputJar: File) {
        println("Scanning: ${inputJar.absolutePath}")
        println("Output: ${outputJar.absolutePath}")

        val classInfos = mutableListOf<ClassInfo>()

        // 扫描所有类
        JarFile(inputJar).use { jar ->
            jar.entries().asSequence()
                .filter { it.name.endsWith(".class") }
                .filter { !it.name.contains("package-info") }
                .forEach { entry ->
                    try {
                        val info = parseClass(jar, entry)
                        if (info != null) {
                            classInfos.add(info)
                        }
                    } catch (e: Exception) {
                        // 忽略解析失败的类
                    }
                }
        }

        println("Found ${classInfos.size} classes")

        // 生成存根并打包
        outputJar.parentFile?.mkdirs()

        JarOutputStream(FileOutputStream(outputJar)).use { jos ->
            // 按包分组
            val packages = classInfos.groupBy { it.packageName }

            packages.forEach { (pkg, classes) ->
                val kotlinSource = generateKotlinStub(pkg, classes)
                val entryName = pkg.replace('.', '/') + "/stub.kt"

                jos.putNextEntry(JarEntry(entryName))
                jos.write(kotlinSource.toByteArray(Charsets.UTF_8))
                jos.closeEntry()
            }

            // 添加核心存根
            val coreStub = generateCoreStub()
            jos.putNextEntry(JarEntry("kts4mc/core.kt"))
            jos.write(coreStub.toByteArray(Charsets.UTF_8))
            jos.closeEntry()
        }

        println("Done! Generated stub JAR with ${classInfos.size} classes")
    }

    private fun parseClass(jar: JarFile, entry: JarEntry): ClassInfo? {
        val className = entry.name.removeSuffix(".class").replace('/', '.')

        // 跳过内部类
        if ('$' in className) return null

        val classReader = ClassReader(jar.getInputStream(entry))
        val visitor = ClassInfoVisitor()
        classReader.accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG)

        return if (visitor.isPublic) {
            ClassInfo(
                name = className,
                packageName = className.substringBeforeLast('.', ""),
                simpleName = className.substringAfterLast('.'),
                isInterface = visitor.isInterface,
                isAbstract = visitor.isAbstract,
                isEnum = visitor.isEnum,
                isFinal = visitor.isFinal,
                isStatic = visitor.isStatic,
                superClass = visitor.superClass,
                interfaces = visitor.interfaces,
                methods = visitor.methods,
                fields = visitor.fields,
                innerClasses = visitor.innerClasses
            )
        } else null
    }

    private fun generateKotlinStub(packageName: String, classes: List<ClassInfo>): String {
        return buildString {
            appendLine("@file:Suppress(\"unused\", \"UNUSED_PARAMETER\", \"PropertyName\", \"FunctionName\", \"RedundantUnitReturnType\", \"ClassName\", \"EnumEntryName\")")
            appendLine()
            appendLine("package $packageName")
            appendLine()

            // 按类型排序：接口 -> 枚举 -> 类
            val sorted = classes.sortedWith(compareBy({ !it.isInterface }, { !it.isEnum }, { it.simpleName }))

            sorted.forEach { info ->
                appendLine(generateClassStub(info))
                appendLine()
            }
        }
    }

    private fun generateClassStub(info: ClassInfo): String {
        return buildString {
            // 类/接口/枚举声明
            when {
                info.isEnum -> append("enum ")
                info.isInterface -> append("interface ")
                else -> {
                    val modifiers = buildList {
                        if (info.isAbstract) add("abstract")
                        if (info.isOpen) add("open")
                    }.joinToString(" ")
                    if (modifiers.isNotEmpty()) append("$modifiers ")
                    append("class ")
                }
            }

            append(info.simpleName)

            // 类型参数
            if (info.typeParameters.isNotEmpty()) {
                append("<")
                append(info.typeParameters.joinToString(", "))
                append(">")
            } else if (!info.isEnum) {
                // 默认类型参数
                append("<T>")
            }

            // 主构造函数（仅对类）
            if (!info.isInterface && !info.isEnum && info.primaryConstructor != null) {
                val params = info.primaryConstructor.parameters.joinToString(", ") {
                    "${it.name}: ${it.type}"
                }
                append("($params)")
            }

            // 继承
            val superTypes = mutableListOf<String>()

            if (!info.isInterface && !info.isEnum && info.superClass != null
                && info.superClass != "java.lang.Object") {
                superTypes.add(info.superClass.replace('$', '.'))
            }

            info.interfaces.forEach {
                superTypes.add(it.replace('$', '.'))
            }

            if (superTypes.isNotEmpty()) {
                append(" : ")
                append(superTypes.joinToString(", "))
            }

            appendLine(" {")

            // 伴生对象
            if (info.companionObject) {
                appendLine("    companion object {")
                appendLine("        // Companion object")
                appendLine("    }")
                appendLine()
            }

            // 枚举常量
            if (info.isEnum && info.enumConstants.isNotEmpty()) {
                info.enumConstants.forEach { constant ->
                    appendLine("    $constant,")
                }
                appendLine("    ;")
                appendLine()
            }

            // 内部类
            info.innerClasses.forEach { inner ->
                appendLine("    // Inner class: $inner")
            }
            if (info.innerClasses.isNotEmpty()) appendLine()

            // 字段 - 不限制数量
            info.fields.forEach { field ->
                val modifiers = buildList {
                    if (field.isStatic) add("")
                    if (field.isFinal) add("val") else add("var")
                }.joinToString(" ")

                append("    $modifiers ${field.name}: ${field.type}")

                if (field.isStatic && field.initialValue != null) {
                    append(" = ${field.initialValue}")
                } else {
                    append(" = TODO()")
                }
                appendLine()
            }

            if (info.fields.isNotEmpty()) appendLine()

            // 构造函数
            info.constructors.forEach { ctor ->
                val params = ctor.parameters.joinToString(", ") { "${it.name}: ${it.type}" }
                append("    constructor($params)")
                if (!info.isInterface && !ctor.isPrimary) {
                    appendLine(" {}")
                } else {
                    appendLine()
                }
            }

            if (info.constructors.isNotEmpty()) appendLine()

            // 方法 - 不限制数量
            info.methods.forEach { method ->
                // 跳过 Object 的方法
                if (method.name in setOf("equals", "hashCode", "toString") && method.parameters.isEmpty()) {
                    return@forEach
                }

                val modifiers = buildList {
                    if (method.isAbstract) add("abstract")
                    if (method.isOpen) add("open")
                    if (method.isStatic) add("")
                }.joinToString(" ")

                if (modifiers.isNotEmpty()) append("$modifiers ")

                append("fun ${method.name}")

                // 类型参数
                if (method.typeParameters.isNotEmpty()) {
                    append("<")
                    append(method.typeParameters.joinToString(", "))
                    append(">")
                }

                // 参数
                val params = method.parameters.joinToString(", ") { "${it.name}: ${it.type}" }
                append("($params)")

                // 返回类型
                if (method.returnType != "void" && method.returnType != "kotlin.Unit") {
                    append(": ${method.returnType}")
                }

                // 实现
                if (info.isInterface || method.isAbstract) {
                    appendLine("")
                } else {
                    appendLine(" = TODO()")
                }
            }

            append("}")
        }
    }

    private fun generateCoreStub(): String {
        return """
@file:Suppress("unused")

package kts4mc

val server: net.minecraft.server.MinecraftServer = TODO()
val source: net.minecraft.commands.CommandSourceStack = TODO()

        """.trimIndent()
    }

    // ==================== 数据类 ====================

    data class ClassInfo(
        val name: String,
        val packageName: String,
        val simpleName: String,
        val isInterface: Boolean,
        val isAbstract: Boolean,
        val isEnum: Boolean,
        val isFinal: Boolean,
        val isStatic: Boolean,
        val superClass: String?,
        val interfaces: List<String>,
        val methods: List<MethodInfo>,
        val fields: List<FieldInfo>,
        val innerClasses: List<String>,
        val typeParameters: List<String> = emptyList(),
        val companionObject: Boolean = false,
        val enumConstants: List<String> = emptyList(),
        val primaryConstructor: ConstructorInfo? = null,
        val constructors: List<ConstructorInfo> = emptyList()
    ) {
        val isOpen: Boolean get() = !isFinal && !isAbstract
    }

    data class MethodInfo(
        val name: String,
        val returnType: String,
        val parameters: List<ParameterInfo>,
        val typeParameters: List<String> = emptyList(),
        val isStatic: Boolean,
        val isAbstract: Boolean,
        val isOpen: Boolean = false,
        val isFinal: Boolean = false
    )

    data class ConstructorInfo(
        val parameters: List<ParameterInfo>,
        val isPrimary: Boolean = false
    )

    data class ParameterInfo(
        val name: String,
        val type: String
    )

    data class FieldInfo(
        val name: String,
        val type: String,
        val isStatic: Boolean,
        val isFinal: Boolean = false,
        val initialValue: String? = null
    )

    // ==================== ASM Visitor ====================

    class ClassInfoVisitor : ClassVisitor(Opcodes.ASM9) {
        var isPublic = false
        var isInterface = false
        var isAbstract = false
        var isEnum = false
        var isFinal = false
        var isStatic = false
        var superClass: String? = null
        var interfaces = mutableListOf<String>()
        var methods = mutableListOf<MethodInfo>()
        var fields = mutableListOf<FieldInfo>()
        var innerClasses = mutableListOf<String>()
        var typeParameters = mutableListOf<String>()
        var companionObject = false
        var enumConstants = mutableListOf<String>()
        var constructors = mutableListOf<ConstructorInfo>()

        override fun visit(version: Int, access: Int, name: String, signature: String?,
                           superName: String?, interfaces: Array<String>?) {
            isPublic = (access and Opcodes.ACC_PUBLIC) != 0
            isInterface = (access and Opcodes.ACC_INTERFACE) != 0
            isAbstract = (access and Opcodes.ACC_ABSTRACT) != 0
            isEnum = (access and Opcodes.ACC_ENUM) != 0
            isFinal = (access and Opcodes.ACC_FINAL) != 0
            isStatic = (access and Opcodes.ACC_STATIC) != 0
            superClass = superName?.replace('/', '.')
            this.interfaces = interfaces?.map { it.replace('/', '.') }?.toMutableList() ?: mutableListOf()
        }

        override fun visitMethod(access: Int, name: String, descriptor: String,
                                 signature: String?, exceptions: Array<String>?): MethodVisitor? {
            // 只处理公共和保护方法
            if ((access and Opcodes.ACC_PUBLIC) == 0 && (access and Opcodes.ACC_PROTECTED) == 0) {
                return null
            }

            val returnType = Type.getReturnType(descriptor).className
            val argTypes = Type.getArgumentTypes(descriptor)

            val isStatic = (access and Opcodes.ACC_STATIC) != 0
            val isAbstract = (access and Opcodes.ACC_ABSTRACT) != 0
            val isFinal = (access and Opcodes.ACC_FINAL) != 0

            val method = MethodInfo(
                name = name,
                returnType = returnType,
                parameters = argTypes.mapIndexed { i, t -> ParameterInfo("p$i", t.className) },
                isStatic = isStatic,
                isAbstract = isAbstract,
                isFinal = isFinal,
                isOpen = !isFinal && !isAbstract && !isStatic
            )

            if (name == "<init>") {
                constructors.add(ConstructorInfo(method.parameters, constructors.isEmpty()))
            } else {
                methods.add(method)
            }

            return null
        }

        override fun visitField(access: Int, name: String, descriptor: String,
                                signature: String?, value: Any?): FieldVisitor? {
            // 只处理公共字段
            if ((access and Opcodes.ACC_PUBLIC) == 0) return null

            val isStatic = (access and Opcodes.ACC_STATIC) != 0
            val isFinal = (access and Opcodes.ACC_FINAL) != 0

            fields.add(FieldInfo(
                name = name,
                type = Type.getType(descriptor).className,
                isStatic = isStatic,
                isFinal = isFinal,
                initialValue = value?.toString()
            ))

            return null
        }

        override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
            if (innerName != null) {
                innerClasses.add(innerName)

                // 检测伴生对象
                if (innerName == "Companion") {
                    companionObject = true
                }
            }
        }
    }
}