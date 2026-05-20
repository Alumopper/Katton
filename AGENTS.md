# Katton

Minecraft Fabric, NeoForge mod & Paper plugin for Kotlin scripting with hot reload. Target MC version 26.1.2.

## Build & Run

- **Java 25** required (enforced by `project.options.release = 25` and `JVM target = 25`).
- **Gradle 9.3.0** (wrapper checked in at `gradle/wrapper/gradle-wrapper.properties`).
- Run via Gradle:
  - `./gradlew :fabric:runClient` — Fabric client
  - `./gradlew :neoforge:runClient` — NeoForge client
  - `./gradlew :paper:runServer` — Paper server (via `xyz.jpenilla.run-paper`)
- Build: `./gradlew build` (produces jars for each submodule).
  - Paper uses **Shadow** (`com.gradleup.shadow`) to produce a fat jar with embedded Kotlin runtime.
- Configuration cache disabled (`org.gradle.configuration-cache=false` in `gradle.properties`) — IntelliJ + Fabric Loom incompatibility.
- **No tests** exist anywhere in the repo (a `test` sourceSet exists in root `build.gradle` pointing to `Katton-Example/`, but no test framework is declared and no actual test files exist).

## Project Structure

Multi-module Gradle build (Groovy DSL, Kotlin plugins via `org.jetbrains.kotlin.jvm` 2.3.10):

| Module | Build system | Entrypoint |
|---|---|---|
| `:common` | Fabric Loom (access widener) + Kotlin JVM | `top.katton.Katton.java` — platform-agnostic init utility |
| `:fabric` | Fabric Loom | `top.katton.KattonFabric.java` — `ModInitializer` |
| | | `top.katton.KattonClientFabric.java` — `ClientModInitializer` |
| `:neoforge` | NeoForge ModDev (`net.neoforged.moddev` 2.0.141) | `top.katton.KattonNeoForge.java` — `@Mod` |
| | | `top.katton.KattonClientNeoForge.java` — `@EventBusSubscriber` (client) |
| `:paper` | Paperweight userdev (`io.papermc.paperweight.userdev` 2.0.0-beta.21) + Shadow | `top.katton.paper.KattonPaperPlugin.java` — `JavaPlugin` |
| `:buildSrc` | Custom Kotlin plugin | `ApiDocGeneratorPlugin` — generates VitePress API docs |

