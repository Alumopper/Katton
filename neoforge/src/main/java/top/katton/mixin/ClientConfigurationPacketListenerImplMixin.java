package top.katton.mixin;

import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.network.ClientNetworkingNeoForge;

/**
 * Mixin to intercept Fabric's registry sync handler.
 * Ensures Katton items are registered before the sync check runs.
 */
@Mixin(ClientConfigurationPacketListenerImpl.class)
public class ClientConfigurationPacketListenerImplMixin {
    
    /**
     * Injects at the start of receivePacket method.
     * Processes any pending Katton item registrations before Fabric's sync check.
     * 
     * Note: This injects into a static method, so the injector method must also be static.
     * The method descriptor must match the target method exactly.
     */
    @Inject(
        method = "handleRegistryData",
        at = @At("HEAD"),
        remap = false
    )
    private static void katton$onHandleRegistryData(ClientboundRegistryDataPacket packet, CallbackInfo ci) {
        // Process any pending item registrations before Fabric's registry sync check
        ClientNetworkingNeoForge.INSTANCE.processPendingRegistrations();
    }
}
