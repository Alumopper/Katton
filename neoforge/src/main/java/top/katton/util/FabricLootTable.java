package top.katton.util;

import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.LootTable;

/** Interface for setting the holder on a loot table, injected via mixin. */
public interface FabricLootTable {
    /**
     * Sets the holder reference on this loot table.
     * @param key the holder to set
     */
    void fabric$setHolder(Holder<LootTable> key);
}