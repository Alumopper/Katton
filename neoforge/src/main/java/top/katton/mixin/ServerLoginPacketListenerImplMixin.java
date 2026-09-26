package top.katton.mixin;

import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.engine.ScriptReloadManager;

/**
 * Pauses Minecraft's slow-login timeout while Katton blocks a connection thread
 * waiting for the initial server script preparation.
 *
 * <p>Configuration-phase packets are handled on the connection (Netty) thread,
 * so NeoForge channel negotiation / Katton pack sync may block there for the
 * whole cold compilation. The login listener stays active on the server thread
 * during that window and would otherwise disconnect the player with
 * {@code multiplayer.disconnect.slow_login} ("Took too long to log in") before
 * the registries are finalized. Resetting {@code tick} keeps a full timeout
 * window once the wait finishes.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin {

    @Shadow
    private int tick;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void katton$pauseSlowLoginTimeout(CallbackInfo ci) {
        if (ScriptReloadManager.isAwaitingServerReload()) {
            this.tick = 0;
            ci.cancel();
        }
    }
}
