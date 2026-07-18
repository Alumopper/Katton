@file:Suppress("unused")

package top.katton.api.dpcaller

import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.commands.arguments.selector.EntitySelector
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.commands.SpreadPlayersCommand
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.level.Level
import net.minecraft.world.level.entity.EntityTypeTest
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import top.katton.api.LOGGER
import top.katton.api.requireServer
import top.katton.util.EventHandler
import top.katton.util.ScriptExecutionContext
import java.util.*
import kotlin.jvm.optionals.getOrNull

/*? if mc_26_2 {*/
private fun isPlayerEntityType(type: EntityType<*>): Boolean = type == EntityTypes.PLAYER
/*?} else {*/
/*private fun isPlayerEntityType(type: EntityType<*>): Boolean = type == EntityType.PLAYER*/
/*?}*/

/*? if mc_26_2 {*/
private fun loadEntityRecursiveCompat(
    tag: CompoundTag,
    level: Level,
    reason: EntitySpawnReason,
    processor: EntityProcessor
): Entity? = EntityType.loadEntityRecursive(tag, level, EntitySpawnRequest(reason, false), processor)
/*?} else {*/
/*private fun loadEntityRecursiveCompat(
    tag: CompoundTag,
    level: Level,
    reason: EntitySpawnReason,
    processor: EntityProcessor
): Entity? = EntityType.loadEntityRecursive(tag, level, reason, processor)*/
/*?}*/

/**
 * %en
 * Get/Set the NBT data of an Entity.
 *
 * %zh
 * 获取或设置 Entity 的 NBT 数据。
 */
var Entity.nbt: CompoundTag
    get() = getEntityNbt(this)
    set(value) {
        setEntityNbt(this, value)
    }

//TODO: Entity tick event

/**
 * %en
 * Collection of all entities across all server levels.
 *
 * %zh
 * 跨所有服务端关卡的实体集合。
 * @property server
 * %en The MinecraftServer instance
 * %zh MinecraftServer 实例。
 */
class KattonServerEntityCollection(
    private val server: MinecraftServer
) {
    /**
 * %en
 * All entities across all levels.
 *
 * %zh
 * 所有关卡中的全部实体。
 */
    val all
        get() = server.allLevels.flatMap { it.allEntities }

    /**
 * %en
 * Get entity collection for a specific level.
 *
 * %zh
 * 获取指定关卡中的实体集合。
 */
    operator fun get(level: ServerLevel): KattonLevelEntityCollection {
        return KattonLevelEntityCollection(level)
    }

    /**
 * %en
 * Find an entity by UUID across all levels.
 *
 * %zh
 * 在所有关卡中按 UUID 查找实体。
 */
    operator fun get(uuid: UUID): Entity? {
        return server.allLevels.map { it.getEntity(uuid) }.firstOrNull()
    }
}

/**
 * %en
 * Collection of entities within a specific level.
 *
 * %zh
 * 指定关卡中的实体集合。
 * @property level
 * %en The ServerLevel containing the entities
 * %zh 包含这些实体的 ServerLevel。
 */
class KattonLevelEntityCollection(
    val level: ServerLevel
) : Iterable<Entity> by level.allEntities {
    /**
 * %en
 * Get entities matching a type test and predicate.
 *
 * %zh
 * 获取与类型测试和谓词匹配的实体。
 */
    operator fun <T : Entity> get(
        entityTypeTest: EntityTypeTest<Entity, T>,
        predicate: (T) -> Boolean = { true }
    ): List<T> = level.getEntities(entityTypeTest, predicate)

    /**
 * %en
 * Get entities within an AABB matching a type test and predicate.
 *
 * %zh
 * 获取 AABB 内与类型测试和谓词匹配的实体。
 */
    operator fun <T : Entity> get(
        entityTypeTest: EntityTypeTest<Entity, T>,
        aabb: AABB,
        predicate: (T) -> Boolean = { true }
    ): List<T> = level.getEntities(entityTypeTest, aabb, predicate)

    /**
 * %en
 * Get entities using an entity selector.
 *
 * %zh
 * 使用实体选择器获取实体。
 */
    operator fun get(selector: EntitySelector): List<Entity> {
        return findEntities(level, selector)
    }

    /**
 * %en
 * Find an entity by UUID in this level.
 *
 * %zh
 * 在当前关卡中按 UUID 查找实体。
 */
    operator fun get(uuid: UUID): Entity? {
        return level.getEntity(uuid)
    }
}

