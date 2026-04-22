package top.katton;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import top.katton.api.event.*;
import top.katton.command.ScriptCommand;
import top.katton.network.Networking;
import top.katton.network.ServerNetworking;
import top.katton.pack.ScriptPackManager;

import static top.katton.Katton.*;

public class KattonFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        //Entrance point for common initialization
        setGameDirectory(FabricLoader.getInstance().getGameDir());
        mainInitialize();
        eventInitialize();

        Networking.initialize();
        ServerNetworking.INSTANCE.setPlaySender(ServerPlayNetworking::send);

        ServerConfigurationNetworking.registerGlobalReceiver(
            top.katton.network.ScriptPackRequestPacket.TYPE,
            (packet, context) -> context.server().execute(() ->
                ServerNetworking.INSTANCE.sendScriptPackBundle(
                    context.packetListener(),
                    packet.getRequestedSyncIds(),
                    ServerConfigurationNetworking::send
                )
            )
        );

        ServerLifecycleEvents.SERVER_STARTED.register(serverInstance -> {
            server = serverInstance;
            globalState = LoadState.SERVER_STARTED;
            reloadScripts(serverInstance);
            ScriptCommand.syncCommandTree(serverInstance);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> {
            server = null;
            globalState = LoadState.SERVER_STOPPED;
            ScriptPackManager.INSTANCE.clearWorldDirectory();
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
