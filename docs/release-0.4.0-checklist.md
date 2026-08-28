# Katton Alpha 0.4.0 Release Checklist

This document is the authoritative release TODO for Alpha 0.4.0. A checked item
must be backed by a repeatable command, an inspected artifact, or a recorded
runtime result. A successful Gradle build alone does not prove that a platform
artifact is loadable.

## Release blockers

- [x] Make NeoForge metadata generation read the controller project's
  `src/main/templates` directory for every Stonecutter target.
- [x] Declare the Paper plugin version as an input to `processResources` so a
  version change cannot reuse an old `paper-plugin.yml`.
- [x] Remove the common module's stale `fabric.mod.json` so it is not copied
  into NeoForge and Paper deployment jars.
- [x] Add an automated release-artifact gate for all six deployment jars.
- [x] Confirm every deployment jar contains exactly the descriptor for its
  platform and that the descriptor version matches the filename.
- [ ] Confirm all six deployment jars load and stop cleanly on their target
  server/runtime.

## Compatibility and migration

- [x] Update the repository README quick start to use `ServerPhase.READY`, a
  valid world-pack manifest, and both supported Minecraft versions.
- [x] Update all Katton-Example world entrypoints to declare an appropriate
  server/client phase.
- [x] Add `dependencies` to every Katton-Example manifest.
- [x] Re-sign every signed Katton-Example pack with signature payload v2.
- [x] Prepare and verify the Katton-Example 0.4.0 migration locally as commit
  `c11949c` on branch `release/0.4.0-migration` (all three subprojects build;
  all three manifests are re-signed with payload v2). The clone is under
  `build/release-work/Katton-Example`, with a durable export at
  `docs/0001-Migrate-examples-to-Katton-0.4.0.patch`; pushing it remains an
  external release action.
- [x] Document the 0.3.x to 0.4.0 migration: mandatory `dependencies`, explicit
  world entrypoint phases, payload-v2 signatures, and Minecraft-qualified Maven
  versions.

## Automated verification

- [x] Run the common test suite for MC 26.1.2 and 26.2 (70 tests per target,
  zero failures on 2026-08-28).
- [x] Run startup-agent capability and real injection verification for both MC
  targets (zero failures on 2026-08-28).
- [x] Run dynamic-Attach real injection and rollback verification for both MC
  targets (zero failures on 2026-08-28).
- [x] Run startup-agent real injection and rollback through all four final
  Fabric/NeoForge deployment jars (zero failures on 2026-08-28).
- [x] Generate bilingual API documentation (95 pages for two locales on
  2026-08-28).
- [x] Run a clean, uncached build after setting `mod_version=0.4.0`.
- [x] Add a GitHub Actions workflow that builds, tests, and audits artifacts on
  pull requests and pushes to `main`.

## Runtime matrix

- [x] Fabric 26.1.2: client/integrated server smoke test; Katton publishes its
  script-pack revision, the local player joins, all dimensions save, and Gradle
  exits 0 (2026-08-28).
- [x] Fabric 26.2: client/integrated server smoke test with the same startup,
  player-join, save, and exit-0 evidence (2026-08-28).
- [ ] Fabric dedicated server/client: initial sync, live revision, failed
  compilation rollback, and removed-pack deactivation.
- [x] NeoForge 26.1.2: client/integrated server smoke test; Katton publishes its
  script-pack revision, the local player joins, all dimensions save, and Gradle
  exits 0 (2026-08-28).
- [x] NeoForge 26.2: client/integrated server smoke test with the same startup,
  player-join, save, and exit-0 evidence (2026-08-28).
- [x] NeoForge 26.1.2: dedicated server reaches `Done` with Katton 0.4.0 loaded
  (port 25577, 2026-08-28).
- [x] NeoForge 26.2: dedicated server reaches `Done` with Katton 0.4.0 loaded
  (port 25576, 2026-08-28).
- [ ] NeoForge dedicated server/client: initial sync, live revision, failed
  compilation rollback, and removed-pack deactivation.
- [ ] Paper 26.1.2: start, `/katton status`, reload, listener cleanup, stop.
- [ ] Paper 26.2: start, `/katton status`, reload, listener cleanup, stop.
- [ ] Folia: scheduling and reload smoke test.
- [ ] Fabric and NeoForge: dynamic Attach injection smoke test.
- [ ] FCL/Android or equivalent: startup-agent injection smoke test if advertised
  as a 0.4.0 feature.

## Release preparation

