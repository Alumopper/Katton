package top.katton;

import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.katton.command.ScriptCommand;
import top.katton.engine.ScriptEngine;
import top.katton.engine.ScriptLoader;
import top.katton.util.Event;

public class Katton implements ModInitializer {
    private static final Logger logger = LoggerFactory.getLogger("katton");

    /**
     * Current minecraft server instance. Maybe null during client-side execution.
     */
    public static MinecraftServer server = null;

    @Override
    public void onInitialize() {
//        File gameDir = FabricLoader.getInstance().getGameDir().toFile();
//        File ktScriptsDir = new File(gameDir, "ktscripts");
//        if (ktScriptsDir.exists() && ktScriptsDir.isDirectory()) {
//            File[] subFolders = ktScriptsDir.listFiles();
//            if (subFolders != null) {
//                for (File subFolder : subFolders) {
//                    if (subFolder.isDirectory()) {
//                        File serversDir = new File(subFolder, "servers");
//                        if (serversDir.exists() && serversDir.isDirectory()) {
//                            File[] scriptFiles = serversDir.listFiles((_, name) -> name.endsWith(".kts"));
//                            if (scriptFiles != null) {
//                                for (File scriptFile : scriptFiles) {
//                                    try {
//                                        BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (_, continuation) -> {
//                                            try {
//                                                logger.info("Compiling and executing script: {}", scriptFile.getAbsolutePath());
//                                                String source = readString(scriptFile.toPath());
//                                                @SuppressWarnings("unchecked") ResultWithDiagnostics<CompiledScript> compileResult = (ResultWithDiagnostics<CompiledScript>) ScriptEngine.INSTANCE.compile(scriptFile.getAbsolutePath(), source, scriptFile.getAbsolutePath(), false, continuation);
//                                                assert compileResult != null;
//                                                var compiled = valueOrThrow(compileResult);
//                                                ScriptEngine.INSTANCE.execute(compiled, null, continuation);
//                                            } catch (Exception e) {
//                                                logger.error("Failed to execute script: {}", scriptFile.getAbsolutePath(), e);
//                                            }
//                                            return Unit.INSTANCE;
//                                        });
//                                    } catch (InterruptedException e) {
//                                        throw new RuntimeException(e);
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }

        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
                Identifier.parse("katton:scripts"), ScriptLoader.INSTANCE
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ScriptCommand.INSTANCE.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(serverInstance -> server = serverInstance);

        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> server = null);

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((_, _, success) -> {
            if (!success) {
                return;
            }

            Event.Companion.getFabricEventRegistry().values().forEach(list -> list.forEach(Event::clear));

            ScriptLoader.getMainScript().forEach((id, script) ->
                    {
                        try {
                            BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (_, continuation) -> {
                                ScriptEngine.execute(script, id.toString(), continuation);
                                return Unit.INSTANCE;
                            });
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
        });
    }
}