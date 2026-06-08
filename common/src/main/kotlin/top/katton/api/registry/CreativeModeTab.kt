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
 * %en
 * CreativeModeTab registration API.
 *
 * This module provides functions to register custom CreativeModeTabs with hot-reload support.
 *
 * %zh
 * CreativeModeTab 注册 API。
 * 该模块提供在热重载支持下注册自定义 CreativeModeTab 的函数。
 */

/**
 * %en
 * Registers a native CreativeModeTab with hot-reload support.
 *
 * %zh
 * 注册原生 CreativeModeTab，并支持热重载。
 * @param id
 * %en Tab identifier (e.g., "mymod:custom_tab")
 * %zh 标签页标识符，例如 "mymod:custom_tab"。
 * @param registerMode
 * %en Registration mode (GLOBAL, WORLD, or RELOADABLE)
 * %zh 注册模式（GLOBAL、WORLD 或 RELOADABLE）。
 * @param tabFactory
 * %en Factory function to create the CreativeModeTab instance
 * %zh 创建 CreativeModeTab 实例的工厂函数。
 * @return
 * %en registered KattonCreativeTabEntry
 * %zh 已注册的 KattonCreativeTabEntry。
 * @example
 * %en
 * ```kotlin
 * registerNativeCreativeTab("mymod:custom_tab") {
 *     CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
 *         .title(Component.literal("Custom Tab"))
 *         .icon { ItemStack(Items.DIAMOND) }
 *         .displayItems { _, items -> items.accept(Items.DIAMOND) }
 *         .build()
 * }
 * ```
 * %zh 示例代码见英文部分。
 */
@ApiStatus.Experimental
fun registerNativeCreativeTab(
    id: String,
    registerMode: RegisterMode = RegisterMode.WORLD,
    tabFactory: () -> CreativeModeTab
): KattonRegistry.KattonCreativeTabEntry = registerNativeCreativeTab(id(id), registerMode, tabFactory)

/**
 * %en
 * Registers a native CreativeModeTab with hot-reload support (Identifier overload).
 *
 * %zh
 * 注册原生 CreativeModeTab，并支持热重载（Identifier 重载）。
 */
@ApiStatus.Experimental
fun registerNativeCreativeTab(
    id: Identifier,
    registerMode: RegisterMode = RegisterMode.WORLD,
    tabFactory: () -> CreativeModeTab
): KattonRegistry.KattonCreativeTabEntry {
    return KattonRegistry.CREATIVE_TABS.newNative(id, registerMode, tabFactory)
}

/**
 * %en
 * Reorders a creative tab within the global tabs list.
 *
 * Uses reflection on Minecraft's internal `CreativeModeTabs` to move a tab
 * after or before another tab. If reflection fails, the tab stays at its
 * default position.
 *
 * %zh
 * 在全局标签页列表中重新排序某个 CreativeModeTab。
 * 该方法会反射 Minecraft 内部的 `CreativeModeTabs`，将标签页移动到其他标签页之后或之前。
 * 如果反射失败，标签页会保持在默认位置。
 * @param tab
 * %en The tab to reorder
 * %zh 需要重新排序的标签页。
 * @param after
 * %en Move after this tab identifier (e.g., "minecraft:building_blocks")
 * %zh 移动到该标签页之后，例如 "minecraft:building_blocks"。
 * @param before
 * %en Move before this tab identifier
 * %zh 移动到该标签页之前。
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
 * %en
 * Extracts the Identifier from a registered CreativeModeTab via the registry.
 *
 * %zh
 * 通过注册表从已注册的 CreativeModeTab 中提取 Identifier。
 */
private fun getTabIdentifier(tab: CreativeModeTab): Identifier? {
    return net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab)
}
