package top.katton;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import top.katton.client.ClientItemRenderMarkerManager;
import top.katton.client.ClientPostEffectManager;
import top.katton.client.ScriptPackUi;
import top.katton.network.ClientNetworkingFabric;
import top.katton.network.Networking;
import top.katton.engine.ScriptReloadManager;
import top.katton.pack.ServerPackCacheManager;

public class KattonClientFabric implements ClientModInitializer {
	private static final KeyMapping.Category KATTON_KEY_CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(Katton.MOD_ID, "keybinds")
	);

	private static final KeyMapping OPEN_PACK_SCREEN = KeyMappingHelper.registerKeyMapping(
			new KeyMapping(
					"key.katton.open_pack_screen",
					InputConstants.Type.KEYSYM,
					GLFW.GLFW_KEY_K,
					KATTON_KEY_CATEGORY
			)
	);

	/** Tracks whether we've handled the first join since last disconnect (avoids respawn re-trigger). */
	private static boolean hasJoinedSinceDisconnect = false;

	@Override
	public void onInitializeClient() {

		// Initialize common networking (payload type registration)
		Networking.initialize();
		ScriptPackUi.installErrorReporter();

		// Initialize client networking for item sync
		ClientNetworkingFabric.INSTANCE.initialize();

		// Trigger client scripts when first joining a singleplayer world.
		// Guarded to avoid re-triggering on respawn transitions.
		ClientPlayConnectionEvents.JOIN.register((_, _, client) -> {
			if (!hasJoinedSinceDisconnect) {
				hasJoinedSinceDisconnect = true;
				if (client.isSingleplayer()) {
					ScriptReloadManager.reloadClientScriptsAsync();
				}
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> {
			hasJoinedSinceDisconnect = false;
			Katton.clearWorldAndServerEvents();
			ClientItemRenderMarkerManager.clear();
			ClientPostEffectManager.INSTANCE.clearAll();
			ServerPackCacheManager.INSTANCE.reset();
		});

		ClientTickEvents.END_CLIENT_TICK.register(_ -> {
			ClientItemRenderMarkerManager.tick();
			while (OPEN_PACK_SCREEN.consumeClick()) {
				ScriptPackUi.openInWorldScreen();
			}
		});
	}
}
