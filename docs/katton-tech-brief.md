# Katton Technical Brief (For AI Quick Read)

## 1. Project Positioning

Katton is a Minecraft Fabric/NeoForge mod that executes Kotlin `.kt` scripts at runtime.

Core capabilities:
- Kotlin scripts as gameplay logic.
- Hot reload without full server restart.
- Script-side APIs for commands, registries, events, datapack mutation.
- Optional unsafe runtime method/class injection for advanced hooks.

Primary upstream docs:
- Home: https://katton.mcfpp.top/
- Quick Start: https://katton.mcfpp.top/quickstart.html
- API index: https://katton.mcfpp.top/api/

## 2. Runtime Modules

- `common`: script engine, shared APIs, registry/datapack mutation, pack system, protocol definitions.
- `fabric`: Fabric entrypoints, Fabric networking registration, Fabric mixins.
- `neoforge`: NeoForge entrypoints, NeoForge payload handlers, NeoForge mixins.

Important entry classes:
- `top.katton.Katton` (common bootstrap and reload orchestration).
- `top.katton.KattonFabric`, `top.katton.KattonClientFabric`.
- `top.katton.KattonNeoForge`, `top.katton.KattonClientNeoForge`.

## 3. Script Execution Model

Entrypoint annotations:
- `@ServerScriptEntrypoint`
- `@ClientScriptEntrypoint`

Compiler/runtime:
- `top.katton.engine.ScriptEngine` compiles imported `.kt` sources and executes static top-level no-arg entrypoint methods.
- Owner-aware execution via `Event.withScriptOwner(...)` allows hot reload cleanup by owner.

Load sources in current implementation:
- Server base scripts: datapack scripts (`ScriptLoader`) + local script packs (`ScriptPackManager`, server env).
- Client base scripts: resourcepack scripts (`ClientScriptLoader`) + local script packs + server-transferred cache packs.

## 4. New Script Pack System (kattonpacks)

Directory model:
- Global packs: `<gameDir>/kattonpacks/<packDir>/...`
- World packs: `<worldDir>/kattonpacks/<packDir>/...`

Each pack directory contains:
- `manifest.json`
- One or more `.kt` files (any nested structure).

Current parser fields (tolerant):
- `id`, `name`, `version`, `description`, `authors[]`
- `enabled` (default true)
- `targets.server`, `targets.client` (default true)

State persistence:
- Per pack local state file: `.kattonpack.state.json` with `enabled` boolean.
- Runtime list and toggle managed by `ScriptPackManager`.

Hashing:
- SHA-256 over `manifest.json` content + ordered script relative paths + script bytes.

## 5. Client/Server Pack Sync Protocol

Purpose:
- During configuration phase, client ensures it has exact server script packs before registry validation flow continues.

Packets:
- `ScriptPackHashListPacket` (S2C): server sends loaded server-pack hash snapshot.
- `ScriptPackRequestPacket` (C2S): client asks for mismatched/missing sync IDs.
- `ScriptPackBundlePacket` (S2C): server sends requested pack manifests and files.

Cache layout on client:
- `<gameDir>/serverpacks/<sha256(serverAddress)>/<base64(syncId)>/...`

Execution timing:
- Client receives hashes -> compares cached packs -> requests mismatches -> stores bundles.
- Before registry sync check (existing mixin interception point), client executes pending synced packs (`ServerPackCacheManager.executePendingScriptsBeforeRegistryCheck()`).

## 6. GUI Pack Manager

Screen:
- `top.katton.client.ScriptPackUi` / `ScriptPackManagerScreen`.

Capabilities:
- View loaded local/global/world/server-cache pack list.
- View details (id/name/version/scope/enabled/hash/authors/description/path).
- Toggle enable/disable for editable local packs.
- Trigger reload after changes.

Lock semantics in-world:
- Global packs are shown as locked when inside a world.
- Only world packs are editable in-world.
- Server cache packs are always read-only.

Open keybind:
- `K` (client-side in both Fabric and NeoForge).

## 7. Existing Reload Semantics

Server reload (`Katton.reloadScripts`):
- Refresh datapack script snapshot.
- Refresh global/world pack snapshot.
- Clear/rebind script-managed state (events, injections, registries, datapack mutation).
- Compile+execute server scripts.
- Apply datapack mutations.
- Sync runtime registry snapshots and script pack hashes to clients.

Client reload (`Katton.reloadClientScripts`):
- Refresh resourcepack script snapshot.
- Refresh local packs.
- Merge local + server-cache script sources and execute client environment scripts.

## 8. Notes for Future AI Refactors

- Keep packet ordering stable in configuration stage; registry sync timing is strict.
- Preserve owner-based cleanup on reload to avoid handler duplication.
- If changing pack hash algorithm, migrate client cache invalidation explicitly.
- Ensure Fabric and NeoForge handler registrations remain parity-tested.
- Do not remove existing datapack/resourcepack script loading unless intentionally breaking compatibility.
