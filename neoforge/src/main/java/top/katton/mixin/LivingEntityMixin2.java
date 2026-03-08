package top.katton.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockStateExtension;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.katton.api.event.*;
import top.katton.bridger.EventResult;
import top.katton.util.JResult;

import java.util.Optional;

@SuppressWarnings({"DataFlowIssue", "ModifyVariableMayBeArgsOnly", "resource", "LocalMayBeArgsOnly"})
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin2 {
    @Shadow
    public abstract boolean isDeadOrDying();

    @Shadow
    public abstract Optional<BlockPos> getSleepingPos();

    @Shadow
    protected float lastHurt;

    @WrapOperation(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;killedEntity(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)Z"))
    private boolean onEntityKilledOther(Entity entity, ServerLevel serverLevel, @Nullable LivingEntity attacker, DamageSource damageSource, Operation<Boolean> original) {
        boolean result = original.call(entity, serverLevel, attacker, damageSource);
        ServerEntityCombatEvent.onAfterKilledOtherEntity.invoke(new AfterKilledOtherEntityArg(serverLevel, entity, attacker, damageSource));
        return result;
    }

    @Inject(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;broadcastEntityEvent(Lnet/minecraft/world/entity/Entity;B)V"))
    private void notifyDeath(DamageSource source, CallbackInfo ci) {
        ServerLivingEntityEvent.onAfterDeath.invoke(new ServerLivingAfterDeathArg((LivingEntity) (Object) this, source));
    }

    @Redirect(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isDeadOrDying()Z", ordinal = 1))
    boolean beforeEntityKilled(LivingEntity livingEntity, ServerLevel level, DamageSource source, float amount) {
        return isDeadOrDying() && ServerLivingEntityEvent.onAllowDeath.invoke(new ServerLivingAllowDeathArg(livingEntity, source, amount)).emptyOrTrue();
    }

    @Inject(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z"), cancellable = true)
    private void beforeDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!ServerLivingEntityEvent.onAllowDamage.invoke(new ServerLivingAllowDamageArg((LivingEntity) (Object) this, source, amount)).notEmptyAndTrue()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hurtServer", at = @At("TAIL"))
    private void afterDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir, @Local(name = "blocked") boolean blocked) {
        if (!isDeadOrDying()) {
            var originalDamage = 0f;
            if(cir.getReturnValue()) {
                 originalDamage = this.lastHurt;
            }
            ServerLivingEntityEvent.onAfterDamage.invoke(new ServerLivingAfterDamageArg((LivingEntity) (Object) this, source, originalDamage, amount, blocked));
        }
    }

    @Inject(method = "startSleeping", at = @At("RETURN"))
    private void onSleep(BlockPos pos, CallbackInfo ci) {
        LivingBehaviorEvent.onStartSleeping.invoke(new SleepingArg((LivingEntity) (Object) this, pos));
    }

    @Inject(method = "stopSleeping", at = @At("HEAD"))
    private void onWakeUp(CallbackInfo ci) {
        BlockPos sleepingPos = getSleepingPos().orElse(null);

        // If actually asleep - this method is often called with data loading, syncing etc. "just to be sure"
        if (sleepingPos != null) {
            LivingBehaviorEvent.onStopSleeping.invoke(new SleepingArg((LivingEntity) (Object) this, sleepingPos));
        }
    }

    @Inject(method = "checkBedExists", at = @At("RETURN"), cancellable = true)
    private void onIsSleepingInBed(CallbackInfoReturnable<Boolean> info) {
        BlockPos sleepingPos = getSleepingPos().orElse(null);
        if (sleepingPos == null) {
            return;
        }

        BlockState bedState = ((LivingEntity) (Object) this).level().getBlockState(sleepingPos);
        JResult<EventResult> result = LivingBehaviorEvent.onAllowBed.invoke(new AllowBedArg((LivingEntity) (Object) this, sleepingPos, bedState, info.getReturnValueZ()));

        if (result.notEmptyAndNotEquals(EventResult.PASS)) {
            info.setReturnValue(result.getOrNull().allowAction());
        }
    }

    @ModifyReturnValue(method = "getBedOrientation", at = @At("RETURN"))
    private @Nullable Direction katton$modifySleepingDirection(@Nullable Direction original) {
        if (original == null) {
            return null;
        }

        BlockPos sleepingPos = this.getSleepingPos().orElse(null);
        if (sleepingPos == null) {
            return original;
        }

        var result = LivingBehaviorEvent.onModifySleepingDirection.invoke(
                new ModifySleepingDirectionArg((LivingEntity) (Object) this, sleepingPos, original)
        );
        return result.getOrDefault(original);
    }
}
