# Katton Example Project

This is an example project for [Katton](https://github.com/Alumopper/Katton), a Minecraft Fabric mod that brings Kotlin scripting support to datapacks. It serves as a comprehensive template to help you start creating your own Katton-based datapacks, demonstrating the essential file structure and setup.

## Example Script

The project includes a sample script showcasing the base API provided by Katton. In `main.main.kts`, you'll find an implementation of an explosive bow that shoots TNT arrows.

**How to use:**

1. Get the custom bow using this command:
   `/give @s bow[custom_data={tnt:true}]`
2. Try modifying the script: Change the explosion power (the fifth argument of the `explode` method).
3. Run the `/reload` command in-game to apply your changes instantly.

## Dependencies Setup

This example now uses a hybrid setup:

- Katton comes from Gradle coordinates.
- Minecraft development jars can still be placed manually in `lib/` for IDE support.

### Publish Katton to the local example repository

From the repository root, run:

```bash
./gradlew publishApiToExampleRepo
```

On Windows:

```powershell
.\gradlew.bat publishApiToExampleRepo
```

This publishes the current `common` and `fabric` artifacts to:

- `mavenLocal()`
- `build/repo`

The example build is already configured to resolve from both locations.

### Provide the Minecraft development jar manually

If your scripts import `net.minecraft.*` types directly, place the remapped Minecraft development jar in `examples/fabric/lib/`.

This keeps the example lightweight while avoiding a local Katton jar workflow.

### Add more dependencies

If you want IDE support for additional Fabric mods, add their Maven repositories and coordinates in [examples/fabric/build.gradle.kts](g:\AST\kts4mc-template-1.21.11\examples\fabric\build.gradle.kts).
