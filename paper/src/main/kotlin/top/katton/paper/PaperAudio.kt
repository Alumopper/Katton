package top.katton.paper

import top.katton.api.audio.BasicAudioScheduler

object PaperAudio {
    @JvmStatic fun install(plugin: KattonPaperPlugin) {
        BasicAudioScheduler.execute = { player, task ->
            val bukkit = checkNotNull(player.toBukkit()) { "Player is unavailable" }
            checkNotNull(bukkit.scheduler.run(plugin, { task.run() }, null)) { "Player retired before sound could be scheduled" }
        }
    }
}
