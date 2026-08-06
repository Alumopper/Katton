# Script Loading Lifecycle

Katton separates where a script pack lives (its scope) from when an entrypoint runs (its phase). Scope is derived only from the pack folder; annotations contain a concrete phase and a replay preference. There is no `AUTO` phase.

## Entrypoint API

```kotlin
@ServerScriptEntrypoint(
    phase = ServerPhase.BOOTSTRAP,
    replay = true
)
fun bootstrap(context: BootstrapContext) { }

@ClientScriptEntrypoint(
    phase = ClientPhase.READY,
    replay = true
)
fun clientReady(context: ClientReadyContext) { }
```

The phase arguments have concrete defaults so simple global entrypoints may omit them. A function may take no arguments or exactly one compatible phase context.

Server phases:

- `BOOTSTRAP`: early process-lifetime setup. Only global packs.
- `READY`: the server, worlds, mods, and Paper plugins are ready. Global or world packs.

Client phases:

- `READY`: one-time client-process setup. Only global packs.
- `REGISTRY_SETUP`: remote or local content setup before registry validation. World and server-cache packs.
- `JOINED`: a connection, local player, and client level exist. World and server-cache packs.

| Pack scope | Server phases | Client phases |
|---|---|---|
| `GLOBAL` | `BOOTSTRAP`, `READY` | `READY` |
| `WORLD` | `READY` | `REGISTRY_SETUP`, `JOINED` |
| `SERVER_CACHE` | none | `REGISTRY_SETUP`, `JOINED` |

Invalid phase/scope combinations and incompatible function parameters fail with a diagnostic containing the pack, class, method, scope, and phase.

## Invocation Context

Every phase context implements `ScriptInvocationContext` and exposes:

- `packId`
- `scope`
- `reason`: `INITIAL_LOAD` or `HOT_RELOAD`
- `cause`: `SERVER_START`, `COMMAND`, `DATAPACK_RELOAD`, `SERVER_PACK_SYNC`, or `CLIENT_JOIN`
- `platform`: `fabric`, `neoforge`, or `paper`

Phase-specific types add only objects guaranteed to exist at that point:

| Context | Extra values |
|---|---|
| `BootstrapContext` | none |
| `ServerReadyContext` | `server` |
| `ClientReadyContext` | `client` |
| `ClientRegistryContext` | `client` |
| `ClientJoinedContext` | `client`, `player`, `level` |

If the player and level do not yet exist, `JOINED` is queued and dispatched from the client lifecycle tick once both are available.

## Replay Rules

Replay applies only to hot reloads:

| Scope | Hot-reload behavior |
|---|---|
| `GLOBAL` | never replayed |
| `WORLD` | follows the annotation's `replay` value; default `true` |
| `SERVER_CACHE` | always replayed, even when the annotation says `false` |

Global server `READY` entrypoints run once for each server instance. Their event, managed-listener, and injection registrations are tagged with the `READY` phase and removed when that server stops. Global `BOOTSTRAP` registrations retain process lifetime.

## Server Lifecycle

```text
platform initialization
  -> scan global packs
  -> GLOBAL / ServerPhase.BOOTSTRAP

server started
  -> GLOBAL / ServerPhase.READY (once for this server)
  -> scan world packs
  -> WORLD / ServerPhase.READY (INITIAL_LOAD, SERVER_START)
  -> mount pack data resources and apply scripted datapacks

successful command or datapack reload
  -> clear only WORLD server-owned registrations
  -> WORLD / ServerPhase.READY (HOT_RELOAD)
  -> publish a new client pack revision

server stopped
  -> clear WORLD state
  -> clear GLOBAL / READY server-lifecycle registrations
  -> retain GLOBAL / BOOTSTRAP process state
```

Paper follows the same server lifecycle. Registry mutation, client phases, networking, and unsafe injection remain unavailable on Paper.

## Client Lifecycle

```text
client initialized
  -> GLOBAL / ClientPhase.READY (once per client process)

singleplayer connection
  -> WORLD / REGISTRY_SETUP
  -> WORLD / JOINED when player and level exist

remote configuration sync
  -> verify, cache, and trust the server snapshot
  -> SERVER_CACHE / REGISTRY_SETUP before registry validation
  -> SERVER_CACHE / JOINED when player and level exist

play-phase server revision
  -> stage and precompile the complete candidate snapshot
  -> atomically switch active snapshot
  -> replay REGISTRY_SETUP and JOINED for every active SERVER_CACHE pack
```

Integrated-server hot reloads call the client reload path directly. They do not send the revision protocol over the in-memory connection.

## Manifest Dependencies

