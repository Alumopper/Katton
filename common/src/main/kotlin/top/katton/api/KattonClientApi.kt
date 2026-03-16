/**
 * Client-side API providing access to client runtime information and operations.
 *
 * This API is designed to work on both client and server sides, but most functions
 * will return null or false when called on a dedicated server. Use these functions
 * to interact with the client player, display messages, play sounds, and query
 * client-side state.
 *
 * All functions in this module are safe to call from any side - they will gracefully
 * handle the case where the client runtime is not available.
 */

@file:Suppress("unused")

package top.katton.api

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.components.toasts.Toast
import net.minecraft.client.gui.components.toasts.TutorialToast
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.phys.Vec3

private val mc = Minecraft.getInstance()
private val player get() = mc.player
private val level get() = mc.level
private val gui get() = mc.gui

private fun asComponent(text: Any?): Component = when (text) {
    is Component -> text
    else -> Component.literal(text.toString())
}

private fun asComponentNullable(text: Any?): Component? = when (text) {
    null -> null
    is Component -> text
    else -> Component.literal(text.toString())
}

/**
 * Gets the raw Minecraft client instance.
 *
 * @return The Minecraft client instance
 */
fun client(): Minecraft = mc

/**
 * Gets the raw client player entity.
 *
 * @return The client player entity, or null if not in a world or on server
 */
fun clientPlayer(): LocalPlayer? = player

/**
 * Gets the raw client level (world).
 *
 * @return The client level instance, or null if not in a world or on server
 */
fun clientLevel(): ClientLevel? = level

/**
 * Sends a message to the client player's chat.
 *
 * @param message The message to display (will be converted to Component if not already)
 * @return true if the message was sent successfully, false otherwise
 */
fun clientTell(message: Any?, overlay: Boolean = true) =
    player?.displayClientMessage(asComponent(message), overlay)
        ?.let { true }?: false

/**
 * Executes an action on the client thread.
 *
 * This is useful for ensuring code runs on the client thread when called
 * from a different thread context.
 *
 * @param action The action to execute
 * @return true if the action was queued/executed successfully
 */
fun runOnClient(action: () -> Unit) = mc.execute(action)

/**
 * Checks if the client game is paused.
 *
 * @return true if the game is paused (e.g., pause menu open), false otherwise
 */
fun isClientPaused(): Boolean = mc.isPaused

/**
 * Checks if the client is currently in a world.
 *
 * @return true if the client has loaded a world, false otherwise
 */
fun isInClientWorld(): Boolean = level != null

/**
 * Gets the client player's X coordinate.
 *
 * @return The X coordinate, or null if not available
 */
fun clientX(): Double? = player?.x

/**
 * Gets the client player's Y coordinate.
 *
 * @return The Y coordinate, or null if not available
 */
fun clientY(): Double? = player?.y

/**
 * Gets the client player's Z coordinate.
 *
 * @return The Z coordinate, or null if not available
 */
fun clientZ(): Double? = player?.z

/**
 * Gets the client player's position as a Vec3.
 *
 * @return The position vector, or null if any coordinate is unavailable
 */
fun clientPos(): Vec3? {
    val x = clientX() ?: return null
    val y = clientY() ?: return null
    val z = clientZ() ?: return null
    return Vec3(x, y, z)
}

/**
 * Gets the client player's yaw rotation.
 *
 * @return The yaw angle in degrees, or null if not available
 */
fun clientYaw(): Float? = player?.yRot

/**
 * Gets the client player's pitch rotation.
 *
 * @return The pitch angle in degrees, or null if not available
 */
fun clientPitch(): Float? = player?.xRot

/**
 * Gets the client player's current dimension identifier.
 *
 * @return The dimension ID string (e.g., "minecraft:overworld"), or null if not available
 */
fun clientDimensionId(): String? = level?.dimension()?.registry()?.toString()

/**
 * Gets the client world's current game time.
 *
 * @return The game time in ticks, or null if not available
 */
fun clientGameTime(): Long? = level?.gameTime

/**
 * Displays a message in the client player's action bar.
 *
 * @param message The message to display
 * @return true if displayed successfully, false otherwise
 */
fun clientActionBar(message: Any): Boolean {
    val p = player ?: return false
    p.displayClientMessage(asComponent(message), true)
    return true
}

/**
 * Displays an overlay message on the client screen.
 *
 * @param message The message to display
 * @param tinted Whether to apply a background tint
 * @return true if displayed successfully, false otherwise
 */
fun clientOverlay(message: Any?, tinted: Boolean = false) =
    gui.setOverlayMessage(asComponent(message), tinted)

/**
 * Clears any active overlay message on the client.
 *
 * @return true if cleared successfully, false otherwise
 */
