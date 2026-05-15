package top.katton.platform

/**
 * Paper-specific entity attribute hooks stub.
 * Paper is server-only and does not support item/block/entity registration,
 * so all attribute hooks are no-ops.
 */
object PaperEntityAttributeHooks {
    @JvmStatic
    fun registerAttributesGlobal(entityType: Any, attributes: Any) {
        // No-op: Paper does not support custom entity registration
    }

    @JvmStatic
    fun registerAttributesReloadable(entityType: Any, attributes: Any) {
        // No-op: Paper does not support custom entity registration
    }
}
