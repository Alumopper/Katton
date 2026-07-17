package top.katton.compat

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.components.toasts.ToastManager
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

internal fun setClientOverlay(gui: Gui, message: Component, tinted: Boolean) {
    gui.setOverlayMessage(message, tinted)
}

internal fun setClientNowPlaying(gui: Gui, message: Component) {
    gui.setNowPlaying(message)
}

internal fun setClientTitle(gui: Gui, message: Component) {
    gui.setTitle(message)
}

internal fun setClientSubtitle(gui: Gui, message: Component) {
    gui.setSubtitle(message)
}

internal fun setClientTitleTimes(gui: Gui, fadeInTicks: Int, stayTicks: Int, fadeOutTicks: Int) {
    gui.setTimes(fadeInTicks, stayTicks, fadeOutTicks)
}

internal fun clearClientTitles(gui: Gui) {
    gui.clearTitles()
}

internal fun clientToastManager(minecraft: Minecraft): ToastManager = minecraft.toastManager

internal fun currentScreen(minecraft: Minecraft): Screen? = minecraft.screen

internal fun setClientScreen(minecraft: Minecraft, screen: Screen?) {
    minecraft.setScreen(screen)
}
