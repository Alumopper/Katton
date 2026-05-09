package top.katton.platform;

import net.minecraft.core.IdMapper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import top.katton.util.ReflectUtil;
import top.katton.util.Result;

/**
 * NeoForge-specific hooks for dynamically registering blocks and their block states
 * at runtime, including cache initialization and id-map synchronization.
 */
public final class NeoForgeDynamicRegistryHooks {

    private static final Class<?> BLOCK_STATE_BASE_CLASS;
    private static final Class<?> NEOFORGE_CALLBACKS_CLASS;

    static {
        Result<Class<?>> baseResult = ReflectUtil.INSTANCE.getPossibleClassFromNames(
            "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase"
        );
        BLOCK_STATE_BASE_CLASS = baseResult.getOrThrow();

        Result<Class<?>> callbacksResult = ReflectUtil.INSTANCE.getPossibleClassFromNames(
            "net.neoforged.neoforge.registries.NeoForgeRegistryCallbacks$BlockCallbacks"
        );
        NEOFORGE_CALLBACKS_CLASS = callbacksResult.getOrThrow();
    }

    private NeoForgeDynamicRegistryHooks() {}

    /**
     * Called after a dynamic block is registered to initialize its state caches
     * and add all possible block states to both the vanilla and NeoForge id maps.
     *
     * @param block the block that was just dynamically registered
     */
    public static void afterDynamicBlockRegistered(Block block) {
        IdMapper<BlockState> neoforgeBlockStateMap = getNeoForgeBlockStateMap();

        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            initCache(state);
            if (Block.BLOCK_STATE_REGISTRY.getId(state) == -1) {
                Block.BLOCK_STATE_REGISTRY.add(state);
            }
            if (neoforgeBlockStateMap.getId(state) == -1) {
                neoforgeBlockStateMap.add(state);
            }
        }
    }

    private static void initCache(BlockState state) {
        ReflectUtil.INSTANCE.invoke(state, "initCache").getOrThrow();
    }

    @SuppressWarnings("unchecked")
    private static IdMapper<BlockState> getNeoForgeBlockStateMap() {
        return (IdMapper<BlockState>) ReflectUtil.INSTANCE
            .getStatic(NEOFORGE_CALLBACKS_CLASS, "BLOCKSTATE_TO_ID_MAP")
            .getOrNull();
    }
}
