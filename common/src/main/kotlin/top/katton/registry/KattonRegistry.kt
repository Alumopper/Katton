package top.katton.registry

import com.mojang.serialization.Codec
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.item.Item
import net.minecraft.world.item.SpawnEggItem
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import top.katton.Katton
import top.katton.Katton.MOD_ID
import top.katton.LoadState
import top.katton.platform.DynamicRegistryHooks
import top.katton.platform.SpawnPlacementHooks
import org.slf4j.LoggerFactory
import top.katton.platform.EntityAttributeHooks

/**
 * Creates an [Identifier] from a namespace and path.
 */
fun id(namespace: String, path: String) = Identifier.fromNamespaceAndPath(namespace, path)

/**
 * Parses an [Identifier] from a string in the format "namespace:path".
 */
fun id(string: String) = Identifier.parse(string)

private val REGLOGGER = LoggerFactory.getLogger("KattonRegistry")

private fun regDebug(msg: () -> String) {
    if (Katton.debugRegistryLogging) {
        REGLOGGER.info(msg())
    }
}

/**
 * Interface for objects that have an [Identifier].
 */
interface Identifiable {
    val id: Identifier
}

/**
 * Registration mode for game objects.
 */
enum class RegisterMode {
    GLOBAL,
    RELOADABLE,
    AUTO
}

/**
 * Base class for Katton registries.
 *
 * Provides internal entry storage with controlled access.
 */
abstract class KattonRegistries<T : Identifiable>(
    override val id: Identifier
) : Identifiable {
    private val entries = mutableMapOf<Identifier, T>()

    internal fun register(entry: T): T {
        entries[entry.id] = entry
        return entry
    }

    fun get(id: Identifier): T? = entries[id]
    fun contains(id: Identifier): Boolean = id in entries
    internal fun remove(id: Identifier): T? = entries.remove(id)
    fun clear() = entries.clear()
    val size: Int get() = entries.size
    fun entries(): Set<Map.Entry<Identifier, T>> = entries.entries
}

/**
 * Central registry for Katton mod components.
 */
object KattonRegistry {

    //这个热重载怎么这么难写啊QwQ
    //感觉会有一堆bug但是现在能运行那就先不管了吧qwq

    data class RegistryHealth(
        val key: String,
        val kattonEntries: Int,
        val managedTracked: Int,
        val staleRetained: Int,
        val pendingRegistrations: Int
    )

    /**
     * Represents a registered item entry.
     */
    data class KattonItemEntry(
        override val id: Identifier,
        val item: Item,
        internal val properties: KattonItemProperties? = null
    ) : Identifiable

    /**
     * Represents a registered mob effect entry.
     */
    data class KattonMobEffectEntry(
        override val id: Identifier,
        val effect: MobEffect
    ) : Identifiable

    /**
     * Represents a registered block entry.
     */
    data class KattonBlockEntry(
        override val id: Identifier,
        val block: Block
    ) : Identifiable

    /**
     * Registry for Katton items.
     */
    object ITEMS : KattonRegistries<KattonItemEntry>(id(MOD_ID, "item")) {

        private val delegate = ReloadableBuiltInRegistry(
            builtInRegistry = BuiltInRegistries.ITEM,
            registryKey = Registries.ITEM,
            requiresIntrusiveHolders = true,
            unregisterOnReload = false
        )

        @Synchronized
        fun beginReload() {
            regDebug { "ITEMS.beginReload()" }
            val removed = delegate.beginReload()
            removed.forEach { remove(it) }
        }

        private fun bindHolderComponents(item: Item, properties: KattonItemProperties?) {
            val holder = item.builtInRegistryHolder
            holder.components = properties?.buildComponent() ?: DataComponentMap.EMPTY
            holder.tags = emptySet()
        }

