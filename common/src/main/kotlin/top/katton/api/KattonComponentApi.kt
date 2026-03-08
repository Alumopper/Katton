package top.katton.api

import net.minecraft.network.chat.Component

operator fun Component?.plus(component: Component?): Component {
    return Component.empty().append(this ?: Component.empty()).append(component ?: Component.empty())
}

operator fun String?.plus(component: Component?): Component {
    return Component.literal(this ?: "").append(component ?: Component.empty())
}

operator fun Component?.plus(string: String?): Component {
    return Component.literal(string ?: "").append(this ?: Component.empty())
}