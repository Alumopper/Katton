@file:Suppress("unused")

package top.katton.api

import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.commands.arguments.selector.EntitySelector
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.*
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.commands.*
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
import net.minecraft.world.scores.*
import java.util.*

var Entity.nbt: CompoundTag
    get() = getEntityNbt(this)
    set(value) {
        setEntityNbt(this, value)
    }

class KattonServerEntityCollection(
    private val server: MinecraftServer
) {
    val all
        get() = server.allLevels.flatMap { it.allEntities }

    operator fun get(level: ServerLevel): KattonLevelEntityCollection {
        return KattonLevelEntityCollection(level)
    }

    operator fun get(uuid: UUID): Entity? {
        return server.allLevels.map { it.getEntity(uuid) }.firstOrNull()
    }
}

class KattonLevelEntityCollection(
    val level: ServerLevel
) : Iterable<Entity> by level.allEntities {
    operator fun <T : Entity> get(
        entityTypeTest: EntityTypeTest<Entity, T>,
        predicate: (T) -> Boolean = { true }
    ): List<T> = level.getEntities(entityTypeTest, predicate)

    operator fun <T : Entity> get(
        entityTypeTest: EntityTypeTest<Entity, T>,
        aabb: AABB,
        predicate: (T) -> Boolean = { true }
    ): List<T> = level.getEntities(entityTypeTest, aabb, predicate)

    operator fun get(selector: EntitySelector): List<Entity> {
        return findEntities(level, selector)
    }

    operator fun get(uuid: UUID): Entity? {
        return level.getEntity(uuid)
    }
}

class KattonEntityAttributeValueMap(
    val entity: LivingEntity
) {
    fun contains(holder: Holder<Attribute>): Boolean {
        return entity.getAttribute(holder) != null
    }

    operator fun get(holder: Holder<Attribute>): Double? {
        return entity.getAttributeValue(holder)
    }

    fun set(holder: Holder<Attribute>, value: Double, vararg modifiers: AttributeModifier) {
        entity.getAttribute(holder)?.baseValue?.let {
            it != value
        } ?: false
        modifiers.forEach {
            entity.getAttribute(holder)?.addTransientModifier(it)
        }
    }
}

val LivingEntity.attributeValues
    get() = KattonEntityAttributeValueMap(this)

fun Entity.damage(amount: Float) = damage(this, amount)

/**
 * Get an attribute value from a LivingEntity.
 *
 * @param entity the entity
 * @param attribute attribute holder to read
 * @return current attribute value
 */
fun getAttribute(entity: LivingEntity, attribute: Holder<Attribute>): Double {
    return entity.getAttributeValue(attribute)
}


/**
 * Check if a LivingEntity has a given attribute.
 *
 * @param entity the entity
 * @param attribute attribute holder to check
 * @return true if attribute present
 */
fun hasAttribute(entity: LivingEntity, attribute: Holder<Attribute>): Boolean {
    return entity.getAttribute(attribute) != null
}


/**
 * Get base attribute value from a LivingEntity.
 *
 * @param entity the entity
 * @param attribute attribute holder to read
 * @return base value or null if attribute missing
 */
fun getBaseAttribute(entity: LivingEntity, attribute: Holder<Attribute>): Double? {
    return entity.getAttribute(attribute)?.baseValue
}


/**
 * Set the base attribute value for a LivingEntity.
 *
 * @param entity the entity
 * @param attribute attribute holder to set
 * @param value new base value
 * @return true if changed, false otherwise
 */
fun setBaseAttribute(entity: LivingEntity, attribute: Holder<Attribute>, value: Double): Boolean {
    return entity.getAttribute(attribute)?.baseValue?.let {
        it != value
    } ?: false
}


/**
 * Add a transient attribute modifier to an entity.
 *
 * @param entity the entity
 * @param attribute attribute holder to modify
 * @param modifier AttributeModifier to add
 */
fun addAttributeModify(entity: LivingEntity, attribute: Holder<Attribute>, modifier: AttributeModifier) {
    entity.getAttribute(attribute)?.addTransientModifier(modifier)
}


/**
 * Remove an attribute modifier from an entity.
 *
 * @param entity the entity
 * @param attribute attribute holder to modify
 * @param modifier AttributeModifier to remove
 */
fun removeAttributeModify(entity: LivingEntity, attribute: Holder<Attribute>, modifier: AttributeModifier) {
    entity.getAttribute(attribute)?.removeModifier(modifier)
}


/**
 * Damage an entity by an amount using generic damage.
 *
 * @param entity target entity
 * @param amount damage amount
 */
fun damage(entity: Entity, amount: Float) {
    entity.hurtServer(
        requireServer().overworld(),
        requireServer().overworld().damageSources().source(DamageTypes.GENERIC),
        amount
    )
}


/**
 * Damage a target entity from an attacker using a damage type key.
 *
 * @param target the entity to damage
 * @param amount damage amount
 * @param attacker the source entity causing damage
 * @param damageType resource key of the DamageType (default GENERIC)
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
 * Damage a target entity from an attacker using a DamageType instance.
 *
 * @param target the entity to damage
 * @param amount damage amount
 * @param attacker the source entity causing damage
 * @param damageType DamageType instance to apply
 */
fun damage(target: Entity, amount: Float, attacker: Entity, damageType: DamageType) {
    target.hurtServer(
        requireServer().overworld(),
        DamageSource(Holder.direct(damageType), attacker, attacker),
        amount
    )
}



