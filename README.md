# Katton

Katton is a Minecraft Fabric mod that brings Kotlin scripting support to datapacks. It allows you to interact directly with the Minecraft server and Fabric APIs within your scripts, enabling the creation of custom gameplay mechanics, commands, and items using Kotlin's expressive and concise syntax. Additionally, Katton features script hot-reloading—similar to vanilla functions—allowing for rapid iteration without the need for server restarts.

## Roadmap

- [x] Basic Kotlin script support
- [x] Script hot-reloading
- [ ] Simple API for common tasks (e.g., registering commands, items, blocks)
- [ ] Documentation and usage examples
- [ ] Support for `#load` and `#tick` tags

## Usage

> [!WARNING]
> Katton is currently in early development and may contain bugs or incomplete features. Feedback and contributions are welcome!

### Getting Started

To get started, add Katton to your Fabric modpack. Then, create a datapack within your world's `datapacks` directory. Inside your namespace folder, create a `scripts` subdirectory and place your Kotlin scripts (with the `.kts` extension) there. Katton automatically compiles these scripts when the datapack is loaded.

### Execution

In-game, you can execute scripts using the command:
`/script <namespace:script_name>`

This functions similarly to the vanilla `/function` command.

### Example Script

Here is a simple example that sends a message to the command source:

```kotlin
// File: data/kts4mc/scripts/test.kts

// Import necessary Minecraft classes
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level

// Declare variables provided by Katton
// These are initialized automatically during execution
lateinit var server: MinecraftServer
lateinit var source: CommandSourceStack

// Script logic
val level = server.getLevel(Level.NETHER)
source.sendSuccess(
    { Component.literal("Nether Level Object: $level") },
    false
)
```

You can modify scripts and apply changes instantly using the `/reload` command, without restarting the server.

### IDE Support

When using an IDE like IntelliJ IDEA, you may encounter unresolved references for Minecraft and Fabric API classes because the IDE is unaware of the game's runtime environment. To resolve this and enable autocomplete, you may set up a basic Gradle project.

You can find an example katton project with the necessary dependencies and configurations in the [Katton-Example](https://github.com/Alumopper/Katton-Example)。
