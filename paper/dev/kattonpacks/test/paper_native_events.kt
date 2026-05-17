@file:Suppress("unused")

import top.katton.api.ServerScriptEntrypoint
import top.katton.api.event.*
import top.katton.api.event.managed.*
import org.bukkit.event.block.*

// ═══════════════════════════════════════════════════════════════
//  Paper Native Events Test
// ═══════════════════════════════════════════════════════════════

@ServerScriptEntrypoint
fun main() {

    // ─── Managed Event: 爆炸监听 ───
    registerEvent<BlockExplodeEvent>(priority = 4) { event ->
        event.blockList().forEach { block ->
            block.world.createExplosion(block.location, 0f, false)
        }
    }
}
