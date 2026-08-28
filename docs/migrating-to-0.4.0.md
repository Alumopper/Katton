# Migrating Script Packs from 0.3.x to 0.4.0

Katton 0.4.0 changes the script-pack lifecycle and hardens dependency and remote
signature validation. Existing packs should be migrated before starting a 0.4.0
runtime; invalid packs are rejected with diagnostics rather than partially run.

## 1. Add the required dependency list

Every `manifest.json` must contain `dependencies`, even when the pack has no
external dependencies:

```json
{
  "id": "my_pack",
  "version": "1.0.0",
  "dependencies": []
}
```

When a script imports another mod or plugin API, declare the runtime dependency
with a non-empty `platforms` list:

```json
{
  "dependencies": [
    {
      "id": "create",
      "version": ">=6.0.0",
      "required": true,
      "platforms": ["fabric", "neoforge"],
      "environment": "both"
    }
  ]
}
```

Katton pack-to-pack dependencies remain optional and use `packDependencies`.

## 2. Choose an explicit phase for world entrypoints

The no-argument annotation defaults are process-lifetime phases intended for
global packs. World packs must use a phase that has the required runtime state.

Server world pack:

```kotlin
import top.katton.api.ServerPhase
import top.katton.api.ServerReadyContext
import top.katton.api.ServerScriptEntrypoint

@ServerScriptEntrypoint(phase = ServerPhase.READY)
fun main(context: ServerReadyContext) {
    println("Server ready for ${context.packId}")
}
```

Client world or server-cache packs use `ClientPhase.REGISTRY_SETUP` for work
that must happen before registry validation, or `ClientPhase.JOINED` when a
player and level are required. `ClientPhase.READY` is only valid for global
packs.

Hot-reload replay rules are scope-aware:

- Global entrypoints are never replayed.
- World entrypoints follow the annotation's `replay` value, which defaults to
  `true`.
- Server-cache entrypoints always replay after a successfully activated server
  revision.

## 3. Re-sign synchronized packs

Katton 0.4.0 accepts only the unambiguous, length-framed signature payload v2.
Legacy v1 signatures, including signatures without `payloadVersion`, are
rejected. Run the 0.4.0 signing plugin again and confirm the resulting manifest
contains:

```json
{
  "signature": {
    "algorithm": "Ed25519",
    "payloadVersion": 2,
    "keyId": "my-server-key",
    "publicKey": "...",
    "signature": "..."
  }
}
```

The signature covers the manifest with the signature object removed, every
script source, and synchronized `assets/**` and `data/**` content. Any content
change requires signing again.

## 4. Select the Minecraft-qualified artifact

0.4.0 publishes one artifact set per Minecraft target:

```text
top.katton:katton-common:0.4.0+mc26.1.2
top.katton:katton-fabric:0.4.0+mc26.1.2
top.katton:katton-neoforge:0.4.0+mc26.1.2
top.katton:katton-paper:0.4.0+mc26.1.2

top.katton:katton-common:0.4.0+mc26.2
top.katton:katton-fabric:0.4.0+mc26.2
top.katton:katton-neoforge:0.4.0+mc26.2
top.katton:katton-paper:0.4.0+mc26.2
```

Match the Maven coordinate and deployment jar to the exact Minecraft version.
Katton 0.4.0 requires Java 25.

## 5. Paper limitations remain explicit

Paper is server-only. Client entrypoints, client synchronization, registry
mutation, runtime bytecode injection, render APIs, and client resource-pack
mounting are unavailable. Use Bukkit/Paper APIs and managed native events for
Paper-only behavior.

## Verification checklist for a migrated pack

- `manifest.json` parses and contains `dependencies`.
- Every world entrypoint declares a valid phase.
- Every declared mod/plugin dependency is installed and version-compatible.
- Every client-synchronized signature has `payloadVersion: 2` and verifies.
- The pack compiles on the exact target platform and Minecraft version.
- A failed hot reload leaves the previously active revision working.
