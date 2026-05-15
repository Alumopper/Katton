package top.katton.platform

/**
 * Paper-specific dynamic registry hooks stub.
 * Paper does not support dynamic block/item registration.
 * All hooks are no-ops.
 */
object PaperDynamicRegistryHooks {
    @JvmStatic
    fun afterDynamicBlockRegistered(block: Any) {
        // No-op
    }
}
