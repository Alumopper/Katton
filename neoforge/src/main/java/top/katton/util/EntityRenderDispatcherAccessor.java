package top.katton.util;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

public interface EntityRenderDispatcherAccessor {
    EntityRendererProvider.Context katton$getContext();
}