fun clearClientOverlay() =
    gui.setOverlayMessage(Component.empty(), false)

/**
 * Displays a "Now Playing" message for music/sound.
 *
 * @param message The message to display
 * @return true if displayed successfully, false otherwise
 */
fun clientNowPlaying(message: Any?) = gui.setNowPlaying(asComponent(message))

/**
 * Plays a sound on the client.
 *
 * @param soundId The sound identifier (e.g., "minecraft:block.note_block.pling")
 * @param volume The volume (0.0 to 1.0+)
 * @param pitch The pitch multiplier (0.5 to 2.0)
 * @return true if the sound was played successfully, false if the sound ID was invalid
 */
fun playClientSound(soundId: String, volume: Float = 1.0f, pitch: Float = 1.0f): Boolean {
    val id = Identifier.tryParse(soundId) ?: return false
    val p = player ?: return false
    p.playSound(SoundEvent.createVariableRangeEvent(id), volume, pitch)
    return true
}

/**
 * Plays a sound on the client.
 *
 * @param soundId The sound identifier (e.g., "minecraft:block.note_block.pling")
 * @param volume The volume (0.0 to 1.0+)
 * @param pitch The pitch multiplier (0.5 to 2.0)
 * @return true if the sound was played successfully, false if the sound ID was invalid
 */
fun playClientSound(soundId: Identifier, volume: Float = 1.0f, pitch: Float = 1.0f): Boolean {
    val p = player ?: return false
    p.playSound(SoundEvent.createVariableRangeEvent(soundId), volume, pitch)
    return true
}

/**
 * Displays a title on the client screen.
 *
 * @param message The title message to display
 * @return true if displayed successfully, false otherwise
 */
fun clientTitle(message: Any?) = gui.setTitle(asComponent(message))

/**
 * Displays a subtitle on the client screen.
 *
 * @param message The subtitle message to display
 * @return true if displayed successfully, false otherwise
 */
fun clientSubtitle(message: Any?) = gui.setSubtitle(asComponent(message))

/**
 * Sets the timing for title display.
 *
 * @param fadeInTicks Ticks for fade-in animation
 * @param stayTicks Ticks to stay visible
 * @param fadeOutTicks Ticks for fade-out animation
 * @return true if timing was set successfully, false otherwise
 */
fun clientTitleTimes(fadeInTicks: Int = 10, stayTicks: Int = 70, fadeOutTicks: Int = 20) = gui.setTimes(fadeInTicks, stayTicks, fadeOutTicks)

/**
 * Clears any active title on the client.
 *
 * @return true if cleared successfully, false otherwise
 */
fun clearClientTitle() = gui.clearTitles()

fun clientToast(toast: Toast) {
    Minecraft.getInstance().toastManager.addToast(toast)
}

fun clientAddSystemToast(id: SystemToast.SystemToastId, title: Any?, description: Any?){
    SystemToast.add(mc.toastManager, id, asComponent(title), asComponentNullable(description))
}

fun clientAddOrUpdateSystemToast(id: SystemToast.SystemToastId, title: Any?, description: Any?){
    SystemToast.addOrUpdate(mc.toastManager, id, asComponent(title), asComponentNullable(description))
}

fun clientHideSystemToast(id: SystemToast.SystemToastId){
    SystemToast.forceHide(mc.toastManager, id)
}

fun clientAddTutorialToast(font: Font = mc.font, icon: TutorialToast.Icons, title: Any?, description: Any?, processable: Boolean = false, timeToDisplayMs: Int = 0): TutorialToast {
    val t = TutorialToast(font, icon, asComponent(title), asComponentNullable(description), processable, timeToDisplayMs)
    clientToast(t)
    return t
}

/**
 * Gets the client's current FPS (frames per second).
 *
 * @return The current FPS, or null if not available
 */
fun clientFps(): Int = mc.fps

/**
 * Checks if the client window is focused.
 *
 * @return true if the window has focus, false otherwise
 */
fun isClientWindowFocused(): Boolean = mc.isWindowActive

fun clientSetTitle(title: String) = mc.window.setTitle(title)

/**
 * Gets the name of the currently open screen.
 *
 * @return The screen class name, or null if no screen is open
 */
fun clientScreenName(): String? = mc.screen?.javaClass?.simpleName

/**
 * Checks if the client is currently in a menu (not in-game).
 *
 * @return true if a menu screen is open, false if in-game
 */
fun isClientInMenu(): Boolean = mc.screen != null

/**
 * Checks if the chat screen is currently open.
 *
 * @return true if chat is open, false otherwise
 */
fun isClientChatOpen(): Boolean = mc.screen is ChatScreen
