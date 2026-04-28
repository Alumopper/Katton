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

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import top.katton.util.FabricLootPoolBuilder;

/** Mixin accessor for {@link LootPool} internals. */
@Mixin(LootPool.class)
public interface LootPoolAccessor extends FabricLootPoolBuilder.LootPoolAccessor {
    /**
     * Gets the rolls number provider.
     * @return the rolls provider
     */
    @Accessor("rolls")
    NumberProvider fabric_getRolls();

    /**
     * Gets the bonus rolls number provider.
     * @return the bonus rolls provider
     */
    @Accessor("bonusRolls")
    NumberProvider fabric_getBonusRolls();

    /**
     * Gets the loot pool entries.
     * @return the list of entries
     */
    @Accessor("entries")
    List<LootPoolEntryContainer> fabric_getEntries();

    /**
     * Gets the loot pool conditions.
     * @return the list of conditions
     */
    @Accessor("conditions")
    List<LootItemCondition> fabric_getConditions();

    /**
     * Gets the loot pool functions.
     * @return the list of functions
     */
    @Accessor("functions")
    List<LootItemFunction> fabric_getFunctions();
}