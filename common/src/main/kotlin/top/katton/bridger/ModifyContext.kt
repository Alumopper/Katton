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
 *
 * Modified by Katton on 2026-3.
 */


package top.katton.bridger

import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Predicate

interface ModifyContext {
    /**
     * Modify the default data components of the specified item.
     *
     * @param itemPredicate A predicate to match items to modify
     * @param builderConsumer A consumer that provides a {@link DataComponentMap.Builder} to modify the item's components.
     */
    fun modify(itemPredicate: Predicate<Item>, builderConsumer: BiConsumer<DataComponentMap.Builder, Item>)

    /**
     * Modify the default data components of the specified item.
     *
     * @param item The item to modify
     * @param builderConsumer A consumer that provides a {@link DataComponentMap.Builder} to modify the item's components.
     */
    fun modify(item: Item, builderConsumer: Consumer<DataComponentMap.Builder>) {
        modify(Predicate.isEqual<Item>(item), { builder, _item -> builderConsumer.accept(builder) });
    }

    /**
     * Modify the default data components of the specified items.
     * @param items The items to modify
     * @param builderConsumer A consumer that provides a {@link DataComponentMap.Builder} to modify the item's components.
     */
    fun modify(items: Collection<Item>, builderConsumer: BiConsumer<DataComponentMap.Builder, Item>) {
        modify(items::contains, builderConsumer);
    }
}


object ModifyContextImpl: ModifyContext {

	override fun modify(itemPredicate: Predicate<Item>, builderConsumer: BiConsumer<DataComponentMap.Builder, Item>) {
		for (item in BuiltInRegistries.ITEM) {
			if (itemPredicate.test(item)) {
				val builder = DataComponentMap.builder().addAll(item.components());
				builderConsumer.accept(builder, item);
				item.builtInRegistryHolder().bindComponents(builder.build());
			}
		}
	}
}