/**
 * %en
 * Map-like access to a living entity's attribute values.
 *
 * %zh
 * 用类似 Map 的方式访问生物实体的属性值。
 * @property entity
 * %en The LivingEntity whose attributes are being accessed
 * %zh 要访问属性的 LivingEntity。
 */
class KattonEntityAttributeValueMap(
    val entity: LivingEntity
) {
    /**
 * %en
 * Check if the entity has a given attribute.
 *
 * %zh
 * 检查指定实体是否具有给定属性。
 */
    fun contains(holder: Holder<Attribute>): Boolean {
        return entity.getAttribute(holder) != null
    }

    /**
 * %en
 * Get the current value of an attribute.
 *
 * %zh
 * 获取属性的当前值
 */
    operator fun get(holder: Holder<Attribute>): Double? {
        return entity.getAttributeValue(holder)
    }

    /**
 * %en
 * Set the base value of an attribute and optionally add modifiers.
 *
 * %zh
 * 设置属性的基础值，并可选添加修饰器。
 */
    fun set(holder: Holder<Attribute>, value: Double, vararg modifiers: AttributeModifier) {
        entity.getAttribute(holder)?.baseValue = value
        modifiers.forEach {
            entity.getAttribute(holder)?.addTransientModifier(it)
        }
    }
}

/**
 * %en
 * Access to a living entity's attribute values.
 *
 * %zh
 * 访问生物实体的属性值。
 */
val LivingEntity.attributeValues
    get() = KattonEntityAttributeValueMap(this)

/**
 * %en
 * Get an attribute value from a LivingEntity.
 *
 * %zh
 * 从 LivingEntity 获取属性值。
 * @param entity
 * %en the entity
 * %zh 实体
 * @param attribute
 * %en holder of the attribute to read
 * %zh 要读取的属性持有者。
 * @return
 * %en attribute value
 * %zh 返回属性值
 */
fun getAttribute(entity: LivingEntity, attribute: Holder<Attribute>): Double {
    return entity.getAttributeValue(attribute)
}


/**
 * %en
 * Check if a LivingEntity has a given attribute.
 *
 * %zh
 * 检查 LivingEntity 是否具有给定属性。
 * @param entity
 * %en the entity
 * %zh 实体
 * @param attribute
 * %en holder of the attribute to check
 * %zh 要检查的属性持有者。
 * @return
 * %en if attribute present
 * %zh 如果属性存在则返回 true。
 */
fun hasAttribute(entity: LivingEntity, attribute: Holder<Attribute>): Boolean {
    return entity.getAttribute(attribute) != null
}


/**
 * %en
 * Get base attribute value from a LivingEntity.
 *
 * %zh
 * 从 LivingEntity 获取基础属性值。
 * @param entity
 * %en the entity
 * %zh 实体
 * @param attribute
 * %en holder of the attribute to check
 * %zh 要读取的属性持有者
 * @return
 * %en base attribute value; null if attribute not present
 * %zh 属性值；如果属性不存在则返回 null。
 */
fun getBaseAttribute(entity: LivingEntity, attribute: Holder<Attribute>): Double? {
    return entity.getAttribute(attribute)?.baseValue
}


/**
 * %en
 * Set the base attribute value for a LivingEntity.
 *
 * %zh
 * 设置 LivingEntity 的基础属性值。
 * @param entity
 * %en the entity
 * %zh 实体。
 * @param attribute
 * %en attribute holder to set
 * %zh 要设置的属性持有者。
 * @param value
 * %en new base value
 * %zh 新的基础值。
 * @return
 * %en if changed, false otherwise
 * %zh 如果发生变化则返回 true，否则返回 false。
 */
