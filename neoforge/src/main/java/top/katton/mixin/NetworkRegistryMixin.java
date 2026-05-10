package top.katton.mixin;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.neoforged.neoforge.network.payload.ModdedNetworkQueryComponent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.engine.ScriptReloadManager;
import top.katton.network.ServerNetworking;

import java.util.Map;
import java.util.Set;

/**
 * Mixin into NeoForge's NetworkRegistry to send Katton sync AFTER
 * NeoForge has completed channel negotiation.
 *
 * initializeNeoForgeConnection is called when the server receives
 * the client's ModdedNetworkQueryPayload and sets up channels.
 * After it returns (successfully), custom payloads can be sent.
 */
@Mixin(value = NetworkRegistry.class, remap = false)
public class NetworkRegistryMixin {

    @Inject(
        method = "initializeNeoForgeConnection",
        at = @At("RETURN"),
        remap = false
    )
    private static void katton$afterNeoForgeNegotiation(
            ServerConfigurationPacketListener listener, Map<ConnectionProtocol, Set<ModdedNetworkQueryComponent>> clientChannels, CallbackInfo ci
    ) {
        // Wait for any in-progress server reload so registries are complete.
        ScriptReloadManager.awaitServerReloadCompletion();
        var impl = (ServerConfigurationPacketListenerImpl) listener;
        ServerNetworking.sendInitialScriptPackSync(impl, ServerConfigurationPacketListenerImpl::send);
    }
}
