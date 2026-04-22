package top.katton;

// 平台无关的公共初始化适配器，供各 loader 子模块调用
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import top.katton.api.KattonClientRenderApiKt;
import top.katton.api.dpcaller.EntityEvent;
import top.katton.datapack.ServerDatapackManager;
import top.katton.pack.ScriptPackManager;
import top.katton.pack.ServerPackCacheManager;
import top.katton.registry.ScriptCommandRegistry;
import top.katton.engine.ScriptEngine;
import top.katton.engine.ScriptEnvironment;
import top.katton.engine.InjectionManager;
import top.katton.registry.KattonRegistry;
import top.katton.util.Event;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public class Katton {
    public static final String MOD_ID = "katton";

    /**
     * Current minecraft server instance. Maybe null during client-side execution.
     */
    public static MinecraftServer server = null;
    public static LoadState globalState = LoadState.INIT;
    public static Path gameDirectory = null;

    // common 初始化点
    public static void mainInitialize() {
        // Initialize KattonRegistry first to register custom DataComponentTypes
        KattonRegistry.INSTANCE.initialize();
        ScriptPackManager.INSTANCE.setGameDirectory(gameDirectory);
        ScriptPackManager.INSTANCE.refreshGlobalPacks();
        ensureDirectory(ScriptPackManager.INSTANCE.getGlobalScriptDirectory());
    }

    public static void setGameDirectory(Path gameDir) {
        gameDirectory = gameDir;
        ScriptPackManager.INSTANCE.setGameDirectory(gameDir);
    }

    /**
     * Reload client-side scripts from local script packs and synchronized server packs.
     */
    public static boolean reloadClientScripts() {
        Event.clearHandlers();
        InjectionManager.beginReload();
        KattonClientRenderApiKt.clearClientRenderers();
        ScriptPackManager.INSTANCE.setGameDirectory(gameDirectory);
        if (server != null) {
            ScriptPackManager.INSTANCE.setWorldDirectory(server.getWorldPath(LevelResource.ROOT));
            ensureDirectory(ScriptPackManager.INSTANCE.getWorldScriptDirectory());
        } else {
            ScriptPackManager.INSTANCE.clearWorldDirectory();
        }
        ScriptPackManager.INSTANCE.refreshGlobalPacks();
        ScriptPackManager.INSTANCE.refreshWorldPacks();
        Set<String> mergedScripts = new LinkedHashSet<>(ScriptPackManager.INSTANCE.collectScripts());
        mergedScripts.addAll(ServerPackCacheManager.INSTANCE.collectScripts());
        ScriptEngine.compileAndExecuteAll(mergedScripts, ScriptEnvironment.CLIENT);
        return true;
    }

    public static boolean reloadScripts(MinecraftServer server) {
        if (server == null) {
            return false;
        }

        ScriptPackManager.INSTANCE.setGameDirectory(gameDirectory);
        ScriptPackManager.INSTANCE.setWorldDirectory(server.getWorldPath(LevelResource.ROOT));
        ensureDirectory(ScriptPackManager.INSTANCE.getWorldScriptDirectory());
        ScriptPackManager.INSTANCE.refreshLocalPacks();

        ScriptCommandRegistry.INSTANCE.beginReload(server);
        KattonRegistry.ITEMS.INSTANCE.beginReload();
        KattonRegistry.EFFECTS.INSTANCE.beginReload();
        KattonRegistry.BLOCKS.INSTANCE.beginReload();
        ServerDatapackManager.INSTANCE.beginReload();
        EntityEvent.INSTANCE.beginReload();
        Event.clearHandlers();
        InjectionManager.beginReload();
        Set<String> mergedScripts = new LinkedHashSet<>(ScriptPackManager.INSTANCE.collectScripts());
        ScriptEngine.compileAndExecuteAll(mergedScripts, ScriptEnvironment.SERVER);
        ServerDatapackManager.INSTANCE.apply(server);
        EntityEvent.INSTANCE.rebindLoadedEntities(server);
        return true;
    }

    private static void ensureDirectory(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path);
        } catch (Exception ignored) {
        }
    }
}
