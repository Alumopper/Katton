package top.katton.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.katton.api.event.ChunkAndBlockEvent;
import top.katton.api.event.ExplosionDetonateArg;

import java.util.List;

@Mixin(ServerExplosion.class)
public class ServerExplosionMixin {
    @WrapOperation(
            method = "explode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private List<Entity> onGetEntities(Level level, Entity source, AABB box, Operation<List<Entity>> original){
        List<Entity> result = original.call(level, source, box);
        //noinspection DataFlowIssue
        ChunkAndBlockEvent.onExplosionDetonate.invoke(new ExplosionDetonateArg(level, (ServerExplosion) (Object) this, result));
        return result;
    }

}