fun setBaseAttribute(entity: LivingEntity, attribute: Holder<Attribute>, value: Double): Boolean {
    val instance = entity.getAttribute(attribute) ?: return false
    val changed = instance.baseValue != value
    instance.baseValue = value
    return changed
}


/**
 * %en
 * Add a transient attribute modifier to an entity.
 *
 * %zh
 * 向实体添加临时属性修饰器。
 * @param entity
 * %en the entity
 * %zh 实体。
 * @param attribute
 * %en attribute holder to modify
 * %zh 要修改的属性持有者。
 * @param modifier
 * %en AttributeModifier to add
 * %zh 要添加的 AttributeModifier。
 */
fun addAttributeModify(entity: LivingEntity, attribute: Holder<Attribute>, modifier: AttributeModifier) {
    entity.getAttribute(attribute)?.addTransientModifier(modifier)
}


/**
 * %en
 * Remove an attribute modifier from an entity.
 *
 * %zh
 * 从实体移除属性修饰器。
 * @param entity
 * %en the entity
 * %zh 实体。
 * @param attribute
 * %en attribute holder to modify
 * %zh 要修改的属性持有者。
 * @param modifier
 * %en AttributeModifier to remove
 * %zh 要移除的 AttributeModifier。
 */
fun removeAttributeModify(entity: LivingEntity, attribute: Holder<Attribute>, modifier: AttributeModifier) {
    entity.getAttribute(attribute)?.removeModifier(modifier)
}


/**
 * %en
 * Damage an entity by an amount using generic damage.
 *
 * %zh
 * 使用通用伤害按指定数值伤害实体。
 * @param entity
 * %en target entity
 * %zh 目标实体。
 * @param amount
 * %en damage amount
 * %zh 伤害值。
 */
fun damage(entity: Entity, amount: Float) {
    entity.hurtServer(
        requireServer().overworld(),
        requireServer().overworld().damageSources().source(DamageTypes.GENERIC),
        amount
    )
}


/**
 * %en
 * Damage a target entity from an attacker using a damage type key.
 *
 * %zh
 * 使用伤害类型键，让攻击者对目标实体造成伤害。
 * @param target
 * %en the entity to damage
 * %zh 要伤害的实体。
 * @param amount
 * %en damage amount
 * %zh 伤害值。
 * @param attacker
 * %en the source entity causing damage
 * %zh 造成伤害的来源实体。
 * @param damageType
 * %en resource key of the DamageType (default GENERIC)
 * %zh DamageType 的资源键（默认 GENERIC）。
 */
fun damage(target: Entity, amount: Float, attacker: Entity, damageType: ResourceKey<DamageType> = DamageTypes.GENERIC) {
    val type = requireServer().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(damageType)
    if(type.isEmpty) {
        LOGGER.warn("DamageType $damageType not found")
        return
    }
    val damageType = type.get()
    target.hurtServer(
        requireServer().overworld(),
        DamageSource(damageType, attacker, attacker),
        amount
    )
}


/**
 * %en
 * Damage a target entity from an attacker using a DamageType instance.
 *
 * %zh
 * 使用 DamageType 实例，让攻击者对目标实体造成伤害。
 * @param target
 * %en the entity to damage
 * %zh 要伤害的实体。
 * @param amount
 * %en damage amount
 * %zh 伤害值。
 * @param attacker
 * %en the source entity causing damage
 * %zh 造成伤害的来源实体。
 * @param damageType
 * %en DamageType instance to apply
 * %zh 要应用的 DamageType 实例。
 */
fun damage(target: Entity, amount: Float, attacker: Entity, damageType: DamageType) {
    target.hurtServer(
        requireServer().overworld(),
        DamageSource(Holder.direct(damageType), attacker, attacker),
        amount
    )
}



/**
 * %en
 * Damage a target entity from a position using a damage type key.
 *
 * %zh
 * 使用伤害类型键，从指定位置对目标实体造成伤害。
 * @param target
 * %en entity to damage
 * %zh 要伤害的实体。
 * @param amount
 * %en damage amount
 * %zh 伤害值。
 * @param pos
 * %en position of damage source
 * %zh 伤害来源位置。
 * @param damageType
 * %en resource key of the DamageType (default GENERIC)
 * %zh DamageType 的资源键（默认 GENERIC）。
 */
