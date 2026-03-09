package top.katton.platform;

import java.util.function.Consumer;
import net.minecraft.world.level.block.Block;

public final class DynamicRegistryHooks {
    private static Consumer<Block> afterDynamicBlockRegistered = block -> {};

    private DynamicRegistryHooks() {}

    public static void setAfterDynamicBlockRegistered(Consumer<Block> hook) {
        afterDynamicBlockRegistered = hook != null ? hook : block -> {};
    }

    public static void onDynamicBlockRegistered(Block block) {
        afterDynamicBlockRegistered.accept(block);
    }
}