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
 * %en
 * Configuration for modifying default attributes of an existing
 * [EntityType] (vanilla or modded).
 *
 * Mirrors the property surface of [top.katton.registry.KattonEntityProperties]
 * but applies to already-registered entity types via
 * [top.katton.registry.DefaultAttributesHelper].
 *
 * %zh
 * 用于修改现有 EntityType 默认属性的配置对象（原版或模组添加的实体都适用）。
 * 它的属性面与 [top.katton.registry.KattonEntityProperties] 对应，但作用对象是已经注册的实体类型，
 * 并通过 [top.katton.registry.DefaultAttributesHelper] 生效。
 * @property entityId
 * %en The identifier of the entity type being modified.
 * %zh 要修改的实体类型标识符。
 */
class EntityTypeModificationConfig(val entityId: Identifier) {
    private val overrides = linkedMapOf<Holder<Attribute>, Double>()

    /**
 * %en
 * Overrides the base value of an arbitrary attribute.
 *
 * %zh
 * 覆盖任意属性的基础值。
 */
    fun attribute(attribute: Holder<Attribute>, value: Double): EntityTypeModificationConfig {
        overrides[attribute] = value
        return this
    }

    /**
 * %en
 *  Overrides max health (`generic.max_health`).
 *
 * %zh
 * 覆盖最大生命值（`generic.max_health`）。
 */
    fun maxHealth(value: Double): EntityTypeModificationConfig = attribute(Attributes.MAX_HEALTH, value)

    /**
 * %en
 *  Overrides movement speed (`generic.movement_speed`).
 *
 * %zh
 * 覆盖移动速度（`generic.movement_speed`）。
 */
    fun movementSpeed(value: Double): EntityTypeModificationConfig = attribute(Attributes.MOVEMENT_SPEED, value)

    /**
 * %en
 *  Overrides knockback resistance (`generic.knockback_resistance`).
 *
 * %zh
 * 覆盖击退抗性（`generic.knockback_resistance`）。
 */
    fun knockbackResistance(value: Double): EntityTypeModificationConfig = attribute(Attributes.KNOCKBACK_RESISTANCE, value)

    /**
 * %en
 *  Overrides attack damage (`generic.attack_damage`).
 *
 * %zh
 * 覆盖攻击伤害（`generic.attack_damage`）。
 */
    fun attackDamage(value: Double): EntityTypeModificationConfig = attribute(Attributes.ATTACK_DAMAGE, value)

    /**
 * %en
 *  Overrides attack speed (`generic.attack_speed`).
 *
 * %zh
 * 覆盖攻击速度（`generic.attack_speed`）。
 */
    fun attackSpeed(value: Double): EntityTypeModificationConfig = attribute(Attributes.ATTACK_SPEED, value)

    /**
 * %en
 *  Overrides armor (`generic.armor`).
 *
 * %zh
 * 覆盖护甲（`generic.armor`）。
 */
    fun armor(value: Double): EntityTypeModificationConfig = attribute(Attributes.ARMOR, value)

    /**
 * %en
 *  Overrides armor toughness (`generic.armor_toughness`).
 *
 * %zh
 * 覆盖护甲韧性（`generic.armor_toughness`）。
 */
    fun armorToughness(value: Double): EntityTypeModificationConfig = attribute(Attributes.ARMOR_TOUGHNESS, value)

    /**
 * %en
 *  Overrides follow range (`generic.follow_range`).
 *
 * %zh
 * 覆盖追踪范围（`generic.follow_range`）。
 */
    fun followRange(value: Double): EntityTypeModificationConfig = attribute(Attributes.FOLLOW_RANGE, value)

    /**
 * %en
 *  Overrides luck (`generic.luck`).
 *
 * %zh
 * 覆盖幸运值（`generic.luck`）。
 */
    fun luck(value: Double): EntityTypeModificationConfig = attribute(Attributes.LUCK, value)

    internal fun overridesSnapshot(): Map<Holder<Attribute>, Double> = overrides.toMap()
}

private val LOGGER = LoggerFactory.getLogger("top.katton.api.mod.KattonEntityTypeModificationApi")

/**
 * %en
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
 * %zh
 * 修改现有实体类型的默认属性。
 * 如果该实体类型已经注册了默认属性供应器，就以现有供应器为基础，再逐项覆盖属性基础值。
 * 如果没有默认供应器，则会使用 [LivingEntity.createLivingAttributes] 构建一个新的基础供应器。
 * 该过程通过 [DefaultAttributesHelper] 生效（会反射 `DefaultAttributes.SUPPLIERS`）。
 * 它既适用于原版实体类型，也适用于以相同机制注册属性的模组实体。
 * @param entityId
 * %en Entity type identifier (e.g. `"minecraft:zombie"`).
 * %zh 实体类型标识符（例如 "minecraft:zombie"）。
 * @param configure
 * %en Configuration lambda.
 * %zh 配置 lambda。
 * @return
 * %en when the supplier was replaced; `false` when the entity type
 * %zh 当供应器已被替换时返回 `true`；当实体类型无法解析或底层注册表不是生物类型时返回 `false`。
 * %en
 *         could not be resolved or the underlying registry is non-living.
 * @example
 * %en
 * ```kotlin
 * modifyEntityType("minecraft:zombie") {
 *     maxHealth(40.0)
 *     attackDamage(8.0)
 *     movementSpeed(0.32)
 * }
 * ```
 * %zh 示例代码见英文部分。
 */
@ApiStatus.Experimental
fun modifyEntityType(entityId: String, configure: EntityTypeModificationConfig.() -> Unit): Boolean {
    return modifyEntityType(id(entityId), configure)
}

/**
 * %en
 * Identifier overload of [modifyEntityType].
 *
 * %zh
 * `modifyEntityType` 的 Identifier 重载。
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
 * %en
 * Builds a fresh [AttributeSupplier.Builder] seeded with every attribute that
 * the existing supplier exposes, except for the ones that the caller is going
 * to override.
 *
 * Reads the supplier through [AttributeSupplier.getValue] for every override
 * key and through reflection-free public APIs for everything else, so this
 * stays compatible across MC patch versions even if internal field names move.
 *
 * %zh
 * 构建一个新的 [AttributeSupplier.Builder]，其初始内容包含现有供应器暴露出的所有属性，
 * 但会排除调用方准备覆盖的那些属性。
 * 对每个覆盖键，会通过 [AttributeSupplier.getValue] 读取供应器；其余内容则通过无反射的公共 API 读取，
 * 因此即使内部字段名在 MC 补丁版本中变动，也能保持兼容。
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
