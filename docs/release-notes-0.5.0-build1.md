# Katton Alpha 0.5.0 build1 — Release Notes

Katton Alpha 0.5.0 build1 adds client camera scenes and world effects. It
supports Minecraft 26.1.2 and 26.2 on Fabric, NeoForge, and Paper. It requires
Java 25.

## Added

- Added camera paths, target follow, look-at, camera shake, and FOV transitions.
- Added linear and Catmull–Rom position interpolation.
- Added shortest-path quaternion rotation interpolation and easing functions.
- Added point, line, ring, sphere, and spiral particle emitters.
- Added beams, trails, rings, textured planes, curves, faces, boxes, spheres,
  cylinders, and cones.
- Added alpha and additive blending. Effects support depth testing and explicit
  through-wall rendering.
- Added a Kotlin scene timeline with sequential, parallel, delayed, paused,
  resumed, repeated, and cancelled tracks.
- Added server commands that start or stop registered scenes for one player or
  nearby players.
- Added client budgets for active effects, particles per tick, and geometry
  vertices per frame.
- Added the `examples/client-scenes` script pack.

## Behavior

- A camera scene changes only the rendered camera. It does not change player
  position, player rotation, or the saved FOV option.
- A full camera scene can block movement, attacks, item use, and mouse rotation.
- Escape cancels the viewer's camera scene and consumes that key event.
- Death, dimension changes, disconnects, target loss, reloads, and cancellation
  release camera and input control.
- A failed precompile keeps the active scene. An activation failure restores the
  old scene definition but does not replay cancelled instances.
- Paper stays server-only. Paper does not initialize the client scene runtime.

## Validation

- Common tests passed on both Minecraft targets: 101 tests per target.
- Fabric and NeoForge builds passed on both Minecraft targets.
- Paper regression builds passed on both Minecraft targets.
- Real Fabric and NeoForge clients passed camera, FOV, particle, geometry,
  texture, Escape, lifecycle, and 100-cycle cleanup checks.
- Two NeoForge clients passed player selection, radius selection, local skip,
  remote stop, and no-late-replay checks.

See the [client scene guide](client-scenes.md) and the
[verification record](client-scenes-verification.md).

## Known limitations

- Client camera and custom rendering require Katton on the client.
- Paper cannot render these client effects.
- Scene playback does not provide frame-accurate synchronization between clients.
- New viewers do not receive an active scene after they join or enter its radius.
- Third-party renderer and custom shader compatibility was not tested for this
  build.

## Release artifacts

The release contains six deployment JARs and `SHA256SUMS`:

- Fabric for Minecraft 26.1.2 and 26.2
- NeoForge for Minecraft 26.1.2 and 26.2
- Paper for Minecraft 26.1.2 and 26.2
