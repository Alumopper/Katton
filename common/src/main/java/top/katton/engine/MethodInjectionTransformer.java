package top.katton.engine;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

final class MethodInjectionTransformer {
    private static final Type INJECTION_MANAGER_TYPE = Type.getObjectType("top/katton/engine/InjectionManager");
    private static final Type OBJECT_TYPE = Type.getType(Object.class);
    private static final Type OBJECT_ARRAY_TYPE = Type.getType(Object[].class);
    private static final Type THROWABLE_TYPE = Type.getType(Throwable.class);
    private static final Type ENTER_RESULT_TYPE = Type.getObjectType("top/katton/engine/InjectionManager$MethodEnterResult");
    private static final Type EXIT_RESULT_TYPE = Type.getObjectType("top/katton/engine/InjectionManager$MethodExitResult");
    private static final Type ENTER_STATE_TYPE = Type.getObjectType("top/katton/engine/InjectionManager$EnterState");
    private static final Method DISPATCH_ENTER = new Method(
            "dispatchMethodEnterByKey",
            "(Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)Ltop/katton/engine/InjectionManager$MethodEnterResult;"
    );
    private static final Method DISPATCH_EXIT = new Method(
            "dispatchMethodExitByKey",
            "(Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Throwable;Ltop/katton/engine/InjectionManager$EnterState;)Ltop/katton/engine/InjectionManager$MethodExitResult;"
    );
    private static final Method GET_ENTER_ARGUMENTS = new Method(
            "methodEnterArguments",
            "(Ltop/katton/engine/InjectionManager$MethodEnterResult;)[Ljava/lang/Object;"
    );
    private static final Method GET_ENTER_STATE = new Method(
            "methodEnterState",
            "(Ltop/katton/engine/InjectionManager$MethodEnterResult;)Ltop/katton/engine/InjectionManager$EnterState;"
    );
    private static final Method GET_EXIT_RESULT = new Method(
            "methodExitResult",
            "(Ltop/katton/engine/InjectionManager$MethodExitResult;)Ljava/lang/Object;"
    );
    private static final Method GET_EXIT_THROWABLE = new Method(
            "methodExitThrowable",
            "(Ltop/katton/engine/InjectionManager$MethodExitResult;)Ljava/lang/Throwable;"
    );

    private MethodInjectionTransformer() {
    }

