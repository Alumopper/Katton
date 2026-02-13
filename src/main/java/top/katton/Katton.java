package top.katton;

import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.script.experimental.api.CompiledScript;
import kotlin.script.experimental.api.ResultWithDiagnostics;
import kotlinx.coroutines.BuildersKt;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.katton.command.ScriptCommand;
import top.katton.engine.ScriptLoader;
import top.katton.util.Event;

import java.io.File;

import static java.nio.file.Files.readString;
import static kotlin.script.experimental.api.ErrorHandlingKt.valueOrThrow;

public class Katton implements ModInitializer {
    private static final Logger logger = LoggerFactory.getLogger("katton");

    /**
     * Current minecraft server instance. Maybe null during client-side execution.
     */
    public static MinecraftServer server = null;

    public static final ScriptLoader scriptLoader = new ScriptLoader();

    @Override
    public void onInitialize() {
        File gameDir = FabricLoader.getInstance().getGameDir().toFile();
        File ktScriptsDir = new File(gameDir, "ktscripts");
        if (ktScriptsDir.exists() && ktScriptsDir.isDirectory()) {
            File[] subFolders = ktScriptsDir.listFiles();
            if (subFolders != null) {
                for (File subFolder : subFolders) {
                    if (subFolder.isDirectory()) {
                        File serversDir = new File(subFolder, "servers");
                        if (serversDir.exists() && serversDir.isDirectory()) {
                            File[] scriptFiles = serversDir.listFiles((_, name) -> name.endsWith(".kts"));
                            if (scriptFiles != null) {
                                for (File scriptFile : scriptFiles) {
                                    try {
                                        BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (_, continuation) -> {
                                            try {
                                                logger.info("Compiling and executing script: {}", scriptFile.getAbsolutePath());
                                                String source = readString(scriptFile.toPath());
                                                @SuppressWarnings("unchecked") ResultWithDiagnostics<CompiledScript> compileResult = (ResultWithDiagnostics<CompiledScript>) scriptLoader.getEngine().compile(scriptFile.getAbsolutePath(), source, scriptFile.getAbsolutePath(), false, continuation);
                                                assert compileResult != null;
                                                var compiled = valueOrThrow(compileResult);
                                                scriptLoader.getEngine().execute(compiled, null, continuation);
                                            } catch (Exception e) {
                                                logger.error("Failed to execute script: {}", scriptFile.getAbsolutePath(), e);
                                            }
                                            return Unit.INSTANCE;
                                        });
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
                Identifier.parse("katton:scripts"), scriptLoader
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> ScriptCommand.INSTANCE.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(serverInstance -> server = serverInstance);

        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> server = null);

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((_, _, success) -> {
            if (!success) {
                return;
            }

            Event.Companion.getFabricEventRegistry().values().forEach(list -> list.forEach(Event::clear));

            scriptLoader.getMainScript().forEach((id, script) ->
                    {
                        try {
                            BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (_, continuation) -> {
                                scriptLoader.getEngine().execute(script, id.toString(), continuation);
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