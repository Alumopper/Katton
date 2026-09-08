# Katton Alpha 0.5.0 build2 — Release Notes

Katton Alpha 0.5.0 build2 completes the script-pack runtime refactor. It
supports Minecraft 26.1.2 and 26.2 on Fabric, NeoForge, and Paper, and requires
Java 25.

## Added

- Script packs can now be loaded from directories or ZIP files. A ZIP must place
  `manifest.json` at its root.
- Packs can carry private `libs/*.jar` dependencies and declare direct Katton
  pack dependencies with explicit transitive exports.
- Dependency output, including Kotlin metadata, is available only through the
  declared dependency graph. Each pack keeps private libraries isolated.
- Pack content, compilation artifacts, signatures, and synchronization now cover
  source files, libraries, assets, and data resources consistently.

## Reload behavior

- Reloading a consumer keeps an unchanged dependency instance alive, preserving
  its shared state.
- Reloading a dependency replaces its affected consumers in the same transaction.
- Failed candidate activation restores managed callbacks and resource values
  without replaying old entrypoints.
- Independent dependency components commit separately so a failing component
  does not discard an unrelated successful update.

## Validation

- Regression coverage was added for dependency visibility, cycles, private
  library isolation, compilation artifacts, rollback, and registry restoration.
- Paper 26.1.2 and Folia 26.1.2 were tested in real temporary servers with both
  directory and ZIP packs. The probes exercised state preservation, dependency
  replacement, managed Bukkit listener rollback, and delayed global callbacks.

See the [pack model verification record](pack-model-paper-folia-live.md) and
the [pack layout reference](kattonpacks-manifest-and-layout.md).

## Known limitations

- Pack isolation is not a security sandbox. Direct script side effects such as
  world edits, file writes, and static state are outside transactional rollback.
- The Folia probe covers global callbacks. Entity-region and multi-region
  concurrency remain untested in this release.
- Global pack code changes still require a game or server restart.

## Release artifacts

The release contains six deployment JARs and `SHA256SUMS`:

- Fabric for Minecraft 26.1.2 and 26.2
- NeoForge for Minecraft 26.1.2 and 26.2
- Paper for Minecraft 26.1.2 and 26.2
