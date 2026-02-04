package top.katton

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import org.slf4j.LoggerFactory
import top.katton.api.KattonEvents
import top.katton.api.server
import top.katton.command.ScriptCommand
import top.katton.engine.ScriptLoader

object Katton : ModInitializer {
    private val logger = LoggerFactory.getLogger("katton")

	val scriptLoader = ScriptLoader()

	override fun onInitialize() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloader(
			Identifier.parse("katton:scripts"), scriptLoader)

		CommandRegistrationCallback.EVENT.register {d,_,_ ->
			ScriptCommand.register(d)
		}

		ServerLifecycleEvents.SERVER_STARTED.register {
			server = it
		}

		ServerLifecycleEvents.SERVER_STOPPED.register {
			server = null
		}
	}
}