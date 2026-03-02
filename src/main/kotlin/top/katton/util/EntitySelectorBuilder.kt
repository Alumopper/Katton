package top.katton.util

import com.mojang.logging.LogUtils
import net.minecraft.advancements.criterion.MinMaxBounds
import net.minecraft.advancements.criterion.MinMaxBounds.FloatDegrees
import net.minecraft.commands.arguments.selector.EntitySelector
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.GameType
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import top.katton.api.dpcaller.getEntityNbt
import java.util.*
import java.util.function.Predicate

/**
 * Builder class for creating EntitySelector instances with a fluent API.
 */
@Suppress("unused")
class EntitySelectorBuilder {

    private var maxResults = 0
    private var includesEntities = false
    private var worldLimited = false
    private var distance: MinMaxBounds.Doubles? = null
    private var level: MinMaxBounds.Ints? = null
    private var x: Double? = null
    private var y: Double? = null
    private var z: Double? = null
    private var deltaX: Double? = null
    private var deltaY: Double? = null
    private var deltaZ: Double? = null
    private var rotX: FloatDegrees? = null
    private var rotY: FloatDegrees? = null
    private val predicates: MutableList<Predicate<Entity>> = ArrayList()
    private var order = EntitySelector.ORDER_ARBITRARY
    private var namePredicate: Predicate<Entity>? = null
    private var hasNameEquals: Boolean = false
    private var hasNameNotEquals: Boolean = false
    private var isLimited = false
    private var isSorted = false
    private var gamemodePredicate: Predicate<Entity>? = null
	private var hasGamemodeEquals = false
	private var hasGamemodeNotEquals = false
    private var teamPredicate: Predicate<Entity>? = null
	private var hasTeamEquals = false
	private var hasTeamNotEquals = false
    private var type: EntityType<*>? = null
    private var typeInverse = false
    private var hasScores = false
    private var hasAdvancements = false

    /**
     * Sets the entity type to filter by. If inverse is true, selects entities that are NOT of the specified type.
     */
    fun type(entityType: EntityType<*>, inverse: Boolean = false): EntitySelectorBuilder {
        this.type = entityType
        this.typeInverse = inverse
        if(entityType == EntityType.PLAYER && !inverse){
            this.includesEntities = false
        }
        return this
    }

    /**
     * Sets the sorting order of the selector results. If not set, defaults to arbitrary order.
     */
    fun orderArbitrary(): EntitySelectorBuilder {
        this.order = EntitySelector.ORDER_ARBITRARY
        this.isSorted = true
        return this
    }

    /**
     * Sets the sorting order of the selector results. If not set, defaults to arbitrary order.
     */
    fun orderNearest(): EntitySelectorBuilder {
        this.order = EntitySelectorParser.ORDER_NEAREST
        this.isSorted = true
        return this
    }

    /**
     * Sets the sorting order of the selector results. If not set, defaults to arbitrary order.
     */
    fun orderFurthest(): EntitySelectorBuilder {
        this.order = EntitySelectorParser.ORDER_FURTHEST
        this.isSorted = true
        return this
    }

    /**
     * Sets the sorting order of the selector results. If not set, defaults to arbitrary order.
     */
    fun orderRandom(): EntitySelectorBuilder {
        this.order = EntitySelectorParser.ORDER_RANDOM
        this.isSorted = true
        return this
    }

    /**
     * Adds a predicate to filter only alive entities.
     */
    fun isAlive(): EntitySelectorBuilder {
        this.predicates.add(Entity::isAlive)
        return this
    }

    /**
     * Used for volume-based selection. Similar to the "x" argument in target selectors in commands.
     */
    fun x(x: Double): EntitySelectorBuilder {
        this.x = x
        this.worldLimited = true
        return this
    }

    /**
     * Used for volume-based selection. Similar to the "y" argument in target selectors in commands.
     */
    fun y(y: Double): EntitySelectorBuilder {
        this.y = y
        this.worldLimited = true
        return this
    }

