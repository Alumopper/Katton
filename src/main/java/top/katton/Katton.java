package top.katton;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.katton.api.EntityEvent;
import top.katton.command.ScriptCommand;
import top.katton.registry.ScriptCommandRegistry;
import top.katton.engine.ScriptEngine;
import top.katton.engine.ScriptLoader;
import top.katton.registry.KattonRegistry;
import top.katton.util.Event;

public class Katton implements ModInitializer {
    public static final String MOD_ID = "katton";
    private static final Logger logger = LoggerFactory.getLogger(MOD_ID);

    /**
     * Current minecraft server instance. Maybe null during client-side execution.
     */
    public static MinecraftServer server = null;
    public static LoadState globalState = LoadState.INIT;

    @Override
    public void onInitialize() {
        KattonRegistry.ITEMS.INSTANCE.initialize();

        EntityEvent.INSTANCE.initialize();

        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
                Identifier.fromNamespaceAndPath(MOD_ID, "scripts"),
                ScriptLoader.INSTANCE
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ScriptCommand.INSTANCE.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(serverInstance -> {
            server = serverInstance;
            globalState = LoadState.SERVER_STARTED;
            reloadScripts(serverInstance);
            ScriptCommand.syncCommandTree(serverInstance);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> {
            server = null;
            globalState = LoadState.SERVER_STOPPED;
        });

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((_, _, success) -> {
            globalState = LoadState.END_DATA_PACK_RELOAD;
            if (!success) {
                return;
            }
            reloadScripts(server);
            ScriptCommand.syncCommandTree(server);
        });
    }

    public static void reloadScripts(MinecraftServer server){
        if (server == null) {
            return;
        }
        ScriptCommandRegistry.INSTANCE.beginReload(server);
        Event.Companion.getFabricEventRegistry().values().forEach(list -> list.forEach(Event::clear));
        ScriptEngine.compileAndExecuteAll(ScriptLoader.getScripts().values());
    }
}