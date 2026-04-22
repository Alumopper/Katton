package top.katton;

import net.minecraft.resources.Identifier;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import top.katton.client.ScriptPackUi;
import top.katton.network.ClientNetworkingNeoForge;
import top.katton.pack.ServerPackCacheManager;

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

    private static final KeyMapping.Category KATTON_KEY_CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(Katton.MOD_ID, "keybinds")
    );

    private static final KeyMapping OPEN_PACK_SCREEN = new KeyMapping(
            "key.katton.open_pack_screen",
            GLFW.GLFW_KEY_K,
        KATTON_KEY_CATEGORY
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PACK_SCREEN);
        Katton.reloadClientScripts();
        ensureGameEventsRegistered();
    }

    private static volatile boolean gameEventsRegistered = false;

    private static void ensureGameEventsRegistered() {
        if (gameEventsRegistered) {
            return;
        }
        gameEventsRegistered = true;
        NeoForge.EVENT_BUS.addListener(KattonClientNeoForge::onDisconnect);
        NeoForge.EVENT_BUS.addListener(KattonClientNeoForge::onLogin);
        NeoForge.EVENT_BUS.addListener(KattonClientNeoForge::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_PACK_SCREEN.consumeClick()) {
            ScriptPackUi.openInWorldScreen();
        }
    }

    private static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Katton.reloadClientScripts();
    }

    private static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientNetworkingNeoForge.INSTANCE.reset();
        ServerPackCacheManager.INSTANCE.reset();
    }

}
