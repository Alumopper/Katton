package top.katton.api.event

import org.bukkit.plugin.java.JavaPlugin; import top.katton.util.createUnit
import top.katton.util.createAll
import top.katton.util.createCancellableUnit
import top.katton.util.createFirstNotNullOfOrNull
import top.katton.util.createReturnIfNot
import top.katton.util.createTriState

/** ALL PLACEHOLDERS �?Paper has no loot table event equivalents. */
object LootTableEvent {
    @JvmField val onLootTableReplace = createUnit<Any>()
    @JvmField val onLootTableModify = createUnit<Any>()
    @JvmField val onLootTableAllLoad = createUnit<Any>()
    @JvmField val onLootTableModifyDrops = createUnit<Any>()
    @JvmStatic
    fun initialize(plugin: JavaPlugin) {}
}




