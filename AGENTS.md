# Katton — kts4mc-template-1.21.11

Minecraft Fabric & NeoForge mod for Kotlin scripting with hot reload.

## Build & Run

- Java 25 required (enforced by `project.options.release = 25` and `JVM target = 25`).
- Run via Gradle: `./gradlew :fabric:runClient` or `./gradlew :neoforge:runClient`.
- Build: `./gradlew build` (produces jars for each submodule).
- Configuration cache disabled (`org.gradle.configuration-cache=false` in `gradle.properties`).
- **No tests** exist anywhere in the repo.

## Project Structure

Multi-module Gradle build (Groovy DSL, Kotlin plugins via `org.jetbrains.kotlin.jvm` 2.3.10):

| Module | Build system | Entrypoint |
|---|---|---|
| `:common` | Fabric Loom (access widener) | `top.katton.Katton.java` — platform-agnostic init |
| `:fabric` | Fabric Loom | `top.katton.KattonFabric.java` — `ModInitializer` |
| `:neoforge` | NeoForge ModDev (`net.neoforged.moddev` 2.0.141) | `top.katton.KattonNeoForge.java` |
| `:buildSrc` | Custom Kotlin plugin | `ApiDocGeneratorPlugin` — generates VitePress API docs |

- Common logic (script engine, networking, registry, API) lives in `common/src/main/kotlin/top/katton/`.
- Platform-specific event APIs mirror each other under `fabric/src/main/kotlin/.../api/event/` and `neoforge/src/main/kotlin/.../api/event/`.
- Mixins: Fabric mixins in Java under `fabric/src/main/java/top/katton/mixin/`, NeoForge mixins under `neoforge/src/main/java/top/katton/mixin/`.
- Kotlin scripting libraries are embedded (`include` in Fabric, `jarJar` in NeoForge) not declared as runtime deps.

## Key Conventions

- Script files are **`.kt`** (not `.kts`). Entrypoints use `@ServerScriptEntrypoint` / `@ClientScriptEntrypoint` annotations on top-level no-arg functions.
- Script packs live in `<gameDir>/kattonpacks/<pack>/` (global) or `<worldDir>/kattonpacks/<pack>/` (world). Each pack needs a `manifest.json`.
- Hot reload: `/katton reload` — recompiles all source packs, updates event handlers and item behaviors.
- Unsafe injection API (`top.katton.api.inject.*`) uses ByteBuddy + custom ASM transformer for before/after/replace/redirect hooks. Registries are cleared on reload.
- Registry operations use an internal `LoadState` enum (`INIT`, `SERVER_STARTED`, etc.) to guard available operations.

## Custom Tasks

- `generateApiDocs` — generates VitePress-ready API docs from KDoc comments (configured for 3 modules in `build.gradle`).
- `copyDatapacks` / `cleanDatapacks` — copy Katton-Example datapacks into a test world save under `run/`.
- `publishAllToPrivateNexus` — publish all subprojects to Nexus (URL configured via `nexusBaseUrl` property, credentials from `nexusUsername`/`nexusPassword` gradle properties or env vars).

## Publishing

- Maven publications: each subproject publishes via `publishAllPublicationsToPrivateNexusRepository`.
- Uses custom `mavenJar` + `mavenSourcesJar` artifacts (not the loom-remapped jar) for Fabric and NeoForge modules.
- `GenerateModuleMetadata` is disabled for Fabric and NeoForge builds.
