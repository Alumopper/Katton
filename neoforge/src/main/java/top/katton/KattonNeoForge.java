package top.katton;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import top.katton.api.event.ChunkAndBlockEvent;
import top.katton.api.event.ItemEvent;
import top.katton.api.event.LivingBehaviorEvent;
import top.katton.api.event.PlayerEvent;
import top.katton.api.event.ServerEntityCombatEvent;
import top.katton.api.event.ServerEntityEvent;
import top.katton.api.event.ServerEvent;
import top.katton.api.event.ServerLivingEntityEvent;
import top.katton.api.event.ServerMessageEvent;
import top.katton.api.event.ServerMobEffectEvent;
import top.katton.api.event.ServerPlayerEvent;
import top.katton.command.ScriptCommand;
import top.katton.network.ServerNetworking;
import top.katton.pack.ScriptPackManager;
import top.katton.platform.DynamicRegistryHooks;
import top.katton.platform.NeoForgeDynamicRegistryHooks;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(Katton.MOD_ID)
public class KattonNeoForge {

    public KattonNeoForge(IEventBus modEventBus) {
        DynamicRegistryHooks.setAfterDynamicBlockRegistered(NeoForgeDynamicRegistryHooks::afterDynamicBlockRegistered);
        ServerNetworking.INSTANCE.setPlaySender(PacketDistributor::sendToPlayer);
        Katton.setGameDirectory(FMLPaths.GAMEDIR.get());
        Katton.mainInitialize();

        registerGameEventBridges();

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
    }

    private void registerGameEventBridges() {
        NeoForge.EVENT_BUS.register(ChunkAndBlockEvent.INSTANCE);
        NeoForge.EVENT_BUS.register(ItemEvent.INSTANCE);
        NeoForge.EVENT_BUS.register(LivingBehaviorEvent.INSTANCE);
        NeoForge.EVENT_BUS.register(PlayerEvent.INSTANCE);
        NeoForge.EVENT_BUS.register(ServerEntityCombatEvent.INSTANCE);
        NeoForge.EVENT_BUS.register(ServerEntityEvent.INSTANCE);
        NeoForge.EVENT_BUS.register(ServerEvent.INSTANCE);
        NeoForge.EVENT_BUS.register(ServerLivingEntityEvent.INSTANCE);
        NeoForge.EVENT_BUS.register(ServerMessageEvent.INSTANCE);
        NeoForge.EVENT_BUS.register(ServerMobEffectEvent.INSTANCE);
        NeoForge.EVENT_BUS.register(ServerPlayerEvent.INSTANCE);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ScriptCommand.INSTANCE.register(event.getDispatcher());
    }

    private void onServerStarted(ServerStartedEvent event) {
        Katton.server = event.getServer();
        Katton.globalState = LoadState.SERVER_STARTED;
        Katton.reloadScripts(event.getServer());
        ScriptCommand.syncCommandTree(event.getServer());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        Katton.server = null;
        Katton.globalState = LoadState.SERVER_STOPPED;
        ScriptPackManager.INSTANCE.clearWorldDirectory();
    }
}
