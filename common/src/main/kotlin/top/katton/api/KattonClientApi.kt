/**
 * %en
 * Client-side API providing access to client runtime information and operations.
 *
 * This API is designed to work on both client and server sides, but most functions
 * will return null or false when called on a dedicated server. Use these functions
 * to interact with the client player, display messages, play sounds, and query
 * client-side state.
 *
 * All functions in this module are safe to call from any side - they will gracefully
 * handle the case where the client runtime is not available.
 *
 * %zh
 * 提供客户端运行时信息和操作能力的客户端 API。
 *
 * 该 API 可在客户端和服务端代码中调用，但在专用服务端上调用时，大多数函数会返回 null 或 false。
 * 可通过这些函数访问客户端玩家、显示消息、播放声音，以及查询客户端状态。
 *
 * 本模块中的所有函数都可以从任意侧安全调用；当客户端运行时不可用时，它们会自行处理并返回合适的结果。
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
 * %en
 * Gets the raw Minecraft client instance.
 *
 * %zh
 * 获取原始 Minecraft 客户端实例。
 * @return
 * %en Minecraft client instance
 * %zh Minecraft 客户端实例。
 */
fun client(): Minecraft = mc

/**
 * %en
 * Gets the raw client player entity.
 *
 * %zh
 * 获取原始客户端玩家实体。
 * @return
 * %en client player entity, or null if not in a world or on server
 * %zh 客户端玩家实体；如果未进入世界或在服务端调用，则返回 null。
 */
fun clientPlayer(): LocalPlayer? = player

/**
 * %en
 * Gets the raw client level (world).
 *
 * %zh
 * 获取原始客户端世界。
 * @return
 * %en client level instance, or null if not in a world or on server
 * %zh 客户端世界实例；如果未进入世界或在服务端调用，则返回 null。
 */
fun clientLevel(): ClientLevel? = level

/**
 * %en
 * Sends a message to the client player's chat.
 *
 * %zh
 * 向客户端玩家聊天栏发送消息。
 * @param message
 * %en The message to display (will be converted to Component if not already)
 * %zh 要显示的消息；如果不是 Component，会自动转换。
 * @return
 * %en if the message was sent successfully, false otherwise
 * %zh 如果消息发送成功则返回 true，否则返回 false。
 */
fun clientTell(message: Any?, overlay: Boolean = true): Boolean {
    player?: return false
    if(overlay){
        player!!.sendOverlayMessage(asComponent(message))
    }else {
        player!!.sendSystemMessage(asComponent(message))
    }
    return true
}

/**
 * %en
 * Executes an action on the client thread.
 *
 * This is useful for ensuring code runs on the client thread when called
 * from a different thread context.
 *
 * %zh
 * 在客户端线程上执行一个动作。
 *
 * 当调用方位于其他线程上下文时，可用它确保代码切回客户端线程执行。
 * @param action
 * %en The action to execute
 * %zh 要执行的动作。
 * @return
 * %en if the action was queued/executed successfully
 * %zh 如果动作成功排队或执行则返回 true。
 */
fun runOnClient(action: () -> Unit) = mc.execute(action)

/**
 * %en
 * Checks if the client game is paused.
 *
 * %zh
 * 检查客户端游戏是否暂停。
 * @return
 * %en if the game is paused (e.g., pause menu open), false otherwise
 * %zh 如果游戏已暂停（例如打开暂停菜单）则返回 true，否则返回 false。
 */
fun isClientPaused(): Boolean = mc.isPaused

/**
 * %en
 * Checks if the client is currently in a world.
 *
 * %zh
 * 检查客户端当前是否已经进入世界。
 * @return
 * %en if the client has loaded a world, false otherwise
 * %zh 如果客户端已加载世界则返回 true，否则返回 false。
 */
fun isInClientWorld(): Boolean = level != null

/**
 * %en
 * Gets the client player's X coordinate.
 *
 * %zh
 * 获取客户端玩家的 X 坐标。
 * @return
 * %en X coordinate, or null if not available
 * %zh X 坐标；如果不可用则返回 null。
 */
fun clientX(): Double? = player?.x

/**
 * %en
 * Gets the client player's Y coordinate.
 *
 * %zh
 * 获取客户端玩家的 Y 坐标。
 * @return
 * %en Y coordinate, or null if not available
 * %zh Y 坐标；如果不可用则返回 null。
 */
fun clientY(): Double? = player?.y

/**
 * %en
 * Gets the client player's Z coordinate.
 *
 * %zh
 * 获取客户端玩家的 Z 坐标。
 * @return
 * %en Z coordinate, or null if not available
 * %zh Z 坐标；如果不可用则返回 null。
 */
fun clientZ(): Double? = player?.z

