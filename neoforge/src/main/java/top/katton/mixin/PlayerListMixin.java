package top.katton.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.katton.api.event.*;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Inject(
            method = "placeNewPlayer",
            at = @At(value = "NEW", target = "net/minecraft/network/protocol/game/ClientboundUpdateRecipesPacket")
    )
    private void hookOnPlayerConnect(Connection connection, ServerPlayer player, CommonListenerCookie arg, CallbackInfo ci) {
        ServerEvent.onSyncDatapackContents.invoke(new SyncDatapackContentsArg(player, true));
    }

    @Inject(
            method = "reloadResources",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/common/ClientboundUpdateTagsPacket;<init>(Ljava/util/Map;)V")
    )
    private void hookOnDataPacksReloaded(CallbackInfo ci) {
        for (ServerPlayer player : ((PlayerList) (Object) this).getPlayers()) {
            ServerEvent.onSyncDatapackContents.invoke(new SyncDatapackContentsArg(player, false));
        }
    }

    @Inject(method = "respawn", at = @At("TAIL"))
    private void afterRespawn(ServerPlayer oldPlayer, boolean alive, Entity.RemovalReason removalReason, CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer newPlayer = cir.getReturnValue();
        ServerPlayerEvent.onAfterPlayerRespawn.invoke(new ServerPlayerAfterRespawnArg(oldPlayer, newPlayer, alive));

        if (oldPlayer.level() != newPlayer.level()) {
            ServerEntityEvent.onAfterPlayerChangeLevel.invoke(new AfterPlayerChangeLevelArg(newPlayer, oldPlayer.level(), newPlayer.level()));
        }
    }

    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    private void firePlayerJoinEvent(Connection connection, ServerPlayer player, CommonListenerCookie clientData, CallbackInfo ci) {
        ServerPlayerEvent.onPlayerJoin.invoke(new PlayerArg(player));
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void firePlayerLeaveEvent(ServerPlayer player, CallbackInfo ci) {
        ServerPlayerEvent.onPlayerLeave.invoke(new PlayerArg(player));
    }
}
