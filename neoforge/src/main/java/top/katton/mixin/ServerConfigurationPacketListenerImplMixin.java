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
 * Mixin to inject Katton sync packet sending during configuration.
 * Sends script pack hash snapshot for client-side cache negotiation.
 */
@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ServerConfigurationPacketListenerImplMixin {

    @SuppressWarnings("DataFlowIssue")
    @Inject(
        method = "<init>",
        at = @At("RETURN")
    )
    private void katton$onInit(MinecraftServer server, Connection connection, CommonListenerCookie cookie, CallbackInfo ci) {
        var THIS = (ServerConfigurationPacketListenerImpl) (Object) this;
        // Send configuration-time script sync before registry validation begins.
        ServerNetworking.sendInitialScriptPackSync(THIS, ServerConfigurationPacketListenerImpl::send);
    }
}
