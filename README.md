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

### Script Debugging (Remote)

Katton supports debugging datapack Kotlin scripts (`.kts`) through JVM remote debugging.

1. Start Minecraft (or dedicated server) with a debug agent, for example:

    ```text
    -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    ```

2. In IntelliJ IDEA, create an **Attach to remote JVM** configuration and connect to the same host/port.
3. Set breakpoints in the actual datapack script file (for example `data/<namespace>/scripts/*.kts`).
4. Trigger script execution with `/script <namespace:script_name>` (or `/reload` then execute again).

For active breakpoint debugging, you can also execute scripts with:

`/script debug <namespace:script_name>`

This debug path compiles on-demand with cache bypass, so breakpoints can be applied without making meaningless source edits.

If you want `/reload` to always force recompilation while debugging, add JVM option:

`-Dkatton.debug=true`

> [!NOTE]
>
> - Attach success does not guarantee script breakpoint hit; source mapping must match the runtime-compiled script source name.
> - Katton now preserves line numbers when processing top-level `@file:` annotations (annotation lines are blanked instead of removed), so breakpoints are less likely to shift.
> - Use `/script debug <id>` for iterative debugging when you do not want to run full `/reload`.
> - When the same script is executed again, Katton replaces event handlers previously registered by that script owner, preventing duplicate registrations during reload/debug iterations.
> - If breakpoints still do not hit, restart the target JVM process and re-attach (to avoid stale classes from previous runs).