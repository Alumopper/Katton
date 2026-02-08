package top.katton

import kotlinx.coroutines.runBlocking
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.packs.PackType
import org.slf4j.LoggerFactory
import top.katton.command.ScriptCommand
import top.katton.engine.ScriptLoader
import top.katton.util.Event
import java.io.File
import kotlin.script.experimental.api.valueOrThrow

object Katton : ModInitializer {
    private val logger = LoggerFactory.getLogger("katton")
    /**
     * Current minecraft server instance. Maybe null during client-side execution.
     */
    var server: MinecraftServer? = null

	val scriptLoader = ScriptLoader()

	override fun onInitialize() {
		val gameDir = FabricLoader.getInstance().gameDir.toFile()
		val ktScriptsDir = File(gameDir, "ktscripts")
		if (ktScriptsDir.exists() && ktScriptsDir.isDirectory) {
			ktScriptsDir.listFiles()?.forEach { subFolder ->
				if (subFolder.isDirectory) {
					val serversDir = File(subFolder, "servers")
					if (serversDir.exists() && serversDir.isDirectory) {
						serversDir.listFiles { _, name -> name.endsWith(".kts") }?.forEach { scriptFile ->
							runBlocking {
								try {
									logger.info("Compiling and executing script: ${scriptFile.absolutePath}")
									val source = scriptFile.readText()
									val compileResult = scriptLoader.engine.compile(scriptFile.absolutePath, source)
									val compiled = compileResult.valueOrThrow()
									scriptLoader.engine.execute(compiled)
								} catch (e: Exception) {
									logger.error("Failed to execute script: ${scriptFile.absolutePath}", e)
								}
							}
						}
					}
				}
			}
		}

		ResourceLoader.get(PackType.SERVER_DATA).registerReloader(
			Identifier.parse("katton:scripts"), scriptLoader
		)

		CommandRegistrationCallback.EVENT.register {d,_,_ ->
			ScriptCommand.register(d)
		}

		ServerLifecycleEvents.SERVER_STARTED.register {
			server = it
		}

		ServerLifecycleEvents.SERVER_STOPPED.register {
			server = null
		}

		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { _, _, success ->
			if(!success) return@register
			//clear event registrations
			Event.fabricEventRegistry.values.forEach { it.forEach { e -> e.clear() } }
			//if reload success, invoke main scripts in every namespace
			scriptLoader.mainScript.values.forEach {
				runBlocking {
					scriptLoader.engine.execute(it)
				}
			}
		}
	}
}