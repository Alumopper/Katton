package top.katton.platform;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.IdMapper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class NeoForgeDynamicRegistryHooks {
    private static final Method INIT_CACHE_METHOD = resolveInitCacheMethod();
    private static final Field NEOFORGE_BLOCKSTATE_ID_MAP_FIELD = resolveNeoForgeBlockStateIdMapField();

    private NeoForgeDynamicRegistryHooks() {}

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
        try {
            INIT_CACHE_METHOD.invoke(state);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to initialize cache for dynamic block state " + state, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static IdMapper<BlockState> getNeoForgeBlockStateMap() {
        try {
            return (IdMapper<BlockState>) NEOFORGE_BLOCKSTATE_ID_MAP_FIELD.get(null);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to access NeoForge block state id map", exception);
        }
    }

    private static Method resolveInitCacheMethod() {
        try {
            Class<?> blockStateBaseClass = Class.forName("net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase");
            Method method = blockStateBaseClass.getDeclaredMethod("initCache");
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Field resolveNeoForgeBlockStateIdMapField() {
        try {
            Class<?> callbacksClass = Class.forName("net.neoforged.neoforge.registries.NeoForgeRegistryCallbacks$BlockCallbacks");
            Field field = callbacksClass.getDeclaredField("BLOCKSTATE_TO_ID_MAP");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}