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

package top.katton.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.storage.loot.LootTable;
import top.katton.bridger.LootTableSource;

public final class LootUtil {
    public static Holder<LootTable> getEntryOrDirect(ServerLevel level, LootTable table) {
        HolderLookup.Provider provider = level
                .getServer()
                .reloadableRegistries()
                .lookup();

        HolderLookup<LootTable> lootTableHolderLookup = provider
                .lookup(Registries.LOOT_TABLE)
                .orElseThrow(() -> new IllegalStateException("Failed to fetch LootTable provider from HolderLookup.Provider"));

        return lootTableHolderLookup
                .listElements()
                .filter(it -> it.value().equals(table))
                .findFirst()
                .map(Function.<Holder<LootTable>>identity())
                .orElseGet(() -> Holder.direct(table));
    }
}
