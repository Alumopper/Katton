package top.kts4mc

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.packs.PackType
import org.slf4j.LoggerFactory
import top.kts4mc.command.ScriptCommand
import top.kts4mc.engine.ScriptLoader

object KTS4MC : ModInitializer {
    private val logger = LoggerFactory.getLogger("kts4mc")

	val scriptLoader = ScriptLoader()

	override fun onInitialize() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloader(
			Identifier.parse("kts4mc:scripts"), scriptLoader)

		CommandRegistrationCallback.EVENT.register {d,_,_ ->
			ScriptCommand.register(d)
		}
	}
}