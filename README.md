<img width="1348" height="256" alt="20260226" src="https://github.com/user-attachments/assets/822f3883-23c9-4bde-88e1-e0a15bb16128" />

# Katton

Katton is a Kotlin scripting runtime for Minecraft **Fabric**, **NeoForge**, and **Paper** (MC `26.1.2`) with hot reload support.  
Write script packs in `.kt`, reload with a command, and extend server/game behavior without rebuilding your whole mod/plugin every iteration.

## Features

- Kotlin-based script packs (`.kt`) with entrypoint annotations
- Hot reload (`/katton reload`)
- Cross-platform event API (Fabric / NeoForge / Paper)
- Registry APIs for mod platforms (Fabric/NeoForge)
- Experimental unsafe runtime injection API (ByteBuddy)
- Paper-specific managed Bukkit event bridge

## Platform Support

| Platform | Type | Client Support | Registry Mutation | Unsafe Injection |
|---|---|---|---|---|
| Fabric | Mod | Yes | Yes | Yes |
| NeoForge | Mod | Yes | Yes | Yes |
| Paper | Plugin | No (server-only) | No (disabled) | No |

> [!NOTE]
> On Paper, Katton runs as a server plugin and intentionally disables custom game registry mutation (items/blocks/entity types) because there is no matching client mod to sync custom registries.

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
import top.katton.api.ServerScriptEntrypoint

@ServerScriptEntrypoint
fun main() {
    println("Hello from Katton script pack")
}
```

A ready-to-use sample project is available at:  
**Katton-Example** → https://github.com/Alumopper/Katton-Example

## Hot Reload

Use:

```text
/katton reload
```

Reload performs:

1. Re-scan enabled script packs
2. Re-compile and execute scripts
3. Refresh event hooks and script-managed runtime state

## `/katton` Command Overview

### Fabric / NeoForge

- `/katton help`
- `/katton status`
- `/katton registry`
- `/katton registry stale`
- `/katton reload`
- `/katton debug registryLogging [on|off]`

### Paper

- `/katton help`
- `/katton status`
- `/katton reload`

## IDE & Debugging

- Use `.kt` for better Kotlin IDE support.
- For remote debugging, run JVM with JDWP agent, e.g.:

```text
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
```

Then attach from IntelliJ IDEA using **Attach to remote JVM**.

## Unsafe Injection API (Experimental)

`top.katton.api.inject` provides runtime method hook capabilities (before/after + rollback).

> [!WARNING]
> This API is intentionally dangerous. It performs runtime class redefinition and may conflict with other transformers/mods. Use only if you understand instrumentation risks.

Reference implementation and entrypoints:

- `common/src/main/kotlin/top/katton/engine/UnsafeInjectionManager.kt`
- `common/src/main/kotlin/top/katton/api/inject/UnsafeApi.kt`

## Project Modules

- `common` - shared scripting engine, APIs, registry, networking
- `fabric` - Fabric integration and event bridge
- `neoforge` - NeoForge integration and event bridge
- `paper` - Paper plugin integration, Bukkit event bridge, Folia scheduler API

## Status

Katton is actively developed. Interfaces and behavior may still change between versions.
