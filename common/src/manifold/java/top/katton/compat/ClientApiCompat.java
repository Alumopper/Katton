package top.katton.compat;

#if MC_VERSION == "26.2"
import net.minecraft.world.level.LightLayer;
#else
import net.minecraft.client.renderer.LevelRenderer;
#endif

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndLightGetter;

public final class ClientApiCompat {
    private ClientApiCompat() {
    }

    public static int getLightCoords(BlockAndLightGetter level, BlockPos pos) {
#if MC_VERSION == "26.2"
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        int sky = level.getBrightness(LightLayer.SKY, pos);
        return (block << 4) | (sky << 20);
#else
        return LevelRenderer.getLightCoords(level, pos);
#endif
    }

    public static void resetLevelRenderer(Minecraft minecraft) {
#if MC_VERSION == "26.2"
        minecraft.levelRenderer.resetLevelRenderData();
#else
        minecraft.levelRenderer.allChanged();
#endif
    }
}
