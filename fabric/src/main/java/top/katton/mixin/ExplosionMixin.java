package top.katton.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.katton.api.event.ChunkAndBlockEvent;
import top.katton.api.event.ExplosionStartArg;

@Mixin(ServerExplosion.class)
public class ExplosionMixin {
    
    @Shadow @Final private ServerLevel level;
    
    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void katton$onExplosionStart(CallbackInfoReturnable<Integer> cir) {
        ChunkAndBlockEvent.onExplosionStart.invoke(new ExplosionStartArg(level, (Explosion) this));
        if (ChunkAndBlockEvent.onExplosionStart.isCanceled()) {
            cir.cancel();
        }
    }
}
