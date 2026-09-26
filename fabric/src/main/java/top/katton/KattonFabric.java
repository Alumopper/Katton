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
import top.katton.engine.ScriptEngine;
import top.katton.pack.ScriptPlatform;
import top.katton.network.ScriptPackRequestPacket;
import top.katton.network.ScriptPackSyncAckPacket;
import top.katton.network.ScriptPayloadPacket;
import top.katton.network.ScriptPlayNetworking;

import static top.katton.Katton.*;

public class KattonFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        //Entrance point for common initialization
        ScriptDependencyManager.install(ScriptPlatform.FABRIC, FabricScriptDependencyResolver.INSTANCE);
        // Fabric API injects interfaces into Minecraft classes. The compiler
        // needs every API module, even when scripts only refer to vanilla types.
        // Loom exposes the fabric-* modules separately in development, so
        // walking only the aggregate fabric-api container misses those modules.
        FabricLoader.getInstance().getAllMods().stream()
            .filter(mod -> mod.getMetadata().getId().startsWith("fabric-"))
            .forEach(mod -> {
                var apiModule = FabricScriptDependencyResolver.INSTANCE.resolve(mod.getMetadata().getId());
                if (apiModule != null) {
                    apiModule.getClasspath().forEach(path -> ScriptEngine.addHostClasspathEntry(path.toFile()));
                }
            });
        setGameDirectory(FabricLoader.getInstance().getGameDir());
        FabricManagedEvents.initialize();
        mainInitialize();
        eventInitialize();

        // Install Fabric-specific attribute registration hooks
        EntityAttributeHooks.setGlobalRegistrar(FabricEntityAttributeHooks::registerAttributes);
        EntityAttributeHooks.setReloadableRegistrar(FabricEntityAttributeHooks::registerAttributes);

        Networking.initialize();
        ServerNetworking.setPlaySender(ServerPlayNetworking::send);
        ServerPlayNetworking.registerGlobalReceiver(
            ScriptPayloadPacket.TYPE,
            (packet, context) -> context.server().execute(() -> ScriptPlayNetworking.receive(context.player(), packet))
        );

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
            // Opt-in only: an IDE launcher sets katton.dev.autoEnable so the bridge appears
            // once the world exists, without the in-game switch. The world id stays stable
            // for the whole server lifetime, so the IDE does not reconnect on world change.
            top.katton.dev.KattonDevBridge.enableIfRequested();
            ScriptReloadManager.reloadScriptsAsync(serverInstance, InvocationReason.INITIAL_LOAD, ReloadCause.SERVER_START, serverOk -> {
                if (serverOk) {
                    serverInstance.execute(() -> ScriptCommand.syncCommandTree(serverInstance));
                }
                return kotlin.Unit.INSTANCE;
            });
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(stoppedServer -> {
            if (!stoppedServer.isDedicatedServer()) {
                top.katton.dev.KattonDevBridge.disable();
            }
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