fun damage(target: Entity, amount: Float, pos: Vec3, damageType: ResourceKey<DamageType> = DamageTypes.GENERIC) {
    val type = requireServer().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(damageType)
    if(type.isEmpty) {
        LOGGER.warn("DamageType $damageType not found")
        return
    }
    val damageType = type.get()
    target.hurtServer(
        requireServer().overworld(),
        DamageSource(damageType, pos),
        amount
    )
}


/**
 * %en
 * Damage a target entity from a position using a DamageType instance.
 *
 * %zh
 * 使用 DamageType 实例，从指定位置对目标实体造成伤害。
 * @param target
 * %en entity to damage
 * %zh 要伤害的实体。
 * @param amount
 * %en damage amount
 * %zh 伤害值。
 * @param pos
 * %en position of damage source
 * %zh 伤害来源位置。
 * @param damageType
 * %en DamageType instance to apply
 * %zh 要应用的 DamageType 实例。
 */
fun damage(target: Entity, amount: Float, pos: Vec3, damageType: DamageType) {
    target.hurtServer(
        requireServer().overworld(),
        DamageSource(Holder.direct(damageType), pos),
        amount
    )
}


/**
 * %en
 * Add a mob effect to a LivingEntity.
 *
 * %zh
 * 向 LivingEntity 添加状态效果。
 * @param entity
 * %en target entity
 * %zh 目标实体。
 * @param effect
 * %en holder of the MobEffect to apply
 * %zh 要应用的 MobEffect Holder。
 * @param duration
 * %en effect duration in ticks (default 600)
 * %zh 效果持续时间，单位为 tick，默认 600。
 * @param amplifier
 * %en effect amplifier level (default 0)
 * %zh 效果放大等级，默认 0。
 * @param showParticles
 * %en whether to show particles
 * %zh 是否显示粒子。
 * @param ambient
 * %en whether effect is ambient
 * %zh 是否为环境效果。
 */
fun addEffect(entity: LivingEntity, effect: Holder<MobEffect>, duration: Int = 600, amplifier: Int = 0, showParticles: Boolean = true, ambient: Boolean = false) {
    entity.addEffect(MobEffectInstance(effect, duration, amplifier, ambient, showParticles))
}


/**
 * %en
 * Remove a specific effect from a LivingEntity.
 *
 * %zh
 * 从 LivingEntity 移除指定效果。
 * @param entity
 * %en target entity
 * %zh 目标实体。
 * @param effect
 * %en holder of the MobEffect to remove
 * %zh 要移除的 MobEffect Holder。
 */
fun removeEffect(entity: LivingEntity, effect: Holder<MobEffect>) {
    entity.removeEffect(effect)
}


/**
 * %en
 * Clear all effects from a LivingEntity.
 *
 * %zh
 * 清除 LivingEntity 身上的所有效果。
 * @param entity
 * %en target entity
 * %zh 目标实体。
 */
fun clearEffects(entity: LivingEntity) {
    entity.removeAllEffects()
}


/**
 * %en
 * Mount a passenger on a vehicle entity.
 *
 * %zh
 * 让乘客骑乘载具实体。
 * @param passenger
 * %en entity to mount
 * %zh 要发起骑乘的实体。
 * @param vehicle
 * %en entity to be ridden
 * %zh 要被骑乘的实体。
 * @return
 * %en if mounting succeeded, false otherwise
 * %zh 如果骑乘成功则返回 true，否则返回 false。
 */
