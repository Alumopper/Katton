package top.katton;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import top.katton.engine.ClientScriptLoader;
import top.katton.network.ClientNetworkingFabric;
import top.katton.network.Networking;
import top.katton.platform.ClientApiHooks;
import top.katton.platform.FabricClientApiBridge;

public class KattonClientFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientApiHooks.setBridge(FabricClientApiBridge.INSTANCE);

		// Initialize common networking (payload type registration)
		Networking.initialize();

		// Initialize client networking for item sync
		ClientNetworkingFabric.INSTANCE.initialize();

		// Register client resource pack reload listener for client scripts
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				Identifier.fromNamespaceAndPath(Katton.MOD_ID, "client_scripts"),
				ClientScriptLoader.INSTANCE
		);
		// Trigger script compilation after resource pack reload applies client scripts
		ClientScriptLoader.onReloadComplete = Katton::reloadClientScripts;

		// Reset state when disconnecting from server
		ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> {
			ClientNetworkingFabric.INSTANCE.reset();
		});
	}
}
