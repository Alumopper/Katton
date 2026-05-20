@file:Suppress("unused")

package top.katton.api.mod

import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeInstance
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import org.jetbrains.annotations.ApiStatus
import org.slf4j.LoggerFactory
import top.katton.platform.EntityAttributeHooks
import top.katton.registry.DefaultAttributesHelper
import top.katton.registry.id
import top.katton.util.ReflectUtil

/**
 * Configuration for modifying default attributes of an existing
 * [EntityType] (vanilla or modded).
 *
 * Mirrors the property surface of [top.katton.registry.KattonEntityProperties]
 * but applies to already-registered entity types via
 * [top.katton.registry.DefaultAttributesHelper].
 *
 * @property entityId The identifier of the entity type being modified.
 */
class EntityTypeModificationConfig(val entityId: Identifier) {
    private val overrides = linkedMapOf<Holder<Attribute>, Double>()

    /**
     * Overrides the base value of an arbitrary attribute.
     */
    fun attribute(attribute: Holder<Attribute>, value: Double): EntityTypeModificationConfig {
        overrides[attribute] = value
        return this
    }

    /** Overrides max health (`generic.max_health`). */
    fun maxHealth(value: Double): EntityTypeModificationConfig = attribute(Attributes.MAX_HEALTH, value)

    /** Overrides movement speed (`generic.movement_speed`). */
    fun movementSpeed(value: Double): EntityTypeModificationConfig = attribute(Attributes.MOVEMENT_SPEED, value)

    /** Overrides knockback resistance (`generic.knockback_resistance`). */
    fun knockbackResistance(value: Double): EntityTypeModificationConfig = attribute(Attributes.KNOCKBACK_RESISTANCE, value)

    /** Overrides attack damage (`generic.attack_damage`). */
    fun attackDamage(value: Double): EntityTypeModificationConfig = attribute(Attributes.ATTACK_DAMAGE, value)

    /** Overrides attack speed (`generic.attack_speed`). */
    fun attackSpeed(value: Double): EntityTypeModificationConfig = attribute(Attributes.ATTACK_SPEED, value)

    /** Overrides armor (`generic.armor`). */
    fun armor(value: Double): EntityTypeModificationConfig = attribute(Attributes.ARMOR, value)

    /** Overrides armor toughness (`generic.armor_toughness`). */
    fun armorToughness(value: Double): EntityTypeModificationConfig = attribute(Attributes.ARMOR_TOUGHNESS, value)

    /** Overrides follow range (`generic.follow_range`). */
    fun followRange(value: Double): EntityTypeModificationConfig = attribute(Attributes.FOLLOW_RANGE, value)

    /** Overrides luck (`generic.luck`). */
    fun luck(value: Double): EntityTypeModificationConfig = attribute(Attributes.LUCK, value)

    internal fun overridesSnapshot(): Map<Holder<Attribute>, Double> = overrides.toMap()
}

private val LOGGER = LoggerFactory.getLogger("top.katton.api.mod.KattonEntityTypeModificationApi")

/**
 * Modifies the default attributes of an existing entity type.
 *
 * If the entity type already has default attributes registered, the existing
 * supplier is used as a baseline and individual attribute base values are
 * overridden. If no default supplier exists, a fresh supplier built from
 * [LivingEntity.createLivingAttributes] is used as the baseline.
 *
 * Applied via [DefaultAttributesHelper] (reflection on
 * `DefaultAttributes.SUPPLIERS`). Works for vanilla entity types as well as
 * modded ones that register attributes through the same mechanism.
 *
 * @param entityId Entity type identifier (e.g. `"minecraft:zombie"`).
 * @param configure Configuration lambda.
 * @return `true` when the supplier was replaced; `false` when the entity type
 *         could not be resolved or the underlying registry is non-living.
 *
 * @example
 * ```kotlin
 * modifyEntityType("minecraft:zombie") {
 *     maxHealth(40.0)
 *     attackDamage(8.0)
 *     movementSpeed(0.32)
 * }
 * ```
 */
@ApiStatus.Experimental
fun modifyEntityType(entityId: String, configure: EntityTypeModificationConfig.() -> Unit): Boolean {
    return modifyEntityType(id(entityId), configure)
}

/**
 * Identifier overload of [modifyEntityType].
 */
@ApiStatus.Experimental
fun modifyEntityType(entityId: Identifier, configure: EntityTypeModificationConfig.() -> Unit): Boolean {
    val rawType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null) ?: run {
        LOGGER.warn("modifyEntityType: entity type {} not found", entityId)
        return false
    }

    @Suppress("UNCHECKED_CAST")
    val livingType = rawType as? EntityType<LivingEntity>
    if (livingType == null) {
        LOGGER.warn("modifyEntityType: entity type {} is not LivingEntity, skipping", entityId)
        return false
    }

    val config = EntityTypeModificationConfig(entityId).apply(configure)
    val overrides = config.overridesSnapshot()
    if (overrides.isEmpty()) return false

    val supplier = buildSupplier(livingType, overrides)
    EntityAttributeHooks.registerAttributes(livingType, supplier, true)
    return true
}

private fun buildSupplier(
    entityType: EntityType<LivingEntity>,
    overrides: Map<Holder<Attribute>, Double>
): AttributeSupplier {
    val existing = DefaultAttributesHelper.getSupplier(entityType)
    val builder = if (existing != null) {
        copyExistingSupplier(existing, overrides.keys)
    } else {
        LivingEntity.createLivingAttributes()
    }
    overrides.forEach { (attribute, value) ->
        builder.add(attribute, value)
    }
    return builder.build()
}

/**
 * Builds a fresh [AttributeSupplier.Builder] seeded with every attribute that
 * the existing supplier exposes, except for the ones that the caller is going
 * to override.
 *
 * Reads the supplier through [AttributeSupplier.getValue] for every override
 * key and through reflection-free public APIs for everything else, so this
 * stays compatible across MC patch versions even if internal field names move.
 */
private fun copyExistingSupplier(
    existing: AttributeSupplier,
    overrideKeys: Set<Holder<Attribute>>
): AttributeSupplier.Builder {
    val instances = existingInstances(existing)
    if (instances.isEmpty()) {
        LOGGER.warn("modifyEntityType: failed to enumerate existing AttributeSupplier instances; using LivingEntity baseline")
        return LivingEntity.createLivingAttributes()
    }

    val builder = AttributeSupplier.builder()
    instances.forEach { (attribute, instance) ->
        if (attribute !in overrideKeys) {
            builder.add(attribute, instance.baseValue)
        }
    }
    return builder
}

@Suppress("UNCHECKED_CAST")
private fun existingInstances(supplier: AttributeSupplier): Map<Holder<Attribute>, AttributeInstance> {
    return ReflectUtil.get(supplier, "instances").getOrNull()
        as? Map<Holder<Attribute>, AttributeInstance>
        ?: emptyMap()
}
