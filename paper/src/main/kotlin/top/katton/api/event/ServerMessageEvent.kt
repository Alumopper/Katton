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
    val onAllowChatMessage = createAll<AllowChatMessageArg>()

    @JvmField
    val onAllowGameMessage = createAll<AllowGameMessageArg>()

    @JvmField
    val onAllowCommandMessage = createAll<AllowCommandMessageArg>()

    @JvmField
    val onChatMessage = createUnit<ChatMessageArg>()

    @JvmField
    val onGameMessage = createUnit<GameMessageArg>()

    @JvmField
    val onCommandMessage = createUnit<CommandMessageArg>()

    @JvmField
    val onServerChat = createCancellableUnit<ServerChatArg>()

    @JvmStatic
    fun initialize(plugin: JavaPlugin) {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onChat(event: AsyncChatEvent) {
                val player = PaperNmsBridge.toNmsPlayer(event.player)
                val chatMessage = PaperNmsBridge.toNmsPlayerChatMessage(event.player, event.message())
                val chatTypeBound = PaperNmsBridge.toNmsChatTypeBound(player)
                val allowArg = AllowChatMessageArg(chatMessage, player, chatTypeBound)
                if (!onAllowChatMessage(allowArg).getOrElse { true }) {
                    event.isCancelled = true
                    return
                }

                val plainText = PlainTextComponentSerializer.plainText().serialize(event.message())
                val chatArg = ServerChatArg(player, plainText, PaperNmsBridge.toNmsComponent(plainText))
                onServerChat(chatArg)
                if (chatArg.isCancelled()) {
                    event.isCancelled = true
                    return
                }

                onChatMessage(ChatMessageArg(chatMessage, player, chatTypeBound))
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onBroadcast(event: BroadcastMessageEvent) {
                val server = PaperNmsBridge.toNmsServer(plugin.server)
                val plainText = PlainTextComponentSerializer.plainText().serialize(event.message())
                val component = PaperNmsBridge.toNmsComponent(plainText)
                val arg = AllowGameMessageArg(server, component, false)
                if (!onAllowGameMessage(arg).getOrElse { true }) {
                    event.isCancelled = true
                    return
                }
                onGameMessage(GameMessageArg(server, component, false))
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
                val player = PaperNmsBridge.toNmsPlayer(event.player)
                val chatMessage = PaperNmsBridge.toNmsPlayerChatMessage(event.message)
                val source = PaperNmsBridge.toNmsCommandSourceStack(player)
                val chatTypeBound = PaperNmsBridge.toNmsChatTypeBound(source)
                val allowArg = AllowCommandMessageArg(chatMessage, source, chatTypeBound)
                if (!onAllowCommandMessage(allowArg).getOrElse { true }) {
                    event.isCancelled = true
                    return
                }
                onCommandMessage(CommandMessageArg(chatMessage, source, chatTypeBound))
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onServerCommand(event: ServerCommandEvent) {
                val server = PaperNmsBridge.toNmsServer(plugin.server)
                val chatMessage = PaperNmsBridge.toNmsPlayerChatMessage(event.command)
                val source = PaperNmsBridge.toNmsCommandSourceStack(server)
                val chatTypeBound = PaperNmsBridge.toNmsChatTypeBound(source)
                val allowArg = AllowCommandMessageArg(chatMessage, source, chatTypeBound)
                if (!onAllowCommandMessage(allowArg).getOrElse { true }) {
                    event.isCancelled = true
                    return
                }
                onCommandMessage(CommandMessageArg(chatMessage, source, chatTypeBound))
            }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            fun onRemoteServerCommand(event: RemoteServerCommandEvent) {
                val server = PaperNmsBridge.toNmsServer(plugin.server)
                val chatMessage = PaperNmsBridge.toNmsPlayerChatMessage(event.command)
                val source = PaperNmsBridge.toNmsCommandSourceStack(server)
                val chatTypeBound = PaperNmsBridge.toNmsChatTypeBound(source)
                val allowArg = AllowCommandMessageArg(chatMessage, source, chatTypeBound)
                if (!onAllowCommandMessage(allowArg).getOrElse { true }) {
                    event.isCancelled = true
                    return
                }
                onCommandMessage(CommandMessageArg(chatMessage, source, chatTypeBound))
            }
        }, plugin)
    }
}
