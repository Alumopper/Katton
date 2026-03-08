package top.katton.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.katton.api.event.EndermanAngerArg;
import top.katton.api.event.ServerEntityEvent;

@Mixin(EnderMan.class)
public class EnderManMixin {
    
    @Inject(method = "isBeingStaredBy", at = @At("HEAD"), cancellable = true)
    private void isBeingStaredBy(Player player, CallbackInfoReturnable<Boolean> cir) {
        EnderMan self = (EnderMan)(Object)this;
        if(!LivingEntity.PLAYER_NOT_WEARING_DISGUISE_ITEM.test(player)) {
            cir.setReturnValue(false);
            return;
        }
        var arg = new EndermanAngerArg(self, player);
        ServerEntityEvent.onEndermanAnger.invoke(arg);
        var result = self.isLookingAtMe(player, 0.025, true, false, self.getEyeY())
                && !ServerEntityEvent.onEndermanAnger.isCanceled();
        cir.setReturnValue(result);
    }
}
