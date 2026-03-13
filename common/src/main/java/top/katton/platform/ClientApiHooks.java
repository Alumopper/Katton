package top.katton.platform;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class ClientApiHooks {
    public interface Bridge {
        boolean isClientRuntime();
        Object rawClient();
        Object rawPlayer();
        Object rawLevel();
        boolean runOnClient(Runnable action);
        boolean isPaused();
        boolean isInWorld();

        Double playerX();
        Double playerY();
        Double playerZ();
        Float playerYaw();
        Float playerPitch();
        String dimensionId();
        Long gameTime();

        boolean tell(Component message);
        boolean actionBar(Component message);
        boolean overlay(Component message, boolean tinted);
        boolean clearOverlay();
        boolean nowPlaying(Component message);
        boolean playSound(Identifier soundId, float volume, float pitch);

        boolean title(Component message);
        boolean subtitle(Component message);
        boolean titleTimes(int fadeInTicks, int stayTicks, int fadeOutTicks);
        boolean clearTitle();
        boolean toast(Component title, Component description);

        Integer fps();
        boolean windowFocused();
        String screenName();
        boolean inMenu();
        boolean chatOpen();

        boolean hudDrawText(Object graphics, Component text, int x, int y, int color, boolean shadow);
        boolean hudFillRect(Object graphics, int x1, int y1, int x2, int y2, int color);
        boolean hudBlitTexture(
            Object graphics,
            Identifier texture,
            int x,
            int y,
            int width,
            int height,
            float u,
            float v,
            int textureWidth,
            int textureHeight
        );

        boolean worldDrawLine(
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            int argbColor,
            float size,
            int segments
        );

        boolean worldDrawBillboard(
            double x,
            double y,
            double z,
            int argbColor,
            float size
        );

        /**
         * Draw a real GPU line segment using VertexConsumer + RenderType.lines().
         *
         * @param viewMatrix      the modelView Matrix4f from the render pipeline
         * @param projMatrix      the projection Matrix4f from the render pipeline
         * @param camera          the Camera object (for world→camera space offset)
         * @param lineWidth       GL line width
         */
        boolean worldDrawMeshLine(
            Object viewMatrix,
            Object projMatrix,
            Object camera,
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            int argbColor,
            float lineWidth
        );

        /**
         * Draw a real GPU camera-facing quad at a world position.
         *
         * @param viewMatrix  the modelView Matrix4f from the render pipeline
         * @param projMatrix  the projection Matrix4f from the render pipeline
         * @param camera      the Camera object
         * @param size        half-extents of the quad
         */
        boolean worldDrawMeshQuad(
            Object viewMatrix,
            Object projMatrix,
            Object camera,
            double x,
            double y,
            double z,
            int argbColor,
            float size
        );
    }

    private static final Bridge NOOP = new Bridge() {
        @Override
        public boolean isClientRuntime() { return false; }

        @Override
        public Object rawClient() { return null; }

        @Override
        public Object rawPlayer() { return null; }

        @Override
        public Object rawLevel() { return null; }

        @Override
        public boolean runOnClient(Runnable action) { return false; }

        @Override
        public boolean isPaused() { return false; }

        @Override
        public boolean isInWorld() { return false; }

        @Override
        public Double playerX() { return null; }

        @Override
        public Double playerY() { return null; }

        @Override
        public Double playerZ() { return null; }

        @Override
        public Float playerYaw() { return null; }

        @Override
        public Float playerPitch() { return null; }

        @Override
        public String dimensionId() { return null; }

        @Override
        public Long gameTime() { return null; }

        @Override
        public boolean tell(Component message) { return false; }

        @Override
        public boolean actionBar(Component message) { return false; }

        @Override
        public boolean overlay(Component message, boolean tinted) { return false; }

        @Override
        public boolean clearOverlay() { return false; }

        @Override
        public boolean nowPlaying(Component message) { return false; }

        @Override
        public boolean playSound(Identifier soundId, float volume, float pitch) { return false; }

        @Override
        public boolean title(Component message) { return false; }

        @Override
        public boolean subtitle(Component message) { return false; }

        @Override
        public boolean titleTimes(int fadeInTicks, int stayTicks, int fadeOutTicks) { return false; }

        @Override
        public boolean clearTitle() { return false; }

        @Override
        public boolean toast(Component title, Component description) { return false; }

        @Override
        public Integer fps() { return null; }

        @Override
        public boolean windowFocused() { return false; }

        @Override
        public String screenName() { return null; }

        @Override
        public boolean inMenu() { return false; }

        @Override
        public boolean chatOpen() { return false; }

        @Override
        public boolean hudDrawText(Object graphics, Component text, int x, int y, int color, boolean shadow) { return false; }

        @Override
        public boolean hudFillRect(Object graphics, int x1, int y1, int x2, int y2, int color) { return false; }

        @Override
        public boolean hudBlitTexture(
            Object graphics,
            Identifier texture,
            int x,
            int y,
            int width,
            int height,
            float u,
            float v,
            int textureWidth,
            int textureHeight
        ) { return false; }

        @Override
        public boolean worldDrawLine(
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            int argbColor,
            float size,
            int segments
        ) { return false; }

        @Override
        public boolean worldDrawBillboard(
            double x,
            double y,
            double z,
            int argbColor,
            float size
        ) { return false; }

        @Override
        public boolean worldDrawMeshLine(
            Object viewMatrix,
            Object projMatrix,
            Object camera,
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            int argbColor,
            float lineWidth
        ) { return false; }

        @Override
        public boolean worldDrawMeshQuad(
            Object viewMatrix,
            Object projMatrix,
            Object camera,
            double x,
            double y,
            double z,
            int argbColor,
            float size
        ) { return false; }
    };

    private static Bridge bridge = NOOP;

    private ClientApiHooks() {}

    public static void setBridge(Bridge value) {
        bridge = value != null ? value : NOOP;
    }

    public static Bridge get() {
        return bridge;
    }
}
