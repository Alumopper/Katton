package top.katton;

// 平台无关的公共初始化适配器，供各 loader 子模块调用
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.entity.AnimationState;
import top.katton.api.KattonClientRenderApiKt;
import top.katton.api.dpcaller.EntityEvent;
import top.katton.client.ReloadProgressState;
import top.katton.datapack.ServerDatapackManager;
import top.katton.pack.ScriptPack;
import top.katton.pack.ScriptPackManager;
import top.katton.pack.ScriptPackScope;
import top.katton.pack.ServerPackCacheManager;
import top.katton.registry.ScriptCommandRegistry;
import top.katton.engine.ScriptEngine;
import top.katton.engine.ScriptEnvironment;
import top.katton.engine.InjectionManager;
import top.katton.registry.KattonRegistry;
import top.katton.util.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class Katton {
    public static final String MOD_ID = "katton";
    private static final Logger LOGGER = LoggerFactory.getLogger(Katton.class);

    /**
     * Cross-ClassLoader bridge for entity animation states.
     * Server scripts write here, client scripts read here — both share the
     * same maps because Katton is loaded by the mod ClassLoader, not the
     * script CompiledScriptClassLoader.
     */
    public static final ConcurrentHashMap<Integer, AnimationState>
            entityIdleAnimationStates = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<Integer, AnimationState>
            entityWalkAnimationStates = new ConcurrentHashMap<>();
    private static final ExecutorService CLIENT_RELOAD_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Katton-ClientReload");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean CLIENT_RELOAD_RUNNING = new AtomicBoolean(false);

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

    // common 初始化点
    public static void mainInitialize() {
        // Initialize KattonRegistry first to register custom DataComponentTypes
        KattonRegistry.INSTANCE.initialize();
        ScriptPackManager.INSTANCE.setGameDirectory(gameDirectory);
        ScriptEngine.setCacheDirectory(gameDirectory == null ? null : gameDirectory.resolve(".katton").resolve("compiled-script-cache"));
        ScriptPackManager.INSTANCE.refreshGlobalPacks();
        ensureDirectory(ScriptPackManager.INSTANCE.getGlobalScriptDirectory());
    }

    public static void setGameDirectory(Path gameDir) {
        gameDirectory = gameDir;
        ScriptPackManager.INSTANCE.setGameDirectory(gameDir);
        ScriptEngine.setCacheDirectory(gameDir == null ? null : gameDir.resolve(".katton").resolve("compiled-script-cache"));
    }

    /**
     * Reload client-side scripts from local script packs and synchronized server packs.
     */
    public static boolean reloadClientScripts() {
        ReloadProgressState.begin("Reloading client scripts", 0.02f);
        boolean preserveIntegratedServerState = server != null && !server.isDedicatedServer();
        if (!preserveIntegratedServerState) {
            Event.clearHandlers();
            ReloadProgressState.update("Clearing handlers", 0.08f);
            InjectionManager.beginReload();
            ReloadProgressState.update("Resetting injections", 0.14f);
        } else {
            ReloadProgressState.update("Preserving server handlers", 0.14f);
        }
        KattonClientRenderApiKt.clearClientRenderers();
        KattonRegistry.ENTITY_RENDERERS.INSTANCE.beginReload();
        ReloadProgressState.update("Refreshing packs", 0.22f);
        ScriptPackManager.INSTANCE.setGameDirectory(gameDirectory);
        if (server != null) {
            ScriptPackManager.INSTANCE.setWorldDirectory(server.getWorldPath(LevelResource.ROOT));
            ensureDirectory(ScriptPackManager.INSTANCE.getWorldScriptDirectory());
        } else {
            ScriptPackManager.INSTANCE.clearWorldDirectory();
        }
        ScriptPackManager.INSTANCE.refreshGlobalPacks();
        ScriptPackManager.INSTANCE.refreshWorldPacks();
        ReloadProgressState.update("Compiling client scripts", 0.42f);
        List<ScriptPack> mergedPacks = new ArrayList<>(ScriptPackManager.INSTANCE.collectExecutablePacks());
        mergedPacks.addAll(ServerPackCacheManager.INSTANCE.collectExecutablePacks());
        ScriptEngine.compileAndExecuteAll(mergedPacks, ScriptEnvironment.CLIENT);
        ReloadProgressState.finish("Client scripts reloaded");
        return true;
    }

    public static boolean reloadClientScriptsAsync() {
        if (!CLIENT_RELOAD_RUNNING.compareAndSet(false, true)) {
            return true;
        }
        CLIENT_RELOAD_EXECUTOR.execute(() -> {
            try {
                reloadClientScripts();
            } catch (Throwable t) {
                LOGGER.error("Failed to reload client scripts asynchronously", t);
                ReloadProgressState.finish("Client script reload failed");
            } finally {
                CLIENT_RELOAD_RUNNING.set(false);
            }
        });
        return true;
    }

    public static boolean isClientReloadRunning() {
        return CLIENT_RELOAD_RUNNING.get();
    }

    public static boolean reloadScripts(MinecraftServer server) {
        if (server == null) {
            return false;
        }

        ReloadProgressState.begin("Reloading server scripts", 0.02f);

        ScriptPackManager.INSTANCE.setGameDirectory(gameDirectory);
        ScriptPackManager.INSTANCE.setWorldDirectory(server.getWorldPath(LevelResource.ROOT));
        ensureDirectory(ScriptPackManager.INSTANCE.getWorldScriptDirectory());
        ScriptPackManager.INSTANCE.refreshLocalPacks();
        ReloadProgressState.update("Preparing registries", 0.12f);

        ScriptCommandRegistry.INSTANCE.beginReload(server);
        KattonRegistry.ITEMS.INSTANCE.beginReload();
        KattonRegistry.EFFECTS.INSTANCE.beginReload();
        KattonRegistry.BLOCKS.INSTANCE.beginReload();
        KattonRegistry.ENTITY_TYPES.INSTANCE.beginReload();
        KattonRegistry.SOUND_EVENTS.INSTANCE.beginReload();
        KattonRegistry.PARTICLE_TYPES.INSTANCE.beginReload();
        KattonRegistry.BLOCK_ENTITY_TYPES.INSTANCE.beginReload();
        KattonRegistry.CREATIVE_TABS.INSTANCE.beginReload();
        KattonRegistry.DATA_COMPONENT_TYPES.INSTANCE.beginReload();
        KattonRegistry.ENTITY_RENDERERS.INSTANCE.beginReload();
        ServerDatapackManager.INSTANCE.beginReload();
        EntityEvent.INSTANCE.beginReload();
        Event.clearHandlers();
        InjectionManager.beginReload();
        ReloadProgressState.update("Compiling server scripts", 0.48f);
        ScriptEngine.compileAndExecuteAll(ScriptPackManager.INSTANCE.collectExecutablePacks(), ScriptEnvironment.SERVER);
        ReloadProgressState.update("Applying datapacks", 0.82f);
        ServerDatapackManager.INSTANCE.apply(server);
        EntityEvent.INSTANCE.rebindLoadedEntities(server);
        ReloadProgressState.finish("Server scripts reloaded");
        return true;
    }

    public static void clearWorldAndServerEvents() {
        Event.clearHandlersByScope(ScriptPackScope.WORLD);
        Event.clearHandlersByScope(ScriptPackScope.SERVER_CACHE);
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
