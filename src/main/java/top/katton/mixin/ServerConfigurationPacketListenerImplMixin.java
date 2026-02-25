package top.katton.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.network.ServerNetworking;

/**
 * Mixin to inject item sync packet sending before Fabric's registry sync.
 * This ensures clients have Katton items registered before the sync check.
 */
@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ServerConfigurationPacketListenerImplMixin {

    /**
     * Injects after player initialization to send item sync packet.
     * This happens before Fabric's registry sync validation.
     */
    @SuppressWarnings("DataFlowIssue")
    @Inject(
        method = "<init>",
        at = @At("RETURN")
    )
    private void katton$onInit(MinecraftServer server, Connection connection, CommonListenerCookie cookie, CallbackInfo ci) {
        // Send item sync packet to the connecting player
        ServerNetworking.INSTANCE.sendItemSyncPacket((ServerConfigurationPacketListenerImpl) (Object) this);
    }
}
