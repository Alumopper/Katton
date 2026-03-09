package top.katton.mixin;

import net.bytebuddy.jar.asm.Opcodes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.api.event.PlayerPickupXpArg;
import top.katton.api.event.ServerPlayerEvent;

@Mixin(ExperienceOrb.class)
public class ExperienceOrbMixin {

    @SuppressWarnings("DiscouragedShift")
    @Inject(
            method = "playerTouch",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Player;takeXpDelay:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            ),
            cancellable = true)
    private void onPlayerTouch(Player player, CallbackInfo ci) {
        ServerPlayerEvent.onPlayerPickupXp.invoke(new PlayerPickupXpArg(player, (ExperienceOrb) (Object) this));
        if(ServerPlayerEvent.onPlayerPickupXp.isCanceled()){
            ci.cancel();
        }
    }
}
