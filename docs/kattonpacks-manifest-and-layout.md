# Katton Packs Layout and Manifest

## 1. Directory Layout

Global:
- `<gameDir>/kattonpacks/<packFolder>/manifest.json`
- `<gameDir>/kattonpacks/<packFolder>/**/*.kt`
- `<gameDir>/kattonpacks/<packFolder>/assets/<namespace>/**`
- `<gameDir>/kattonpacks/<packFolder>/data/<namespace>/**`

World:
- `<worldDir>/kattonpacks/<packFolder>/manifest.json`
- `<worldDir>/kattonpacks/<packFolder>/**/*.kt`
- `<worldDir>/kattonpacks/<packFolder>/assets/<namespace>/**`
- `<worldDir>/kattonpacks/<packFolder>/data/<namespace>/**`

Server-transferred cache on client:
- `<gameDir>/serverpacks/<sha256(serverAddress)>/<base64(syncId)>/manifest.json`
- `<gameDir>/serverpacks/<sha256(serverAddress)>/<base64(syncId)>/**/*.kt`
- `<gameDir>/serverpacks/<sha256(serverAddress)>/<base64(syncId)>/assets/<namespace>/**`
- `<gameDir>/serverpacks/<sha256(serverAddress)>/<base64(syncId)>/data/<namespace>/**`

Live revision staging:
- `<gameDir>/serverpacks/<sha256(serverAddress)>/revisions/<revision>/<base64(syncId)>/**`

Optional `assets/` directories use the standard Minecraft resource pack layout.
Katton exposes them as generated client resource packs, so script packs can ship
textures, models, shaders, lang files, and other client resources without asking
users to enable a separate resource pack. `pack.mcmeta` is not required for this
generated pack layer.

Optional `data/` directories use the standard Minecraft data pack layout.
Katton exposes them as generated required server data packs, so script packs can
ship recipes, loot tables, tags, advancements, functions, predicates, and other
server data without asking users to enable a separate data pack. `pack.mcmeta`
is not required for this generated pack layer.

Example:

```text
kattonpacks/example_pack/
├── manifest.json
├── main.kt
└── assets/
    └── example_pack/
        ├── lang/en_us.json
        ├── models/item/ruby_staff.json
        └── textures/item/ruby_staff.png
```

Data file path example:

```text
kattonpacks/example_pack/data/example_pack/recipe/ruby_staff.json
kattonpacks/example_pack/data/example_pack/tags/item/magic_tools.json
```

## 2. Manifest Example

```json
{
  "id": "example_pack",
  "name": "Example Pack",
  "version": "1.0.0",
  "description": "Example scripts for Katton pack system",
  "authors": ["YourName"],
  "enabled": true,
  "clientSync": true,
  "dependencies": [],
  "packDependencies": [],
  "signature": {
    "algorithm": "Ed25519",
    "payloadVersion": 2,
    "keyId": "example-server-key",
    "publicKey": "base64-x509-public-key",
    "signature": "base64-signature"
  }
}
```

## 3. Manifest Field Meaning

- `id`: stable logical ID of the pack.
- `name`: display name in UI.
- `version`: human-readable version string.
- `description`: description in UI.
- `authors`: optional string array.
- `enabled`: default enabled state if no local state file exists.
- `clientSync`: whether this pack should be sent to multiplayer clients during Katton server sync. Defaults to `true` for compatibility.
- `dependencies`: required array of external Fabric mods, NeoForge mods, or Paper plugins. Use `[]` when the pack has none.
- `packDependencies`: optional array of Katton pack dependencies. Dependencies are validated by ID/version and entrypoints run in topological order.
- `signature`: recommended for remote client-synced packs. Uses Ed25519 payload format v2 and signs the pack's canonical content digest.

