package top.katton.engine;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.lang.reflect.Method;

public final class UnsafeMethodAdvice {
    private UnsafeMethodAdvice() {
    }

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
    public static InjectionManager.EnterState onEnter(
            @Advice.Origin Method method,
            @Advice.This(optional = true) Object instance,
            @Advice.AllArguments Object[] args
    ) {
        InjectionManager.EnterControl enterControl = InjectionManager.createEnterControl();
        InjectionManager.dispatchBefore(method, instance, args, enterControl);

        if (!enterControl.getSkip()) {
            return null;
        }

        Object returnValue = enterControl.getOverrideReturn()
                ? enterControl.getReturnValue()
                : InjectionManager.defaultReturnValueFor(method.getReturnType());
        return new InjectionManager.EnterState(returnValue);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
            @Advice.Origin Method method,
            @Advice.This(optional = true) Object instance,
            @Advice.AllArguments Object[] args,
            @Advice.Enter InjectionManager.EnterState enterState,
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object result,
            @Advice.Thrown(readOnly = false) Throwable throwable
    ) {
        Object mutableResult = result;
        Throwable mutableThrowable = throwable;

        if (enterState != null) {
            mutableResult = enterState.getReturnValue();
            mutableThrowable = null;
        }

        InjectionManager.ExitControl exitControl = InjectionManager.createExitControl();
        InjectionManager.dispatchAfter(method, instance, args, mutableResult, mutableThrowable, exitControl);

        if (exitControl.getOverrideReturn()) {
            mutableResult = exitControl.getReturnValue();
            mutableThrowable = null;
        }

        result = mutableResult;
        throwable = mutableThrowable;
    }
}