- Common logic (script engine, networking, registry, API) lives in `common/src/main/kotlin/top/katton/` and `common/src/main/java/top/katton/`.
- Platform-specific event APIs mirror each other: **14 event classes** each under `fabric/`, `neoforge/`, and `paper/` (see [Event System](#event-system)).
- Mixins: Fabric mixins in Java under `fabric/src/main/java/top/katton/mixin/` (**16 files**), NeoForge mixins under `neoforge/src/main/java/top/katton/mixin/` (**30 files**). Paper has **no mixins** — it operates as a standard Paper plugin. A `common/src/main/resources/kts4mc.mixins.json` exists with an empty mixin list.
- Kotlin scripting libraries are embedded (`include` in Fabric, `jarJar` in NeoForge, `implementation`→shadow in Paper, `compileOnly` in common).
- NeoForge uses **access transformers** (`src/main/resources/META-INF/accesstransformer.cfg`) in addition to the access widener from common. Paper uses **no access transformers**.
- A **stale** `common/src/main/resources/fabric.mod.json` exists referencing non-existent entrypoint classes (`Katton` and `KattonClient` as Fabric entrypoints) — ignore it; only the fabric-module copy is active.

### Common Source Package Map

```
common/src/main/kotlin/top/katton/
├── api/          │ Public script API (annotations, registry functions, inject, render, mod, DP callers, datapacks)
│   ├── inject/     │ UnsafeApi.kt — before/after/replace/redirect hooks (ByteBuddy)
│   ├── registry/   │ Per-type registry helpers (Item, Block, Entity, etc.)
│   ├── mod/        │ Item/Block modification API (KubeJS-like)
│   ├── dpcaller/   │ 13 DP caller wrappers (BlockApi, EntityApi, ItemApi, etc.)
│   ├── datapack/   │ Recipes.kt, Tags.kt
│   ├── event/      │ KattonEventsArg.kt — shared event argument data classes
│   └── event/managed/ │ ManagedEvents.kt — cross-platform managed listener abstraction
├── bridge/       │ KattonBridge.kt — inter-module bridge
├── bridger/      │ Entity/LootTable/Enchantment bridges + EventResult/ModifyContext
├── client/       │ ScriptPackUi.kt, ReloadProgressOverlay.kt, ReloadProgressState
├── command/      │ ScriptCommand.kt — `/katton` command tree
├── datapack/     │ ServerDatapackManager.kt — reloadable server datapack injection
├── engine/       │ ScriptEngine.kt, ScriptEnvironment.kt, InjectionManager.kt, JavaCompilationUtil.kt
├── network/      │ ServerNetworking.kt, 3 packet types (request/hashlist/bundle)
├── pack/         │ ScriptPackManager.kt, ScriptPackManifest.kt, ScriptPackScope.kt, ScriptPackTypes.kt, ServerPackCacheManager.kt
├── platform/     │ EntityRendererHooks.kt — platform-abstraction interfaces
├── registry/     │ KattonRegistry.kt (10 sub-registries), ReloadableBuiltInRegistry.kt, OwnershipTracker.kt, RegistryMutationUtil.kt
└── util/         │ Event.kt, Extension.kt, Result.kt, JResult.kt, ReflectUtil.kt, ScriptExecutionContext.kt, EntitySelectorBuilder.kt
```

### Paper Source Package Map

```
paper/src/main/
├── java/top/katton/paper/
│   ├── KattonPaperPlugin.java    │ Paper plugin entrypoint (JavaPlugin + Listener, 160 lines)
│   └── KattonPaperCommand.java   │ /katton command via Brigadier BasicCommand
├── kotlin/top/katton/
│   ├── paper/
│   │   ├── PaperNmsBridge.kt     │ Bukkit↔NMS type conversion (~30 methods, 275 lines)
│   │   └── FoliaSchedulerApi.kt  │ Region-aware scheduling API (entity/position/global)
│   ├── api/event/                │ 14 Paper event bridge classes (mirrors Fabric/NeoForge)
│   │   ├── ServerEvent.kt        │ Server lifecycle (tick, world load/save, datapack reload)
│   │   ├── PlayerEvent.kt        │ Player interaction (use item, attack, interact)
│   │   ├── ServerPlayerEvent.kt  │ Player lifecycle (join/leave/respawn/xp/jump/launch)
│   │   ├── ServerEntityEvent.kt  │ Entity lifecycle (load/unload/teleport/equip/jump)
│   │   ├── ServerLivingEntityEvent.kt │ Living damage/death/fall/transform
│   │   ├── ServerEntityCombatEvent.kt │ Combat (kill/shield block/critical hit)
│   │   ├── ServerMobEffectEvent.kt    │ Potion effects (add/remove/modify)
│   │   ├── ServerMessageEvent.kt      │ Chat/broadcast/command messages
│   │   ├── LivingBehaviorEvent.kt     │ Mob behavior (tame/breed/sleep/elytra/bed)
│   │   ├── ItemEvent.kt              │ Item use (use on block, use in air)
│   │   ├── ItemComponentEvent.kt      │ Enchantment (prepare/execute) — TODO: raw Bukkit event
│   │   ├── LivingUseItemEvent.kt      │ Item usage lifecycle (start/stop/finish)
│   │   ├── ChunkAndBlockEvent.kt      │ Chunk load/unload, block break/place/explode
│   │   ├── LootTableEvent.kt          │ Loot generation — TODO: raw Bukkit event
│   │   └── managed/
│   │       └── PaperManagedEvents.kt  │ Bukkit PluginManager.registerEvent bridge for ManagedEvents
│   └── stub/                          │ Client API stubs (Paper is server-only)
│       ├── StubClient.kt              │ KattonClientApi — all methods @Deprecated(ERROR)
│       ├── StubClientRender.kt        │ KattonClientRenderApi — all methods @Deprecated(ERROR)
│       ├── StubGui.kt                 │ ScriptPackUi, ReloadProgressState — no-ops
│       └── StubNetworking.kt          │ ServerPackCacheManager — returns empty, no client sync
└── resources/
    └── paper-plugin.yml               │ Plugin manifest: folia-supported: true
```

### Other notable directories

| Path | Purpose |
|---|---|
| `api/` (root) | Separate VitePress API documentation project (git submodule) |
| `docs/` | `katton-tech-brief.md` and `kattonpacks-manifest-and-layout.md` |
| `plans/` | Planning documents: event API layering, gap matrix, spec docs |
| `net/` | Skeletal compiled `.class` files for reflection testing |
| `run/world/datapacks/` | Katton-Example git submodule |
| `paper/run/` | Paper server runtime directory (worlds, logs, kattonpacks) |

## Key Conventions

- Script files are **`.kt`** (not `.kts`). Entrypoints use `@ServerScriptEntrypoint` / `@ClientScriptEntrypoint` annotations on top-level no-arg functions.
  - `.kt` provides full Kotlin compiler type-checking, better IDE support (IntelliJ), and compiles to JVM bytecode for JIT optimization — unlike `.kts` script mode which has limited IDE support and slower execution.
- Script packs live in `<gameDir>/kattonpacks/<pack>/` (global) or `<worldDir>/kattonpacks/<pack>/` (world). Each pack needs a `manifest.json`.
- Hot reload: `/katton reload` — recompiles all source packs, updates event handlers, item behaviors, entity renderers, and datapacks.
  - On Fabric/NeoForge: clears and re-registers event hooks, injection hooks, and registry entries.
  - On Paper: clears managed Bukkit listeners and re-registers them; registry operations are **disabled** (no client to sync with).
- Unsafe injection API (`top.katton.api.inject.*`) uses ByteBuddy + custom ASM transformer (`MethodInjectionTransformer.java`) for before/after/replace/redirect hooks. Managed by `InjectionManager.kt`. Registries and hooks are cleared on reload. **Fabric/NeoForge only** — Paper does not support runtime bytecode injection.
- Registry operations use an internal `LoadState` enum to guard available operations:
  - `INIT` → `SERVER_STARTED` → `END_DATA_PACK_RELOAD` → `SERVER_STOPPED`

### `/katton` Command Tree

**Fabric/NeoForge** (full command tree via Minecraft Brigadier):
```
/katton help
/katton status          — show globalState, server binding, reload status
/katton registry        — full registry health snapshot
/katton registry stale  — stale retained entries only
/katton reload          — requires gamemaster permission
/katton debug registryLogging [on|off]  — verbose registration logging
```

**Paper** (simplified via Paper Brigadier `BasicCommand`):
```
/katton help
/katton status          — show globalState, server binding
/katton reload          — requires katton.admin permission or OP
```
Paper omits `registry` and `debug` subcommands since registry operations are disabled.

## Event System

Katton uses a **platform-bridge pattern**: each platform (Fabric/NeoForge/Paper) has 14 event bridge objects that listen to platform-native events, convert types via platform-specific bridges, and dispatch to the common event system.

### Architecture

```
Script (.kt) → Common Event API (top.katton.api.event.KattonEventsArg)
                   ↑
Platform Bridge (14 event objects × platform)
    Fabric:  net.fabricmc.fabric.api.event → direct NMS types
    NeoForge: @SubscribeEvent → direct NMS types
    Paper:   Bukkit @EventHandler → PaperNmsBridge → NMS types
```

### Event Classes (all 3 platforms share the same 14 categories)

| # | Category | Examples |
|---|---|---|
| 1 | ServerEvent | tick start/end, world load/save, datapack reload |
| 2 | PlayerEvent | use item on block, attack entity, interact |
| 3 | ServerPlayerEvent | join, leave, respawn, xp change, jump, launch projectile |
| 4 | ServerEntityEvent | load, unload, teleport, equipment change, jump |
| 5 | ServerLivingEntityEvent | damage, death, fall, mob conversion |
| 6 | ServerEntityCombatEvent | kill other, shield block, critical hit |
| 7 | ServerMobEffectEvent | potion effect add/remove/modify |
| 8 | ServerMessageEvent | chat, broadcast, command messages |
| 9 | LivingBehaviorEvent | tame, breed, sleep, elytra, bed enter/leave |
| 10 | ItemEvent | use item on block, use in air |
| 11 | ItemComponentEvent | enchantment prepare/execute |
| 12 | LivingUseItemEvent | item use start/stop/finish |
| 13 | ChunkAndBlockEvent | chunk load/unload, block break/place, explosion |
| 14 | LootTableEvent | loot generation modify drops |

### Managed Events (Paper-specific bonus)

Paper's `PaperManagedEvents.kt` bridges the common `ManagedListenerProvider` interface to Bukkit's `PluginManager.registerEvent()`. This allows scripts to register **any** Bukkit event listener directly:

```kotlin
import top.katton.api.event.managed.*
import org.bukkit.event.block.*

registerEvent<BlockExplodeEvent>(priority = 4) { event ->
    event.blockList().forEach { block -> println(block) }
}
```

Listeners are tracked by `ScriptPackScope` and automatically cleaned up on reload or server stop. This is **Paper-only** — Fabric/NeoForge use their own native event systems.

## Registry System

`KattonRegistry.kt` manages 10 hot-reloadable sub-registries using `ReloadableBuiltInRegistry<T>`:

| Registry | Type | Mode |
|---|---|---|
| `ITEMS` | `Item` | RELOADABLE |
| `BLOCKS` | `Block` | RELOADABLE |
| `ENTITY_TYPES` | `EntityType<?>` | RELOADABLE |
| `BLOCK_ENTITY_TYPES` | `BlockEntityType<?>` | RELOADABLE |
| `EFFECTS` | `MobEffect` | RELOADABLE |
| `SOUND_EVENTS` | `SoundEvent` | RELOADABLE |
| `PARTICLE_TYPES` | `ParticleType<?>` | RELOADABLE |
| `CREATIVE_TABS` | `CreativeModeTab` | RELOADABLE |
| `DATA_COMPONENT_TYPES` | `DataComponentType<?>` | RELOADABLE |
| `ENTITY_RENDERERS` | Entity renderer entries | RELOADABLE |

Each sub-registry supports `RegisterMode.AUTO`, `RegisterMode.RELOADABLE`, and a persistent variant. Registry mutation uses `RegistryMutationUtil` to temporarily unfreeze Minecraft's built-in registries during reload windows.

> **Paper limitation**: `Katton.paperInitialize()` sets `registrationEnabled = false`. Paper cannot register custom Items, Blocks, EntityTypes, etc. because there is no client mod to sync registry entries with. Scripts on Paper should use vanilla items/blocks or Bukkit API equivalents. This is a fundamental architectural difference from the mod platforms.

## Paper Module Architecture

### How Paper differs from Fabric/NeoForge

| Aspect | Fabric / NeoForge | Paper |
|---|---|---|
| **Type** | Mod (runs inside Minecraft process) | Plugin (runs via Bukkit API) |
| **Entrypoint** | `ModInitializer` / `@Mod` | `JavaPlugin` |
| **Build** | Fabric Loom (remapping) | Paperweight (dev bundle mapping) + Shadow (fat jar) |
| **Mixin** | Yes (16/30 files) | No — standard plugin only |
| **Access transformers** | Fabric: access widener; NeoForge: AT | None |
| **NMS access** | Direct (same classloader) | Via Paperweight dev bundle → `PaperNmsBridge` |
| **Client support** | Full (rendering, HUD, client events) | None (4 stub files, `hasClient = false`) |
| **Registry mutations** | Full (unfreeze→register→refreeze) | Disabled (`registrationEnabled = false`) |
| **ByteBuddy injection** | Full (`UnsafeApi.kt`) | Disabled |
| **Networking** | Custom packets (config phase) | Stubbed (no client sync needed) |
| **Scheduling** | Standard Minecraft tick | Folia-aware (`FoliaSchedulerApi.kt`: entity/position/global) |
| **Event input** | Fabric callbacks / NeoForge events | Bukkit `@EventHandler` → `PaperNmsBridge` → NMS |
| **Folia support** | N/A | `folia-supported: true` |

### PaperNmsBridge

Converts between Bukkit and NMS (Mojang-mapped) types. Uses `CraftBukkit` handle access when available, falls back to `ReflectUtil.invoke(getHandle)`. Covers:

- **Server/World**: `Server → MinecraftServer`, `World → ServerLevel`
- **Entity**: `Player → ServerPlayer`, `Entity → Entity`, `LivingEntity → LivingEntity`
- **Item**: `ItemStack` ↔ `CraftItemStack.asNMSCopy/asBukkitCopy`
- **Spatial**: `BlockPos`, `Vec3`, `Direction`, `BlockFace` conversions
- **Game mechanics**: `DamageSource`, `Explosion`, `InteractionHand`, `EquipmentSlot`
- **Chat/Commands**: `PlayerChatMessage`, `CommandSourceStack`, `ChatType.Bound`, Adventure `Component` ↔ NMS `Component`

### FoliaSchedulerApi

Region-aware scheduling API exposed to scripts. Works on both Folia and standard Paper:

```kotlin
import top.katton.paper.*

// Entity region thread
player.schedule { /* runs on player's region */ }
player.schedule(delayTicks = 40) { /* delayed */ }
val task = entity.scheduleRepeating(0, 20) { /* repeating */ }

// Position region thread
scheduleAt(world, blockPos) { /* runs on chunk's region */ }
scheduleAt(world, blockPos, delayTicks = 60) { /* delayed */ }

// Global region thread (world time, weather, console commands)
scheduleGlobal { /* runs on global region */ }
scheduleGlobal(delayTicks = 100) { /* delayed */ }
val globalTask = scheduleGlobalRepeating(0, 20) { /* repeating */ }

// Cancel
cancelScheduledTask(task)
```

## Networking

### Fabric/NeoForge: Client-Server Sync

Script pack synchronization between server and clients uses 3 custom payload types over configuration-phase networking:

- `ScriptPackHashListPacket` (S→C) — available packs with content hashes
- `ScriptPackRequestPacket` (C→S) — client requests packs it doesn't have
- `ScriptPackBundlePacket` (S→C) — bundled script file content

### Paper: No Networking

On Paper, `ServerPackCacheManager` is **stubbed** — returns empty lists, no client sync. All script packs are server-local files loaded from `<serverDir>/kattonpacks/` or `<worldDir>/kattonpacks/`. No client-side script execution exists on Paper.

## Dependencies

| Library | Version | Common | Fabric | NeoForge | Paper |
|---|---|---|---|---|---|
| Kotlin compiler/scripting/reflect/stdlib (9 artifacts) | 2.3.10 | compileOnly | include | jarJar | implementation→shadow |
| `kotlinx-coroutines-core-jvm` | 1.8.0 | compileOnly | include | jarJar | implementation→shadow |
| `kotlinx-coroutines-jdk8` | 1.8.0 | compileOnly | include | jarJar | implementation→shadow |
| `net.bytebuddy:byte-buddy` | 1.17.8 | compileOnly | include | jarJar | implementation→shadow |
| `net.bytebuddy:byte-buddy-agent` | 1.17.8 | compileOnly | include | jarJar | implementation→shadow |
| `org.jetbrains:annotations` | 26.0.2 | compileOnly | include | jarJar | implementation→shadow |
| `org.ow2.asm:asm` | 9.9.1 | compileOnly | — | — | implementation→shadow |
| `org.ow2.asm:asm-commons` | 9.9.1 | compileOnly | — | — | implementation→shadow |
| `fabric-loader` | 0.18.4 | — | implementation | — | — |
| `fabric-api` | 0.144.0+26.1 | compileOnly | implementation | — | — |
| `neoforge` | 26.1.2.30-beta | — | — | via moddev | — |
| `paper-api` | 26.1.2.build.+ | — | — | — | compileOnly |
| Paper dev bundle | 26.1.2.build.+ | — | — | — | paperweight |

## Custom Tasks

- `generateApiDocs` — generates VitePress-ready API docs from KDoc comments (configured for 3 modules: `common`, `fabric`, `neoforge` in `build.gradle`). **Paper module is not yet included**.
- `copyDatapacks` / `cleanDatapacks` — copy Katton-Example datapacks into `run/saves/test/datapacks/`.
- `:paper:shadowJar` — produces the fat jar for Paper deployment (includes all Kotlin dependencies).
- `:paper:runServer` — runs a Paper server for testing (via `xyz.jpenilla.run-paper` plugin).
- `publishAllToPrivateNexus` — publish all subprojects to Nexus (URL configured via `nexusBaseUrl` property, credentials from `nexusUsername`/`nexusPassword` gradle properties or env vars).

## Publishing

- Maven publications: each subproject publishes via `publishAllPublicationsToPrivateNexusRepository`.
- Uses custom `mavenJar` + `mavenSourcesJar` artifacts (not the loom-remapped jar) for Fabric, NeoForge, and Paper modules.
- `GenerateModuleMetadata` is disabled for Fabric and NeoForge builds (`useLeanMavenPublication = true`).
- Paper uses `useLeanMavenPublication = true` and publishes a lean jar (class files only, no embedded deps) for Maven consumption. The fat shadow jar is the deployment artifact.
