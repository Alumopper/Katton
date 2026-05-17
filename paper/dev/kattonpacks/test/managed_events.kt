@file:Suppress("unused")

import top.katton.api.ServerScriptEntrypoint
import top.katton.api.event.*
import top.katton.api.event.managed.*
import top.katton.Katton
import org.bukkit.event.player.*
import org.bukkit.event.block.*
import org.bukkit.event.entity.*
import org.bukkit.event.EventPriority.*
import net.minecraft.network.chat.Component

// ═══════════════════════════════════════════════════════════════
//  Katton Managed Event Listener Test
//  ═══════════════════════════════════════════════════════════════

@ServerScriptEntrypoint
fun main() {
    // ─── 1. Katton 包装事件 ───
    ServerEvent.onServerStarted += { arg ->
        arg.server.sendSystemMessage(
            Component.literal("[Katton Test] Server started! Managed events test pack loaded.")
        )
    }

    ServerPlayerEvent.onPlayerJoin += { arg ->
        val name = arg.player.name.string
        arg.player.sendSystemMessage(
            Component.literal("§aWelcome $name! Katton managed events are active.")
        )
    }

    ServerPlayerEvent.onPlayerLeave += { arg ->
        Katton.server?.sendSystemMessage(
            Component.literal("§7${arg.player.name.string} left the server.")
        )
    }

    // ─── 2. Managed Event ───
    val moveHandle = registerEvent<PlayerMoveEvent>(priority = 4) { event ->
    }

    // ─── 3. Managed Event ───
    registerEvent<BlockBreakEvent>(priority = 4) { event ->
        if (event.block.type.name.contains("DIAMOND")) {
            event.player.sendMessage("§c⚠ Diamond blocks are protected by Katton!")
            event.isCancelled = true
        }
    }

    // ─── 4. Managed Event ───
    registerEvent<EntityDamageEvent>(priority = 2) { event ->
        if (event.entity is org.bukkit.entity.Player) {
            val player = event.entity as org.bukkit.entity.Player
            if (event.damage > 10.0) {
                player.sendMessage("§e⚠ You took ${event.damage} damage from ${event.cause}!")
            }
        }
    }

    // ─── 5. Managed Event ───
    registerEvent<AsyncPlayerChatEvent>(priority = 4) { event ->
        if (event.message.contains("badword", ignoreCase = true)) {
            event.player.sendMessage("§c⚠ Please keep chat clean!")
            event.isCancelled = true
        }
    }

    // ─── 6. Managed Event ───
    registerEvent<PlayerInteractEvent>(priority = 3) { event ->
        if (event.action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            val block = event.clickedBlock ?: return@registerEvent
            if (block.type == org.bukkit.Material.CHEST) {
                event.player.sendMessage("§6📦 You opened a chest!")
            }
        }
    }

    // ─── 7. Katton 消息事件 ───
    ServerMessageEvent.onAllowChatMessage += { arg ->
        val playerName = arg.sender.name.string
        if (playerName == "Notch") {
            arg.sender.sendSystemMessage(
                Component.literal("§6Welcome back, Notch!")
            )
        }
        true
    }

    ServerMessageEvent.onServerChat += { arg ->
        if (arg.message.contains("secret")) {
            arg.cancel()
            arg.player.sendSystemMessage(
                Component.literal("§c⚠ Message blocked by Katton filter.")
            )
        }
    }

    // ─── 8. Katton 实体事件 ───
    ServerLivingEntityEvent.onLivingHurt += { arg ->
        if (arg.entity is net.minecraft.world.entity.player.Player && arg.amount > 10f) {
            (arg.entity as net.minecraft.world.entity.player.Player).sendSystemMessage(
                Component.literal("§cOuch! That hurt: ${arg.amount} damage")
            )
        }
    }

    ServerLivingEntityEvent.onAfterDeath += { arg ->
        arg.entity.level().server?.sendSystemMessage(
            Component.literal("§7${arg.entity.name.string} has died.")
        )
    }

    // ─── 9. 注册统计 ───
    ServerEvent.onServerStarted += {
        it.server.sendSystemMessage(
            Component.literal("""
                §a════════════════════════════════
                §a  Katton Test Pack Loaded
                §a  - 8 managed event listeners
                §a  - 4 Katton event handlers
                §a  - Scope: WORLD (auto-cleanup on reload)
                §a════════════════════════════════
            """.trimIndent())
        )
    }
}
