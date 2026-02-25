package top.katton;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import top.katton.network.ClientNetworking;
import top.katton.network.Networking;

public class KattonClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Initialize common networking (payload type registration)
		Networking.initialize();
		
		// Initialize client networking for item sync
		ClientNetworking.INSTANCE.initialize();
		
		// Reset state when disconnecting from server
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientNetworking.INSTANCE.reset();
		});
	}
}
