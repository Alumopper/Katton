package top.katton;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import top.katton.engine.ClientScriptLoader;
import top.katton.network.ClientNetworkingNeoForge;
import top.katton.platform.ClientApiHooks;
import top.katton.platform.NeoForgeClientApiBridge;

/**
 * Client-only event listeners for NeoForge.
 * MOD-bus events (AddClientReloadListenersEvent) and GAME-bus events (disconnect)
 * are registered separately to avoid wrong-bus errors.
 */
@EventBusSubscriber(
        modid = Katton.MOD_ID,
        value = { Dist.CLIENT }
)
public class KattonClientNeoForge {

    /** Registers client resource reload listener for client scripts (MOD bus). */
    @SubscribeEvent
    public static void onAddClientReloadListeners(AddClientReloadListenersEvent event) {
        ClientApiHooks.setBridge(NeoForgeClientApiBridge.INSTANCE);
        ClientScriptLoader.onReloadComplete = Katton::reloadClientScripts;
        event.addListener(Identifier.fromNamespaceAndPath(Katton.MOD_ID, "client_scripts"), ClientScriptLoader.INSTANCE);
        // Also wire game-bus events that are client-only (runs once per client init)
        if (!gameEventsRegistered) {
            gameEventsRegistered = true;
            NeoForge.EVENT_BUS.addListener(KattonClientNeoForge::onDisconnect);
        }
    }

    private static volatile boolean gameEventsRegistered = false;

    private static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientNetworkingNeoForge.INSTANCE.reset();
    }

}
