package top.katton.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.engine.ScriptReloadManager;

import java.util.concurrent.TimeUnit;

/**
 * Gives an integrated client's local connection time to compile world scripts
 * on both sides of configuration sync.
 *
 * <p>Configuration-phase packets are handled on the connection (Netty) thread,
 * so {@code ServerConfigurationPacketListenerImpl} may block there for the whole
 * cold compilation. The client then compiles its own synchronized scripts,
 * which can take longer than the vanilla 30-second login limit. The server
 * reload wait is paused outright; a local connection receives at most two
 * minutes of extra grace before the vanilla timeout resumes. Remote clients
 * retain Minecraft's normal timeout.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin {

    @Shadow
    private int tick;

    @Shadow @Final
    private MinecraftServer server;

    @Shadow @Final
    private Connection connection;

    @Unique
    private long katton$localGraceStartNanos;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void katton$pauseSlowLoginTimeout(CallbackInfo ci) {
        if (ScriptReloadManager.isAwaitingServerReload()) {
            this.tick = 0;
            ci.cancel();
            return;
        }
        if (!server.isDedicatedServer() && connection.isMemoryConnection()) {
            if (katton$localGraceStartNanos == 0L) {
                katton$localGraceStartNanos = System.nanoTime();
            }
            if (System.nanoTime() - katton$localGraceStartNanos < TimeUnit.MINUTES.toNanos(2)) {
                this.tick = 0;
            }
        }
    }
}
