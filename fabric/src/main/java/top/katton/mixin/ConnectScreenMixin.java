package top.katton.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.katton.pack.ServerPackCacheManager;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {

    @Inject(
        method = "startConnecting",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;prepareForMultiplayer()V",
            shift = At.Shift.AFTER
        )
    )
    private static void katton$captureRemoteServer(
        Screen parent,
        Minecraft minecraft,
        ServerAddress address,
        ServerData serverData,
        boolean quickPlay,
        TransferState transferState,
        CallbackInfo ci
    ) {
        ServerPackCacheManager.beginRemoteConnection(serverData.ip);
    }
}
