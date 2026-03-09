package top.katton.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.api.event.ChunkAndBlockEvent;
import top.katton.api.event.ExplosionStartArg;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Inject(method = "explode", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerExplosion;explode()I"), cancellable = true)
    private void beforeExplode(CallbackInfo ci, @Local(name = "explosion") ServerExplosion explosion) {
        ChunkAndBlockEvent.onExplosionStart.invoke(new ExplosionStartArg(explosion.level(), explosion));
        if(ChunkAndBlockEvent.onExplosionStart.isCanceled()) ci.cancel();
    }
}
