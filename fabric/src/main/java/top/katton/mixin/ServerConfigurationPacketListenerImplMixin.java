package top.katton.mixin;

import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.engine.ScriptReloadManager;
import top.katton.network.ServerNetworking;

/**
 * Mixin to inject Katton sync packet sending before Fabric's registry sync.
 */
@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ServerConfigurationPacketListenerImplMixin {

    /**
     * Injects after player initialization to send item sync packet.
     * Waits for any in-progress server reload to complete so the client
     * receives finalized registries.
     */
    @SuppressWarnings("DataFlowIssue")
    @Inject(
        method = "<init>",
        at = @At("RETURN")
    )
    private void katton$onInit(MinecraftServer server, Connection connection, CommonListenerCookie cookie, CallbackInfo ci) {
        // Wait for server reload to finish so registries are complete before syncing.
        ScriptReloadManager.awaitServerReloadCompletion();
        var THIS = (ServerConfigurationPacketListenerImpl) (Object) this;
        ServerNetworking.sendInitialScriptPackSync(THIS, ServerConfigurationNetworking::send);
    }
}
