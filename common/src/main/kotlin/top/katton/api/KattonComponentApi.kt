package top.katton.api

import net.minecraft.network.chat.Component

/**
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
 */

/**
 * Combines two nullable Components into a new Component.
 *
 * If either Component is null, it is treated as an empty Component.
 *
 * @param component The Component to append
 * @return A new Component containing both texts
 */
operator fun Component?.plus(component: Component?): Component {
    return Component.empty().append(this ?: Component.empty()).append(component ?: Component.empty())
}

/**
 * Combines a nullable String with a nullable Component.
 *
 * The string is converted to a literal Component before appending.
 *
 * @param component The Component to append
 * @return A new Component containing both texts
 */
operator fun String?.plus(component: Component?): Component {
    return Component.literal(this ?: "").append(component ?: Component.empty())
}

/**
 * Combines a nullable Component with a nullable String.
 *
 * The string is converted to a literal Component and prepended to the Component.
 *
 * @param string The string to prepend
 * @return A new Component containing both texts
 */
operator fun Component?.plus(string: String?): Component {
    return Component.literal(string ?: "").append(this ?: Component.empty())
}