package top.katton.mixin;

import net.bytebuddy.jar.asm.Opcodes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.api.event.*;

@Mixin(Player.class)
public class PlayerMixin {
    @Inject(method = "stopSleepInBed", at = @At("HEAD"))
    private void onStopSleepInBed(boolean forcefulWakeUp, boolean updateLevelList) {
        LivingBehaviorEvent.onPlayerWakeUp.invoke(new PlayerWakeUpArg((Player) (Object) this, forcefulWakeUp, updateLevelList));
    }

    @Inject(method = "giveExperiencePoints", at = @At("HEAD"), cancellable = true)
    private void onGiveExperiencePoints(int points, CallbackInfo ci) {
        ServerPlayerEvent.onPlayerXpChange.invoke(new PlayerXpChangeArg((Player) (Object) this, points));
        if(ServerPlayerEvent.onPlayerPickupXp.isCanceled()){
            ci.cancel();
        }
    }

    @Inject(method = "giveExperienceLevels", at = @At("HEAD"), cancellable = true)
    private void onGiveExperienceLevels(int points, CallbackInfo ci) {
        ServerPlayerEvent.onPlayerXpLevelChange.invoke(new PlayerXpLevelChangeArg((Player) (Object) this, points));
        if(ServerPlayerEvent.onPlayerXpLevelChange.isCanceled()){
            ci.cancel();
        }
    }

    @Inject(
            method = "playerTouch",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Player;takeXpDelay:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private void onPlayerTouch(Player player, CallbackInfo ci) {
        ServerPlayerEvent.onPlayerPickupXp.invoke(new PlayerPickupXpArg(player, (ExperienceOrb) (Object) this));
        if(ServerPlayerEvent.onPlayerPickupXp.isCanceled()){
            ci.cancel();
        }
    }
}
