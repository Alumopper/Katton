package top.katton.mixin;

import net.fabricmc.fabric.impl.client.registry.sync.ClientRegistrySyncHandler;
import net.fabricmc.fabric.impl.registry.sync.packet.RegistrySyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.network.ClientNetworking;

/**
 * Mixin to intercept Fabric's registry sync handler.
 * Ensures Katton items are registered before the sync check runs.
 */
@Mixin(ClientRegistrySyncHandler.class)
public class ClientRegistrySyncHandlerMixin {
    
    /**
     * Injects at the start of receivePacket method.
     * Processes any pending Katton item registrations before Fabric's sync check.
     * 
     * Note: This injects into a static method, so the injector method must also be static.
     * The method descriptor must match the target method exactly.
     */
    @Inject(
        method = "receivePacket",
        at = @At("HEAD"),
        remap = false
    )
    private static void katton$onReceivePacketHead(RegistrySyncPayload payload, ClientConfigurationNetworking.Context context, CallbackInfo ci) {
        // Process any pending item registrations before Fabric's registry sync check
        ClientNetworking.INSTANCE.processPendingRegistrations();
    }
}
