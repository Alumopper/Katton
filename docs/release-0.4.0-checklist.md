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
- [x] Confirm all six deployment jars load and stop cleanly on their target
  server/runtime.

## Compatibility and migration

- [x] Update the repository README quick start to use `ServerPhase.READY`, a
  valid world-pack manifest, and both supported Minecraft versions.
- [x] Update all Katton-Example world entrypoints to declare an appropriate
  server/client phase.
- [x] Add `dependencies` to every Katton-Example manifest.
- [x] Re-sign every signed Katton-Example pack with signature payload v2.
- [x] Prepare, verify, and push the Katton-Example 0.4.0 migration as commit
  `c11949c` to `master` (all three subprojects build; all three manifests are
  re-signed with payload v2). A durable export remains at
  `docs/0001-Migrate-examples-to-Katton-0.4.0.patch`.
- [x] Document the 0.3.x to 0.4.0 migration: mandatory `dependencies`, explicit
  world entrypoint phases, payload-v2 signatures, and Minecraft-qualified Maven
  versions.

## Automated verification

- [x] Run the common test suite for MC 26.1.2 and 26.2 (76 tests per target,
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
- [x] Fabric dedicated server/client: initial sync on both 26.1.2 and 26.2; on
  26.2, live revision success/ACK, failed candidate activation rollback and
  disconnect, removed-pack deactivation/ACK, and clean client/server stops
  (2026-08-28).
- [x] NeoForge 26.1.2: client/integrated server smoke test; Katton publishes its
  script-pack revision, the local player joins, all dimensions save, and Gradle
  exits 0 (2026-08-28).
- [x] NeoForge 26.2: client/integrated server smoke test with the same startup,
  player-join, save, and exit-0 evidence (2026-08-28).
- [x] NeoForge 26.1.2: dedicated server reaches `Done` with Katton 0.4.0 loaded
  (port 25577, 2026-08-28).
- [x] NeoForge 26.2: dedicated server reaches `Done` with Katton 0.4.0 loaded
  (port 25576, 2026-08-28).
- [x] NeoForge dedicated server/client: initial sync on both 26.1.2 and 26.2;
  on 26.2, live revision success/ACK, failed candidate activation rollback,
  removed-pack deactivation/ACK, and clean server stop (2026-08-28).
- [x] Paper 26.1.2: start, typed READY context, `/katton status`, global scheduler,
  reload, managed-listener cleanup, and clean stop (Paper build 74, 2026-08-28).
- [x] Paper 26.2: the same READY, status, scheduler, reload, listener-cleanup, and
  clean-stop flow (Paper build 119, 2026-08-28).
- [x] Standard Paper script-pack data: 26.1.2 loaded and executed a generated
  load function; 26.2 did the same and applied a live `data/**` revision before
  stopping cleanly (2026-08-28).
- [x] Folia 26.2 build 7: startup, typed READY context, immediate/delayed global
  scheduler callbacks, live reload, managed-listener cleanup, and region-aware
  clean stop (2026-08-28). Cold compiler initialization emitted one five-second
  watchdog diagnostic; the pack still activated and the live reload took under
  one second.
- [x] Fabric and NeoForge: dynamic Attach injection and rollback smoke tests on
  both supported MC targets (covered by the real-injection gate, 2026-08-28).
- [x] Keep FCL/Android explicitly experimental rather than claim unverified
  on-device support; the desktop FCL-equivalent startup-agent path passed through
  every final Fabric/NeoForge jar (2026-08-28).

## Release preparation

- [x] Finish and commit the FCL injection work as `bfc6e96`.
- [x] Review the 0.3.0 to 0.4.0 diff and write user-facing release notes.
- [x] Set `mod_version=0.4.0` and verify Maven coordinates use
  `0.4.0+mc26.1.2` and `0.4.0+mc26.2`.
- [x] Publish common, Fabric, NeoForge, and Paper Maven artifacts for both MC
  targets. All eight POMs resolve with HTTP 200 from the public aggregate
  repository (2026-08-29).
- [x] Verify local Maven publication for the 26.1.2 common, Fabric, NeoForge,
  Paper, and signing-plugin artifacts. This caught and fixed a duplicate
  `sign-plugin` publication that previously broke Gradle module metadata. The
  signing plugin marker, implementation POM, JAR, and Gradle module metadata
  also resolve publicly with HTTP 200 (2026-08-29).
- [x] Prepare and verify the katton-api 0.4.0 template update locally through
  commit `01b3887` on branch `release/0.4.0-template`. Its six generated platform/MC
  combinations pass `pnpm verify:template`, the full VitePress site passes
  `pnpm docs:build`, and the durable export is
  `docs/0001-Update-template-generator-for-Katton-0.4.0.patch`.
- [x] Apply/push the stable katton-api template update through commit `01b3887`
  to `main` after the 0.4.0 Maven artifacts became publicly resolvable.
- [x] Produce SHA-256 checksums for the six deployment jars.
- [x] Create the stable GitHub `v0.4.0` Release (not a pre-release) with all six
  jars, checksums, migration notes, supported-version matrix, and known
  limitations. The public release targets `3965de5`; all seven uploaded assets
  match their local SHA-256 digests (2026-08-29).

## Recorded partial runtime evidence

- The final release gate completed 112 Gradle tasks from a clean tree with
  `--no-build-cache` on 2026-08-28. It rebuilt and tested both targets,
  regenerated 95 bilingual API pages, audited all six descriptors, exercised
  both startup-agent paths, and staged the verified jars plus `SHA256SUMS`.
- After the client-runtime mixin fixes, `prepareReleaseArtifacts
  --no-build-cache --max-workers=1` passed again (69 tasks: 22 executed and 47
  up-to-date), re-audited all six descriptors, reran the four final-jar startup
  agent probes, and regenerated the staged jars and checksums on 2026-08-28.
- After the dedicated synchronization fixes, the same release task passed again
  in 1m 6s (69 tasks: 22 executed and 47 up-to-date). An independent SHA-256
  comparison confirmed that all six staged jars match `SHA256SUMS`.
- After the Paper classloader and Folia scheduler fixes, the current source ran
  the release task again in 59s (69 tasks: 31 executed and 38 up-to-date), then
  passed a separate uncached 72-test run on each MC target. A second independent
  SHA-256 comparison matched all six regenerated staged jars.
- After the Paper data-pack compatibility and Folia capability-guard fixes, the
  final release task passed again in 1m 5s (69 tasks: 28 executed and 41
  up-to-date). A separate uncached run passed all 76 tests on each MC target, and
  an independent SHA-256 comparison matched every regenerated staged jar.
- After the NeoForge production Jar-in-Jar fix, `prepareReleaseArtifacts` passed
  all descriptor, embedded-compiler, and startup-agent gates (71 tasks). A forced
  uncached rerun then passed all 76 tests on each MC target with zero failures,
  errors, or skips; an independent SHA-256 comparison matched all six staged
  jars (2026-08-29).
- Fabric dedicated clients used opt-in isolated run directories so local packs
  could not contaminate remote-sync evidence. Both targets downloaded and
  executed the world pack from the server cache. Fabric 26.2 then applied a live
  v2 revision, rejected and rolled back an intentionally failing v3 client
  activation, and acknowledged an empty v5 revision after pack removal. Both
  client and server tasks exited with `BUILD SUCCESSFUL` after clean shutdown.
- Paper runtime verification found and fixed a plugin classloader identity bug:
  asynchronous reload workers previously caused a second copy of
  `ServerReadyContext`, rejecting valid typed entrypoints. Both Paper targets now
  execute the typed context and preserve only the new managed listener after a
  reload.
- The first real Folia run found and fixed direct use of Minecraft's
  `server.execute`, which Folia rejects. Paper now injects the Global Region
  Scheduler for serialized Katton server mutations, and the build exposes a
  repeatable `:paper:<mc>:runFolia` task.
- Data-bearing Paper smoke packs found a second runtime mapping difference:
  Paper's `PackRepository.setSelected` adds a required-pack flag absent from the
  Mojang runtime. The compatibility bridge now handles both signatures. Standard
  Paper 26.1.2 loaded the generated function and 26.2 loaded it, applied a live
  revision, and stopped cleanly. Folia 26.2 rejects Minecraft's resource-reload
  operation itself, so Katton now rejects `data/**` packs there with a precise
  capability message instead of attempting a partial activation.
- The release-agent gate used each of the four final Fabric/NeoForge deployment
  jars as `-javaagent`, modified an already-loaded method, and rolled the
  injection back successfully on 2026-08-28.
- The exact Fabric deployment jars were loaded by Loom's production server task
  with Fabric Loader 0.18.4 and the matching Fabric API. Both `0.4.0+mc26.1.2`
  and `0.4.0+mc26.2` reached `Done`, accepted an isolated RCON `stop`, saved all
  dimensions, and returned `BUILD SUCCESSFUL` on ports 25570 and 25571
  (2026-08-28/29). The local run directories were restored to online mode,
  RCON disabled, and port 25565 afterward.
- The exact NeoForge deployment jars were tested in isolated servers installed
  with the official NeoForge installers (`26.1.2.30-beta` and `26.2.0.7-beta`).
  This production scan found and fixed invalid optional JLine provider metadata
  in the embedded Kotlin compiler. The rebuilt jars loaded as Katton
  `0.4.0+mc26.1.2` and `0.4.0+mc26.2`, reached `Done` on ports 25572 and 25573,
  then accepted RCON `stop` and shut down cleanly (2026-08-29).
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
- NeoForge dedicated synchronization found and fixed two additional blockers:
  configuration packets arrived before Minecraft exposed `currentServer`, and
  NeoForge dispatched the configuration handler on the render thread. Katton
  now captures the logical `ServerData.ip` before connecting and uses Minecraft's
  managed task pump while waiting for the async configuration reload.
- NeoForge 26.1.2 and 26.2 both downloaded, compiled, and executed an unsigned
  pre-trusted localhost test pack during initial configuration. The repeatable
  client command is `runClient -PkattonQuickPlayServer=<host:port>`.
- The NeoForge 26.2 live test published revision 2 to one remote player, executed
  the new client entrypoint, and received a success ACK. An intentionally failing
  client candidate at revision 3 restored and re-executed the previous v3
  snapshot, returned a failure ACK, and was rejected by the server. Removing the
  pack then published an empty revision 5 and received a success ACK. A temporary
  localhost-only RCON endpoint triggered the reloads and performed a clean
  `stop`; it was disabled again after the test.
- NeoForge 26.1.2 logged repeated NVIDIA/OpenGL framebuffer-completeness debug
  errors during early display initialization on this machine, but continued to
  load resources, enter and render the world, save cleanly, and exit 0.
- The user accepted the Minecraft EULA for the four local Fabric/Paper target
  run directories on 2026-08-28. All four `eula.txt` files remain `true`; the
  temporary offline-mode ports used for isolated smokes were restored to their
  original `online-mode=true`, port-25565 configurations afterward.
- NeoForge 26.2 loads Katton `0.4.0+mc26.2`, initializes its networking and
  mixins, creates a world, reaches `Done`, and stops cleanly after saving all
  dimensions. The test used port 25576 because port 25565 belongs to a separate
  vanilla server and was deliberately left untouched.
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
- Eliminating the one-time Folia global-region watchdog diagnostic during a cold
  embedded-compiler initialization; verified hot reloads do not reproduce it.
- Runtime mounting of script-pack `data/**` on Folia; Folia 26.2 does not support
  the underlying server resource-reload operation. Native datapacks remain the
  workaround.
- Physical-device FCL/Android verification; 0.4.0 only claims the verified
  desktop-equivalent startup-agent path.