    /**
     * Used for volume-based selection. Similar to the "z" argument in target selectors in commands.
     */
    fun z(z: Double): EntitySelectorBuilder {
        this.z = z
        this.worldLimited = true
        return this
    }

    /**
     * Used for volume-based selection. Similar to the "dx" argument in target selectors in commands.
     */
    fun dx(deltaX: Double): EntitySelectorBuilder {
        this.deltaX = deltaX
        this.worldLimited = true
        return this
    }

    /**
     * Used for volume-based selection. Similar to the "dy" argument in target selectors in commands.
     */
    fun dy(deltaY: Double): EntitySelectorBuilder {
        this.deltaY = deltaY
        this.worldLimited = true
        return this
    }

    /**
     * Used for volume-based selection. Similar to the "dz" argument in target selectors in commands.
     */
    fun dz(deltaZ: Double): EntitySelectorBuilder {
        this.deltaZ = deltaZ
        this.worldLimited = true
        return this
    }

    /**
     * Filter target selection by name.
     */
    fun name(name: String, inverse: Boolean = false): EntitySelectorBuilder {
        if(this.namePredicate != null) {
            this.predicates.remove(namePredicate)
        }
        val predicate = Predicate<Entity> { entity -> entity.plainTextName == name != inverse }
        this.namePredicate = predicate
        this.predicates.add(predicate)
        this.hasNameNotEquals = inverse
        this.hasNameEquals = !inverse
        return this
    }

    /**
     * Filter target selection based on their Euclidean distances from some point, searching for the target's feet (a point at the bottom of the center of their hitbox).
     */
    fun distance(minDistance: Double, maxDistance: Double): EntitySelectorBuilder {
        if(minDistance < 0 || maxDistance < 0) {
            LOGGER.error("Distance cannot be negative")
            return this
        }
        if(minDistance > maxDistance) {
            LOGGER.error("Min distance cannot be greater than max distance")
            return this
        }
        this.distance = MinMaxBounds.Doubles.between(minDistance, maxDistance)
        this.worldLimited = true
        return this
    }

    /**
     * Filter target selection based on their Euclidean distances from some point, searching for the target's feet (a point at the bottom of the center of their hitbox).
     */
    fun distanceBelow(maxDistance: Double): EntitySelectorBuilder {
        if(maxDistance < 0) {
            LOGGER.error("Distance cannot be negative")
            return this
        }
        this.distance = MinMaxBounds.Doubles.atMost(maxDistance)
        this.worldLimited = true
        return this
    }

    /**
     * Filter target selection based on their Euclidean distances from some point, searching for the target's feet (a point at the bottom of the center of their hitbox).
     */
    fun distanceAbove(minDistance: Double): EntitySelectorBuilder {
        if(minDistance < 0) {
            LOGGER.error("Distance cannot be negative")
            return this
        }
        this.distance = MinMaxBounds.Doubles.atLeast(minDistance)
        this.worldLimited = true
        return this
    }

    /**
     * Filter target selection based on the entity's experience levels. This naturally filters out all non-player targets.
     */
    fun level(minLevel: Int, maxLevel: Int): EntitySelectorBuilder {
        if (minLevel < 0 || maxLevel < 0) {
            LOGGER.error("Level cannot be negative")
            return this
        }
        if (minLevel > maxLevel) {
            LOGGER.error("Min level cannot be greater than max level")
            return this
        }
        this.level = MinMaxBounds.Ints.between(minLevel, maxLevel)
        this.includesEntities = false
        return this
    }

    /**
     * Filter target selection based on the entity's experience levels. This naturally filters out all non-player targets.
     */
    fun levelBelow(maxLevel: Int): EntitySelectorBuilder {
        if (maxLevel < 0) {
            LOGGER.error("Level cannot be negative")
            return this
        }
        this.level = MinMaxBounds.Ints.atMost(maxLevel)
        this.includesEntities = false
        return this
    }

