/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.katton.mixin;

import com.mojang.datafixers.util.Either;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;

import top.katton.api.event.AllowResettingTimeArg;
import top.katton.api.event.AllowSleepingArg;
import top.katton.api.event.LivingBehaviorEvent;

@SuppressWarnings("DataFlowIssue")
@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "startSleepInBed", at = @At("HEAD"), cancellable = true)
    private void onStartSleepInBed(BlockPos pos, CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> info) {
        var result = LivingBehaviorEvent.onAllowSleeping.invoke(new AllowSleepingArg((Player) (Object) this, pos)).getOrNull();

        if (result != null) {
            info.setReturnValue(Either.left(result));
        }
    }

    @Inject(method = "isSleepingLongEnough", at = @At("RETURN"), cancellable = true)
    private void onIsSleepingLongEnough(CallbackInfoReturnable<Boolean> info) {
        if (info.getReturnValueZ()) {
            var result = LivingBehaviorEvent.onAllowResettingTime.invoke(new AllowResettingTimeArg((Player) (Object) this));
            info.setReturnValue(result.emptyOrTrue());
        }
    }
}
