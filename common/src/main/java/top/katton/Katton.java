package top.katton;

import net.minecraft.server.MinecraftServer;
import top.katton.pack.ScriptPackManager;
import top.katton.pack.ScriptPackScope;
import top.katton.engine.ScriptEngine;
import top.katton.engine.ScriptReloadManager;
import top.katton.registry.KattonRegistry;
import top.katton.util.Event;

import java.nio.file.Files;
import java.nio.file.Path;

public class Katton {
    public static final String MOD_ID = "katton";

    /**
     * When true, logs every registration call (items, blocks, entities, etc.)
     * to the game log. Useful for debugging script pack issues.
     * Toggle via script: {@code Katton.debugRegistryLogging = true}
     */
    public static boolean debugRegistryLogging = false;

    /**
     * Current minecraft server instance. Maybe null during client-side execution.
     */
    public static MinecraftServer server = null;
    public static LoadState globalState = LoadState.INIT;
    public static Path gameDirectory = null;

    /**
     * Platform-agnostic initialization.
     * Called once during mod initialization by both Fabric and NeoForge entrypoints.
     */
    public static void mainInitialize() {
        KattonRegistry.INSTANCE.initialize();
        ScriptPackManager.INSTANCE.setGameDirectory(gameDirectory);
        ScriptEngine.setCacheDirectory(gameDirectory == null ? null : gameDirectory.resolve(".katton").resolve("compiled-script-cache"));
        ScriptPackManager.INSTANCE.refreshGlobalPacks();
        ScriptReloadManager.initializeGlobalPacks();
        Path globalDir = ScriptPackManager.INSTANCE.getGlobalScriptDirectory();
        if (globalDir != null) {
            try {
                Files.createDirectories(globalDir);
            } catch (Exception ignored) {
            }
        }
    }

    public static void setGameDirectory(Path gameDir) {
        gameDirectory = gameDir;
        ScriptPackManager.INSTANCE.setGameDirectory(gameDir);
        ScriptEngine.setCacheDirectory(gameDir == null ? null : gameDir.resolve(".katton").resolve("compiled-script-cache"));
    }

    /**
     * Clears world-scoped and server-cache-scoped event handlers.
     * Called on world disconnect or server stop.
     * Global-scoped handlers persist.
     */
    public static void clearWorldAndServerEvents() {
        Event.clearHandlersByScope(ScriptPackScope.WORLD);
        Event.clearHandlersByScope(ScriptPackScope.SERVER_CACHE);
    }
}