/**
 * Damage a target entity from a position using a damage type key.
 *
 * @param target entity to damage
 * @param amount damage amount
 * @param pos position of damage source
 * @param damageType resource key of the DamageType (default GENERIC)
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
 * Damage a target entity from a position using a DamageType instance.
 *
 * @param target entity to damage
 * @param amount damage amount
 * @param pos position of damage source
 * @param damageType DamageType instance to apply
 */
fun damage(target: Entity, amount: Float, pos: Vec3, damageType: DamageType) {
    target.hurtServer(
        requireServer().overworld(),
        DamageSource(Holder.direct(damageType), pos),
        amount
    )
}


/**
 * Add a mob effect to a LivingEntity.
 *
 * @param entity target entity
 * @param effect holder of the MobEffect to apply
 * @param duration effect duration in ticks (default 600)
 * @param amplifier effect amplifier level (default 0)
 * @param showParticles whether to show particles
 * @param ambient whether effect is ambient
 */
fun addEffect(entity: LivingEntity, effect: Holder<MobEffect>, duration: Int = 600, amplifier: Int = 0, showParticles: Boolean = true, ambient: Boolean = false) {
    entity.addEffect(MobEffectInstance(effect, duration, amplifier, ambient, showParticles))
}


/**
 * Remove a specific effect from a LivingEntity.
 *
 * @param entity target entity
 * @param effect holder of the MobEffect to remove
 */
fun removeEffect(entity: LivingEntity, effect: Holder<MobEffect>) {
    entity.removeEffect(effect)
}


/**
 * Clear all effects from a LivingEntity.
 *
 * @param entity target entity
 */
fun clearEffects(entity: LivingEntity) {
    entity.removeAllEffects()
}


/**
 * Mount a passenger on a vehicle entity.
 *
 * @param passenger entity to mount
 * @param vehicle entity to be ridden
 * @return true if mounting succeeded, false otherwise
 */
fun mount(passenger: Entity, vehicle: Entity): Boolean {
    val exisingVehicle = passenger.vehicle
    if(exisingVehicle != null){
        LOGGER.error("${passenger.displayName} is already riding in ${exisingVehicle.displayName}")
        return false
    }else if(vehicle.type == EntityType.PLAYER){
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
 * Dismount a passenger from its vehicle.
 *
 * @param passenger entity to dismount
 * @return true if dismounted, false if not riding
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
 * Rotate an entity by a Vec2 (pitch, yaw).
 *
 * @param target target entity
 * @param rot rotation vector (x=pitch, y=yaw)
 * @param relative whether rotation is relative
 */
fun rotate(target: Entity, rot: Vec2, relative: Boolean = false){
    target.forceSetRotation(rot.y, relative, rot.x, relative)
}


/**
 * Rotate an entity to look at another entity.
 *
 * @param target entity to rotate
 * @param lookAt entity to look at
 * @param targetAnchor anchor point on the target
 * @param lookAtAnchor anchor point on lookAt entity
 */
fun rotate(target: Entity, lookAt: Entity, targetAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET, lookAtAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET){
    if(target is ServerPlayer){
        target.lookAt(targetAnchor, lookAt, lookAtAnchor)
    }else {
        target.lookAt(targetAnchor, lookAtAnchor.apply(lookAt))
    }
}


/**
 * Rotate an entity to look at a position.
 *
 * @param target entity to rotate
 * @param lookAt position to look at
 * @param targetAnchor anchor on target entity
 */
fun rotate(target: Entity, lookAt: Vec3, targetAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET, lookAtAnchor: EntityAnchorArgument.Anchor = EntityAnchorArgument.Anchor.FEET){
    target.lookAt(targetAnchor, lookAt)
}

/**
 * Spread players around a center point.
 *
 * @param level server level used for context
 * @param center center position vector (x=z, y ignored)
 * @param spreadDistance minimum distance between players
 * @param maxRange max spread radius
 * @param maxHeight maximum height difference
 * @param respectTeams whether to keep teams together
 * @param targets collection of entities to spread
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
 * Summon an entity of a given type at a position with optional NBT.
 *
 * @param level server level to spawn in
 * @param reference reference to the EntityType to summon
 * @param vec3 spawn position
 * @param entityData optional NBT override for the entity
 * @return spawned Entity or null on failure
 */
fun summon(
    level: ServerLevel,
    reference: Holder.Reference<EntityType<*>>,
    vec3: Vec3,
    entityData: CompoundTag? = null
): Entity? {
    val blockPos = BlockPos.containing(vec3)
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
        val entity: Entity? = EntityType.loadEntityRecursive(
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
 * Get tags attached to an entity.
 *
 * @param entity target entity
 * @return mutable collection of tag strings
 */
fun getTags(entity: Entity): MutableCollection<String> {
    return entity.entityTags()
}


/**
 * Add a tag to an entity.
 *
 * @param entity target entity
 * @param string tag to add
 * @return true if tag was added, false if already present
 */
fun addTag(entity: Entity, string: String): Boolean {
    return entity.addTag(string)
}


/**
 * Remove a tag from an entity.
 *
 * @param entity target entity
 * @param string tag to remove
 * @return true if the tag was removed
 */
fun removeTag(entity: Entity, string: String): Boolean {
    return entity.removeTag(string)
}


