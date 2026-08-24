package top.katton;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import top.katton.api.event.*;
import top.katton.api.InvocationReason;
import top.katton.api.ReloadCause;
import top.katton.api.event.managed.FabricManagedEvents;
import top.katton.command.ScriptCommand;
import top.katton.engine.ScriptReloadManager;
import top.katton.engine.InternalDatapackReloads;
import top.katton.network.Networking;
import top.katton.network.ServerNetworking;
import top.katton.pack.ScriptPackManager;
import top.katton.registry.KattonRegistry;
import top.katton.platform.EntityAttributeHooks;
import top.katton.platform.FabricEntityAttributeHooks;
import top.katton.platform.FabricScriptDependencyResolver;
import top.katton.engine.ScriptDependencyManager;
import top.katton.pack.ScriptPlatform;
import top.katton.network.ScriptPackRequestPacket;
import top.katton.network.ScriptPackSyncAckPacket;

import static top.katton.Katton.*;

public class KattonFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        //Entrance point for common initialization
        ScriptDependencyManager.install(ScriptPlatform.FABRIC, FabricScriptDependencyResolver.INSTANCE);
        setGameDirectory(FabricLoader.getInstance().getGameDir());
        FabricManagedEvents.initialize();
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
        ServerPlayNetworking.registerGlobalReceiver(
            ScriptPackRequestPacket.TYPE,
            (packet, context) -> context.server().execute(() ->
                ServerNetworking.handlePlayRequest(context.player(), packet)
            )
        );
        ServerPlayNetworking.registerGlobalReceiver(
            ScriptPackSyncAckPacket.TYPE,
            (packet, context) -> context.server().execute(() ->
                ServerNetworking.handleSyncAck(context.player(), packet)
            )
        );
        ServerTickEvents.END_SERVER_TICK.register(ServerNetworking::pollSyncTimeouts);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ScriptCommand.INSTANCE.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(serverInstance -> {
            server = serverInstance;
            globalState = LoadState.SERVER_STARTED;
            ScriptReloadManager.reloadScriptsAsync(serverInstance, InvocationReason.INITIAL_LOAD, ReloadCause.SERVER_START, serverOk -> {
                if (serverOk) {
                    serverInstance.execute(() -> ScriptCommand.syncCommandTree(serverInstance));
                }
                return kotlin.Unit.INSTANCE;
            });
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> {
            ScriptReloadManager.resetServerLifecycle(server);
            server = null;
            globalState = LoadState.SERVER_STOPPED;
            KattonRegistry.INSTANCE.clearWorldRegistrations();
            clearWorldAndServerEvents();
            ScriptPackManager.INSTANCE.clearWorldDirectory();
        });

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((_, _, success) -> {
            globalState = LoadState.END_DATA_PACK_RELOAD;
            boolean internalReload = server != null && InternalDatapackReloads.consume(server);
            if (!success) {
                return;
            }
            if (internalReload) {
                return;
            }
            ScriptReloadManager.reloadScriptsAsync(server, InvocationReason.HOT_RELOAD, ReloadCause.DATAPACK_RELOAD, serverOk -> {
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
