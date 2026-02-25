package top.katton.engine;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.lang.reflect.Method;

/**
 * Universal method advice for unsafe runtime injections.
 *
 * Supports:
 * - before/after dispatch
 * - cancellable execution (skip original body)
 * - return value override
 * - argument mutation (via shared args array)
 */
public final class UnsafeMethodAdvice {
    private UnsafeMethodAdvice() {
    }

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class, suppress = Throwable.class)
    public static UnsafeInjectionManager.EnterState onEnter(
            @Advice.Origin Method method,
            @Advice.This(optional = true) Object instance,
            @Advice.AllArguments Object[] args
    ) {
        UnsafeInjectionManager.EnterControl enterControl = UnsafeInjectionManager.createEnterControl();
        UnsafeInjectionManager.dispatchBefore(method, instance, args, enterControl);

        if (!enterControl.getSkip()) {
            return null;
        }

        Object returnValue = enterControl.getOverrideReturn()
                ? enterControl.getReturnValue()
                : UnsafeInjectionManager.defaultReturnValueFor(method.getReturnType());
        return new UnsafeInjectionManager.EnterState(returnValue);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
            @Advice.Origin Method method,
            @Advice.This(optional = true) Object instance,
            @Advice.AllArguments Object[] args,
            @Advice.Enter UnsafeInjectionManager.EnterState enterState,
            @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object result,
            @Advice.Thrown(readOnly = false) Throwable throwable
    ) {
        Object mutableResult = result;
        Throwable mutableThrowable = throwable;

        if (enterState != null) {
            mutableResult = enterState.getReturnValue();
            mutableThrowable = null;
        }

        UnsafeInjectionManager.ExitControl exitControl = UnsafeInjectionManager.createExitControl();
        UnsafeInjectionManager.dispatchAfter(method, instance, args, mutableResult, mutableThrowable, exitControl);

        if (exitControl.getOverrideReturn()) {
            mutableResult = exitControl.getReturnValue();
            mutableThrowable = null;
        }

        result = mutableResult;
        throwable = mutableThrowable;
    }
}

