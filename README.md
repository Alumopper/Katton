<img width="1348" height="256" alt="20260226" src="https://github.com/user-attachments/assets/822f3883-23c9-4bde-88e1-e0a15bb16128" />

# Katton

Katton is a Kotlin scripting runtime for Minecraft **Fabric**, **NeoForge**, and **Paper** (MC `26.1.2` and `26.2`) with hot reload support.
Write script packs in `.kt`, reload with a command, and extend server/game behavior without rebuilding your whole mod/plugin every iteration.

The script pack signing Gradle plugin is maintained separately in [Katton-Sign](https://github.com/Alumopper/Katton-Sign). See the [migration guide](docs/sign-plugin-migration.md) for build and publishing commands.

## Features

- Kotlin-based script packs (`.kt`) with entrypoint annotations
- Hot reload (`/katton reload`)
- Cross-platform event API (Fabric / NeoForge / Paper)
- Registry APIs for mod platforms (Fabric/NeoForge)
- Experimental unsafe runtime injection API (ByteBuddy)
- Paper-specific managed Bukkit event bridge
- Client camera paths, shake/FOV, particle and geometry effects, and Kotlin scene timelines with server triggers ([guide and examples](docs/client-scenes.md))
- Audio playback: client audio from pack files, resources, or sound events (WAV/MP3/Ogg Vorbis/FLAC) with pause, seek, rate, fade, loop, and spatial placement; remote control of another player's client; and a vanilla-safe basic sound API on all three platforms ([guide](docs/audio-control.md))
- Optional loopback IDE bridge for local script deployment and in-game diagnostics ([guide](docs/idea-development.md))

## Platform Support

| Platform | Type | Client Support | Registry Mutation | Unsafe Injection |
|---|---|---|---|---|
| Fabric | Mod | Yes | Yes | Yes |
| NeoForge | Mod | Yes | Yes | Yes |
| Paper | Plugin | No (server-only) | No (disabled) | No |

> [!NOTE]
> On Paper, Katton runs as a server plugin and intentionally disables custom game registry mutation (items/blocks/entity types) because there is no matching client mod to sync custom registries.

> [!IMPORTANT]
> Folia supports Katton scripts, managed events, hot reload, and the region-aware
> scheduler API. Folia does not support Katton's runtime `data/**` mounting in
> 0.4.0 because its server resource-reload operation is unavailable; Katton
> rejects data-bearing script packs there instead of partially activating them.

## Requirements

- **Java 25**
- **Gradle 9.3.0** (wrapper included)

## Build & Run

```bash
./gradlew build
```

Run targets:

```bash
./gradlew :fabric:runClient
./gradlew :neoforge:runClient
./gradlew :paper:runServer
./gradlew :paper:runFolia
```

## Script Pack Layout

Script packs are discovered from:

- Global: `<gameDir>/kattonpacks/<pack>/...`
- World: `<worldDir>/kattonpacks/<pack>/...`

Each pack must contain:

- `manifest.json`
- One or more Kotlin source files with `.kt`

Entrypoints are selected by annotations such as `@ServerScriptEntrypoint` and `@ClientScriptEntrypoint`.

## Quick Start Example

```kotlin
import top.katton.api.ServerPhase
import top.katton.api.ServerScriptEntrypoint

@ServerScriptEntrypoint(ServerPhase.READY)
fun main() {
    println("Hello from Katton script pack")
}
```

For a world script pack, add a `manifest.json` next to the source file:

```json
{
  "id": "hello_katton",
  "name": "Hello Katton",
  "version": "1.0.0",
  "dependencies": []
}
```

`dependencies` is required, even when the pack has no external mod or plugin dependencies.

A minimal compatible pack is included in [`examples/hello-pack`](examples/hello-pack/).
The larger, separately versioned sample project is available at
**Katton-Example** → https://github.com/Alumopper/Katton-Example.

When upgrading an existing pack from 0.3.x, follow the
[0.4.0 migration guide](docs/migrating-to-0.4.0.md) before loading it.

## Hot Reload

Use:

```text
/katton reload
```

Reload performs:

1. Re-scan enabled script packs
2. Re-compile and execute scripts
3. Refresh event hooks and script-managed runtime state

On standard Paper, Fabric, and NeoForge, a changed script-pack `data/**` tree is
also reloaded transactionally. This data-resource step is unavailable on Folia
in Alpha 0.4.0 as noted above.

## `/katton` Command Overview

### Fabric / NeoForge

- `/katton help`
- `/katton status`
- `/katton registry`
- `/katton registry stale`
- `/katton reload`
- `/katton capabilities injection`
- `/katton debug registryLogging [on|off]`
- `/katton debug injection`
- `/katton dev enable|disable`

### Paper

- `/katton help`
- `/katton status`
- `/katton reload`
- `/katton dev enable|disable`

## IDE & Debugging

- Use `.kt` for better Kotlin IDE support.
- The optional IDE bridge (`/katton dev enable`, or the **IDE** button in the script pack
  screen) lets the Katton IDEA plugin deploy world script packs and stream diagnostics.
  It listens on loopback only and is off by default. See
  [docs/idea-development.md](docs/idea-development.md).
- For remote debugging, run JVM with JDWP agent, e.g.:

```text
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
```

Then attach from IntelliJ IDEA using **Attach to remote JVM**.

For Fold Craft Launcher (FCL), Android's Java runtime may not include runtime Attach.
Use the Katton mod jar as a startup agent and follow the desktop reproduction and ADB
workflow in [docs/fcl-injection-debugging.md](docs/fcl-injection-debugging.md).

## Unsafe Injection API (Experimental)

`top.katton.api.inject` provides runtime method hook capabilities (before/after + rollback).

> [!WARNING]
> This API is intentionally dangerous. It performs runtime class redefinition and may conflict with other transformers/mods. Use only if you understand instrumentation risks.

Reference implementation and entrypoints:

- `common/src/main/kotlin/top/katton/engine/InjectionManager.kt`
- `common/src/main/kotlin/top/katton/api/inject/InjectApi.kt`
- `common/src/main/java/top/katton/engine/KattonAgent.java`

## Project Modules

- `common` - shared scripting engine, APIs, registry, networking
- `fabric` - Fabric integration and event bridge
- `neoforge` - NeoForge integration and event bridge
- `paper` - Paper plugin integration, Bukkit event bridge, Folia scheduler API

## Status

Katton is actively developed. Interfaces and behavior may still change between versions.
