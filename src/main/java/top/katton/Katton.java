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
import top.katton.command.ScriptCommand;
import top.katton.engine.ScriptEngine;
import top.katton.engine.ScriptLoader;
import top.katton.util.Event;

public class Katton implements ModInitializer {
    private static final Logger logger = LoggerFactory.getLogger("katton");

    /**
     * Current minecraft server instance. Maybe null during client-side execution.
     */
    public static MinecraftServer server = null;

    @Override
    public void onInitialize() {
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
                Identifier.parse("katton:scripts"), ScriptLoader.INSTANCE
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ScriptCommand.INSTANCE.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(serverInstance -> {
            server = serverInstance;
            loadScript();
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> server = null);

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((_, _, success) -> {
            if (!success) {
                return;
            }
            loadScript();
        });
    }

    public void loadScript(){
        Event.Companion.getFabricEventRegistry().values().forEach(list -> list.forEach(Event::clear));
        ScriptEngine.compileAndExecuteAll(ScriptLoader.getScripts().values());
    }
}