    /**
     * Filter target selection based on the entity's experience levels. This naturally filters out all non-player targets.
     */
    fun levelAbove(minLevel: Int): EntitySelectorBuilder {
        if (minLevel < 0) {
            LOGGER.error("Level cannot be negative")
            return this
        }
        this.level = MinMaxBounds.Ints.atLeast(minLevel)
        this.includesEntities = false
        return this
    }

    /**
     * Filter target selection based on the entity's rotation along the pitch axis, measured in degrees.
     */
    fun x_rotation(minDegrees: Float, maxDegrees: Float): EntitySelectorBuilder {
        if(minDegrees > maxDegrees) {
            LOGGER.error("Min rotation cannot be greater than max rotation")
            return this
        }
        this.rotX = FloatDegrees(MinMaxBounds.Bounds<Float>(Optional.of(minDegrees), Optional.of(maxDegrees)))
        return this
    }

    /**
     * Filter target selection based on the entity's rotation along the yaw axis, measured clockwise in degrees from due south (or the positive Z direction).
     */
    fun y_rotation(minDegrees: Float, maxDegrees: Float): EntitySelectorBuilder {
        if(minDegrees > maxDegrees) {
            LOGGER.error("Min rotation cannot be greater than max rotation")
            return this
        }
        this.rotY = FloatDegrees(MinMaxBounds.Bounds<Float>(Optional.of(minDegrees), Optional.of(maxDegrees)))
        return this
    }

    /**
     * Limit the number of selectable targets for a target selector
     */
    fun limit(i: Int): EntitySelectorBuilder {
        if(i < 1) {
            LOGGER.error("Limit must be at least 1")
            return this
        }
        this.maxResults = i
        this.isLimited = true
        return this
    }

    /**
     * Filter target selection by game mode. This naturally filters out all non-player targets.
     */
    fun gamemode(gamemode: GameType, inverse: Boolean = false): EntitySelectorBuilder {
        if(this.gamemodePredicate != null) {
            this.predicates.remove(gamemodePredicate)
        }
        val predicate = Predicate<Entity> { entity ->
            if(entity is ServerPlayer){
                entity.gameMode() == gamemode != inverse
            }else{
                false
            }
        }
        this.gamemodePredicate = predicate
        this.includesEntities = false
        this.predicates.add(predicate)
        this.hasGamemodeEquals = !inverse
        this.hasGamemodeNotEquals = inverse
        return this
    }

    /**
     * Filter target selection based on teams.
     */
    fun team(team: String, inverse: Boolean = false): EntitySelectorBuilder {
        if(this.teamPredicate != null) {
            this.predicates.remove(teamPredicate)
        }
        val predicate = Predicate<Entity> { entity ->
            (entity.team?.name ?: "") == team != inverse
        }
        this.teamPredicate = predicate
        this.predicates.add(predicate)
        this.hasTeamEquals = !inverse
        this.hasTeamNotEquals = inverse
        return this
    }

    /**
     * Filter target selection based on entity type. If inverse is true, selects entities that are NOT of the specified type.
     */
    fun type(type: Identifier, inverse: Boolean = false): EntitySelectorBuilder {
        val entityType = EntityType.byString(type.toString()).orElse(null)
        if(entityType == null) {
            LOGGER.error("Invalid entity type: $type")
            return this
        }
        return type(entityType, inverse)
    }

    /**
     * Filter target selection based on entity type. If inverse is true, selects entities that are NOT of the specified type.
     */
    fun type(typeTag: TagKey<EntityType<*>>, inverse: Boolean = false): EntitySelectorBuilder {
        val predicate = Predicate<Entity> { entity ->
            entity.`is`(typeTag) != inverse
        }
        this.predicates.add(predicate)
        return this
    }

    /**
     * Filter target selection based on the entity's scoreboard tags.
     */
    fun tag(tag: String, inverse: Boolean = false): EntitySelectorBuilder {
        val predicate = Predicate<Entity> { entity ->
            if(tag == "") {
                entity.entityTags().isEmpty() != inverse
            }else{
                entity.entityTags().contains(tag) != inverse
            }
        }
        this.predicates.add(predicate)
        return this
    }

