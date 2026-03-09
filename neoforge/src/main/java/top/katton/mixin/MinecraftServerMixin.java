package top.katton.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.katton.Katton;
import top.katton.LoadState;
import top.katton.api.event.EndDatapackReloadArg;
import top.katton.api.event.ServerSaveArg;
import top.katton.api.event.ServerEvent;
import top.katton.api.event.StartDatapackReloadArg;
import top.katton.command.ScriptCommand;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("DataFlowIssue")
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Shadow
    private MinecraftServer.ReloadableResources resources;

    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void startResourceReload(Collection<String> collection, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        ServerEvent.onStartDatapackReload.invoke(new StartDatapackReloadArg((MinecraftServer) (Object) this, this.resources.resourceManager()));
    }

    @Inject(method = "reloadResources", at = @At("TAIL"))
    private void endResourceReload(Collection<String> collection, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        cir.getReturnValue().handleAsync((v, throwable) -> {
            // Hook into fail
            MinecraftServer server = (MinecraftServer) (Object) this;
            boolean success = throwable == null;

            ServerEvent.onEndDatapackReload.invoke(new EndDatapackReloadArg(server, this.resources.resourceManager(), success));
            Katton.globalState = LoadState.END_DATA_PACK_RELOAD;

            if (success) {
                Katton.reloadScripts(server);
                ScriptCommand.syncCommandTree(server);
            }
            return v;
        }, (MinecraftServer) (Object) this);
    }

    @Inject(method = "saveAllChunks", at = @At("HEAD"))
    private void startSave(boolean suppressLogs, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
        ServerEvent.onBeforeSave.invoke(new ServerSaveArg((MinecraftServer) (Object) this, flush, force));
    }

    @Inject(method = "saveAllChunks", at = @At("TAIL"))
    private void endSave(boolean suppressLogs, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
        ServerEvent.onAfterSave.invoke(new ServerSaveArg((MinecraftServer) (Object) this, flush, force));
    }
}