    static byte[] transform(byte[] originalBytes, Collection<java.lang.reflect.Method> methods) {
        Map<String, String> methodKeys = new HashMap<>();
        for (java.lang.reflect.Method method : methods) {
            methodKeys.put(method.getName() + Type.getMethodDescriptor(method), InjectionManager.methodKeyForVisitor(method));
        }

        ClassReader classReader = new ClassReader(originalBytes);
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, classWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                String methodKey = methodKeys.get(name + descriptor);
                if (methodKey == null) {
                    return methodVisitor;
                }
                return new InjectingMethodVisitor(methodVisitor, access, name, descriptor, methodKey);
            }
        };
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
        return classWriter.toByteArray();
    }

    private static final class InjectingMethodVisitor extends AdviceAdapter {
        private final String methodKey;
        private final Type[] argumentTypes;
        private final Type returnType;
        private final boolean isStaticMethod;
        private boolean syntheticExitEmission;
        private final Label skipOriginal = new Label();
        private int argsLocal;
        private int enterResultLocal;
        private int enterStateLocal;
        private int exitResultLocal;
        private int resultLocal;
        private int throwableLocal;

        private InjectingMethodVisitor(MethodVisitor methodVisitor, int access, String name, String descriptor, String methodKey) {
            super(Opcodes.ASM9, methodVisitor, access, name, descriptor);
            this.methodKey = methodKey;
            this.argumentTypes = Type.getArgumentTypes(descriptor);
            this.returnType = Type.getReturnType(descriptor);
            this.isStaticMethod = (access & Opcodes.ACC_STATIC) != 0;
        }

        @Override
        protected void onMethodEnter() {
            argsLocal = newLocal(OBJECT_ARRAY_TYPE);
            enterResultLocal = newLocal(ENTER_RESULT_TYPE);
            enterStateLocal = newLocal(ENTER_STATE_TYPE);
            exitResultLocal = newLocal(EXIT_RESULT_TYPE);
            resultLocal = newLocal(OBJECT_TYPE);
            throwableLocal = newLocal(THROWABLE_TYPE);

            push(argumentTypes.length);
            newArray(OBJECT_TYPE);
            storeLocal(argsLocal);

            for (int index = 0; index < argumentTypes.length; index++) {
                loadLocal(argsLocal);
                push(index);
                loadArg(index);
                box(argumentTypes[index]);
                arrayStore(OBJECT_TYPE);
            }

            visitLdcInsn(methodKey);
            if (isStaticMethod) {
                visitInsn(ACONST_NULL);
            } else {
                loadThis();
            }
            loadLocal(argsLocal);
            invokeStatic(INJECTION_MANAGER_TYPE, DISPATCH_ENTER);
            storeLocal(enterResultLocal);

            loadLocal(enterResultLocal);
            invokeStatic(INJECTION_MANAGER_TYPE, GET_ENTER_ARGUMENTS);
            storeLocal(argsLocal);

            for (int index = 0; index < argumentTypes.length; index++) {
                loadLocal(argsLocal);
                push(index);
                arrayLoad(OBJECT_TYPE);
                unbox(argumentTypes[index]);
                storeArg(index);
            }

            loadLocal(enterResultLocal);
            invokeStatic(INJECTION_MANAGER_TYPE, GET_ENTER_STATE);
            storeLocal(enterStateLocal);

            loadLocal(enterStateLocal);
            ifNonNull(skipOriginal);
        }

        @Override
        protected void onMethodExit(int opcode) {
            if (syntheticExitEmission) {
                return;
            }

            if (opcode == ATHROW) {
                storeLocal(throwableLocal);
                visitInsn(ACONST_NULL);
                storeLocal(resultLocal);
            } else {
                if (opcode == RETURN) {
                    visitInsn(ACONST_NULL);
                    storeLocal(resultLocal);
                } else {
                    box(returnType);
                    storeLocal(resultLocal);
                }
                visitInsn(ACONST_NULL);
                storeLocal(throwableLocal);
            }

            emitExitAndTerminate();
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            mark(skipOriginal);
            visitInsn(ACONST_NULL);
            storeLocal(resultLocal);
            visitInsn(ACONST_NULL);
            storeLocal(throwableLocal);
            emitExitAndTerminate();
            super.visitMaxs(maxStack, maxLocals);
        }

        private void emitExitAndTerminate() {
            syntheticExitEmission = true;

            visitLdcInsn(methodKey);
            if (isStaticMethod) {
                visitInsn(ACONST_NULL);
            } else {
                loadThis();
            }
            loadLocal(argsLocal);
            loadLocal(resultLocal);
            loadLocal(throwableLocal);
            loadLocal(enterStateLocal);
            invokeStatic(INJECTION_MANAGER_TYPE, DISPATCH_EXIT);
            storeLocal(exitResultLocal);

            loadLocal(exitResultLocal);
            invokeStatic(INJECTION_MANAGER_TYPE, GET_EXIT_THROWABLE);
            storeLocal(throwableLocal);

            Label noThrowable = new Label();
            loadLocal(throwableLocal);
            ifNull(noThrowable);
            loadLocal(throwableLocal);
            throwException();

            visitLabel(noThrowable);
            if (returnType.equals(Type.VOID_TYPE)) {
                visitInsn(RETURN);
                return;
            }

            loadLocal(exitResultLocal);
            invokeStatic(INJECTION_MANAGER_TYPE, GET_EXIT_RESULT);
            unbox(returnType);
            visitInsn(returnType.getOpcode(IRETURN));
        }
    }
}