package top.katton;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import top.katton.api.event.*;
import top.katton.command.ScriptCommand;
import top.katton.engine.ScriptReloadManager;
import top.katton.network.Networking;
import top.katton.network.ServerNetworking;
import top.katton.pack.ScriptPackManager;
import top.katton.platform.EntityAttributeHooks;
import top.katton.platform.FabricEntityAttributeHooks;
import top.katton.network.ScriptPackRequestPacket;

import static top.katton.Katton.*;

public class KattonFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        //Entrance point for common initialization
        setGameDirectory(FabricLoader.getInstance().getGameDir());
        mainInitialize();
        eventInitialize();

        // Install Fabric-specific attribute registration hooks
        EntityAttributeHooks.setGlobalRegistrar(FabricEntityAttributeHooks::registerAttributes);
        EntityAttributeHooks.setReloadableRegistrar(FabricEntityAttributeHooks::registerAttributes);

        Networking.initialize();
        ServerNetworking.setPlaySender(ServerPlayNetworking::send);

        ServerConfigurationNetworking.registerGlobalReceiver(
            ScriptPackRequestPacket.TYPE,
            (packet, context) -> context.server().execute(() ->
                ServerNetworking.sendScriptPackBundle(
                    context.packetListener(),
                    packet.getRequestedSyncIds(),
                    ServerConfigurationNetworking::send
                )
            )
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ScriptCommand.INSTANCE.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(serverInstance -> {
            server = serverInstance;
            globalState = LoadState.SERVER_STARTED;
            ScriptReloadManager.reloadScriptsAsync(serverInstance, serverOk -> {
                if (serverOk) {
                    serverInstance.execute(() -> ScriptCommand.syncCommandTree(serverInstance));
                }
                return kotlin.Unit.INSTANCE;
            });
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> {
            server = null;
            globalState = LoadState.SERVER_STOPPED;
            clearWorldAndServerEvents();
            ScriptPackManager.INSTANCE.clearWorldDirectory();
        });

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((_, _, success) -> {
            globalState = LoadState.END_DATA_PACK_RELOAD;
            if (!success) {
                return;
            }
            ScriptReloadManager.reloadScriptsAsync(server, serverOk -> {
                if (serverOk && server != null) {
                    server.execute(() -> ScriptCommand.syncCommandTree(server));
                }
                return kotlin.Unit.INSTANCE;
            });
        });
    }

    private void eventInitialize() {
        ChunkAndBlockEvent.INSTANCE.initialize();
        ItemComponentEvent.INSTANCE.initialize();
        ItemEvent.INSTANCE.initialize();
        LivingBehaviorEvent.INSTANCE.initialize();
        LootTableEvent.INSTANCE.initialize();
        PlayerEvent.INSTANCE.initialize();
        ServerEntityCombatEvent.INSTANCE.initialize();
        ServerEntityEvent.INSTANCE.initialize();
        ServerEvent.INSTANCE.initialize();
        ServerLivingEntityEvent.INSTANCE.initialize();
        ServerMessageEvent.INSTANCE.initialize();
        ServerMobEffectEvent.INSTANCE.initialize();
        ServerPlayerEvent.INSTANCE.initialize();
    }
}