fun mount(passenger: Entity, vehicle: Entity): Boolean {
    val exisingVehicle = passenger.vehicle
    if(exisingVehicle != null){
        LOGGER.error("${passenger.displayName} is already riding in ${exisingVehicle.displayName}")
        return false
    }else if(isPlayerEntityType(vehicle.type)){
        LOGGER.error("Players can't be ridden")
    }else if(passenger.selfAndPassengers.anyMatch { it == vehicle }) {
        LOGGER.error("Can't mount entity on itself or any of its passengers")
        return false
    }else if(passenger.level() != vehicle.level()){
        LOGGER.error("Can't mount entity in different dimension")
        return false
    }else if(!passenger.startRiding(vehicle, true, true)){
        LOGGER.error("${passenger.displayName} couldn't start riding ${vehicle.displayName}")
        return false
    }
    return true
}


/**
 * %en
 * Dismount a passenger from its vehicle.
 *
 * %zh
 * 让乘客从载具上解除骑乘。
 * @param passenger
 * %en entity to dismount
 * %zh 要解除骑乘的实体。
 * @return
 * %en if dismounted, false if not riding
 * %zh 如果已解除骑乘则返回 true，否则在未骑乘时返回 false。
 */
fun dismount(passenger: Entity): Boolean {
    if(passenger.vehicle == null){
        LOGGER.error("${passenger.displayName} is not riding any vehicle")
        return false
    }
    passenger.stopRiding()
    return true
}


/**
 * %en
 * Rotate an entity by a Vec2 (pitch, yaw).
 *
 * %zh
 * 使用 Vec2（pitch, yaw）旋转实体。
 * @param target
 * %en target entity
 * %zh 目标实体。
 * @param rot
 * %en rotation vector (x=pitch, y=yaw)
 * %zh 旋转向量（x=pitch, y=yaw）。
 * @param relative
 * %en whether rotation is relative
 * %zh 是否按相对角度旋转。
 */
fun rotate(target: Entity, rot: Vec2, relative: Boolean = false){
    target.forceSetRotation(rot.y, relative, rot.x, relative)
}


/**
 * %en
 * Rotate an entity to look at another entity.
 *
 * %zh
 * 旋转实体，使其看向另一个实体。
 * @param target
 * %en entity to rotate
 * %zh 要旋转的实体。
 * @param lookAt
 * %en entity to look at
 * %zh 要看向的实体。
 * @param targetAnchor
 * %en anchor point on the target
 * %zh 目标实体上的锚点。
 * @param lookAtAnchor
 * %en anchor point on lookAt entity
 * %zh 被看向实体上的锚点。
 */
fun rotate(target: Entity, lookAt: Entity, targetAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET, lookAtAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET){
    if(target is ServerPlayer){
        target.lookAt(targetAnchor, lookAt, lookAtAnchor)
    }else {
        target.lookAt(targetAnchor, lookAtAnchor.apply(lookAt))
    }
}


/**
 * %en
 * Rotate an entity to look at a position.
 *
 * %zh
 * 旋转实体，使其看向指定位置。
 * @param target
 * %en entity to rotate
 * %zh 要旋转的实体。
 * @param lookAt
 * %en position to look at
 * %zh 要看向的位置。
 * @param targetAnchor
 * %en anchor point on the target
 * %zh 目标实体上的锚点。
 */
fun rotate(target: Entity, lookAt: Vec3, targetAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET, lookAtAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET){
    target.lookAt(targetAnchor, lookAt)
}

/**
 * %en
 * Spread players around a center point.
 *
 * %zh
 * 将玩家分散到中心点周围。
 * @param level
 * %en server level used for context
 * %zh 用作上下文的服务端关卡。
 * @param center
 * %en center position vector (x/z used, y ignored)
 * %zh 中心位置向量（使用 x/z，忽略 y）。
 * @param spreadDistance
 * %en minimum distance between players
 * %zh 玩家之间的最小距离。
 * @param maxRange
 * %en max spread radius
 * %zh 最大分散半径。
 * @param maxHeight
 * %en maximum height difference
 * %zh 最大高度差。
 * @param respectTeams
 * %en whether to keep teams together
 * %zh 是否保持队伍成员在一起。
 * @param targets
 * %en entities to spread
 * %zh 要分散的实体集合。
 */