Side behavior:
- Runtime side-specific execution is still decided by function annotations (`@ServerScriptEntrypoint`, `@ClientScriptEntrypoint`).
- `clientSync` only controls whether the server includes the pack in the client download/sync snapshot.
- Use `"clientSync": false` for pure server-side packs that do not contain client entrypoints or client-required registry/rendering code.
- `assets/` resources are client-side only. They are loaded on Fabric/NeoForge clients before client script entrypoints run, then refreshed again after scripts execute when the active asset set changed.
- Paper is server-only, so `assets/` has no client resource-pack effect there.
- `data/` resources are server-side only. They are loaded as generated required data packs after server scripts execute and before scripted datapack mutations are applied.
- On Fabric/NeoForge/Paper servers, `data/` can use the standard vanilla data pack namespace layout under `data/<namespace>/...`.

Dependency example:

```json
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
```

`platforms` must be non-empty. `environment` defaults to `both`, `required` defaults to `true`, and `version` defaults to `*`. Missing `dependencies` is a manifest error.

Katton pack dependency example:

```json
"packDependencies": [
  { "id": "core-library", "version": ">=2.0", "required": true }
]
```

Use a scope-qualified ID such as `world:core-library` when the same manifest ID exists in more than one scope. Missing required packs, incompatible versions, ambiguous IDs, and dependency cycles reject only the affected dependency chain; independent packs remain eligible.

Runtime config overrides are stored under `<gameDir>/.katton/config-overrides/` using the scope-qualified sync ID. Server-cache configs add a `server_cache:` runtime prefix so a remote `global:x` cannot overwrite a local `global:x`. Katton never rewrites a pack manifest for config changes, so directory, JAR, and signed packs persist settings consistently and global/world packs with the same manifest ID do not collide.

Signature behavior:
- Signed client-synced packs are verified before they are written to the client's `serverpacks` cache.
- Unsigned client-synced packs remain compatible, but they rely only on the blocking trust prompt and do not have tamper-evident author verification.
- Signature payload v2 length-prefixes the pack `syncId`, scope, manifest JSON with `signature` removed, file count, and every sorted relative path/content pair. The framing is unambiguous even when binary assets contain zero bytes.
- Synced `assets/**` and `data/**` files are included in the hash, bundle payload, and signature payload.
- Legacy payload v1 (including signature objects without `payloadVersion`) is rejected; run `signKattonPack` again to migrate it.
- `publicKey` is an X.509-encoded Ed25519 public key in Base64. After the user trusts a server/key, Katton stores the trusted public key in `<gameDir>/.katton/remote-script-trust.json`.
- If a trusted `keyId` later presents a different embedded public key, verification fails and the remote scripts are rejected.

Pack input safety:

- Directory packs may contain at most 4,096 synchronized files, 16 MiB per file, and 64 MiB total including the manifest; `manifest.json` itself is limited to 1 MiB.
- Symbolic links inside a directory pack are rejected. JAR packs are checked for unsafe paths, duplicate entries, excessive expansion, and the same per-entry/content limits before loading.
- Synchronized paths must be portable: traversal segments, Windows-reserved names/characters, and case- or Unicode-normalization collisions are rejected before a Linux server sends a pack to other clients.

## 4. State File

Katton writes a per-pack local state file when user toggles enable/disable in GUI:

- `.kattonpack.state.json`

Structure:

```json
{
  "enabled": false
}
```

Priority:
- If state file exists, it overrides `manifest.json` `enabled`.
- If state file is absent, `manifest.json` `enabled` is used.

## 5. Hash Input

Current hash calculation uses:
- raw `manifest.json` string bytes (UTF-8)
- sorted script relative path bytes
- script file bytes
- sorted `assets/**` relative path bytes
- asset file bytes
- sorted `data/**` relative path bytes
- data file bytes

Algorithm: SHA-256 (hex lowercase). Hash format v2 gives every variable field a length prefix and records each file-category count, so binary content cannot be reinterpreted as a path or an extra file. Upgrading invalidates an old cache entry once; Katton rebuilds it automatically.
