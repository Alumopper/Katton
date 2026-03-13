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

While this project uses Gradle for structure, **Kotlin script dependencies are not managed by Gradle**. instead, you need to manually manage them to enable IDE support (code completion and error checking).

### How to configure dependencies for IDE support:

1. Place the required `.jar` files into the `libs` folder.
2. Declare them in your script files using the `@file:DependsOn` annotation.

**Base dependencies required:**

- **Minecraft jar**: Locate it in your Minecraft installation directory (`versions/<version>/<version>.jar`). Copy it to `libs`.
- **Katton jar**: Build it from source or download a release. Copy it to `libs`.

You can add other mod jars to `libs` and declare them in your script to access their APIs. Just remember to keep these jars updated alongside your game and mods.

> [!TIP]
> Some mods (like **fabric-api**) distribute multiple jars nested inside a single file. IDEs cannot read jars within jars. **You must extract these inner jar files** and place them individually into the `libs` folder.