Every directory and jar pack must contain a `dependencies` array. An empty array is valid and is the migration for old manifests:

```json
{
  "id": "example_pack",
  "dependencies": []
}
```

Example with loader and Paper integrations:

```json
{
  "id": "economy_integration",
  "dependencies": [
    {
      "id": "create",
      "version": ">=6.0.0",
      "required": true,
      "platforms": ["fabric", "neoforge"],
      "environment": "both"
    },
    {
      "id": "Vault",
      "version": ">=1.7.0",
      "required": true,
      "platforms": ["paper"],
      "environment": "server"
    }
  ]
}
```

Fields:

- `id`: mod ID on Fabric/NeoForge or plugin name on Paper.
- `version`: `*`, an exact version, comparisons such as `>=1.7.0`, an AND range such as `>=1.7 <2`, or `||` alternatives.
- `required`: defaults to `true`. A missing optional dependency does not disable the pack.
- `platforms`: required non-empty subset of `fabric`, `neoforge`, and `paper`.
- `environment`: `server`, `client`, or `both`; defaults to `both`.

Katton disables a pack for the current environment when an applicable required dependency is absent, disabled, or version-incompatible. A receiving client performs its own dependency validation before accepting a synchronized pack.

Scripts can query optional dependencies without reflection:

```kotlin
if (dependencies.isLoaded("optional-mod")) {
    println(dependencies.version("optional-mod"))
}
```

## Dependency Compilation and Classloading

Fabric and NeoForge add the declared mod plus its required transitive mod closure to Kotlin and Java compiler classpaths. Runtime calls still resolve through the loader's transformed shared classloader.

Paper resolves installed plugins through `PluginManager`, verifies that they are enabled and version-compatible at `ServerPhase.READY`, and adds their installed jar paths only to the compiler classpath. Compiled script classes delegate plugin-owned runtime classes to each plugin's existing classloader. Katton never loads a second plugin copy. If two declared Paper plugins export the same class, the pack is rejected as ambiguous.

Paper plugin dependencies cannot be used from `ServerPhase.BOOTSTRAP`: a dynamic pack manifest cannot establish Paper's static plugin load order. `paper-plugin.yml` therefore remains unchanged. Once classes are resolved, typed plugin API calls are ordinary JVM calls; reflection is not used on each invocation.

Resolved dependency IDs, versions, paths, sizes, and modification times participate in script compilation cache keys.

## Initial and Live Server-Pack Sync

Initial login remains in configuration networking because `REGISTRY_SETUP` must finish before Minecraft validates registries. The server sends a revision-zero hash snapshot followed by its full bundle. The client verifies signatures and hashes, asks for user trust when necessary, and blocks configuration progress until activation completes.

After every successful server hot reload, Fabric and NeoForge publish a play-phase revision:

```text
server allocates revision
  -> broadcast complete hash snapshot (including an empty snapshot)
  -> client copies unchanged packs into serverpacks/<server>/revisions/<revision>/
  -> client requests only changed or missing packs
  -> verify signatures and complete the staging snapshot
  -> validate manifest dependencies and precompile without executing
  -> atomically activate and replay all SERVER_CACHE entrypoints
  -> acknowledge success or failure
```

Packs omitted from the snapshot are deactivated. Unchanged packs are not retransferred but are still replayed. Until trust, hash verification, dependency validation, and compilation succeed, the previous revision remains active. Activation failure restores the previous pack list and reports failure. The server disconnects a player after a negative acknowledgement or a 30-second acknowledgement timeout to avoid registry/script mismatch.

Paper is server-only and does not participate in either client sync protocol.

## Main Implementation Files

- `common/src/main/kotlin/top/katton/api/ScriptEntrypoint.kt`
- `common/src/main/kotlin/top/katton/engine/ScriptInvocation.kt`
- `common/src/main/kotlin/top/katton/engine/ScriptLifecyclePolicy.kt`
- `common/src/main/kotlin/top/katton/engine/ScriptDependencyManager.kt`
- `common/src/main/kotlin/top/katton/engine/ScriptEngine.kt`
- `common/src/main/kotlin/top/katton/engine/ScriptReloadManager.kt`
- `common/src/main/kotlin/top/katton/network/ServerNetworking.kt`
- `common/src/main/kotlin/top/katton/pack/ServerPackCacheManager.kt`
- `fabric/src/main/kotlin/top/katton/platform/FabricScriptDependencyResolver.kt`
- `neoforge/src/main/kotlin/top/katton/platform/NeoForgeScriptDependencyResolver.kt`
- `paper/src/main/kotlin/top/katton/paper/PaperScriptDependencyResolver.kt`
