package top.katton;

// 平台无关的公共初始化适配器，供各 loader 子模块调用
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.katton.api.dpcaller.EntityEvent;
import top.katton.registry.ScriptCommandRegistry;
import top.katton.engine.ScriptEngine;
import top.katton.engine.ScriptLoader;
import top.katton.engine.InjectionManager;
import top.katton.registry.KattonRegistry;
import top.katton.util.Event;

public class Katton {
    public static final String MOD_ID = "katton";
    private static final Logger logger = LoggerFactory.getLogger(MOD_ID);

    /**
     * Current minecraft server instance. Maybe null during client-side execution.
     */
    public static MinecraftServer server = null;
    public static LoadState globalState = LoadState.INIT;

    // common 初始化点
    public static void mainInitialize() {
        // Initialize KattonRegistry first to register custom DataComponentTypes
        KattonRegistry.INSTANCE.initialize();
    }

    public static void reloadScripts(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ScriptCommandRegistry.INSTANCE.beginReload(server);
        KattonRegistry.ITEMS.INSTANCE.beginReload();
        KattonRegistry.EFFECTS.INSTANCE.beginReload();
        KattonRegistry.BLOCKS.INSTANCE.beginReload();
        EntityEvent.INSTANCE.beginReload();
        Event.clearHandlers();
        InjectionManager.beginReload();
        //TODO: reload scripts
        ScriptEngine.compileAndExecuteAll(ScriptLoader.getScripts().values());
        EntityEvent.INSTANCE.rebindLoadedEntities(server);
    }
}
