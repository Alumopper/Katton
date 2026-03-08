package top.katton.util;

import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.LootTable;

public interface FabricLootTable {
    void fabric$setHolder(Holder<LootTable> key);
}
