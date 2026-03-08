package top.katton;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import top.katton.network.ClientNetworkingFabric;
import top.katton.network.Networking;

public class KattonClientFabric implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Initialize common networking (payload type registration)
		Networking.initialize();

		// Initialize client networking for item sync
		ClientNetworkingFabric.INSTANCE.initialize();

		// Reset state when disconnecting from server
		ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> {
			ClientNetworkingFabric.INSTANCE.reset();
		});
	}
}