fun spreadPlayers(
    level: ServerLevel,
    center: Vec2,
    spreadDistance: Float,
    maxRange: Float,
    maxHeight: Int,
    respectTeams: Boolean,
    targets: Collection<Entity>
) {
    if (targets.isEmpty()) return
    val source = requireServer().createCommandSourceStack().withLevel(level)
    SpreadPlayersCommand.spreadPlayers(source, center, spreadDistance, maxRange, maxHeight, respectTeams, targets)
}


/**
 * %en
 * Summon an entity of a given type at a position with optional NBT.
 *
 * %zh
 * 在指定位置召唤给定类型的实体，并可选应用 NBT。
 * @param level
 * %en server level to spawn in
 * %zh 用于生成实体的服务端关卡。
 * @param reference
 * %en reference to the EntityType to summon
 * %zh 要召唤的 EntityType 引用。
 * @param vec3
 * %en spawn position
 * %zh 生成位置。
 * @param entityData
 * %en optional NBT override for the entity
 * %zh 可选的实体 NBT 覆盖数据。
 * @return
 * %en Entity or null on failure
 * %zh 返回生成的实体；失败时返回 null。
 */
fun summon(
    level: ServerLevel,
    id: String,
    vec3: Vec3,
    entityData: CompoundTag? = null
): Entity? {
    val blockPos = BlockPos.containing(vec3)
    //try get entity
    val key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.parse(id))
    val reference = level.registryAccess().get(key).getOrNull()
    if(reference == null){
        LOGGER.error("EntityType $id not found")
        return null
    }
    if (!Level.isInSpawnableBounds(blockPos)) {
        LOGGER.error("Invalid postion for summon")
        return null
    } else if (level.difficulty == Difficulty.PEACEFUL && !reference.value().isAllowedInPeaceful) {
        LOGGER.error("Monsters cannot be summoned in Peaceful difficulty")
        return null
    } else {
        var bl = false
        val compoundTag2 = entityData?.copy() ?: run {
            bl = true
            CompoundTag()
        }
        compoundTag2.putString("id", reference.key().identifier().toString())
        val entity: Entity? = loadEntityRecursiveCompat(
            compoundTag2,
            level,
            EntitySpawnReason.COMMAND
        ) { e: Entity? ->
            e?.snapTo(vec3.x, vec3.y, vec3.z, e.yRot, e.xRot)
            e
        }
        if (entity == null) {
            LOGGER.error("Unable to summon entity")
            return null
        } else {
            if (bl && entity is Mob) {
                entity.finalizeSpawn(
                    level,
                    level.getCurrentDifficultyAt(entity.blockPosition()),
                    EntitySpawnReason.COMMAND,
                    null
                )
            }

            if (!level.tryAddFreshEntityWithPassengers(entity)) {
                LOGGER.error("Unable to summon entity due to duplicate UUID")
                return null
            } else {
                return entity
            }
        }
    }
}



/**
 * %en
 * Get tags attached to an entity.
 *
 * %zh
 * 获取附加到实体上的标签。
 * @param entity
 * %en target entity
 * %zh 目标实体。
 * @return
 * %en tag string collection
 * %zh 返回标签字符串集合。
 */
fun getTags(entity: Entity): MutableCollection<String> {
    return entity.entityTags()
}


/**
 * %en
 * Add a tag to an entity.
 *
 * %zh
 * 向实体添加标签。
 * @param entity
 * %en target entity
 * %zh 目标实体。
 * @param string
 * %en tag to add
 * %zh 要添加的标签。
 * @return
 * %en if tag was added, false if already present
 * %zh 如果标签已添加则返回 true，若已存在则返回 false。
 */
fun addTag(entity: Entity, string: String): Boolean {
    return entity.addTag(string)
}


/**
 * %en
 * Remove a tag from an entity.
 *
 * %zh
 * 从实体移除标签。
 * @param entity
 * %en target entity
 * %zh 目标实体。
 * @param string
 * %en tag to remove
 * %zh 要移除的标签。
 * @return
 * %en if the tag was removed
 * %zh 如果标签已移除则返回 true。
 */
fun removeTag(entity: Entity, string: String): Boolean {
    return entity.removeTag(string)
}