    /**
     * Filter target selection based on the entity's NBT data. Note that this selector argument should be used with care, as accessing NBT data is a heavy process for the CPU.
     */
    fun nbt(nbt: CompoundTag, inverse: Boolean = false): EntitySelectorBuilder {
        val predicate = Predicate<Entity> { entity ->
            NbtUtils.compareNbt(nbt, getEntityNbt(entity), true) != inverse
        }
        this.predicates.add(predicate)
        return this
    }

    /**
     *  Filter target selection based on their scores in the specified objectives.
     */
    fun score(objectiveName: String, min: Int? = null, max: Int? = null): EntitySelectorBuilder {
        val predicate = Predicate<Entity> { entity ->
            val scoreboard = entity.level().scoreboard
            val objective = scoreboard.getObjective(objectiveName)?: return@Predicate false
            val score = scoreboard.getPlayerScoreInfo(entity, objective)?.value() ?: return@Predicate false
            val minCheck = min?.let { score >= it } ?: true
            val maxCheck = max?.let { score <= it } ?: true
            return@Predicate (minCheck && maxCheck)
        }
        this.predicates.add(predicate)
        this.hasScores = true
        return this
    }

    /**
     * Filter target selection based on the entity's advancements. This naturally filters out all non-player targets.
     */
    fun advancements(advancement: Identifier, isDone: Boolean): EntitySelectorBuilder {
        val predicate = Predicate<Entity> { entity ->
            if(entity !is ServerPlayer) return@Predicate false
            val progress = entity.advancements.getOrStartProgress(entity.level().server.advancements.get(advancement) ?: return@Predicate false)
            val hasAdvancement = progress.isDone
            return@Predicate hasAdvancement != isDone
        }
        this.predicates.add(predicate)
        this.hasAdvancements = true
        this.includesEntities = false
        return this
    }

    /**
     * Filter target selection based on the entity's advancements. This naturally filters out all non-player targets.
     */
    fun advancements(advancement: Identifier, criterion: String, isDone: Boolean): EntitySelectorBuilder {
        val predicate = Predicate<Entity> { entity ->
            if(entity !is ServerPlayer) return@Predicate false
            val progress = entity.advancements.getOrStartProgress(entity.level().server.advancements.get(advancement) ?: return@Predicate false)
            val criterionProgress = progress.getCriterion(criterion) ?: return@Predicate false
            val hasCriterion = criterionProgress.isDone
            return@Predicate hasCriterion != isDone
        }
        this.predicates.add(predicate)
        this.hasAdvancements = true
        this.includesEntities = false
        return this
    }

    /**
     * Filter target selection by predicates.
     */
    fun predicate(predicateKey: ResourceKey<LootItemCondition>, inverse: Boolean = false): EntitySelectorBuilder {
        val predicate = Predicate<Entity> { entity ->
            if(entity.level() !is ServerLevel) return@Predicate false
            val condition = entity.level().server?.reloadableRegistries()?.lookup()?.get(predicateKey)?.map { it.value() } ?: return@Predicate false
            if(condition.isEmpty) return@Predicate false
            val params = LootParams.Builder(entity.level() as ServerLevel)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .create(LootContextParamSets.SELECTOR)
            val context = LootContext.Builder(params).create(Optional.empty())
            return@Predicate condition.get().test(context) != inverse
        }
        this.predicates.add(predicate)
        return this
    }

    /**
     * Filter target selection by predicates.
     */
    fun predicate(predicate: LootItemCondition, inverse: Boolean = false): EntitySelectorBuilder {
        val predicateWrapper = Predicate<Entity> { entity ->
            if(entity.level() !is ServerLevel) return@Predicate false
            val params = LootParams.Builder(entity.level() as ServerLevel)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .create(LootContextParamSets.SELECTOR)
            val context = LootContext.Builder(params).create(Optional.empty())
            return@Predicate predicate.test(context) != inverse
        }
        this.predicates.add(predicateWrapper)
        return this
    }

