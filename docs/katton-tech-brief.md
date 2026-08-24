# Katton Technical Brief

## 1. Project Positioning

Katton is a Minecraft Fabric/NeoForge mod and Paper/Folia plugin that executes Kotlin `.kt` scripts at runtime.

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
- `paper`: Paper/Folia plugin entrypoint, Bukkit event bridges, NMS conversion, and region-aware scheduling.

Important entry classes:
- `top.katton.Katton` (common bootstrap and reload orchestration).
- `top.katton.KattonFabric`, `top.katton.KattonClientFabric`.
- `top.katton.KattonNeoForge`, `top.katton.KattonClientNeoForge`.
- `top.katton.paper.KattonPaperPlugin`.

## 3. Script Execution Model

Entrypoint annotations:
- `@ServerScriptEntrypoint`
- `@ClientScriptEntrypoint`

Compiler/runtime:
- `top.katton.engine.ScriptEngine` compiles imported `.kt` sources and executes static top-level no-arg entrypoint methods.
- Owner-aware execution via `Event.withScriptOwner(...)` allows hot reload cleanup by owner.

Load sources in current implementation:
- Server base scripts: local script packs (`ScriptPackManager`).
- Client base scripts: local script packs + server-transferred cache packs.

Side execution model:
- Script packs are side-agnostic.
- A single pack can contain both `@ServerScriptEntrypoint` and `@ClientScriptEntrypoint` functions.
- Which functions execute is decided by the runtime environment, not by manifest-side flags.

## 4. New Script Pack System (kattonpacks)

Directory model:
- Global packs: `<gameDir>/kattonpacks/<packDir>/...`
- World packs: `<worldDir>/kattonpacks/<packDir>/...`

Each pack directory contains:
- `manifest.json`
- One or more `.kt` files (any nested structure).
- Optional `assets/<namespace>/**` standard resource-pack resources.
- Optional `data/<namespace>/**` standard data-pack resources.

Current parser fields:
- `id`, `name`, `version`, `description`, `authors[]`
- `enabled` (default true)
- required external `dependencies[]` plus optional Katton `packDependencies[]`
- optional primitive `config` defaults; user overrides live outside the pack under `.katton/config-overrides/` and server-cache keys are isolated from local sync IDs

State persistence:
- Per pack local state file: `.kattonpack.state.json` with `enabled` boolean.
- Runtime list and toggle managed by `ScriptPackManager`.

Hashing:
- SHA-256 over `manifest.json` content + ordered script, `assets/**`, and `data/**` relative paths + file bytes.

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
- Scan and precompile a candidate while the last-known-good snapshot stays active.
- Validate pack dependencies and isolate independent dependency components; a rejected local component retains its last-known-good snapshot without blocking unrelated packs.
- Clear/rebind script-managed state only after preflight succeeds.
- Compile+execute server scripts.
- Atomically mount static script-pack `data/**` resources; restore the prior snapshot on failure.
- Apply datapack mutations.
- Sync runtime registry snapshots and script pack hashes to clients.

Client reload (`Katton.reloadClientScripts`):
- Precompile the candidate before clearing client-owned handlers.
- Isolate independent dependency components and retain their prior snapshots on compile or entrypoint failure.
- Merge local + server-cache script sources and execute client environment scripts.
- Atomically mount script-pack `assets/**` resources and restore the prior runtime/resources on failure.

## 8. Event Capability Query

Event bridges intentionally expose their real platform limits instead of simulating semantics that the loader cannot provide:

- Script/API: `EventCapabilities.query("LivingUseItemEvent.onUseItemTick")` and `EventCapabilities.notable()`.
- Command: `/katton capabilities events [EventObject.onEvent]`.
- Results are `SUPPORTED`, `PARTIAL`, or `UNSUPPORTED`, include execution context, and use an explicit `不支持：...` reason for unavailable events.
- Paper raw Bukkit events and async chat callbacks are reported as partial; unavailable hooks remain unimplemented.

## 9. Notes for Future AI Refactors

- Keep packet ordering stable in configuration stage; registry sync timing is strict.
- Preserve owner-based cleanup on reload to avoid handler duplication.
- If changing pack hash algorithm, migrate client cache invalidation explicitly.
- Ensure Fabric and NeoForge handler registrations remain parity-tested.
