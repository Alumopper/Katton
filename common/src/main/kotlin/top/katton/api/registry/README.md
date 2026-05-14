# Katton Registry API

This document covers every public registration function in `top.katton.api.registry`. All functions support hot reload when used with the appropriate [RegisterMode](#registermode).

---

## Table of Contents

- [RegisterMode](#registermode)
- [Hot Reload](#hot-reload)
- [Items](#items)
- [Blocks](#blocks)
- [Effects](#effects)
- [Sound Events](#sound-events)
- [Particle Types](#particle-types)
- [Block Entity Types](#block-entity-types)
- [Creative Mode Tabs](#creative-mode-tabs)
- [Data Component Types](#data-component-types)
- [Entity Types](#entity-types)
- [Entities](#entities)

---

## RegisterMode

Every registration function accepts a `registerMode: RegisterMode` parameter that controls how the entry behaves during `/katton reload`.

```kotlin
enum class RegisterMode {
    GLOBAL,
    WORLD,
    RELOADABLE
}
```

| Mode | Behavior |
|------|----------|
| `GLOBAL` | Permanent — only allowed during `LoadState.INIT`. Never affected by reload or world changes. Must be called from a global script pack or during mod initialization. |
| `WORLD` | Registered on world enter, unregistered on world leave. Survives `/katton reload` within the same world session. This is the default for most scripts. |
| `RELOADABLE` | Cleaned up and re-registered on every `/katton reload`. Use during development so changes take effect without restarting. |

The default value for every API is `RegisterMode.WORLD`.

---

## Hot Reload

Running `/katton reload` recompiles all source packs and refreshes every reloadable registration. The following cleanup happens for `RELOADABLE` entries:

- **Items** — Reloadable items are unregistered from `BuiltInRegistries.ITEM` and removed from Katton's internal `ITEMS` registry.
- **Blocks** — Reloadable blocks are unregistered from `BuiltInRegistries.BLOCK` and removed from Katton's internal `BLOCKS` registry. Dynamic registry hooks are also cleaned up.
- **Mob Effects** — Reloadable effects are unregistered from `BuiltInRegistries.MOB_EFFECT` and removed from Katton's internal `EFFECTS` registry.
- **Sound Events** — Reloadable sound events are unregistered from `BuiltInRegistries.SOUND_EVENT` and removed from Katton's internal `SOUND_EVENTS` registry.
- **Particle Types** — Reloadable particle types are unregistered from `BuiltInRegistries.PARTICLE_TYPE` and removed from Katton's internal `PARTICLE_TYPES` registry.
- **Block Entity Types** — Reloadable block entity types are unregistered from `BuiltInRegistries.BLOCK_ENTITY_TYPE` and removed from Katton's internal `BLOCK_ENTITY_TYPES` registry.
- **Creative Mode Tabs** — Reloadable tabs are unregistered from `BuiltInRegistries.CREATIVE_MODE_TAB` and removed from Katton's internal `CREATIVE_TABS` registry.
- **Data Component Types** — Reloadable component types are unregistered from `BuiltInRegistries.DATA_COMPONENT_TYPE` and removed from Katton's internal `DATA_COMPONENT_TYPES` registry.
- **Entity Types** — Reloadable entity types are unregistered from `BuiltInRegistries.ENTITY_TYPE` and removed from Katton's internal `ENTITY_TYPES` registry. Attributes and spawn placements tied to removed entities are also unregistered.

`GLOBAL` entries are skipped during reload cleanup (and banned outside `INIT`).  
`WORLD` entries survive `/katton reload` — they are only cleaned up when the server stops (world leave).  
`RELOADABLE` entries are cleaned up and re-registered on each `/katton reload`.

---

## Items

### `registerNativeItem(id: String, registerMode, configure, itemFactory)`

Registers a native `Item` with hot-reload support.

**Signature**

```kotlin
fun registerNativeItem(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    configure: KattonItemProperties.() -> Unit = {},
    itemFactory: (KattonItemProperties) -> Item
): KattonRegistry.KattonItemEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `String` | Item identifier (for example, `"mymod:my_item"`). |
| `registerMode` | `RegisterMode` | Registration mode (`GLOBAL`, `WORLD`, or `RELOADABLE`). |
| `configure` | `KattonItemProperties.() -> Unit` | Configuration lambda for item properties. |
| `itemFactory` | `(KattonItemProperties) -> Item` | Factory function that creates the `Item` instance. |

**Returns**

`KattonRegistry.KattonItemEntry` — the registered entry containing the identifier and the item instance.

**Example**

```kotlin
registerNativeItem(
    id = "mymod:custom_sword",
    registerMode = RegisterMode.RELOADABLE,
    configure = {
        setName(Component.literal("Custom Sword"))
        stacksTo(1)
    }
) { properties ->
    object : Item(properties) {
        override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
            // Custom behavior
            return InteractionResult.SUCCESS
        }
    }
}
```

**Reload Behavior**

- `RELOADABLE` — the item is removed and re-registered on reload. The item instance is reconstructed by calling `itemFactory` again.
- `GLOBAL` — the item is registered once and never removed.
- `WORLD` — survives `/katton reload`, unregistered on world leave.

---

### `registerNativeItem(id: Identifier, registerMode, configure, itemFactory)`

Registers a native `Item` with hot-reload support (`Identifier` overload).

**Signature**

```kotlin
fun registerNativeItem(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    configure: KattonItemProperties.() -> Unit = {},
    itemFactory: (KattonItemProperties) -> Item
): KattonRegistry.KattonItemEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `Identifier` | Item identifier. |
| `registerMode` | `RegisterMode` | Registration mode. |
| `configure` | `KattonItemProperties.() -> Unit` | Configuration lambda for item properties. |
| `itemFactory` | `(KattonItemProperties) -> Item` | Factory function that creates the `Item` instance. |

**Returns**

`KattonRegistry.KattonItemEntry`

**Reload Behavior**

Same as the `String` overload.

---

### `registerNativeItem(id: String, properties, registerMode, itemFactory)`

Registers a native `Item` with pre-configured properties.

**Signature**

```kotlin
fun registerNativeItem(
    id: String,
    properties: KattonItemProperties,
    registerMode: RegisterMode = RegisterMode.WORLD,
    itemFactory: (KattonItemProperties) -> Item
): KattonRegistry.KattonItemEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `String` | Item identifier. |
| `properties` | `KattonItemProperties` | Pre-configured item properties. |
| `registerMode` | `RegisterMode` | Registration mode. |
| `itemFactory` | `(KattonItemProperties) -> Item` | Factory function that creates the `Item` instance. |

**Returns**

`KattonRegistry.KattonItemEntry`

**Reload Behavior**

Same as the `String` overload with `configure`.

---

## Blocks

### `registerNativeBlock(id: String, registerMode, blockFactory)`

Registers a native `Block` with hot-reload support.

**Signature**

```kotlin
fun registerNativeBlock(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    blockFactory: (BlockBehaviour.Properties) -> Block
): KattonRegistry.KattonBlockEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `String` | Block identifier (for example, `"mymod:custom_block"`). |
| `registerMode` | `RegisterMode` | Registration mode. |
| `blockFactory` | `(BlockBehaviour.Properties) -> Block` | Factory function that receives `BlockBehaviour.Properties` and returns the `Block` instance. |

**Returns**

`KattonRegistry.KattonBlockEntry`

**Example**

```kotlin
registerNativeBlock("mymod:custom_block") { properties ->
    object : Block(properties) {
        // Custom block behavior
    }
}
```

**Reload Behavior**

- `RELOADABLE` — the block is removed and re-registered on reload. Existing block states in the world may become invalid until the block is re-registered.
- `GLOBAL` — the block persists across reloads.
- `WORLD` — same timing rules as items.

---

### `registerNativeBlock(id: Identifier, registerMode, blockFactory)`

Registers a native `Block` with hot-reload support (`Identifier` overload).

**Signature**

```kotlin
fun registerNativeBlock(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    blockFactory: (BlockBehaviour.Properties) -> Block
): KattonRegistry.KattonBlockEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `Identifier` | Block identifier. |
| `registerMode` | `RegisterMode` | Registration mode. |
| `blockFactory` | `(BlockBehaviour.Properties) -> Block` | Factory function that creates the `Block` instance. |

**Returns**

`KattonRegistry.KattonBlockEntry`

**Reload Behavior**

Same as the `String` overload.

---

### `createSimpleBlock(properties)`

Utility factory for quickly creating a simple custom block.

**Signature**

```kotlin
fun createSimpleBlock(
    properties: BlockBehaviour.Properties = BlockBehaviour.Properties.of()
): Block
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `properties` | `BlockBehaviour.Properties` | Block behavior properties. Defaults to basic properties. |

**Returns**

`Block` — a plain `Block` instance.

**Example**

```kotlin
val block = createSimpleBlock(BlockBehaviour.Properties.of().strength(1.5f))
```

---

## Effects

### `registerNativeEffect(id: String, registerMode, effectFactory)`

Registers a native `MobEffect` with hot-reload support.

**Signature**

```kotlin
fun registerNativeEffect(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    effectFactory: () -> MobEffect
): KattonRegistry.KattonMobEffectEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `String` | Effect identifier (for example, `"mymod:custom_effect"`). |
| `registerMode` | `RegisterMode` | Registration mode. |
| `effectFactory` | `() -> MobEffect` | Factory function that creates the `MobEffect` instance. |

**Returns**

`KattonRegistry.KattonMobEffectEntry`

**Example**

```kotlin
registerNativeEffect("mymod:custom_effect") {
    object : MobEffect(MobEffectCategory.BENEFICIAL, 0xFF5500) {
        override fun applyEffectTick(entity: LivingEntity, amplifier: Int) {
            // Custom tick logic
        }
    }
}
```

**Reload Behavior**

- `RELOADABLE` — the effect is removed and re-registered on reload. Active effect instances on entities will reference the old instance until reapplied.
- `GLOBAL` — the effect persists across reloads.
- `WORLD` — same timing rules as items.

---

### `registerNativeEffect(id: Identifier, registerMode, effectFactory)`

Registers a native `MobEffect` with hot-reload support (`Identifier` overload).

**Signature**

```kotlin
fun registerNativeEffect(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    effectFactory: () -> MobEffect
): KattonRegistry.KattonMobEffectEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `Identifier` | Effect identifier. |
| `registerMode` | `RegisterMode` | Registration mode. |
| `effectFactory` | `() -> MobEffect` | Factory function that creates the `MobEffect` instance. |

**Returns**

`KattonRegistry.KattonMobEffectEntry`

**Reload Behavior**

Same as the `String` overload.

---

### `createSimpleEffect(category, color)`

Utility factory for quickly creating a simple custom `MobEffect`.

**Signature**

```kotlin
fun createSimpleEffect(
    category: MobEffectCategory,
    color: Int
): MobEffect
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `category` | `MobEffectCategory` | The effect category (`BENEFICIAL`, `HARMFUL`, or `NEUTRAL`). |
| `color` | `Int` | The effect color in RGB format. |

**Returns**

`MobEffect` — a basic `MobEffect` instance with no custom tick logic.

**Example**

```kotlin
val effect = createSimpleEffect(MobEffectCategory.HARMFUL, 0xFF0000)
```

---

## Sound Events

### `registerNativeSoundEvent(id: String, registerMode, soundEventFactory)`

Registers a native `SoundEvent` with hot-reload support.

**Signature**

```kotlin
fun registerNativeSoundEvent(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    soundEventFactory: () -> SoundEvent
): KattonRegistry.KattonSoundEventEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `String` | Sound identifier (for example, `"mymod:custom_sound"`). |
| `registerMode` | `RegisterMode` | Registration mode. |
| `soundEventFactory` | `() -> SoundEvent` | Factory function that creates the `SoundEvent` instance. |

**Returns**

`KattonRegistry.KattonSoundEventEntry`

**Example**

```kotlin
registerNativeSoundEvent("mymod:custom_sound") {
    SoundEvent.createVariableRangeEvent(id("mymod:custom_sound"))
}
```

**Reload Behavior**

- `RELOADABLE` — the sound event is removed and re-registered on reload.
- `GLOBAL` — the sound event persists across reloads.
- `WORLD` — same timing rules as items.

---

### `registerNativeSoundEvent(id: Identifier, registerMode, soundEventFactory)`

Registers a native `SoundEvent` with hot-reload support (`Identifier` overload).

**Signature**

```kotlin
fun registerNativeSoundEvent(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    soundEventFactory: () -> SoundEvent
): KattonRegistry.KattonSoundEventEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `Identifier` | Sound identifier. |
| `registerMode` | `RegisterMode` | Registration mode. |
| `soundEventFactory` | `() -> SoundEvent` | Factory function that creates the `SoundEvent` instance. |

**Returns**

`KattonRegistry.KattonSoundEventEntry`

**Reload Behavior**

Same as the `String` overload.

---

### `createVariableRangeSoundEvent(id)`

Utility factory for quickly creating a variable-range `SoundEvent`.

**Signature**

```kotlin
fun createVariableRangeSoundEvent(id: String): SoundEvent
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `String` | The sound identifier. |

**Returns**

`SoundEvent` — a new `SoundEvent` with variable range.

**Example**

```kotlin
val sound = createVariableRangeSoundEvent("mymod:ambient_sound")
```

---

## Particle Types

### `registerNativeParticleType(id: String, registerMode, particleTypeFactory)`

Registers a native `ParticleType` with hot-reload support.

**Signature**

```kotlin
fun registerNativeParticleType(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    particleTypeFactory: () -> ParticleType<*>
): KattonRegistry.KattonParticleTypeEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `String` | Particle identifier (for example, `"mymod:custom_particle"`). |
| `registerMode` | `RegisterMode` | Registration mode. |
| `particleTypeFactory` | `() -> ParticleType<*>` | Factory function that creates the `ParticleType` instance. |

**Returns**

`KattonRegistry.KattonParticleTypeEntry`

**Example**

```kotlin
registerNativeParticleType("mymod:sparkle") {
    SimpleParticleType(false)
}
```

**Reload Behavior**

- `RELOADABLE` — the particle type is removed and re-registered on reload. Existing particles in the world will use the old type until they despawn.
- `GLOBAL` — the particle type persists across reloads.
- `WORLD` — same timing rules as items.

---

### `registerNativeParticleType(id: Identifier, registerMode, particleTypeFactory)`

Registers a native `ParticleType` with hot-reload support (`Identifier` overload).

**Signature**

```kotlin
fun registerNativeParticleType(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    particleTypeFactory: () -> ParticleType<*>
): KattonRegistry.KattonParticleTypeEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `Identifier` | Particle identifier. |
| `registerMode` | `RegisterMode` | Registration mode. |
| `particleTypeFactory` | `() -> ParticleType<*>` | Factory function that creates the `ParticleType` instance. |

**Returns**

`KattonRegistry.KattonParticleTypeEntry`

**Reload Behavior**

Same as the `String` overload.

---

## Block Entity Types

### `registerNativeBlockEntityType(id: String, registerMode, blockEntityTypeFactory)`

Registers a native `BlockEntityType` with hot-reload support.

**Signature**

```kotlin
fun registerNativeBlockEntityType(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    blockEntityTypeFactory: () -> BlockEntityType<*>
): KattonRegistry.KattonBlockEntityTypeEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `String` | BlockEntityType identifier (for example, `"mymod:custom_block_entity"`). |
| `registerMode` | `RegisterMode` | Registration mode. |
| `blockEntityTypeFactory` | `() -> BlockEntityType<*>` | Factory function that creates the `BlockEntityType` instance. |

**Returns**

`KattonRegistry.KattonBlockEntityTypeEntry`

**Example**

```kotlin
registerNativeBlockEntityType("mymod:chest_entity") {
    BlockEntityType.Builder.of(
        ::CustomBlockEntity,
        ModBlocks.CUSTOM_BLOCK
    ).build(null)
}
```

**Reload Behavior**

- `RELOADABLE` — the block entity type is removed and re-registered on reload. Existing block entities in the world may become invalid until the type is re-registered.
- `GLOBAL` — the block entity type persists across reloads.
- `WORLD` — same timing rules as items.

---

### `registerNativeBlockEntityType(id: Identifier, registerMode, blockEntityTypeFactory)`

Registers a native `BlockEntityType` with hot-reload support (`Identifier` overload).

**Signature**

```kotlin
fun registerNativeBlockEntityType(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    blockEntityTypeFactory: () -> BlockEntityType<*>
): KattonRegistry.KattonBlockEntityTypeEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `Identifier` | BlockEntityType identifier. |
| `registerMode` | `RegisterMode` | Registration mode. |
| `blockEntityTypeFactory` | `() -> BlockEntityType<*>` | Factory function that creates the `BlockEntityType` instance. |

**Returns**

`KattonRegistry.KattonBlockEntityTypeEntry`

**Reload Behavior**

Same as the `String` overload.

---

## Creative Mode Tabs

### `registerNativeCreativeTab(id: String, registerMode, tabFactory)`

Registers a native `CreativeModeTab` with hot-reload support.

**Signature**

```kotlin
fun registerNativeCreativeTab(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    tabFactory: () -> CreativeModeTab
): KattonRegistry.KattonCreativeTabEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `String` | Tab identifier (for example, `"mymod:custom_tab"`). |
| `registerMode` | `RegisterMode` | Registration mode. |
| `tabFactory` | `() -> CreativeModeTab` | Factory function that creates the `CreativeModeTab` instance. |

**Returns**

`KattonRegistry.KattonCreativeTabEntry`

**Example**

```kotlin
registerNativeCreativeTab("mymod:custom_tab") {
    CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
        .title(Component.literal("Custom Tab"))
        .icon { ItemStack(Items.DIAMOND) }
        .displayItems { _, items -> items.accept(Items.DIAMOND) }
        .build()
}
```

**Reload Behavior**

- `RELOADABLE` — the tab is removed and re-registered on reload.
- `GLOBAL` — the tab persists across reloads.
- `WORLD` — same timing rules as items.

---

### `registerNativeCreativeTab(id: Identifier, registerMode, tabFactory)`

Registers a native `CreativeModeTab` with hot-reload support (`Identifier` overload).

**Signature**

```kotlin
fun registerNativeCreativeTab(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    tabFactory: () -> CreativeModeTab
): KattonRegistry.KattonCreativeTabEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `Identifier` | Tab identifier. |
| `registerMode` | `RegisterMode` | Registration mode. |
| `tabFactory` | `() -> CreativeModeTab` | Factory function that creates the `CreativeModeTab` instance. |

**Returns**

`KattonRegistry.KattonCreativeTabEntry`

**Reload Behavior**

Same as the `String` overload.

---

### `reorderCreativeTab(tab, after, before)`

Reorders a creative tab within the global tabs list.

Uses reflection on Minecraft's internal `CreativeModeTabs` to move a tab after or before another tab. If reflection fails, the tab stays at its default position.

**Signature**

```kotlin
fun reorderCreativeTab(
    tab: CreativeModeTab,
    after: String? = null,
    before: String? = null
)
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `tab` | `CreativeModeTab` | The tab to reorder. |
| `after` | `String?` | Move after this tab identifier (for example, `"minecraft:building_blocks"`). |
| `before` | `String?` | Move before this tab identifier. |

**Returns**

`Unit`

**Example**

```kotlin
val entry = registerNativeCreativeTab("mymod:custom_tab") { ... }
reorderCreativeTab(entry.tab, after = "minecraft:building_blocks")
```

**Reload Behavior**

This function mutates the live tabs list immediately. It does not participate in reload cleanup because it is a one-time reordering operation. If the tab is `RELOADABLE`, it will be removed and re-registered, so you may need to call `reorderCreativeTab` again after reload.

---

## Data Component Types

### `registerNativePersistentDataComponentType(id: String, registerMode, codec)`

Registers a persistent (saved to disk) `DataComponentType` with hot-reload support.

Persistent components are serialized using their codec and saved with the item. Use this for data that must survive across save and load cycles.

**Signature**

```kotlin
fun <T : Any> registerNativePersistentDataComponentType(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    codec: Codec<T>
): KattonRegistry.KattonDataComponentTypeEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `String` | Component identifier (for example, `"mymod:custom_data"`). |
| `registerMode` | `RegisterMode` | Registration mode. |
| `codec` | `Codec<T>` | The codec for serializing and deserializing the component value. |

**Returns**

`KattonRegistry.KattonDataComponentTypeEntry`

**Example**

```kotlin
registerNativePersistentDataComponentType(
    id = "mymod:charge_level",
    codec = Codec.INT
)
```

**Reload Behavior**

- `RELOADABLE` — the component type is removed and re-registered on reload. Items in the world that carry this component may lose the data until the type is re-registered.
- `GLOBAL` — the component type persists across reloads.
- `WORLD` — same timing rules as items.

---

### `registerNativePersistentDataComponentType(id: Identifier, registerMode, codec)`

Registers a persistent `DataComponentType` with hot-reload support (`Identifier` overload).

**Signature**

```kotlin
fun <T : Any> registerNativePersistentDataComponentType(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    codec: Codec<T>
): KattonRegistry.KattonDataComponentTypeEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `Identifier` | Component identifier. |
| `registerMode` | `RegisterMode` | Registration mode. |
| `codec` | `Codec<T>` | The codec for serializing and deserializing the component value. |

**Returns**

`KattonRegistry.KattonDataComponentTypeEntry`

**Reload Behavior**

Same as the `String` overload.

---

### `registerNativeNetworkDataComponentType(id: String, registerMode, streamCodec)`

Registers a network-synchronized `DataComponentType` with hot-reload support.

Network-synchronized components are sent to the client but NOT saved to disk. Use this for data that is computed at runtime, such as render-only state.

**Signature**

```kotlin
fun <T : Any> registerNativeNetworkDataComponentType(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    streamCodec: StreamCodec<*, T>
): KattonRegistry.KattonDataComponentTypeEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `String` | Component identifier (for example, `"mymod:sync_data"`). |
| `registerMode` | `RegisterMode` | Registration mode. |
| `streamCodec` | `StreamCodec<*, T>` | The stream codec for network synchronization. |

**Returns**

`KattonRegistry.KattonDataComponentTypeEntry`

**Example**

```kotlin
registerNativeNetworkDataComponentType(
    id = "mymod:glow_color",
    streamCodec = ByteBufCodecs.INT.mapStream { it }
)
```

**Reload Behavior**

- `RELOADABLE` — the component type is removed and re-registered on reload.
- `GLOBAL` — the component type persists across reloads.
- `WORLD` — same timing rules as items.

---

### `registerNativeNetworkDataComponentType(id: Identifier, registerMode, streamCodec)`

Registers a network-synchronized `DataComponentType` with hot-reload support (`Identifier` overload).

**Signature**

```kotlin
fun <T : Any> registerNativeNetworkDataComponentType(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    streamCodec: StreamCodec<*, T>
): KattonRegistry.KattonDataComponentTypeEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `Identifier` | Component identifier. |
| `registerMode` | `RegisterMode` | Registration mode. |
| `streamCodec` | `StreamCodec<*, T>` | The stream codec for network synchronization. |

**Returns**

`KattonRegistry.KattonDataComponentTypeEntry`

**Reload Behavior**

Same as the `String` overload.

---

## Entity Types

### `registerNativeEntityType(id: String, registerMode, entityTypeFactory)`

Registers a native `EntityType` with hot-reload support.

This is a lower-level API that only registers the `EntityType` itself. For complete entity registration, including attributes, spawn egg, and spawn placement, use [registerNativeEntity](#registernativeentity) instead.

**Signature**

```kotlin
fun registerNativeEntityType(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    entityTypeFactory: () -> EntityType<*>
): KattonRegistry.KattonEntityTypeEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `String` | Entity identifier (for example, `"mymod:custom_entity"`). |
| `registerMode` | `RegisterMode` | Registration mode. |
| `entityTypeFactory` | `() -> EntityType<*>` | Factory function that creates the `EntityType` instance. |

**Returns**

`KattonRegistry.KattonEntityTypeEntry`

**Example**

```kotlin
registerNativeEntityType("mymod:projectile") {
    EntityType.Builder.of(::CustomProjectile, MobCategory.MISC)
        .sized(0.5f, 0.5f)
        .clientTrackingRange(4)
        .updateInterval(10)
        .build("mymod:projectile")
}
```

**Reload Behavior**

- `RELOADABLE` — the entity type is removed and re-registered on reload. Existing entities in the world may become invalid until the type is re-registered.
- `GLOBAL` — the entity type persists across reloads.
- `WORLD` — same timing rules as items.

---

### `registerNativeEntityType(id: Identifier, registerMode, entityTypeFactory)`

Registers a native `EntityType` with hot-reload support (`Identifier` overload).

**Signature**

```kotlin
fun registerNativeEntityType(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    entityTypeFactory: () -> EntityType<*>
): KattonRegistry.KattonEntityTypeEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `Identifier` | Entity identifier. |
| `registerMode` | `RegisterMode` | Registration mode. |
| `entityTypeFactory` | `() -> EntityType<*>` | Factory function that creates the `EntityType` instance. |

**Returns**

`KattonRegistry.KattonEntityTypeEntry`

**Reload Behavior**

Same as the `String` overload.

---

## Entities

### `registerNativeEntity(id: String, registerMode, configure, entityFactory)`

Registers a complete native `Entity` with hot-reload support.

This is the primary API for registering custom entities from scripts. It handles `EntityType` registration plus optional attributes, spawn egg, and spawn placement configuration in a single call.

**Signature**

```kotlin
fun registerNativeEntity(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    configure: KattonEntityProperties.() -> Unit = {},
    entityFactory: (KattonEntityProperties) -> EntityType<*>
): KattonRegistry.KattonEntityTypeEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `String` | Entity identifier (for example, `"mymod:custom_mob"`). |
| `registerMode` | `RegisterMode` | Registration mode. |
| `configure` | `KattonEntityProperties.() -> Unit` | Configuration lambda for entity properties (dimensions, category, attributes, spawn egg, spawn placement). |
| `entityFactory` | `(KattonEntityProperties) -> EntityType<*>` | Factory function that creates the `EntityType` instance. |

**Returns**

`KattonRegistry.KattonEntityTypeEntry`

**Example**

```kotlin
registerNativeEntity(
    id = "mymod:custom_mob",
    registerMode = RegisterMode.GLOBAL,
    configure = {
        dimensions(0.6f, 1.8f)
        category = MobCategory.CREATURE
        maxHealth(20.0)
        movementSpeed(0.25)
        followRange(32.0)
        withSpawnEgg()
        spawnPlacement(SpawnPlacementTypes.ON_GROUND)
    }
) { props ->
    EntityType.Builder.create<CustomMob>(::CustomMob, props.category)
        .dimensions(props.dimensions.width, props.dimensions.height)
        .clientTrackingRange(props.clientTrackingRange)
        .updateInterval(props.trackingTickInterval)
        .build(ResourceKey.create(Registries.ENTITY_TYPE, id(props.id)))
}
```

**Reload Behavior**

- `RELOADABLE` — the entity type, attributes, spawn placement, and spawn egg item are all removed and re-registered on reload.
- `GLOBAL` — everything persists across reloads.
- `WORLD` — same timing rules as items.

---

### `registerNativeEntity(id: Identifier, registerMode, configure, entityFactory)`

Registers a complete native `Entity` with hot-reload support (`Identifier` overload).

**Signature**

```kotlin
fun registerNativeEntity(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    configure: KattonEntityProperties.() -> Unit = {},
    entityFactory: (KattonEntityProperties) -> EntityType<*>
): KattonRegistry.KattonEntityTypeEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `Identifier` | Entity identifier. |
| `registerMode` | `RegisterMode` | Registration mode. |
| `configure` | `KattonEntityProperties.() -> Unit` | Configuration lambda for entity properties. |
| `entityFactory` | `(KattonEntityProperties) -> EntityType<*>` | Factory function that creates the `EntityType` instance. |

**Returns**

`KattonRegistry.KattonEntityTypeEntry`

**Reload Behavior**

Same as the `String` overload.

---

### `registerEntityAttributes(id, entityType, configure, reloadable)`

Registers entity default attributes independently.

Use this when you want to register attributes for an entity that was already registered via [registerNativeEntityType](#registernativeentitytype). For new entities, prefer [registerNativeEntity](#registernativeentity) which handles attributes automatically.

**Signature**

```kotlin
fun registerEntityAttributes(
    id: String,
    entityType: EntityType<out LivingEntity>,
    configure: KattonEntityProperties.() -> Unit,
    reloadable: Boolean = true
)
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `String` | Entity identifier. |
| `entityType` | `EntityType<out LivingEntity>` | The already-registered entity type. |
| `configure` | `KattonEntityProperties.() -> Unit` | Configuration lambda for attributes. |
| `reloadable` | `Boolean` | `true` for RELOADABLE or WORLD, `false` for GLOBAL. Defaults to `true`. |

**Returns**

`Unit`

**Example**

```kotlin
registerEntityAttributes(
    id = "mymod:boss",
    entityType = ModEntityTypes.BOSS,
    configure = {
        maxHealth(200.0)
        attackDamage(15.0)
        armor(10.0)
    }
)
```

**Reload Behavior**

- `reloadable = true` — attributes are registered through the reloadable path and will be cleaned up on `/katton reload`.
- `reloadable = false` — attributes are registered globally and persist across reloads.

---

### `registerSpawnPlacement(entityType, placementType, heightmap, predicate, reloadable)`

Registers a spawn placement rule independently.

**Signature**

```kotlin
fun <T : Mob> registerSpawnPlacement(
    entityType: EntityType<T>,
    placementType: SpawnPlacementType,
    heightmap: Heightmap.Types = Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
    predicate: SpawnPlacements.SpawnPredicate<T>,
    reloadable: Boolean = true
)
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `entityType` | `EntityType<T>` | The entity type. |
| `placementType` | `SpawnPlacementType` | Where the entity can spawn (for example, `ON_GROUND`, `IN_WATER`). |
| `heightmap` | `Heightmap.Types` | The heightmap type for spawn checks. Defaults to `MOTION_BLOCKING_NO_LEAVES`. |
| `predicate` | `SpawnPlacements.SpawnPredicate<T>` | Custom spawn predicate. |
| `reloadable` | `Boolean` | `true` for RELOADABLE or WORLD, `false` for GLOBAL. Defaults to `true`. |

**Returns**

`Unit`

**Example**

```kotlin
registerSpawnPlacement(
    entityType = ModEntityTypes.CUSTOM_MOB,
    placementType = SpawnPlacementTypes.ON_GROUND,
    heightmap = Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
    predicate = SpawnPlacements.SpawnPredicate { _, _, _, _, _ -> true }
)
```

**Reload Behavior**

- `reloadable = true` — the spawn placement is registered through the reloadable path and will be cleaned up on `/katton reload`.
- `reloadable = false` — the spawn placement persists across reloads.

---

### `registerSpawnEgg(id: String, entityType, registerMode)`

Registers a spawn egg item for an entity type independently.

Use this to create a spawn egg for an entity registered via [registerNativeEntityType](#registernativeentitytype). For new entities, prefer [registerNativeEntity](#registernativeentity) with `withSpawnEgg()`.

In Minecraft 1.21.11+, spawn egg colors are derived from the entity type automatically.

**Signature**

```kotlin
fun registerSpawnEgg(
    id: String,
    entityType: EntityType<out Mob>,
    registerMode: RegisterMode = RegisterMode.WORLD
): KattonRegistry.KattonItemEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `String` | Spawn egg item identifier (for example, `"mymod:custom_mob_spawn_egg"`). |
| `entityType` | `EntityType<out Mob>` | The entity type this egg spawns. |
| `registerMode` | `RegisterMode` | Registration mode. |

**Returns**

`KattonRegistry.KattonItemEntry`

**Example**

```kotlin
registerSpawnEgg(
    id = "mymod:custom_mob_spawn_egg",
    entityType = ModEntityTypes.CUSTOM_MOB,
    registerMode = RegisterMode.RELOADABLE
)
```

**Reload Behavior**

- `RELOADABLE` — the spawn egg item is removed and re-registered on reload.
- `GLOBAL` — the spawn egg item persists across reloads.
- `WORLD` — same timing rules as items.

---

### `registerSpawnEgg(id: Identifier, entityType, registerMode)`

Registers a spawn egg item for an entity type independently (`Identifier` overload).

**Signature**

```kotlin
fun registerSpawnEgg(
    id: Identifier,
    entityType: EntityType<out Mob>,
    registerMode: RegisterMode = RegisterMode.WORLD
): KattonRegistry.KattonItemEntry
```

**Parameters**

| Name | Type | Description |
|------|------|-------------|
| `id` | `Identifier` | Spawn egg item identifier. |
| `entityType` | `EntityType<out Mob>` | The entity type this egg spawns. |
| `registerMode` | `RegisterMode` | Registration mode. |

**Returns**

`KattonRegistry.KattonItemEntry`

**Reload Behavior**

Same as the `String` overload.