        /**
         * Reapplies custom ITEM_NAME and ITEM_MODEL components to all registered Katton items.
         *
         * This must be called from the client-side mixin (RegistryDataCollectorMixin)
         * after DataComponentInitializers.build() has run during
         * RegistryDataCollector.collectGameRegistries(), because Minecraft's Item
         * constructor registers a component initializer via finalizeInitializer()
         * that overrides custom ITEM_NAME and ITEM_MODEL with defaults.
         *
         * Called from RegistryDataCollectorMixin on the Fabric client thread.
         */
        @JvmStatic
        fun reapplyCustomItemComponents() {
            entries().forEach { (_, entry) ->
                entry.properties?.let { props ->
                    bindHolderComponents(entry.item, props)
                }
            }
        }

        fun newNative(
            components: KattonItemProperties,
            registerMode: RegisterMode = RegisterMode.AUTO,
            itemFactory: (KattonItemProperties) -> Item
        ): KattonItemEntry {
            components.setId(ResourceKey.create(Registries.ITEM, components.id))

            val delayedFactory = {
                components.finalizeComponentInitializer()
                itemFactory(components)
            }
            val item = delegate.registerWithMode(components.id, registerMode) { delayedFactory() }
            bindHolderComponents(item, components)
            val entry = KattonItemEntry(id = components.id, item = item, properties = components)
            register(entry)
            delegate.markManaged(components.id, registerMode)
            return entry
        }

        fun registryHealth(): RegistryHealth = RegistryHealth(
            key = "items",
            kattonEntries = size,
            managedTracked = delegate.managedIdsSnapshot().size,
            staleRetained = delegate.staleManagedIdsSnapshot().size,
            pendingRegistrations = 0
        )
    }

    /**
     * Registry for Katton mob effects.
     */
    object EFFECTS : KattonRegistries<KattonMobEffectEntry>(id(MOD_ID, "effect")) {

        private val delegate = ReloadableBuiltInRegistry(
            builtInRegistry = BuiltInRegistries.MOB_EFFECT,
            registryKey = Registries.MOB_EFFECT,
            requiresIntrusiveHolders = false,
            unregisterOnReload = false
        )

        @Synchronized
        fun beginReload() {
            val removed = delegate.beginReload()
            removed.forEach { remove(it) }
        }

        fun newNative(
            id: Identifier,
            registerMode: RegisterMode = RegisterMode.AUTO,
            effectFactory: () -> MobEffect
        ): KattonMobEffectEntry {
            val effect = delegate.registerWithMode(id, registerMode, effectFactory)
            val entry = KattonMobEffectEntry(id = id, effect = effect)
            register(entry)
            delegate.markManaged(id, registerMode)
            return entry
        }

        fun registryHealth(): RegistryHealth = RegistryHealth(
            key = "effects",
            kattonEntries = size,
            managedTracked = delegate.managedIdsSnapshot().size,
            staleRetained = delegate.staleManagedIdsSnapshot().size,
            pendingRegistrations = 0
        )
    }

    /**
     * Registry for Katton blocks.
     */
    object BLOCKS : KattonRegistries<KattonBlockEntry>(id(MOD_ID, "block")) {

        private val delegate = ReloadableBuiltInRegistry(
            builtInRegistry = BuiltInRegistries.BLOCK,
            registryKey = Registries.BLOCK,
            requiresIntrusiveHolders = true,
            unregisterOnReload = false
        )

        @Synchronized
        fun beginReload() {
            val removed = delegate.beginReload()
            removed.forEach { remove(it) }
        }

