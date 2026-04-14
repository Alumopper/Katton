package top.katton.platform;

import java.util.function.Consumer;
import net.minecraft.world.level.block.Block;

/**
 * Provides a small Java bridge for platform code to react to dynamically registered blocks.
 */
public final class DynamicRegistryHooks {
    private static Consumer<Block> afterDynamicBlockRegistered = block -> {};

    private DynamicRegistryHooks() {}

    /**
     * Replaces the callback invoked after a dynamic block registration completes.
     *
     * @param hook the callback to invoke; resets to a no-op when {@code null}
     */
    public static void setAfterDynamicBlockRegistered(Consumer<Block> hook) {
        afterDynamicBlockRegistered = hook != null ? hook : block -> {};
    }

    /**
     * Invokes the currently configured callback for a dynamically registered block.
     *
     * @param block the block that has just been registered
     */
    public static void onDynamicBlockRegistered(Block block) {
        afterDynamicBlockRegistered.accept(block);
    }
}
