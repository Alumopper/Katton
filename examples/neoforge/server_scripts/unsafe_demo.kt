import net.minecraft.network.chat.Component
import top.katton.api.dpcaller.tell
import top.katton.api.inject.*
import top.katton.registry.registerCommand
import kotlin.jvm.java

private class UnsafeDemoTarget {
    fun add(a: Int, b: Int): Int = a + b

    fun divide(a: Int, b: Int): Int = a / b

    fun multiply(a: Int, b: Int): Int = a * b

    fun addRedirect(a: Int, b: Int): Int = a + b

    fun multiplyTarget(a: Int, b: Int): Int = a * b
}

fun unsafeDemo() {
    registerCommand("unsafe_demo") {
        literal("test") {
            executes { ctx ->
                val method = UnsafeDemoTarget::class.java.getDeclaredMethod(
                    "add",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )

                val before = injectBefore(method) { call ->
                    tell("[unsafe_demo] before ${call.method.name}, args=${call.arguments.contentToString()}")
                }

                val after = injectAfter(method) { call, result, throwable ->
                    tell("[unsafe_demo] after ${call.method.name}, result=$result, throwable=${throwable?.message}")
                }

                val methodDivide = UnsafeDemoTarget::class.java.getDeclaredMethod(
                    "divide",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )

                val beforeMutate = injectBefore(methodDivide) { call ->
                    // argument rewrite: force denominator to 1 if input is 0
                    if ((call.arguments[1] as Int) == 0) {
                        call.setArgument(1, 1)
                    }
                }

                val beforeCancel = injectBefore(methodDivide) { call ->
                    // cancellable: if numerator is negative, skip original method body
                    if ((call.arguments[0] as Int) < 0) {
                        call.cancelWith(42)
                    }
                }

                val afterOverride = injectAfter(methodDivide) { call, result, throwable ->
                    // return override: multiply positive result by 10
                    if (throwable == null && result is Int && result > 0) {
                        call.setReturnValue(result * 10)
                    }
                }

                val methodMultiply = UnsafeDemoTarget::class.java.getDeclaredMethod(
                    "multiply",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )

                val replaceMultiply = replace(methodMultiply) { call ->
                    val a = call.arguments[0] as Int
                    val b = call.arguments[1] as Int
                    // replace: ignore original body and return custom value
                    a * b + 100
                }

                val sourceRedirect = UnsafeDemoTarget::class.java.getDeclaredMethod(
                    "addRedirect",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                val targetRedirect = UnsafeDemoTarget::class.java.getDeclaredMethod(
                    "multiplyTarget",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                val redirectAdd = redirect(sourceRedirect, targetRedirect)

                val target = UnsafeDemoTarget()
                val value = target.add(2, 3)
                val divA = target.divide(10, 0)
                val divB = target.divide(-5, 0)
                val mul = target.multiply(2, 3)
                val redirected = target.addRedirect(4, 5)

                rollbackUnsafe(before)
                rollbackUnsafe(after)
                rollbackUnsafe(beforeMutate)
                rollbackUnsafe(beforeCancel)
                rollbackUnsafe(afterOverride)
                rollbackUnsafe(replaceMultiply)
                rollbackUnsafe(redirectAdd)

                ctx.source.sendSuccess(
                    {
                        Component.literal(
                            "[unsafe_demo] add=$value, divA=$divA, divB=$divB, replaceMul=$mul, redirect=$redirected"
                        )
                    },
                    false
                )
                1
            }
        }
    }
}

@Suppress("unused")
private val unsafeDemo = unsafeDemo()

