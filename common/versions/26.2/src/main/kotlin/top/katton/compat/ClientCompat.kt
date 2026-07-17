package top.katton.compat

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.components.toasts.ToastManager
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

internal fun setClientOverlay(gui: Gui, message: Component, tinted: Boolean) {
    gui.hud.setOverlayMessage(message, tinted)
}

internal fun setClientNowPlaying(gui: Gui, message: Component) {
    gui.hud.setNowPlaying(message)
}

internal fun setClientTitle(gui: Gui, message: Component) {
    gui.hud.setTitle(message)
}

internal fun setClientSubtitle(gui: Gui, message: Component) {
    gui.hud.setSubtitle(message)
}

internal fun setClientTitleTimes(gui: Gui, fadeInTicks: Int, stayTicks: Int, fadeOutTicks: Int) {
    gui.hud.setTimes(fadeInTicks, stayTicks, fadeOutTicks)
}

internal fun clearClientTitles(gui: Gui) {
    gui.hud.clearTitles()
}

internal fun clientToastManager(minecraft: Minecraft): ToastManager = minecraft.gui.toastManager()

internal fun currentScreen(minecraft: Minecraft): Screen? = minecraft.gui.screen()

internal fun setClientScreen(minecraft: Minecraft, screen: Screen?) {
    minecraft.gui.setScreen(screen)
}
