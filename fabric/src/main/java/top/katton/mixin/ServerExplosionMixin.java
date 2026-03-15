package top.katton.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
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
    @SuppressWarnings({"rawtypes", "unchecked"})
    @WrapOperation(
            method = "hurtEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
            )
    )
    private List<Entity> onGetEntities(ServerLevel instance, Entity entity, AABB aabb, Operation<List> original){
        List<Entity> result = original.call(instance, entity, aabb);
        //noinspection DataFlowIssue
        ChunkAndBlockEvent.onExplosionDetonate.invoke(new ExplosionDetonateArg(instance, (ServerExplosion) (Object) this, result));
        return result;
    }

}
