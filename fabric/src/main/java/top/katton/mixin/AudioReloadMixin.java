package top.katton.mixin;

import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.client.audio.ClientAudioManager;

/** Preserve media position before resource reload/device replacement destroys native channels. */
@Mixin(SoundEngine.class)
public abstract class AudioReloadMixin {
    @Inject(method = "reload", at = @At("HEAD"))
    private void katton$beforeAudioReload(CallbackInfo ci) {
        ClientAudioManager.beforeSoundReload();
    }
}
