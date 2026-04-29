package top.katton;

import net.minecraft.resources.Identifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import top.katton.client.ScriptPackUi;
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

    /** Ensures client-only game bus listeners are wired once during client init. */
    @SubscribeEvent
    public static void onAddClientReloadListeners(AddClientReloadListenersEvent event) {
        // Initialize entity renderer hooks for hot-reloadable renderer registration
        try {
            Class.forName("top.katton.platform.NeoForgeEntityRendererHooks");
        } catch (ClassNotFoundException ignored) {
        }

        // Also wire game-bus events that are client-only (runs once per client init)
        if (!gameEventsRegistered) {
            gameEventsRegistered = true;
            NeoForge.EVENT_BUS.addListener(KattonClientNeoForge::onLoggingIn);
            NeoForge.EVENT_BUS.addListener(KattonClientNeoForge::onDisconnect);
            NeoForge.EVENT_BUS.addListener(KattonClientNeoForge::onClientTick);
        }
    }

    /** Registers the Katton pack screen keybinding. */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PACK_SCREEN);
    }

    private static volatile boolean gameEventsRegistered = false;

    private static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_PACK_SCREEN.consumeClick()) {
            ScriptPackUi.openInWorldScreen();
        }
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (Minecraft.getInstance().isSingleplayer()) {
            Katton.reloadClientScriptsAsync();
        }
    }

    private static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        Katton.clearWorldAndServerEvents();
        ServerPackCacheManager.INSTANCE.reset();
    }

}
