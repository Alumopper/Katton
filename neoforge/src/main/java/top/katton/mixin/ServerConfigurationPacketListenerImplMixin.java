package top.katton.mixin;

import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Marker mixin for NeoForge. Actual script pack sync is sent by
 * {@link NetworkRegistryMixin} after NeoForge channel negotiation.
 */
@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ServerConfigurationPacketListenerImplMixin {
}
