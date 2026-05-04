# Katton — kts4mc-template-1.21.11

Minecraft Fabric & NeoForge mod for Kotlin scripting with hot reload. Target MC 1.21.5 (version 26.1.2).

## Build & Run

- **Java 25** required (enforced by `project.options.release = 25` and `JVM target = 25`).
- **Gradle 9.3.0** (wrapper checked in at `gradle/wrapper/gradle-wrapper.properties`).
- Run via Gradle: `./gradlew :fabric:runClient` or `./gradlew :neoforge:runClient`.
- Build: `./gradlew build` (produces jars for each submodule).
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
| `:buildSrc` | Custom Kotlin plugin | `ApiDocGeneratorPlugin` — generates VitePress API docs |

- Common logic (script engine, networking, registry, API) lives in `common/src/main/kotlin/top/katton/` and `common/src/main/java/top/katton/`.
- Platform-specific event APIs mirror each other under `fabric/src/main/kotlin/.../api/event/` and `neoforge/src/main/kotlin/.../api/event/` (14 event classes each).
- Mixins: Fabric mixins in Java under `fabric/src/main/java/top/katton/mixin/` (16 files), NeoForge mixins under `neoforge/src/main/java/top/katton/mixin/` (22 files). A `common/src/main/resources/kts4mc.mixins.json` exists with an empty mixin list.
- Kotlin scripting libraries are embedded (`include` in Fabric, `jarJar` in NeoForge, `compileOnly` in common).
- NeoForge uses **access transformers** (`src/main/resources/META-INF/accesstransformer.cfg`) in addition to the access widener from common.
- A **stale** `common/src/main/resources/fabric.mod.json` exists referencing non-existent entrypoint classes (`Katton` and `KattonClient` as Fabric entrypoints) — ignore it; only the fabric-module copy is active.

### Source Package Map

```
common/src/main/kotlin/top/katton/
├── api/          │ Public script API (annotations, registry functions, inject, render, mod, DP callers, datapacks)
│   ├── inject/     │ InjectApi.kt — before/after/replace/redirect hooks
│   ├── registry/   │ Per-type registry helpers (Item, Block, Entity, etc.)
│   ├── mod/        │ Item/Block modification API (KubeJS-like)
│   ├── dpcaller/   │ 13 DP caller wrappers (BlockApi, EntityApi, ItemApi, etc.)
│   ├── datapack/   │ Recipes.kt, Tags.kt
│   └── event/      │ KattonEventsArg.kt — shared event argument data classes
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

### Other notable directories

| Path | Purpose |
|---|---|
| `api/` (root) | Separate VitePress API documentation project (git submodule) |
| `docs/` | `katton-tech-brief.md` and `kattonpacks-manifest-and-layout.md` |
| `plans/` | Planning documents: event API layering, gap matrix, spec docs |
| `net/` | Skeletal compiled `.class` files for reflection testing |
| `run/world/datapacks/` | Katton-Example git submodule |

## Key Conventions

- Script files are **`.kt`** (not `.kts`). Entrypoints use `@ServerScriptEntrypoint` / `@ClientScriptEntrypoint` annotations on top-level no-arg functions.
- Script packs live in `<gameDir>/kattonpacks/<pack>/` (global) or `<worldDir>/kattonpacks/<pack>/` (world). Each pack needs a `manifest.json`.
- Hot reload: `/katton reload` — recompiles all source packs, updates event handlers, item behaviors, entity renderers, and datapacks.
- Unsafe injection API (`top.katton.api.inject.*`) uses ByteBuddy + custom ASM transformer (`MethodInjectionTransformer.java`) for before/after/replace/redirect hooks. Managed by `InjectionManager.kt`. Registries and hooks are cleared on reload.
- Registry operations use an internal `LoadState` enum to guard available operations:
  - `INIT` → `SERVER_STARTED` → `END_DATA_PACK_RELOAD` → `SERVER_STOPPED`

### `/katton` Command Tree

```
/katton help
/katton status          — show globalState, server binding, reload status
/katton registry        — full registry health snapshot
/katton registry stale  — stale retained entries only
/katton reload          — requires gamemaster permission
/katton debug registryLogging [on|off]  — verbose registration logging
```

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

## Networking

Script pack synchronization between server and clients uses 3 custom payload types over configuration-phase networking:

- `ScriptPackHashListPacket` (S→C) — available packs with content hashes
- `ScriptPackRequestPacket` (C→S) — client requests packs it doesn't have
- `ScriptPackBundlePacket` (S→C) — bundled script file content

## Dependencies

| Library | Version | Common | Fabric | NeoForge |
|---|---|---|---|---|
| Kotlin compiler/scripting/reflect/stdlib (9 artifacts) | 2.3.10 | compileOnly | include | jarJar |
| `kotlinx-coroutines-core-jvm` | 1.8.0 | compileOnly | include | jarJar |
| `kotlinx-coroutines-jdk8` | 1.8.0 | compileOnly | include | jarJar |
| `net.bytebuddy:byte-buddy` | 1.17.8 | compileOnly | include | jarJar |
| `net.bytebuddy:byte-buddy-agent` | 1.17.8 | compileOnly | include | jarJar |
| `org.jetbrains:annotations` | 26.0.2 | compileOnly | include | jarJar |
| `org.ow2.asm:asm` | 9.9.1 | compileOnly | — | — |
| `org.ow2.asm:asm-commons` | 9.9.1 | compileOnly | — | — |
| `fabric-loader` | 0.18.4 | — | implementation | — |
| `fabric-api` | 0.144.0+26.1 | compileOnly | implementation | — |
| `neoforge` | 26.1.2.30-beta | — | — | via moddev |

## Custom Tasks

- `generateApiDocs` — generates VitePress-ready API docs from KDoc comments (configured for 3 modules in `build.gradle`).
- `copyDatapacks` / `cleanDatapacks` — copy Katton-Example datapacks into `run/saves/test/datapacks/`.
- `publishAllToPrivateNexus` — publish all subprojects to Nexus (URL configured via `nexusBaseUrl` property, credentials from `nexusUsername`/`nexusPassword` gradle properties or env vars).

## Publishing

- Maven publications: each subproject publishes via `publishAllPublicationsToPrivateNexusRepository`.
- Uses custom `mavenJar` + `mavenSourcesJar` artifacts (not the loom-remapped jar) for Fabric and NeoForge modules.
- `GenerateModuleMetadata` is disabled for Fabric and NeoForge builds (`useLeanMavenPublication = true`).
