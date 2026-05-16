package top.katton.api.event

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.server.BroadcastMessageEvent
import org.bukkit.event.server.RemoteServerCommandEvent
import org.bukkit.event.server.ServerCommandEvent
import org.bukkit.plugin.java.JavaPlugin
import top.katton.paper.PaperNmsBridge
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createUnit

object ServerMessageEvent {
    @JvmField
    val onAllowChatMessage = createAll<Any>()

    @JvmField
    val onAllowGameMessage = createAll<Any>()

    @JvmField
    val onAllowCommandMessage = createAll<Any>()

    @JvmField
    val onChatMessage = createUnit<Any>()

    @JvmField
    val onGameMessage = createUnit<Any>()

    @JvmField
    val onCommandMessage = createUnit<Any>()

    @JvmField
    val onServerChat = createCancellableUnit<ServerChatArg>()

    @JvmStatic
    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onChat(event: AsyncChatEvent) {
                if (!onAllowChatMessage(event).getOrElse { true }) {
                    event.isCancelled = true
                    return
                }

                val arg = ServerChatArg(
                    PaperNmsBridge.toNmsPlayer(event.player),
                    PlainTextComponentSerializer.plainText().serialize(event.message()),
                    PaperNmsBridge.toNmsComponent(
                        PlainTextComponentSerializer.plainText().serialize(event.message())
                    )
                )
                onServerChat(arg)
                if (arg.isCancelled()) {
                    event.isCancelled = true
                    return
                }

                onChatMessage(event)
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onBroadcast(event: BroadcastMessageEvent) {
                if (!onAllowGameMessage(event).getOrElse { true }) {
                    event.isCancelled = true
                    return
                }
                onGameMessage(event)
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
                if (!onAllowCommandMessage(event).getOrElse { true }) {
                    event.isCancelled = true
                    return
                }
                onCommandMessage(event)
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onServerCommand(event: ServerCommandEvent) {
                if (!onAllowCommandMessage(event).getOrElse { true }) {
                    event.isCancelled = true
                    return
                }
                onCommandMessage(event)
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onRemoteServerCommand(event: RemoteServerCommandEvent) {
                if (!onAllowCommandMessage(event).getOrElse { true }) {
                    event.isCancelled = true
                    return
                }
                onCommandMessage(event)
            }
        }, plugin)
    }
}