/**
 * %en
 * Gets the client player's position as a Vec3.
 *
 * %zh
 * 以 Vec3 形式获取客户端玩家位置。
 * @return
 * %en position vector, or null if any coordinate is unavailable
 * %zh 位置向量；如果任一坐标不可用则返回 null。
 */
fun clientPos(): Vec3? {
    val x = clientX() ?: return null
    val y = clientY() ?: return null
    val z = clientZ() ?: return null
    return Vec3(x, y, z)
}

/**
 * %en
 * Gets the client player's yaw rotation.
 *
 * %zh
 * 获取客户端玩家的 yaw 旋转角。
 * @return
 * %en yaw angle in degrees, or null if not available
 * %zh 以度为单位的 yaw 角；如果不可用则返回 null。
 */
fun clientYaw(): Float? = player?.yRot

/**
 * %en
 * Gets the client player's pitch rotation.
 *
 * %zh
 * 获取客户端玩家的 pitch 旋转角。
 * @return
 * %en pitch angle in degrees, or null if not available
 * %zh 以度为单位的 pitch 角；如果不可用则返回 null。
 */
fun clientPitch(): Float? = player?.xRot

/**
 * %en
 * Gets the client player's current dimension identifier.
 *
 * %zh
 * 获取客户端玩家当前所在维度的标识符。
 * @return
 * %en dimension ID string (e.g., "minecraft:overworld"), or null if not available
 * %zh 维度 ID 字符串，例如 "minecraft:overworld"；如果不可用则返回 null。
 */
fun clientDimensionId(): String? = level?.dimension()?.registry()?.toString()

/**
 * %en
 * Gets the client world's current game time.
 *
 * %zh
 * 获取客户端世界当前游戏时间。
 * @return
 * %en game time in ticks, or null if not available
 * %zh 以 tick 为单位的游戏时间；如果不可用则返回 null。
 */
fun clientGameTime(): Long? = level?.gameTime

/**
 * %en
 * Displays a message in the client player's action bar.
 *
 * %zh
 * 在客户端玩家的 action bar 中显示消息。
 * @param message
 * %en The message to display
 * %zh 要显示的消息。
 * @return
 * %en if displayed successfully, false otherwise
 * %zh 如果显示成功则返回 true，否则返回 false。
 */
fun clientActionBar(message: Any): Boolean {
    val p = player ?: return false
    p.sendOverlayMessage(asComponent(message))
    return true
}

/**
 * %en
 * Displays an overlay message on the client screen.
 *
 * %zh
 * 在客户端屏幕上显示覆盖消息。
 * @param message
 * %en The message to display
 * %zh 要显示的消息。
 * @param tinted
 * %en Whether to apply a background tint
 * %zh 是否应用背景着色。
 * @return
 * %en if displayed successfully, false otherwise
 * %zh 如果显示成功则返回 true，否则返回 false。
 */
fun clientOverlay(message: Any?, tinted: Boolean = false) =
    gui.setOverlayMessage(asComponent(message), tinted)

/**
 * %en
 * Clears any active overlay message on the client.
 *
 * %zh
 * 清除客户端当前活动的覆盖消息。
 * @return
 * %en if cleared successfully, false otherwise
 * %zh 如果清除成功则返回 true，否则返回 false。
 */
fun clearClientOverlay() =
    gui.setOverlayMessage(Component.empty(), false)

/**
 * %en
 * Displays a "Now Playing" message for music/sound.
 *
 * %zh
 * 为音乐或声音显示 "Now Playing" 消息。
 * @param message
 * %en The message to display
 * %zh 要显示的消息。
 * @return
 * %en if displayed successfully, false otherwise
 * %zh 如果显示成功则返回 true，否则返回 false。
 */
fun clientNowPlaying(message: Any?) = gui.setNowPlaying(asComponent(message))

/**
 * %en
 * Plays a sound on the client.
 *
 * %zh
 * 在客户端播放声音。
 * @param soundId
 * %en The sound identifier (e.g., "minecraft:block.note_block.pling")
 * %zh 声音标识符，例如 "minecraft:block.note_block.pling"。
 * @param volume
 * %en The volume (0.0 to 1.0+)
 * %zh 音量，范围通常为 0.0 到 1.0 以上。
 * @param pitch
 * %en The pitch multiplier (0.5 to 2.0)
 * %zh 音高倍率，通常为 0.5 到 2.0。
 * @return
 * %en if the sound was played successfully, false if the sound ID was invalid
 * %zh 如果声音播放成功则返回 true；如果声音 ID 无效则返回 false。
 */
fun playClientSound(soundId: String, volume: Float = 1.0f, pitch: Float = 1.0f): Boolean {
    val id = Identifier.tryParse(soundId) ?: return false
    val p = player ?: return false
    p.playSound(SoundEvent.createVariableRangeEvent(id), volume, pitch)
    return true
}

