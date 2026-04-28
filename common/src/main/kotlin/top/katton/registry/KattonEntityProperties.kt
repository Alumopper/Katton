package top.katton.registry

import net.minecraft.core.Holder
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes

/**
 * Extended entity configuration for registerNativeEntity.
 *
 * Provides a DSL for configuring entity dimensions, category, attributes,
 * spawn egg settings, and spawn placement rules.
 *
 * @param id The entity identifier
 */
@Suppress("unused")
class KattonEntityProperties(
    override val id: Identifier
) : Identifiable {

    private val attributeEntries = mutableListOf<AttributeEntry>()

    // ── EntityType dimensions ──────────────────────────────────────

    /**
     * Entity dimensions (width x height). Default: 0.6 x 1.8 (like Player).
     */
    var dimensions: EntityDimensions = EntityDimensions.scalable(0.6f, 1.8f)

    /**
     * Sets the entity dimensions (width x height).
     */
    fun dimensions(width: Float, height: Float): KattonEntityProperties {
        this.dimensions = EntityDimensions.scalable(width, height)
        return this
    }

    /**
     * Mob category for spawning. Default: [MobCategory.MISC].
     */
    var category: MobCategory = MobCategory.MISC

    /**
     * Sets the mob category.
     */
    fun category(category: MobCategory): KattonEntityProperties {
        this.category = category
        return this
    }

    /**
     * Whether the entity should be serialized. Default: true.
     */
    var serialize: Boolean = true

    /**
     * Client tracking range in chunks. Default: 5.
     */
    var clientTrackingRange: Int = 5

    /**
     * Tracking tick interval (update frequency). Default: 1 (every tick).
     * For less important entities, use higher values (e.g., 3 for projectiles).
     */
    var trackingTickInterval: Int = 1

    // ── Attributes (health, speed, etc.) ───────────────────────────

    private data class AttributeEntry(
        val attribute: Holder<Attribute>,
        val value: Double
    )

    /**
     * Adds a generic attribute with the given value.
     */
    fun addAttribute(attribute: Holder<Attribute>, value: Double): KattonEntityProperties {
        attributeEntries.add(AttributeEntry(attribute, value))
        return this
    }

    /** Sets max health attribute. */
    fun maxHealth(value: Double): KattonEntityProperties = addAttribute(Attributes.MAX_HEALTH, value)

    /** Sets movement speed attribute. */
    fun movementSpeed(value: Double): KattonEntityProperties = addAttribute(Attributes.MOVEMENT_SPEED, value)

    /** Sets knockback resistance attribute. */
    fun knockbackResistance(value: Double): KattonEntityProperties = addAttribute(Attributes.KNOCKBACK_RESISTANCE, value)

    /** Sets attack damage attribute. */
    fun attackDamage(value: Double): KattonEntityProperties = addAttribute(Attributes.ATTACK_DAMAGE, value)

    /** Sets armor attribute. */
    fun armor(value: Double): KattonEntityProperties = addAttribute(Attributes.ARMOR, value)

    /** Sets armor toughness attribute. */
    fun armorToughness(value: Double): KattonEntityProperties = addAttribute(Attributes.ARMOR_TOUGHNESS, value)

    /** Sets follow range attribute. */
    fun followRange(value: Double): KattonEntityProperties = addAttribute(Attributes.FOLLOW_RANGE, value)

    /** Sets attack speed attribute. */
    fun attackSpeed(value: Double): KattonEntityProperties = addAttribute(Attributes.ATTACK_SPEED, value)

    /** Sets luck attribute. */
    fun luck(value: Double): KattonEntityProperties = addAttribute(Attributes.LUCK, value)

    // ── Spawn Egg ───────────────────────────────────────────────────

    /**
     * Whether to automatically create a spawn egg item for this entity.
     * Default: false.
     *
     * In MC 1.21.11+, spawn egg colors are derived from the entity type
     * and aren't set manually. The spawn egg binds to the entity via
     * `Item.Properties.spawnEgg(entityType)`.
     */
    var spawnEgg: Boolean = false

    /**
     * Enables spawn egg creation.
     */
    fun withSpawnEgg(): KattonEntityProperties {
        this.spawnEgg = true
        return this
    }

    // ── Spawn Placement ────────────────────────────────────────────

    /**
     * Spawn placement type for natural spawning.
     * Null means no spawn placement will be registered.
     */
    var spawnPlacementType: net.minecraft.world.entity.SpawnPlacementType? = null

    /**
     * Heightmap type for spawn placement. Default: MOTION_BLOCKING_NO_LEAVES.
     */
    var spawnHeightmap: net.minecraft.world.level.levelgen.Heightmap.Types =
        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES

    /**
     * Enables spawn placement with the given type and default heightmap.
     */
    fun spawnPlacement(
        type: net.minecraft.world.entity.SpawnPlacementType,
        heightmap: net.minecraft.world.level.levelgen.Heightmap.Types =
            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
    ): KattonEntityProperties {
        this.spawnPlacementType = type
        this.spawnHeightmap = heightmap
        return this
    }

    // ── Internal ───────────────────────────────────────────────────

    /**
     * Whether any attributes were configured.
     */
    val hasAttributes: Boolean get() = attributeEntries.isNotEmpty()

    /**
     * Builds the [AttributeSupplier] from configured attributes.
     * Returns null if no attributes were configured.
     */
    internal fun buildAttributes(): AttributeSupplier? {
        if (attributeEntries.isEmpty()) return null
        // Start from LivingEntity defaults to ensure baseline attributes
        // (e.g., SCALE/STEP_HEIGHT/GRAVITY...) always exist on custom living types.
        val builder = LivingEntity.createLivingAttributes()
        for (entry in attributeEntries) {
            builder.add(entry.attribute, entry.value)
        }
        return builder.build()
    }
}
