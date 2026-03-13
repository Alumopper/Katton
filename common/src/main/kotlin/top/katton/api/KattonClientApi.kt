@file:Suppress("unused")

package top.katton.api

import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import top.katton.platform.ClientApiHooks

private val clientBridge: ClientApiHooks.Bridge
    get() = ClientApiHooks.get()

private fun asComponent(message: Any): Component =
    message as? Component ?: Component.literal(message.toString())

val isClientRuntime: Boolean
    get() = clientBridge.isClientRuntime()

fun client(): Any? = clientBridge.rawClient()

fun clientPlayer(): Any? = clientBridge.rawPlayer()

fun clientLevel(): Any? = clientBridge.rawLevel()

fun clientTell(message: Any): Boolean = clientBridge.tell(asComponent(message))

fun runOnClient(action: () -> Unit): Boolean = clientBridge.runOnClient(Runnable(action))

fun isClientPaused(): Boolean = clientBridge.isPaused()

fun isInClientWorld(): Boolean = clientBridge.isInWorld()

fun clientX(): Double? = clientBridge.playerX()

fun clientY(): Double? = clientBridge.playerY()

fun clientZ(): Double? = clientBridge.playerZ()

fun clientPos(): Vec3? {
    val x = clientX() ?: return null
    val y = clientY() ?: return null
    val z = clientZ() ?: return null
    return Vec3(x, y, z)
}

fun clientYaw(): Float? = clientBridge.playerYaw()

fun clientPitch(): Float? = clientBridge.playerPitch()

fun clientDimensionId(): String? = clientBridge.dimensionId()

fun clientGameTime(): Long? = clientBridge.gameTime()

fun clientActionBar(message: Any): Boolean = clientBridge.actionBar(asComponent(message))

fun clientOverlay(message: Any, tinted: Boolean = false): Boolean =
    clientBridge.overlay(asComponent(message), tinted)

fun clearClientOverlay(): Boolean = clientBridge.clearOverlay()

fun clientNowPlaying(message: Any): Boolean = clientBridge.nowPlaying(asComponent(message))

fun playClientSound(soundId: String, volume: Float = 1.0f, pitch: Float = 1.0f): Boolean {
    val id = runCatching { Identifier.parse(soundId) }.getOrNull() ?: return false
    return clientBridge.playSound(id, volume, pitch)
}

fun clientTitle(message: Any): Boolean = clientBridge.title(asComponent(message))

fun clientSubtitle(message: Any): Boolean = clientBridge.subtitle(asComponent(message))

fun clientTitleTimes(fadeInTicks: Int = 10, stayTicks: Int = 70, fadeOutTicks: Int = 20): Boolean =
    clientBridge.titleTimes(fadeInTicks, stayTicks, fadeOutTicks)

fun clearClientTitle(): Boolean = clientBridge.clearTitle()

fun clientToast(title: Any, description: Any? = null): Boolean {
    val titleComponent = asComponent(title)
    val descComponent = when (description) {
        null -> Component.empty()
        is Component -> description
        else -> Component.literal(description.toString())
    }
    return clientBridge.toast(titleComponent, descComponent)
}

fun clientFps(): Int? = clientBridge.fps()

fun isClientWindowFocused(): Boolean = clientBridge.windowFocused()

fun clientScreenName(): String? = clientBridge.screenName()

fun isClientInMenu(): Boolean = clientBridge.inMenu()

fun isClientChatOpen(): Boolean = clientBridge.chatOpen()
