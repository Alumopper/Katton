# Katton

Katton is a Minecraft Fabric mod that brings Kotlin scripting to datapacks. It lets you interact directly with Minecraft server internals and Fabric APIs from scripts, making it easy to build custom mechanics, commands, and items with Kotlin’s concise syntax. Katton also supports hot reloading (similar to vanilla functions), so you can iterate quickly without restarting the server.

## Roadmap

- [x] Basic Kotlin script support
- [x] Script hot reloading
- [ ] Simple APIs for common tasks (for example, registering commands, items, and blocks)
- [ ] Documentation and usage examples
- [ ] Support for `#load` and `#tick` tags

## Usage

> [!WARNING]
> Katton is still in early development. Bugs and incomplete features are expected. Feedback and contributions are welcome.

### Getting Started

Add Katton to your Fabric modpack, then create a datapack in your world’s `datapacks` directory. Inside your datapack namespace folder, create a `scripts` subdirectory and place your Kotlin script files there (typically `.kts`, optionally `.kt`). Katton compiles these scripts automatically when the datapack is loaded.

### Execution

Run a script in game with:

`/script <namespace:script_name>`

This works similarly to the vanilla `/function` command.

### IDE Support

In IDEs such as IntelliJ IDEA, you may see unresolved references for Minecraft/Fabric classes because those types are provided by the game runtime. To fix this and enable completion, create a minimal Gradle project for script development.

A ready-to-use example project (with dependencies and basic configuration) is available at [Katton-Example](https://github.com/Alumopper/Katton-Example).

> [!NOTE]
> Even though these are “Kotlin Script” files, using the `.kt` extension usually provides better IDE support. Katton treats `.kt` files as scripts and compiles them the same way.
>
> Some IDE inspections may still complain about top-level statements in `.kt` files. In that case, use `fun main()` as the script entry point, move your top-level logic into `main`, and invoke it at the end of the file with `val __entrypoint__ = main()`.

### Script Debugging (Remote)

Katton supports debugging datapack Kotlin scripts through standard JVM remote debugging.

1. Start Minecraft (or the dedicated server) with a debug agent, for example:

   ```text
   -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
   ```

2. In IntelliJ IDEA, create an **Attach to remote JVM** run configuration and connect to the same host and port.
3. Set breakpoints in the actual datapack script file (for example, `data/<namespace>/scripts/*.kt`).
4. Trigger execution with `/script <namespace:script_name>` (or run `/reload` and execute again).

If you want `/reload` to always force recompilation while debugging, add this JVM option:

`-Dkatton.debug=true`

> [!NOTE]
>
> - A successful debugger attachment does not guarantee that script breakpoints will be hit; source mapping must match the runtime-compiled script source name.
> - Katton preserves line numbers when processing top-level `@file:` annotations (annotation lines are blanked instead of removed), reducing breakpoint shifts.
> - When the same script is executed again, Katton replaces event handlers previously registered by that script owner, preventing duplicate registrations during reload/debug iteration.
> - If breakpoints still do not trigger, restart the target JVM process and attach again to avoid stale classes from previous runs.
