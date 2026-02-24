# Katton

Katton is a Minecraft Fabric mod that brings Kotlin scripting to datapacks. It lets you interact directly with Minecraft server internals and Fabric APIs from scripts, making it easy to build custom mechanics, commands, and items with Kotlin's concise syntax. Katton also supports hot reloading (similar to vanilla functions), so you can iterate quickly without restarting the server.

## Roadmap

- [x] Basic Kotlin script support
- [x] Script hot reloading
- [x] Remote JVM debugging support
- [x] Simple APIs for common tasks (registering commands, items)
- [ ] Documentation and usage examples

## Usage

> [!WARNING]
> Katton is still in early development. Bugs and incomplete features are expected. Feedback and contributions are welcome.

### Getting Started

Add Katton to your Fabric modpack, then create a datapack in your world's `datapacks` directory. Inside your datapack namespace folder, create a `scripts` subdirectory and place your Kotlin script files there. Katton compiles these scripts automatically when the datapack is loaded.

A ready-to-use example project (with dependencies and basic configuration) is available at [Katton-Example](https://github.com/Alumopper/Katton-Example).

### IDE Support

In IDEs such as IntelliJ IDEA, you may see unresolved references for Minecraft/Fabric classes because those types are provided by the game runtime. To fix this and enable completion, create a minimal Gradle project for script development.

> [!NOTE]
> Even though these are "Kotlin Script" files, using the `.kt` extension usually provides better IDE support. So Katton only processes `.kt` files as scripts, and you can use regular Kotlin syntax without worrying about script-specific limitations.
>
> Some IDE inspections may complain about top-level statements in `.kt` files. In that case, use `fun main()` as the script entry point, move your top-level logic into `main`, and invoke it at the end of the file with `val __entrypoint__ = main()`.

### Script Debugging (Remote)

Katton supports debugging datapack Kotlin scripts through standard JVM remote debugging.

1. Start Minecraft (or the dedicated server) with a debug agent, for example:

   ```text
   -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
   ```

2. In IntelliJ IDEA, create an **Attach to remote JVM** run configuration and connect to the same host and port.
3. Set breakpoints in the actual datapack script file (for example, `data/<namespace>/scripts/*.kt`).
4. Enjoy debugging your scripts with the IDE's standard debugging tools.

> [!NOTE]
>
> - A successful debugger attachment does not guarantee that script breakpoints will be hit; source mapping must match the runtime-compiled script source name.
> - When the same script is executed again, Katton replaces event handlers previously registered by that script owner, preventing duplicate registrations during reload/debug iteration.
> - If breakpoints still do not trigger, restart the target JVM process and attach again to avoid stale classes from previous runs.

## Registering Items

Katton supports registering native Minecraft Items from scripts with hot-reload capability. Items registered this way have the same capabilities as items registered by regular Fabric mods.

### Basic Item Registration

Use `registerNativeItem` to register a custom Item:

```kotlin
import net.minecraft.world.item.Item
import top.katton.api.*

registerNativeItem(
    id = "mymod:custom_item",
    registerMode = RegisterMode.RELOADABLE,
    configure = {
        stacksTo(64)  // max stack size
    }
) {
    Item(it)  // 'it' is the configured Item.Properties
}
```

### Custom Item with Behavior

To create an item with custom behavior (like right-click actions), create an anonymous object extending `Item`:

```kotlin
import net.minecraft.world.item.Item
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import top.katton.api.*

registerNativeItem(
    id = "mymod:magic_wand",
    registerMode = RegisterMode.RELOADABLE,
    configure = {
        stacksTo(1)
    }
) { props ->
    object : Item(props) {
        override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
            if (level.isClientSide) return InteractionResult.SUCCESS
            // Custom server-side logic here
            return InteractionResult.SUCCESS
        }
    }
}
```

### Register Modes

- `RegisterMode.AUTO` - Automatically choose the best mode based on current game state
- `RegisterMode.RELOADABLE` - Item can be hot-reloaded; changes take effect after `/katton reload`
- `RegisterMode.PERSISTENT` - Item persists across reloads; use for items that should always exist

### Important Notes

1. **Item Construction Timing**: The `itemFactory` lambda is called during the registration window when the registry is temporarily unfrozen. Do not construct Item instances outside this lambda.

2. **Hot Reload Behavior**: When using `RELOADABLE` mode, the item's behavior can be updated by modifying the script and running `/katton reload`. The item instance itself remains registered in the game's registry.

3. **Accessing Registered Items**: Use the `ITEMS` registry to get your registered items:

```kotlin
import top.katton.registry.KattonRegistry.ITEMS
import top.katton.registry.id

val myItem = ITEMS[id("mymod:custom_item")]
val itemStack = myItem?.getDefaultInstance()
```

### Giving Items to Players

```kotlin
import top.katton.api.*

// Give item to player
ITEMS[id("mymod:custom_item")]?.let {
    giveItem(player, it.getDefaultInstance())
}
```

## Hot Reloading

Run `/katton reload` to reload all scripts without restarting the server. This will:

1. Re-read all script files from datapacks
2. Re-compile and execute the scripts
3. Update event handlers and item behaviors

> [!NOTE]
> Items registered with `RELOADABLE` mode will have their behavior updated on reload, but the item instance itself remains in the game registry. To add new items, simply add new `registerNativeItem` calls in your scripts.