- [x] Finish and commit the FCL injection work as `bfc6e96`.
- [x] Review the 0.3.0 to 0.4.0 diff and write user-facing release notes.
- [x] Set `mod_version=0.4.0` and verify Maven coordinates use
  `0.4.0+mc26.1.2` and `0.4.0+mc26.2`.
- [ ] Publish common, Fabric, NeoForge, and Paper Maven artifacts for both MC
  targets.
- [x] Verify local Maven publication for the 26.1.2 common, Fabric, NeoForge,
  Paper, and signing-plugin artifacts. This caught and fixed a duplicate
  `sign-plugin` publication that previously broke Gradle module metadata.
- [x] Prepare and verify the katton-api 0.4.0 template update locally as commit
  `e5c377a` on branch `release/0.4.0-template`. Its six generated platform/MC
  combinations pass `pnpm verify:template`, the full VitePress site passes
  `pnpm docs:build`, and the durable export is
  `docs/0001-Update-template-generator-for-Katton-0.4.0.patch`.
- [ ] Apply/push the katton-api template update after the 0.4.0 Maven artifacts
  are published, so the public generator never points at unavailable coordinates.
- [x] Produce SHA-256 checksums for the six deployment jars.
- [ ] Create the GitHub `v0.4.0` pre-release with all six jars, checksums,
  migration notes, supported-version matrix, and known limitations.

## Recorded partial runtime evidence

- The final release gate completed 112 Gradle tasks from a clean tree with
  `--no-build-cache` on 2026-08-28. It rebuilt and tested both targets,
  regenerated 95 bilingual API pages, audited all six descriptors, exercised
  both startup-agent paths, and staged the verified jars plus `SHA256SUMS`.
- After the client-runtime mixin fixes, `prepareReleaseArtifacts
  --no-build-cache --max-workers=1` passed again (69 tasks: 22 executed and 47
  up-to-date), re-audited all six descriptors, reran the four final-jar startup
  agent probes, and regenerated the staged jars and checksums on 2026-08-28.
- The release-agent gate used each of the four final Fabric/NeoForge deployment
  jars as `-javaagent`, modified an already-loaded method, and rolled the
  injection back successfully on 2026-08-28.
- The four Fabric/NeoForge client runs used a 3 GiB maximum heap, entered an
  integrated-server world, published Katton script-pack revision 1, rendered a
  local player, saved every dimension, and exited through the game window with
  Gradle status 0. The repeatable commands are Fabric `runClient` with
  `--args=--quickPlaySingleplayer <world>` and NeoForge `runClient` with
  `-PkattonQuickPlayWorld=<world>`.
- These client runs caught and fixed two release-blocking mixin regressions that
  compilation alone could not detect: the 26.1.2 `LevelRenderer` callback needs
  `ChunkSectionsToRender`, while 26.2 uses `render` without it; NeoForge 26.2's
  HUD target is `Hud` rather than `Gui`.
- NeoForge 26.1.2 logged repeated NVIDIA/OpenGL framebuffer-completeness debug
  errors during early display initialization on this machine, but continued to
  load resources, enter and render the world, save cleanly, and exit 0.
- Fabric 26.1.2 reaches Fabric Loader 0.18.4 with Katton
  `0.4.0+mc26.1.2`, then stops at the unaccepted local Minecraft EULA.
- Fabric 26.2 reaches Fabric Loader with Katton `0.4.0+mc26.2`, then stops at
  the unaccepted local Minecraft EULA.
- Paper 26.1.2 reaches Paper build 74 and discovers Katton
  `0.4.0+mc26.1.2`, then stops at the unaccepted local Minecraft EULA.
- Paper 26.2 reaches Paper build 119 and discovers Katton
  `0.4.0+mc26.2`, then stops at the unaccepted local Minecraft EULA.
- NeoForge 26.2 loads Katton `0.4.0+mc26.2`, initializes its networking and
  mixins, creates a world, and reaches `Done`. The test used port 25576 because
  port 25565 belongs to a separate vanilla server and was deliberately left
  untouched.
- NeoForge 26.1.2 loads Katton `0.4.0+mc26.1.2`, initializes its networking and
  mixins, creates a world, and reaches `Done` on port 25577. ModDevGradle did not
  forward console input to the server, so this records startup compatibility but
  not a clean `stop` command result.

## Explicitly deferred from 0.4.0

These are known Alpha limitations, not release blockers unless the advertised
scope changes:

- Paper unsafe injection / Ignite integration (GitHub issue #2).
- Paper raw loot-table replacement hooks.
- Strongly typed Paper enchanting bridge arguments.
- Entity tick event and NeoForge client block-entity lifecycle callbacks.
- Gradle 10 migration and removal of the Kotlin compiler build-classpath warning.