        private fun blockProperties(id: Identifier) =
            BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id))

        fun newNative(
            id: Identifier,
            registerMode: RegisterMode = RegisterMode.AUTO,
            blockFactory: (BlockBehaviour.Properties) -> Block
        ): KattonBlockEntry {
            val block = when (registerMode) {
                RegisterMode.GLOBAL -> {
                    delegate.registerGlobal(id, blockFactory(blockProperties(id)))
                }
                RegisterMode.RELOADABLE -> {
                    delegate.ensureRegistered(
                        id = id,
                        builder = { blockFactory(blockProperties(id)) },
                        onExisting = {
                            @Suppress("DEPRECATION")
                            it.builtInRegistryHolder().tags = emptySet()
                        }
                    )
                }
                RegisterMode.AUTO -> {
                    if (Katton.globalState.after(LoadState.INIT)) {
                        delegate.ensureRegistered(
                            id = id,
                            builder = { blockFactory(blockProperties(id)) },
                            onExisting = {
                                @Suppress("DEPRECATION")
                                it.builtInRegistryHolder().tags = emptySet()
                            }
                        )
                    } else {
                        delegate.registerGlobal(id, blockFactory(blockProperties(id)))
                    }
                }
            }
            @Suppress("DEPRECATION")
            block.builtInRegistryHolder().tags = emptySet()
            DynamicRegistryHooks.onDynamicBlockRegistered(block)
            val entry = KattonBlockEntry(id = id, block = block)
            register(entry)
            delegate.markManaged(id, registerMode)
            return entry
        }

        fun registryHealth(): RegistryHealth = RegistryHealth(
            key = "blocks",
            kattonEntries = size,
            managedTracked = delegate.managedIdsSnapshot().size,
            staleRetained = delegate.staleManagedIdsSnapshot().size,
            pendingRegistrations = 0
        )
    }

    /**
     * Represents a registered entity type entry.
     */
    data class KattonEntityTypeEntry(
        override val id: Identifier,
        val entityType: EntityType<*>,
        val properties: KattonEntityProperties? = null,
        val spawnEggEntry: KattonItemEntry? = null
    ) : Identifiable

    /**
     * Represents a registered sound event entry.
     */
    data class KattonSoundEventEntry(
        override val id: Identifier,
        val soundEvent: net.minecraft.sounds.SoundEvent
    ) : Identifiable

    /**
     * Represents a registered particle type entry.
     */
    data class KattonParticleTypeEntry(
        override val id: Identifier,
        val particleType: net.minecraft.core.particles.ParticleType<*>
    ) : Identifiable

    /**
     * Registry for Katton entity types.
     */
    object ENTITY_TYPES : KattonRegistries<KattonEntityTypeEntry>(id(MOD_ID, "entity_type")) {

        private val delegate = ReloadableBuiltInRegistry(
            builtInRegistry = BuiltInRegistries.ENTITY_TYPE,
            registryKey = Registries.ENTITY_TYPE,
            requiresIntrusiveHolders = true,
            unregisterOnReload = false
        )

        /** Tracks entity IDs that have registered attributes, for cleanup on reload. */
        private val registeredAttributes = mutableSetOf<Identifier>()

        /** Tracks entity IDs that have registered spawn placements, for cleanup on reload. */
        private val registeredSpawnPlacements = mutableSetOf<Identifier>()

        @Synchronized
        fun beginReload() {
            val removed = delegate.beginReload()
            // Unregister attributes for removed entities
            removed.forEach { entityId ->
                unregisterAttributes(entityId)
                unregisterSpawnPlacement(entityId)
                remove(entityId)
            }
        }

        private fun clearEntityTypeTags(entityType: EntityType<*>) {
            runCatching {
                @Suppress("DEPRECATION")
                entityType.builtInRegistryHolder().tags = emptySet()
            }
        }

        fun newNative(
            id: Identifier,
            registerMode: RegisterMode = RegisterMode.AUTO,
            entityTypeFactory: () -> EntityType<*>
        ): KattonEntityTypeEntry {
            val entityType = delegate.registerWithMode(id, registerMode, entityTypeFactory)
            clearEntityTypeTags(entityType)
            val entry = KattonEntityTypeEntry(id = id, entityType = entityType)
            register(entry)
            delegate.markManaged(id, registerMode)
            return entry
        }

        /**
         * Registers a complete entity with attributes, spawn egg, and spawn placement.
         *
         * @param id Entity identifier
         * @param registerMode Registration mode
         * @param properties Entity configuration (dimensions, category, attributes, spawn egg, spawn placement)
         * @param entityFactory Factory that creates the EntityType from the configured properties
         * @return The registered KattonEntityTypeEntry
         */
        fun newNativeWithProperties(
            id: Identifier,
            registerMode: RegisterMode = RegisterMode.AUTO,
            properties: KattonEntityProperties,
            entityFactory: (KattonEntityProperties) -> EntityType<*>
        ): KattonEntityTypeEntry {
            val entityType = delegate.registerWithMode(id, registerMode) { entityFactory(properties) }
            clearEntityTypeTags(entityType)
            val isReloadable = registerMode == RegisterMode.RELOADABLE ||
                (registerMode == RegisterMode.AUTO && Katton.globalState.after(LoadState.INIT))

            @Suppress("UNCHECKED_CAST")
            val castType = entityType as? EntityType<LivingEntity>

            // Register attributes if configured and entity is a LivingEntity
            if (castType != null && properties.hasAttributes) {
                val attributeSupplier = properties.buildAttributes()
                if (attributeSupplier != null) {
                    registerEntityAttributes(castType, attributeSupplier, isReloadable)
                    registeredAttributes.add(id)
                }
            }

            // Register spawn placement if configured
            if (castType != null && properties.spawnPlacementType != null) {
                @Suppress("UNCHECKED_CAST")
                registerSpawnPlacement(
                    castType as EntityType<Mob>,
                    properties.spawnPlacementType!!,
                    properties.spawnHeightmap,
                    isReloadable
                ) { _, _, _, _, _ -> true }
                registeredSpawnPlacements.add(id)
            }

            // Register spawn egg if configured
            var spawnEggEntry: KattonItemEntry? = null
            if (properties.spawnEgg) {
                spawnEggEntry = registerSpawnEgg(id, entityType, registerMode)
            }

            val entry = KattonEntityTypeEntry(
                id = id,
                entityType = entityType,
                properties = properties,
                spawnEggEntry = spawnEggEntry
            )
            register(entry)
            delegate.markManaged(id, registerMode)
            return entry
        }

        /**
         * Registers entity attributes via the platform hook.
         *
         * @param entityType the entity type
         * @param attributeSupplier the attribute supplier
         * @param reloadable true for RELOADABLE/AUTO-after-init, false for GLOBAL/AUTO-at-init
         */
        private fun registerEntityAttributes(
            entityType: EntityType<out LivingEntity>,
            attributeSupplier: AttributeSupplier,
            reloadable: Boolean
        ) {
            EntityAttributeHooks.registerAttributes(entityType, attributeSupplier, reloadable)
        }

        /**
         * Unregisters attributes for an entity (hot-reload cleanup).
         */
        private fun unregisterAttributes(entityId: Identifier) {
            if (entityId !in registeredAttributes) return
            registeredAttributes.remove(entityId)
            val entityType = delegate.builtInRegistry.getOptional(entityId)
            if (entityType.isPresent) {
                DefaultAttributesHelper.unregister(entityType.get())
            }
        }

        /**
         * Registers a spawn placement rule, choosing platform-specific path based on mode.
         */
        private fun <T : Mob> registerSpawnPlacement(
            entityType: EntityType<T>,
            placementType: net.minecraft.world.entity.SpawnPlacementType,
            heightmap: net.minecraft.world.level.levelgen.Heightmap.Types,
            reloadable: Boolean,
            predicate: net.minecraft.world.entity.SpawnPlacements.SpawnPredicate<T>
        ) {
            if (reloadable) {
                SpawnPlacementHooks.registerReloadable(entityType, placementType, heightmap, predicate)
            } else {
                SpawnPlacementHooks.registerGlobal(entityType, placementType, heightmap, predicate)
            }
        }

        /**
         * Unregisters a spawn placement (hot-reload cleanup).
         */
        private fun unregisterSpawnPlacement(entityId: Identifier) {
            if (entityId !in registeredSpawnPlacements) return
            registeredSpawnPlacements.remove(entityId)
            val entityType = delegate.builtInRegistry.getOptional(entityId)
            if (entityType.isPresent) {
                SpawnPlacementHooks.unregister(entityType.get())
            }
        }

        /**
         * Registers a spawn egg item for an entity type.
         *
         * In MC 1.21.11+, spawn egg items use data components instead of constructor args.
         * The entity type is bound via `Item.Properties.spawnEgg(entityType)`.
         * Spawn egg colors are derived from the entity type automatically.
         */
        private fun registerSpawnEgg(
            entityId: Identifier,
            entityType: EntityType<*>,
            registerMode: RegisterMode
        ): KattonItemEntry {
            val eggId = entityId.withPath("${entityId.path}_spawn_egg")
            val eggProperties = KattonItemProperties(eggId).apply {
                stacksTo(64)
                setModel(id("minecraft:item/zombie_spawn_egg"))
                // Bind the entity type to the spawn egg item via data component.
                // In MC 1.21.11+, spawnEgg() sets DataComponents.ENTITY_DATA.
                spawnEgg(entityType)
            }
            return ITEMS.newNative(eggProperties, registerMode) { props ->
                SpawnEggItem(props)
            }
        }

        fun registryHealth(): RegistryHealth = RegistryHealth(
            key = "entity_types",
            kattonEntries = size,
            managedTracked = delegate.managedIdsSnapshot().size,
            staleRetained = delegate.staleManagedIdsSnapshot().size,
            pendingRegistrations = 0
        )
    }

    /**
     * Registry for Katton sound events.
     */
    object SOUND_EVENTS : KattonRegistries<KattonSoundEventEntry>(id(MOD_ID, "sound_event")) {

        private val delegate = ReloadableBuiltInRegistry(
            builtInRegistry = BuiltInRegistries.SOUND_EVENT,
            registryKey = Registries.SOUND_EVENT,
            requiresIntrusiveHolders = false,
            unregisterOnReload = false
        )

        @Synchronized
        fun beginReload() {
            val removed = delegate.beginReload()
            removed.forEach { remove(it) }
        }

        fun newNative(
            id: Identifier,
            registerMode: RegisterMode = RegisterMode.AUTO,
            soundEventFactory: () -> net.minecraft.sounds.SoundEvent
        ): KattonSoundEventEntry {
            val soundEvent = delegate.registerWithMode(id, registerMode, soundEventFactory)
            val entry = KattonSoundEventEntry(id = id, soundEvent = soundEvent)
            register(entry)
            delegate.markManaged(id, registerMode)
            return entry
        }

        fun registryHealth(): RegistryHealth = RegistryHealth(
            key = "sound_events",
            kattonEntries = size,
            managedTracked = delegate.managedIdsSnapshot().size,
            staleRetained = delegate.staleManagedIdsSnapshot().size,
            pendingRegistrations = 0
        )
    }

    /**
     * Registry for Katton particle types.
     */
    object PARTICLE_TYPES : KattonRegistries<KattonParticleTypeEntry>(id(MOD_ID, "particle_type")) {

        private val delegate = ReloadableBuiltInRegistry(
            builtInRegistry = BuiltInRegistries.PARTICLE_TYPE,
            registryKey = Registries.PARTICLE_TYPE,
            requiresIntrusiveHolders = true,
            unregisterOnReload = false
        )

        @Synchronized
        fun beginReload() {
            val removed = delegate.beginReload()
            removed.forEach { remove(it) }
        }

        fun newNative(
            id: Identifier,
            registerMode: RegisterMode = RegisterMode.AUTO,
            particleTypeFactory: () -> net.minecraft.core.particles.ParticleType<*>
        ): KattonParticleTypeEntry {
            val particleType = delegate.registerWithMode(id, registerMode, particleTypeFactory)
            val entry = KattonParticleTypeEntry(id = id, particleType = particleType)
            register(entry)
            delegate.markManaged(id, registerMode)
            return entry
        }

        fun registryHealth(): RegistryHealth = RegistryHealth(
            key = "particle_types",
            kattonEntries = size,
            managedTracked = delegate.managedIdsSnapshot().size,
            staleRetained = delegate.staleManagedIdsSnapshot().size,
            pendingRegistrations = 0
        )
    }

    /**
     * Represents a registered block entity type entry.
     */
    data class KattonBlockEntityTypeEntry(
        override val id: Identifier,
        val blockEntityType: net.minecraft.world.level.block.entity.BlockEntityType<*>
    ) : Identifiable

    /**
     * Registry for Katton block entity types.
     */
    object BLOCK_ENTITY_TYPES : KattonRegistries<KattonBlockEntityTypeEntry>(id(MOD_ID, "block_entity_type")) {

        private val delegate = ReloadableBuiltInRegistry(
            builtInRegistry = BuiltInRegistries.BLOCK_ENTITY_TYPE,
            registryKey = Registries.BLOCK_ENTITY_TYPE,
            requiresIntrusiveHolders = true,
            unregisterOnReload = false
        )

        @Synchronized
        fun beginReload() {
            val removed = delegate.beginReload()
            removed.forEach { remove(it) }
        }

        fun newNative(
            id: Identifier,
            registerMode: RegisterMode = RegisterMode.AUTO,
            blockEntityTypeFactory: () -> net.minecraft.world.level.block.entity.BlockEntityType<*>
        ): KattonBlockEntityTypeEntry {
            val blockEntityType = delegate.registerWithMode(id, registerMode, blockEntityTypeFactory)
            val entry = KattonBlockEntityTypeEntry(id = id, blockEntityType = blockEntityType)
            register(entry)
            delegate.markManaged(id, registerMode)
            return entry
        }

        fun registryHealth(): RegistryHealth = RegistryHealth(
            key = "block_entity_types",
            kattonEntries = size,
            managedTracked = delegate.managedIdsSnapshot().size,
            staleRetained = delegate.staleManagedIdsSnapshot().size,
            pendingRegistrations = 0
        )
    }

    /**
     * Represents a registered creative mode tab entry.
     */
    data class KattonCreativeTabEntry(
        override val id: Identifier,
        val tab: net.minecraft.world.item.CreativeModeTab
    ) : Identifiable

    /**
     * Registry for Katton creative mode tabs.
     */
    object CREATIVE_TABS : KattonRegistries<KattonCreativeTabEntry>(id(MOD_ID, "creative_tab")) {

        private val delegate = ReloadableBuiltInRegistry(
            builtInRegistry = BuiltInRegistries.CREATIVE_MODE_TAB,
            registryKey = Registries.CREATIVE_MODE_TAB,
            requiresIntrusiveHolders = false
        )

        @Synchronized
        fun beginReload() {
            val removed = delegate.beginReload()
            removed.forEach { remove(it) }
        }

        fun newNative(
            id: Identifier,
            registerMode: RegisterMode = RegisterMode.AUTO,
            tabFactory: () -> net.minecraft.world.item.CreativeModeTab
        ): KattonCreativeTabEntry {
            val tab = delegate.registerWithMode(id, registerMode, tabFactory)
            val entry = KattonCreativeTabEntry(id = id, tab = tab)
            register(entry)
            delegate.markManaged(id, registerMode)
            return entry
        }

        fun registryHealth(): RegistryHealth = RegistryHealth(
            key = "creative_tabs",
            kattonEntries = size,
            managedTracked = delegate.managedIdsSnapshot().size,
            staleRetained = delegate.staleManagedIdsSnapshot().size,
            pendingRegistrations = 0
        )
    }

    /**
     * Represents a registered data component type entry.
     */
    data class KattonDataComponentTypeEntry(
        override val id: Identifier,
        val componentType: DataComponentType<*>
    ) : Identifiable

    /**
     * Registry for Katton data component types.
     */
    object DATA_COMPONENT_TYPES : KattonRegistries<KattonDataComponentTypeEntry>(id(MOD_ID, "data_component_type")) {

        private val delegate = ReloadableBuiltInRegistry(
            builtInRegistry = BuiltInRegistries.DATA_COMPONENT_TYPE,
            registryKey = Registries.DATA_COMPONENT_TYPE,
            requiresIntrusiveHolders = false,
            unregisterOnReload = false
        )

        @Synchronized
        fun beginReload() {
            val removed = delegate.beginReload()
            removed.forEach { remove(it) }
        }

        fun <T : Any> newNative(
            id: Identifier,
            registerMode: RegisterMode = RegisterMode.AUTO,
            componentTypeFactory: () -> DataComponentType<T>
        ): KattonDataComponentTypeEntry {
            val componentType = delegate.registerWithMode(id, registerMode, componentTypeFactory)
            val entry = KattonDataComponentTypeEntry(id = id, componentType = componentType)
            register(entry)
            delegate.markManaged(id, registerMode)
            return entry
        }

        fun registryHealth(): RegistryHealth = RegistryHealth(
            key = "data_component_types",
            kattonEntries = size,
            managedTracked = delegate.managedIdsSnapshot().size,
            staleRetained = delegate.staleManagedIdsSnapshot().size,
            pendingRegistrations = 0
        )
    }

    /**
     * Registry for Katton entity renderers.
     *
     * Unlike other registries, entity renderers are not stored in a
     * Minecraft BuiltInRegistry. Instead, they are injected directly
     * into [EntityRendererRegistration.rend] via platform hooks.
     * Hot reload is handled by [EntityRendererRegistration].
     */
    object ENTITY_RENDERERS : KattonRegistries<Identifiable>(id(MOD_ID, "entity_renderer")) {
        // No BuiltInRegistry delegate — entity renderers use EntityRendererRegistration

        @Synchronized
        fun beginReload() {
            EntityRendererRegistration.beginReload()
            EntityRendererRegistration.beginModelLayerReload()
        }

        fun registryHealth(): RegistryHealth = RegistryHealth(
            key = "entity_renderers",
            kattonEntries = EntityRendererRegistration.managedCount(),
            managedTracked = EntityRendererRegistration.managedCount(),
            staleRetained = 0,
            pendingRegistrations = 0
        )
    }

    @JvmStatic
    fun flushPendingRegistrations() {
        EntityRendererRegistration.flushPendingRegistrations()
    }

    fun registryHealthSnapshot(): List<RegistryHealth> = listOf(
        ITEMS.registryHealth(),
        EFFECTS.registryHealth(),
        BLOCKS.registryHealth(),
        ENTITY_TYPES.registryHealth(),
        SOUND_EVENTS.registryHealth(),
        PARTICLE_TYPES.registryHealth(),
        BLOCK_ENTITY_TYPES.registryHealth(),
        CREATIVE_TABS.registryHealth(),
        DATA_COMPONENT_TYPES.registryHealth(),
        ENTITY_RENDERERS.registryHealth()
    )

    /**
     * Custom data component types for Katton.
     */
    object COMPONENTS {
        lateinit var KATTON_ID: DataComponentType<String>

        fun initialize() {
            if (::KATTON_ID.isInitialized) return

            @Suppress("UNCHECKED_CAST")
            val componentRegistry = BuiltInRegistries.DATA_COMPONENT_TYPE as net.minecraft.core.MappedRegistry<DataComponentType<*>>

            withUnfrozenRegistry(componentRegistry) {
                KATTON_ID = Registry.register(
                    BuiltInRegistries.DATA_COMPONENT_TYPE,
                    Identifier.fromNamespaceAndPath(MOD_ID, "id"),
                    DataComponentType.builder<String>().persistent(Codec.STRING).build()
                )
            }
        }
    }

    /**
     * Initializes all Katton registries.
     */
    fun initialize() {
        COMPONENTS.initialize()
    }
}
