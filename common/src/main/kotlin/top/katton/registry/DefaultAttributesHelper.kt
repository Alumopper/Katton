package top.katton.registry

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.DefaultAttributes
import org.slf4j.LoggerFactory
import top.katton.util.ReflectUtil

/**
 * Helper for registering and hot-reloading entity default attributes.
 */
internal object DefaultAttributesHelper {

    private val logger = LoggerFactory.getLogger(DefaultAttributesHelper::class.java)

    @Volatile
    private var initialized = false

    @Synchronized
    fun ensureMutable() {
        if (initialized) return

        val result = ReflectUtil.getStatic(DefaultAttributes::class.java, "SUPPLIERS")
        if (result.isFailure) {
            logger.error("Failed to read DefaultAttributes.SUPPLIERS")
            return
}

        @Suppress("UNCHECKED_CAST")
        val immutableMap = result.getOrNull() as? Map<EntityType<out LivingEntity>, AttributeSupplier>
        if (immutableMap == null) {
            logger.error("DefaultAttributes.SUPPLIERS is null or wrong type")
            return
        }
        val mutableMap = HashMap(immutableMap)

        val setResult = ReflectUtil.setStaticFinal(DefaultAttributes::class.java, "SUPPLIERS", mutableMap)
        if (setResult.isSuccess) {
            initialized = true
            logger.info("DefaultAttributes SUPPLIERS map replaced with mutable HashMap")
        } else {
            logger.error("Failed to make DefaultAttributes SUPPLIERS mutable")
        }
    }

    fun register(
        entityType: EntityType<out LivingEntity>,
        supplier: AttributeSupplier
    ): Boolean {
        if (!initialized) ensureMutable()

        val result = ReflectUtil.getStatic(DefaultAttributes::class.java, "SUPPLIERS")
        if (result.isFailure) {
            logger.error("Failed to read DefaultAttributes.SUPPLIERS")
            return false
        }

        @Suppress("UNCHECKED_CAST")
        val map = result.getOrNull() as? MutableMap<EntityType<out LivingEntity>, AttributeSupplier>
            ?: return false
        map.put(entityType, supplier)
        return true
    }

    fun unregister(entityType: EntityType<*>): Boolean {
        if (!initialized) return false

        val result = ReflectUtil.getStatic(DefaultAttributes::class.java, "SUPPLIERS")
        if (result.isFailure) return false

        @Suppress("UNCHECKED_CAST")
        val map = result.getOrNull() as? MutableMap<EntityType<out LivingEntity>, AttributeSupplier>
            ?: return false
        return map.remove(entityType) != null
    }

    fun hasSupplier(entityType: EntityType<*>): Boolean {
        return DefaultAttributes.hasSupplier(entityType)
    }
}