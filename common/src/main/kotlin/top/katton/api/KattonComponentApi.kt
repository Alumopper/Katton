package top.katton.api

import net.minecraft.network.chat.Component

/**
 * %en
 * Component (text) API providing extension operators for text manipulation.
 *
 * This module provides convenient operator overloads for combining text Components
 * and strings, making it easier to build complex text messages.
 *
 * Example usage:
 * ```kotlin
 * val text = "Hello " + Component.literal("World") + "!"
 * // Result: Component containing "Hello World!"
 * ```
 *
 * %zh
 * 提供用于文本拼接的 Component（文本）扩展运算符。
 *
 * 这个模块提供了便捷的运算符重载，用来组合 Component 和字符串，更容易构造复杂文本消息。
 *
 * 示例：
 * ```kotlin
 * val text = "Hello " + Component.literal("World") + "!"
 * // 结果：包含 "Hello World!" 的 Component
 * ```
 */

/**
 * %en
 * Combines two nullable Components into a new Component.
 *
 * If either Component is null, it is treated as an empty Component.
 *
 * %zh
 * 将两个可空的 Component 合并为一个新的 Component。
 *
 * 如果任意一侧为 null，就把它当作空 Component 处理。
 *
 * @param component
 * %en The Component to append
 * %zh 要追加的 Component。
 * @return
 * %en new Component containing both texts
 * %zh 包含两段文本的新 Component。
 */
operator fun Component?.plus(component: Component?): Component {
    return Component.empty().append(this ?: Component.empty()).append(component ?: Component.empty())
}

/**
 * %en
 * Combines a nullable String with a nullable Component.
 *
 * The string is converted to a literal Component before appending.
 *
 * %zh
 * 将可空字符串与可空 Component 组合。
 *
 * 字符串会先转换成字面量 Component，再追加到结果中。
 *
 * @param component
 * %en The Component to append
 * %zh 要追加的 Component。
 * @return
 * %en new Component containing both texts
 * %zh 包含两段文本的新 Component。
 */
operator fun String?.plus(component: Component?): Component {
    return Component.literal(this ?: "").append(component ?: Component.empty())
}

/**
 * %en
 * Combines a nullable Component with a nullable String.
 *
 * The string is converted to a literal Component and prepended to the Component.
 *
 * %zh
 * 将可空 Component 与可空字符串组合。
 *
 * 字符串会先转换成字面量 Component，再作为前缀加到 Component 前面。
 *
 * @param string
 * %en The string to prepend
 * %zh 要作为前缀的字符串。
 * @return
 * %en new Component containing both texts
 * %zh 包含两段文本的新 Component。
 */
operator fun Component?.plus(string: String?): Component {
    return Component.literal(string ?: "").append(this ?: Component.empty())
}
