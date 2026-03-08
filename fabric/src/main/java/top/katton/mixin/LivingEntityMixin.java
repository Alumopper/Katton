package top.katton.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.bytebuddy.jar.asm.Opcodes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.katton.api.event.*;

@SuppressWarnings("DataFlowIssue")
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    protected int useItemRemaining;

    @Shadow
    protected abstract void completeUsingItem();

    @Shadow
    protected ItemStack useItem;

    @Shadow
    protected abstract void updatingUsingItem();
    
    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void katton$onFall(float distance, float damageMultiplier, 
                               CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        ServerLivingEntityEvent.onLivingFall.invoke(new LivingFallArg(self, distance, damageMultiplier));
        if (ServerLivingEntityEvent.onLivingFall.isCanceled()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "startUsingItem",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/LivingEntity;useItem:Lnet/minecraft/world/item/ItemStack;",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            ),
            cancellable = true)
    private void beforeSetUseItem(InteractionHand hand, CallbackInfo ci, @Local ItemStack itemStack) {
        LivingEntity self = (LivingEntity)(Object)this;
        LivingUseItemEvent.onUseItemStart.invoke(new LivingUseItemStartArg(self, itemStack, hand, itemStack.getUseDuration(self)));
        if(LivingUseItemEvent.onUseItemStart.isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = "updateUsingItem", at = @At("HEAD"), cancellable = true)
    protected void onUpdateUsingItem(ItemStack useItem, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!useItem.isEmpty()) {
            var arg = new LivingUseItemTickArg(self, useItem, self.getUseItemRemainingTicks());
            LivingUseItemEvent.onUseItemTick.invoke(arg);
            if(LivingUseItemEvent.onUseItemTick.isCanceled()){
                this.useItemRemaining = -1;
            }else{
                this.useItemRemaining = arg.getDuration();
            }
        }
        if (self.getUseItemRemainingTicks() > 0)
            useItem.onUseTick(self.level(), self, self.getUseItemRemainingTicks());
        if (--this.useItemRemaining <= 0 && !self.level().isClientSide() && !useItem.useOnRelease()) {
            this.completeUsingItem();
        }
        ci.cancel();
    }

    @Inject(method = "releaseUsingItem", at = @At("HEAD"), cancellable = true)
    public void releaseUsingItem(CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        ItemStack itemInUsedHand = self.getItemInHand(self.getUsedItemHand());
        if (!useItem.isEmpty() && ItemStack.isSameItem(itemInUsedHand, useItem)) {
            useItem = itemInUsedHand;
            LivingUseItemEvent.onUseItemStop.invoke(new LivingUseItemStopArg(self, useItem, self.getUseItemRemainingTicks()));
            if (!LivingUseItemEvent.onUseItemStop.isCanceled()) {
                ItemStack copy = self instanceof Player ? useItem.copy() : null;
                this.useItem.releaseUsing(self.level(), self, self.getUseItemRemainingTicks());
                if (copy != null && useItem.isEmpty()) PlayerEvent.onDestroyItem.invoke(new PlayerDestroyItemArg((Player)self, copy, self.getUsedItemHand()));
            }
            if (this.useItem.useOnRelease()) {
                this.updatingUsingItem();
            }
        }

        self.stopUsingItem();

        ci.cancel();
    }

    @Inject(method = "completeUsingItem", at = @At("HEAD"), cancellable = true)
    protected void onCompleteUsingItem(CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!self.level().isClientSide() || self.isUsingItem()) {
            InteractionHand hand = self.getUsedItemHand();
            if (!this.useItem.equals(self.getItemInHand(hand))) {
                self.releaseUsingItem();
            } else {
                if (!this.useItem.isEmpty() && self.isUsingItem()) {
                    ItemStack copy = this.useItem.copy();
                    var arg = new LivingUseItemFinishArg(self, copy, self.getUseItemRemainingTicks(), this.useItem.finishUsingItem(self.level(), self));
                    LivingUseItemEvent.onUseItemFinish.invoke(arg);
                    ItemStack result = arg.getResult();
                    if (result != this.useItem) {
                        self.setItemInHand(hand, result);
                    }

                    self.stopUsingItem();
                }
            }
        }
        ci.cancel();
    }


    @Inject(
            method = "applyItemBlocking",
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lnet/minecraft/world/item/component;resolveBlockedDamage(Lnet/minecraft/world/damagesource/DamageSource;FD)F"
            ),
            cancellable = true
    )
    private void afterResolveBlockedDamage(
            DamageSource source,
            float damage,
            float angle,
            CallbackInfoReturnable<Float> cir,
            @Local BlocksAttacks blocksAttacks,
            @Local LocalFloatRef damageBlocked
    ) {
        LivingEntity self = (LivingEntity) (Object) this;

        var arg = new ShieldBlockArg(self, source, damage, !blocksAttacks.bypassedBy().map(source::is).orElse(false));
        var blocked = ServerEntityCombatEvent.onShieldBlock.invoke(arg).getOrDefault(arg.getOriginalBlockedState());
        if(!blocked){
            cir.setReturnValue(0.0f);
            return;
        }
        damageBlocked.set(arg.getBlockedDamage());
    }

    @Inject(
            method = "hurtServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true
    )
    private void beforeSleepingCheck(
            ServerLevel level,
            DamageSource source,
            float damage,
            CallbackInfoReturnable<Boolean> cir
    ) {
        LivingEntity self = (LivingEntity) (Object) this;
        ServerLivingEntityEvent.onLivingHurt.invoke(new LivingHurtArg(self, source, damage));
        if(ServerLivingEntityEvent.onLivingHurt.isCanceled()){
            cir.setReturnValue(false);
        }
    }
}

