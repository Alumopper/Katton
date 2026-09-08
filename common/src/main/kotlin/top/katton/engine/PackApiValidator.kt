package top.katton.engine

import org.objectweb.asm.*
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import top.katton.pack.ScriptPack

/** Checks bytecode without loading or initializing any script class. */
internal object PackApiValidator {
    fun validate(pack: ScriptPack, artifact: PackArtifact, libraries: PrivateLibraries, visible: List<PackPreparation>, hidden: Map<String, String> = emptyMap()) {
        val forbidden = libraries.classes.keys.associateWith { "private libs/ of ${pack.syncId}" }.toMutableMap()
        visible.forEach { dependency -> dependency.libraries.classes.keys.forEach { forbidden.putIfAbsent(it, "private libs/ of ${dependency.pack.syncId}") } }
        forbidden.putAll(hidden)
        val visibleClasses = artifact.classNames + visible.flatMap { it.artifact.classNames }
        forbidden.keys.removeAll(visibleClasses)
        fun checkType(type: Type, api: String) {
            when (type.sort) {
                Type.OBJECT -> require(type.className !in forbidden) { "Pack ${pack.syncId}: API $api leaks type ${type.className} from ${forbidden[type.className]}" }
                Type.ARRAY -> checkType(type.elementType, api)
                Type.METHOD -> { checkType(type.returnType, api); type.argumentTypes.forEach { checkType(it, api) } }
            }
        }
        fun signature(value: String?, api: String) {
            if (value == null) return
            val visitor = object : SignatureVisitor(Opcodes.ASM9) {
                override fun visitClassType(name: String) { checkType(Type.getObjectType(name), api) }
            }
            SignatureReader(value).accept(visitor)
        }
        artifact.files.filterKeys { it.endsWith(".class") }.forEach { (path, bytes) ->
            ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                var publicClass = false
                override fun visit(version: Int, access: Int, name: String, sig: String?, superName: String?, interfaces: Array<out String>?) {
                    publicClass = access and Opcodes.ACC_PUBLIC != 0
                    if (!publicClass) return
                    signature(sig, path)
                    (listOfNotNull(superName) + interfaces.orEmpty()).forEach { checkType(Type.getObjectType(it), path) }
                }
                override fun visitField(access: Int, name: String, desc: String, sig: String?, value: Any?): FieldVisitor? {
                    if (publicClass && access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED) != 0) {
                        checkType(Type.getType(desc), "$path.$name"); signature(sig, "$path.$name")
                    }
                    return null
                }
                override fun visitMethod(access: Int, name: String, desc: String, sig: String?, exceptions: Array<out String>?): MethodVisitor? {
                    if (!publicClass || access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED) == 0) return null
                    val api = "$path.$name$desc"
                    checkType(Type.getMethodType(desc), api); signature(sig, api)
                    exceptions.orEmpty().forEach { checkType(Type.getObjectType(it), api) }
                    return object : MethodVisitor(Opcodes.ASM9) {
                        val types = mutableListOf<Type>()
                        var inline = false
                        fun constant(value: Any?) {
                            when (value) {
                                is Type -> types += value
                                is Handle -> {
                                    types += Type.getObjectType(value.owner)
                                    types += Type.getType(value.desc)
                                }
                                is ConstantDynamic -> {
                                    types += Type.getType(value.descriptor)
                                    constant(value.bootstrapMethod)
                                    repeat(value.bootstrapMethodArgumentCount) { constant(value.getBootstrapMethodArgument(it)) }
                                }
                            }
                        }
                        override fun visitLocalVariable(name: String, descriptor: String, signature: String?, start: Label, end: Label, index: Int) {
                            if (name.startsWith("\$i\$f\$")) inline = true
                        }
                        override fun visitTypeInsn(opcode: Int, type: String) { types += Type.getObjectType(type) }
                        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) { types += Type.getObjectType(owner); types += Type.getType(descriptor) }
                        override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) { types += Type.getObjectType(owner); types += Type.getMethodType(descriptor) }
                        override fun visitLdcInsn(value: Any?) { constant(value) }
                        override fun visitInvokeDynamicInsn(name: String, descriptor: String, bootstrapMethodHandle: Handle, vararg bootstrapMethodArguments: Any) {
                            types += Type.getMethodType(descriptor)
                            constant(bootstrapMethodHandle)
                            bootstrapMethodArguments.forEach(::constant)
                        }
                        override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) { types += Type.getType(descriptor) }
                        override fun visitTryCatchBlock(start: Label, end: Label, handler: Label?, type: String?) {
                            if (type != null) types += Type.getObjectType(type)
                        }
                        override fun visitEnd() { if (inline) types.forEach { checkType(it, "$api (public inline)") } }
                    }
                }
            }, ClassReader.SKIP_FRAMES)
        }
    }
}
