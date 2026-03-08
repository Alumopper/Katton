package top.katton.mixin;

import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.api.event.ChunkAndBlockEvent;
import top.katton.api.event.ExplosionStartArg;

@Mixin(Explosion.class)
public class ExplosionMixin {
    
    @Shadow @Final private Level level;
    
    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void katton$onExplosionStart(CallbackInfo ci) {
        ChunkAndBlockEvent.onExplosionStart.invoke(new ExplosionStartArg(level, (Explosion) this));
        if (ChunkAndBlockEvent.onExplosionStart.isCanceled()) {
            ci.cancel();
        }
    }
}