    /**
     * Build an EntitySelector instance based on the current configuration of this builder.
     */
    fun create(): EntitySelector {
        var aABB: AABB?
		if (this.deltaX == null && this.deltaY == null && this.deltaZ == null) {
			if (this.distance != null && this.distance!!.max().isPresent) {
				val d = this.distance!!.max().get()
				aABB = AABB(-d, -d, -d, d + 1.0, d + 1.0, d + 1.0);
			} else {
				aABB = null;
			}
		} else {
			aABB = this.createAabb(
                if(this.deltaX == null) 0.0 else this.deltaX!!,
                if(this.deltaY == null) 0.0 else this.deltaY!!,
                if(this.deltaZ == null) 0.0 else this.deltaZ!!
            )
		}
        val function =
		if (this.x == null && this.y == null && this.z == null) {
            { it }
		} else {
            { vec3: Vec3 -> Vec3(if(this.x == null) vec3.x else this.x!!, if(this.y == null) vec3.y else this.y!!, if(this.z == null) vec3.z else this.z!!) }
		}
        return EntitySelector(
			this.maxResults,
			this.includesEntities,
			this.worldLimited,
            java.util.List.copyOf(this.predicates),
			this.distance,
			function,
			aABB,
			this.order,
			false,
			null,
			null,
			this.type,
			true
		);
    }

    private fun createAabb(d: Double, e: Double, f: Double): AABB {
		val bl = d < 0.0;
		val bl2 = e < 0.0;
		val bl3 = f < 0.0;
		val g = if(bl) d else 0.0;
		val h = if(bl2) e else 0.0;
		val i = if(bl3) f else 0.0;
		val j = (if(bl) 0.0 else d) + 1.0;
		val k = (if(bl2) 0.0 else e) + 1.0;
		val l = (if(bl3) 0.0 else f) + 1.0;
		return AABB(g, h, i, j, k, l);
	}

    companion object {

        private val LOGGER = LogUtils.getLogger()

        /**
         * Return an EntitySelectorBuilder that selects all players in the world.
         */
        fun allPlayers(): EntitySelectorBuilder {
            return EntitySelectorBuilder().apply {
                maxResults = Int.MAX_VALUE
                includesEntities = false
            }.type(EntityType.PLAYER)
        }

        /**
         * Return an EntitySelectorBuilder that selects all entities in the world, including players.
         */
        fun allEntities(): EntitySelectorBuilder {
            return EntitySelectorBuilder().apply {
                maxResults = Int.MAX_VALUE
                includesEntities = true
            }
        }

        /**
         * Return an EntitySelectorBuilder that selects the nearest player to some point.
         */
        fun nearestPlayer(): EntitySelectorBuilder {
            return EntitySelectorBuilder().apply {
                maxResults = 1
                includesEntities = false

            }.orderNearest().type(EntityType.PLAYER)
        }

        /**
         * Return an EntitySelectorBuilder that selects the nearest entity to some point, including players.
         */
        fun nearestEntity(): EntitySelectorBuilder {
            return EntitySelectorBuilder().apply {
                maxResults = 1
                includesEntities = true
            }.orderNearest()
        }

        /**
         * Return an EntitySelectorBuilder that selects random entities in the world. Defaults to selecting one random entity, but can be configured to select more.
         */
        fun randomEntity(): EntitySelectorBuilder {
            return EntitySelectorBuilder().apply {
                maxResults = 1
                includesEntities = true
            }.orderRandom()
        }

        /**
         * Return an EntitySelectorBuilder that selects a random player in the world. Defaults to selecting one random player, but can be configured to select more.
         */
        fun randomPlayer(): EntitySelectorBuilder {
            return EntitySelectorBuilder().apply {
                maxResults = 1
                includesEntities = false
            }.orderRandom().type(EntityType.PLAYER)
        }
    }

}