@file:Suppress("unused")

package top.katton.api.registry

import net.minecraft.resources.Identifier
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import org.jetbrains.annotations.ApiStatus
import top.katton.registry.KattonRegistry
import top.katton.registry.RegisterMode
import top.katton.registry.id
import top.katton.util.ReflectUtil

/**
 * CreativeModeTab registration API.
 *
 * This module provides functions to register custom CreativeModeTabs with hot-reload support.
 */

/**
 * Registers a native CreativeModeTab with hot-reload support.
 *
 * @param id Tab identifier (e.g., "mymod:custom_tab")
 * @param registerMode Registration mode (GLOBAL, RELOADABLE, or AUTO)
 * @param tabFactory Factory function to create the CreativeModeTab instance
 * @return The registered KattonCreativeTabEntry
 *
 * @example
 * ```kotlin
 * registerNativeCreativeTab("mymod:custom_tab") {
 *     CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
 *         .title(Component.literal("Custom Tab"))
 *         .icon { ItemStack(Items.DIAMOND) }
 *         .displayItems { _, items -> items.accept(Items.DIAMOND) }
 *         .build()
 * }
 * ```
 */
@ApiStatus.Experimental
fun registerNativeCreativeTab(
    id: String,
    registerMode: RegisterMode = RegisterMode.AUTO,
    tabFactory: () -> CreativeModeTab
): KattonRegistry.KattonCreativeTabEntry = registerNativeCreativeTab(id(id), registerMode, tabFactory)

/**
 * Registers a native CreativeModeTab with hot-reload support (Identifier overload).
 */
@ApiStatus.Experimental
fun registerNativeCreativeTab(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.AUTO,
    tabFactory: () -> CreativeModeTab
): KattonRegistry.KattonCreativeTabEntry {
    return KattonRegistry.CREATIVE_TABS.newNative(id, registerMode, tabFactory)
}

/**
 * Reorders a creative tab within the global tabs list.
 *
 * Uses reflection on Minecraft's internal `CreativeModeTabs` to move a tab
 * after or before another tab. If reflection fails, the tab stays at its
 * default position.
 *
 * @param tab The tab to reorder
 * @param after Move after this tab identifier (e.g., "minecraft:building_blocks")
 * @param before Move before this tab identifier
 */
@ApiStatus.Experimental
fun reorderCreativeTab(tab: CreativeModeTab, after: String? = null, before: String? = null) {
    val tabsClass = ReflectUtil.getPossibleClassFromNames(
        "net.minecraft.world.item.CreativeModeTabs"
    ).getOrNull() ?: return

    val vh = ReflectUtil.findFirstVarHandle(tabsClass, MutableList::class.java) ?: return

    @Suppress("UNCHECKED_CAST")
    val tabsList = vh.get() as? MutableList<CreativeModeTab> ?: return

    val currentIndex = tabsList.indexOf(tab)
    if (currentIndex < 0) return

    tabsList.removeAt(currentIndex)

    when {
        after != null -> {
            val afterId = Identifier.parse(after)
            val targetIndex = tabsList.indexOfFirst { tabEntry ->
                getTabIdentifier(tabEntry) == afterId
            }
            if (targetIndex >= 0) tabsList.add(targetIndex + 1, tab)
            else tabsList.add(tab)
        }
        before != null -> {
            val beforeId = Identifier.parse(before)
            val targetIndex = tabsList.indexOfFirst { tabEntry ->
                getTabIdentifier(tabEntry) == beforeId
            }
            if (targetIndex >= 0) tabsList.add(targetIndex, tab)
            else tabsList.add(0, tab)
        }
    }
}

/**
 * Extracts the Identifier from a registered CreativeModeTab via the registry.
 */
private fun getTabIdentifier(tab: CreativeModeTab): Identifier? {
    return net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab)
}