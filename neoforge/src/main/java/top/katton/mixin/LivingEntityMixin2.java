package top.katton.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
    private void afterDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir, @Local(name = "originalDamage") float originalDamage, @Local(name = "blocked") boolean blocked) {
        if (!isDeadOrDying()) {
            ServerLivingEntityEvent.onAfterDamage.invoke(new ServerLivingAfterDamageArg((LivingEntity) (Object) this, source, originalDamage, amount, blocked));
        }
    }

    @Inject(method = "startSleeping", at = @At("RETURN"))
    private void onSleep(BlockPos pos, CallbackInfo info) {
        LivingBehaviorEvent.onStartSleeping.invoke(new SleepingArg((LivingEntity) (Object) this, pos));
    }

    @Inject(method = "stopSleeping", at = @At("HEAD"))
    private void onWakeUp(CallbackInfo info) {
        BlockPos sleepingPos = getSleepingPos().orElse(null);

        // If actually asleep - this method is often called with data loading, syncing etc. "just to be sure"
        if (sleepingPos != null) {
            LivingBehaviorEvent.onStopSleeping.invoke(new SleepingArg((LivingEntity) (Object) this, sleepingPos));
        }
    }

    @Dynamic("lambda$checkBedExists$0: Synthetic lambda body for Optional.map in checkBedExists")
    @Inject(method = "lambda$checkBedExists$0", at = @At("RETURN"), cancellable = true)
    private void onIsSleepingInBed(BlockPos sleepingPos, CallbackInfoReturnable<Boolean> info) {
        BlockState bedState = ((LivingEntity) (Object) this).level().getBlockState(sleepingPos);
        JResult<EventResult> result = LivingBehaviorEvent.onAllowBed.invoke(new AllowBedArg((LivingEntity) (Object) this, sleepingPos, bedState, info.getReturnValueZ()));

        if (result.notEmptyAndNotEquals(EventResult.PASS)) {
            info.setReturnValue(result.getOrNull().allowAction());
        }
    }

    @WrapOperation(method = "getBedOrientation", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/BedBlock;getBedOrientation(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Direction;"))
    private Direction onGetSleepingDirection(BlockGetter level, BlockPos sleepingPos, Operation<Direction> operation) {
        final Direction sleepingDirection = operation.call(level, sleepingPos);
        var result = LivingBehaviorEvent.onModifySleepingDirection.invoke(new ModifySleepingDirectionArg((LivingEntity) (Object) this, sleepingPos, sleepingDirection));
        return result.getOrDefault(sleepingDirection);
    }

    // This is needed 1) so that the vanilla logic in wakeUp runs for modded beds and 2) for the injector below.
    // The injector is shared because lambda$stopSleeping$23 and sleep share much of the structure here.
    @Dynamic("lambda$stopSleeping$0: Synthetic lambda body for Optional.ifPresent in stopSleeping")
    @ModifyVariable(method = {"lambda$stopSleeping$0", "startSleeping"}, at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState modifyBedForOccupiedState(BlockState state, BlockPos sleepingPos) {
        var result = LivingBehaviorEvent.onAllowBed.invoke(new AllowBedArg((LivingEntity) (Object) this, sleepingPos, state, state.getBlock() instanceof BedBlock));

        // If a valid bed, replace with vanilla red bed so that the vanilla instanceof check succeeds.
        //noinspection NullableProblems
        return result.notEmptyAndMatch(e -> e.allowAction(false)) ? Blocks.RED_BED.defaultBlockState() : state;
    }

    // The injector is shared because lambda$stopSleeping$23 and sleep share much of the structure here.
    @Dynamic("lambda$stopSleeping$0: Synthetic lambda body for Optional.ifPresent in stopSleeping")
    @Redirect(method = {"lambda$stopSleeping$0", "startSleeping"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean setOccupiedState(Level level, BlockPos pos, BlockState state, int flags) {
        // This might have been replaced by a red bed above, so we get it again.
        // Note that we *need* to replace it so the state.with(OCCUPIED, ...) call doesn't crash
        // when the bed doesn't have the property.
        BlockState originalState = level.getBlockState(pos);
        boolean occupied = state.getValue(BedBlock.OCCUPIED);

        if (LivingBehaviorEvent.onSetBedOccupationState.invoke(new SetBedOccupationStateArg((LivingEntity) (Object) this, pos, originalState, occupied)).emptyOrTrue()) {
            return true;
        } else if (originalState.hasProperty(BedBlock.OCCUPIED)) {
            // This check is widened from (instanceof BedBlock) to a property check to allow modded blocks
            // that don't use the event.
            return level.setBlock(pos, originalState.setValue(BedBlock.OCCUPIED, occupied), flags);
        } else {
            return false;
        }
    }

    @Dynamic("lambda$stopSleeping$0: Synthetic lambda body for Optional.ifPresent in stopSleeping")
    @Redirect(method = "lambda$stopSleeping$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/BedBlock;findStandUpPosition(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/CollisionGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;F)Ljava/util/Optional;"))
    private Optional<Vec3> modifyWakeUpPosition(EntityType<?> type, CollisionGetter level, BlockPos pos, Direction direction, float yaw) {
        Optional<Vec3> original = Optional.empty();
        BlockState bedState = level.getBlockState(pos);

        if (bedState.getBlock() instanceof BedBlock) {
            original = BedBlock.findStandUpPosition(type, level, pos, direction, yaw);
        }

        Vec3 newPos = LivingBehaviorEvent.onModifyWakeUpPosition.invoke(new ModifyWakeUpPositionArg((LivingEntity) (Object) this, pos, bedState, original.orElse(null))).getOrNull();
        return Optional.ofNullable(newPos);
	}
}
