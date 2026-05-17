package top.katton;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
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
import top.katton.api.event.managed.NeoForgeManagedEvents;
import top.katton.command.ScriptCommand;
import top.katton.network.ServerNetworking;
import top.katton.network.ServerNetworkingNeoForge;
import top.katton.engine.ScriptReloadManager;
import top.katton.pack.ScriptPackManager;
import top.katton.registry.KattonRegistry;
import top.katton.platform.DynamicRegistryHooks;
import top.katton.platform.EntityAttributeHooks;
import top.katton.platform.NeoForgeDynamicRegistryHooks;
import top.katton.platform.NeoForgeEntityAttributeHooks;
import top.katton.platform.NeoForgeSpawnPlacementHooks;
import net.neoforged.neoforge.network.PacketDistributor;

/** NeoForge mod entry point that initializes Katton and bridges NeoForge lifecycle events. */
@Mod(Katton.MOD_ID)
public class KattonNeoForge {

    /**
     * Constructs the mod instance, initializes Katton core, and registers event bridges.
     *
     * @param modEventBus the NeoForge mod event bus
     */
    public KattonNeoForge(IEventBus modEventBus) {
        DynamicRegistryHooks.setAfterDynamicBlockRegistered(NeoForgeDynamicRegistryHooks::afterDynamicBlockRegistered);

        // Mode-aware attribute registration: GLOBAL uses ModBus event, RELOADABLE uses reflection
        EntityAttributeHooks.setGlobalRegistrar(NeoForgeEntityAttributeHooks::registerAttributesGlobal);
        EntityAttributeHooks.setReloadableRegistrar(NeoForgeEntityAttributeHooks::registerAttributesReloadable);
        modEventBus.addListener(this::onEntityAttributeCreation);
        modEventBus.addListener(this::onRegisterSpawnPlacements);
        modEventBus.addListener(ServerNetworkingNeoForge.INSTANCE::onRegisterPayloadHandlers);

        ServerNetworking.setPlaySender(PacketDistributor::sendToPlayer);
        Katton.setGameDirectory(FMLPaths.GAMEDIR.get());
        Katton.mainInitialize();
        NeoForgeManagedEvents.initialize();

        registerGameEventBridges();

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
    }

    private void registerGameEventBridges() {
        NeoForge.EVENT_BUS.register(ChunkAndBlockEvent.class);
        NeoForge.EVENT_BUS.register(ItemEvent.class);
        NeoForge.EVENT_BUS.register(LivingBehaviorEvent.class);
        NeoForge.EVENT_BUS.register(PlayerEvent.class);
        NeoForge.EVENT_BUS.register(ServerEntityCombatEvent.class);
        NeoForge.EVENT_BUS.register(ServerEntityEvent.class);
        NeoForge.EVENT_BUS.register(ServerEvent.class);
        NeoForge.EVENT_BUS.register(ServerLivingEntityEvent.class);
        NeoForge.EVENT_BUS.register(ServerMessageEvent.class);
        NeoForge.EVENT_BUS.register(ServerMobEffectEvent.class);
        NeoForge.EVENT_BUS.register(ServerPlayerEvent.class);
    }

    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        NeoForgeEntityAttributeHooks.flushOnModBus(event);
    }

    private void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        NeoForgeSpawnPlacementHooks.flushOnModBus(event);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        ScriptCommand.INSTANCE.register(event.getDispatcher());
    }

    private void onServerStarted(ServerStartedEvent event) {
        Katton.server = event.getServer();
        Katton.globalState = LoadState.SERVER_STARTED;
        ScriptReloadManager.reloadScriptsAsync(event.getServer(), serverOk -> {
            if (serverOk) {
                event.getServer().execute(() -> ScriptCommand.syncCommandTree(event.getServer()));
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    private void onServerStopped(ServerStoppedEvent event) {
        Katton.server = null;
        Katton.globalState = LoadState.SERVER_STOPPED;
        KattonRegistry.INSTANCE.clearWorldRegistrations();
        Katton.clearWorldAndServerEvents();
        ScriptPackManager.INSTANCE.clearWorldDirectory();
    }
}