/**
 * %en
 * Plays a sound on the client.
 *
 * %zh
 * 在客户端播放声音。
 * @param soundId
 * %en The sound identifier (e.g., "minecraft:block.note_block.pling")
 * %zh 声音标识符，例如 "minecraft:block.note_block.pling"。
 * @param volume
 * %en The volume (0.0 to 1.0+)
 * %zh 音量，范围通常为 0.0 到 1.0 以上。
 * @param pitch
 * %en The pitch multiplier (0.5 to 2.0)
 * %zh 音高倍率，通常为 0.5 到 2.0。
 * @return
 * %en if the sound was played successfully, false if the sound ID was invalid
 * %zh 如果声音播放成功则返回 true；如果声音 ID 无效则返回 false。
 */
fun playClientSound(soundId: Identifier, volume: Float = 1.0f, pitch: Float = 1.0f): Boolean {
    val p = player ?: return false
    p.playSound(SoundEvent.createVariableRangeEvent(soundId), volume, pitch)
    return true
}

/**
 * %en
 * Displays a title on the client screen.
 *
 * %zh
 * 在客户端屏幕上显示标题。
 * @param message
 * %en The title message to display
 * %zh 要显示的标题消息。
 * @return
 * %en if displayed successfully, false otherwise
 * %zh 如果显示成功则返回 true，否则返回 false。
 */
fun clientTitle(message: Any?) = gui.setTitle(asComponent(message))

/**
 * %en
 * Displays a subtitle on the client screen.
 *
 * %zh
 * 在客户端屏幕上显示副标题。
 * @param message
 * %en The subtitle message to display
 * %zh 要显示的副标题消息。
 * @return
 * %en if displayed successfully, false otherwise
 * %zh 如果显示成功则返回 true，否则返回 false。
 */
fun clientSubtitle(message: Any?) = gui.setSubtitle(asComponent(message))

/**
 * %en
 * Sets the timing for title display.
 *
 * %zh
 * 设置标题显示的时间参数。
 * @param fadeInTicks
 * %en Ticks for fade-in animation
 * %zh 淡入动画持续的 tick 数。
 * @param stayTicks
 * %en Ticks to stay visible
 * %zh 保持可见的 tick 数。
 * @param fadeOutTicks
 * %en Ticks for fade-out animation
 * %zh 淡出动画持续的 tick 数。
 * @return
 * %en if timing was set successfully, false otherwise
 * %zh 如果时间设置成功则返回 true，否则返回 false。
 */
fun clientTitleTimes(fadeInTicks: Int = 10, stayTicks: Int = 70, fadeOutTicks: Int = 20) = gui.setTimes(fadeInTicks, stayTicks, fadeOutTicks)

/**
 * %en
 * Clears any active title on the client.
 *
 * %zh
 * 清除客户端当前活动的标题。
 * @return
 * %en if cleared successfully, false otherwise
 * %zh 如果清除成功则返回 true，否则返回 false。
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
 * %en
 * Gets the client's current FPS (frames per second).
 *
 * %zh
 * 获取客户端当前 FPS（每秒帧数）。
 * @return
 * %en current FPS, or null if not available
 * %zh 当前 FPS。
 */
fun clientFps(): Int = mc.fps

/**
 * %en
 * Checks if the client window is focused.
 *
 * %zh
 * 检查客户端窗口是否获得焦点。
 * @return
 * %en if the window has focus, false otherwise
 * %zh 如果窗口获得焦点则返回 true，否则返回 false。
 */
fun isClientWindowFocused(): Boolean = mc.isWindowActive

fun clientSetTitle(title: String) = mc.window.setTitle(title)

/**
 * %en
 * Gets the name of the currently open screen.
 *
 * %zh
 * 获取当前打开屏幕的名称。
 * @return
 * %en screen class name, or null if no screen is open
 * %zh 屏幕类名；如果没有打开屏幕则返回 null。
 */
fun clientScreenName(): String? = mc.screen?.javaClass?.simpleName

/**
 * %en
 * Checks if the client is currently in a menu (not in-game).
 *
 * %zh
 * 检查客户端当前是否位于菜单中，而不是游戏内。
 * @return
 * %en if a menu screen is open, false if in-game
 * %zh 如果打开了菜单屏幕则返回 true；如果在游戏内则返回 false。
 */
fun isClientInMenu(): Boolean = mc.screen != null

/**
 * %en
 * Checks if the chat screen is currently open.
 *
 * %zh
 * 检查聊天屏幕当前是否打开。
 * @return
 * %en if chat is open, false otherwise
 * %zh 如果聊天屏幕已打开则返回 true，否则返回 false。
 */
fun isClientChatOpen(): Boolean = mc.screen is ChatScreen
