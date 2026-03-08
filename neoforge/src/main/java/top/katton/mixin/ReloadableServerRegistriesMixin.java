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

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.DynamicOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.Validatable;
import top.katton.api.event.LootTableAllLoadArg;
import top.katton.api.event.LootTableEvent;
import top.katton.api.event.LootTableModifyArg;
import top.katton.api.event.LootTableReplaceArg;
import top.katton.util.FabricLootTable;
import top.katton.util.FabricLootTableBuilder;

@Mixin(ReloadableServerRegistries.class)
abstract class ReloadableServerRegistriesMixin {
    /**
     * Due to possible cross-thread handling, this uses WeakHashMap instead of ThreadLocal.
     */
    @Unique
    private static final WeakHashMap<RegistryOps<JsonElement>, HolderLookup.Provider> WRAPPERS = new WeakHashMap<>();

    @WrapOperation(method = "reload", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/HolderLookup$Provider;createSerializationContext(Lcom/mojang/serialization/DynamicOps;)Lnet/minecraft/resources/RegistryOps;"))
    private static RegistryOps<JsonElement> storeOps(HolderLookup.Provider holder, DynamicOps<JsonElement> ops, Operation<RegistryOps<JsonElement>> original) {
        RegistryOps<JsonElement> created = original.call(holder, ops);
        WRAPPERS.put(created, holder);
        return created;
    }

    @WrapOperation(method = "reload", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private static CompletableFuture<LayeredRegistryAccess<RegistryLayer>> removeOps(CompletableFuture<List<WritableRegistry<?>>> future, Function<? super List<WritableRegistry<?>>, ? extends LayeredRegistryAccess<RegistryLayer>> fn, Executor executor, Operation<CompletableFuture<LayeredRegistryAccess<RegistryLayer>>> original, @Local(name = "ops") RegistryOps<JsonElement> ops) {
        return original.call(future.thenApply(v -> {
            WRAPPERS.remove(ops);
            return v;
        }), fn, executor);
    }

    @Inject(method = "lambda$scheduleRegistryLoad$0", at = @At(value = "INVOKE", target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V"))
    private static <T extends Validatable> void modifyLootTable(LootDataType<T> lootDataType, ResourceManager resourceManager, RegistryOps<JsonElement> registryOps, CallbackInfoReturnable<WritableRegistry<?>> cir, @Local(name = "elements") Map<Identifier, T> elements) {
        elements.replaceAll((identifier, t) -> modifyLootTable(t, identifier, registryOps));
    }

    @Unique
    private static <T> T modifyLootTable(T value, Identifier id, RegistryOps<JsonElement> ops) {
        if (!(value instanceof LootTable table)) return value;

        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, id);
        // Populated above.
        HolderLookup.Provider provider = WRAPPERS.get(ops);
        // Invoke the REPLACE event for the current loot table.
        LootTable replacement = LootTableEvent.onLootTableReplace.invoke(new LootTableReplaceArg(key, table, provider)).getOrNull();

        if (replacement != null) {
            table = replacement;
        }

        // Turn the current table into a modifiable builder and invoke the MODIFY event.
        LootTable.Builder builder = FabricLootTableBuilder.copyOf(table);
        LootTableEvent.onLootTableModify.invoke(new LootTableModifyArg(key, builder, provider));

        return (T) builder.build();
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "lambda$scheduleRegistryLoad$0", at = @At("RETURN"))
    private static <T extends Validatable> void onLootTablesLoaded(LootDataType<T> lootDataType, ResourceManager resourceManager, RegistryOps<JsonElement> registryOps, CallbackInfoReturnable<WritableRegistry<?>> cir) {
        if (lootDataType != LootDataType.TABLE) return;

        Registry<LootTable> lootTableRegistry = (Registry<LootTable>) cir.getReturnValue();

        LootTableEvent.onLootTableAllLoad.invoke(new LootTableAllLoadArg(resourceManager, lootTableRegistry));
       lootTableRegistry.listElements().forEach(reference -> ((FabricLootTable) reference.value()).fabric$setHolder(reference));
    }
}
