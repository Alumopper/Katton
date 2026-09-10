@file:Suppress("unused")
package top.katton.api.audio

import net.minecraft.core.Holder
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CompletableFuture

/** Platform scheduling hook; Paper installs entity-region scheduling. */
object BasicAudioScheduler {
    @Volatile var execute: (ServerPlayer, Runnable) -> Unit = { player, task -> player.level().server.execute(task) }
}
private fun scheduled(player: ServerPlayer, action: () -> Unit): CompletableFuture<Unit> {
    val result = CompletableFuture<Unit>()
    try { BasicAudioScheduler.execute(player, Runnable {
        try { action(); result.complete(Unit) } catch (failure: Throwable) { result.completeExceptionally(failure) }
    }) } catch (failure: Throwable) { result.completeExceptionally(failure) }
    return result.orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
}
/** %en Play a client-known sound on all three platforms; completion confirms sending only.
 * %zh 三平台均可播放客户端已有的声音；异步完成仅表示已发送。
 */
fun playBasicSound(player: ServerPlayer, soundId: String, position: Vec3? = null,
    category: SoundSource = SoundSource.MUSIC, volume: Float = 1f, pitch: Float = 1f): CompletableFuture<Unit> {
    val id = Identifier.parse(soundId)
    require(volume.isFinite() && volume >= 0 && pitch.isFinite() && pitch > 0)
    require(position == null || listOf(position.x, position.y, position.z).all { it.isFinite() && kotlin.math.abs(it) <= 6e7 })
    return scheduled(player) {
        val pos = position ?: player.position()
        player.connection.send(ClientboundSoundPacket(Holder.direct(SoundEvent.createVariableRangeEvent(id)), category,
            pos.x, pos.y, pos.z, volume, pitch, player.level().random.nextLong()))
    }
}
/** %en Stop by ID/category, not by playback instance. Null filters match all sounds.
 * %zh 按声音 ID／分类停止，无法区分相同 ID 的多个实例；空筛选匹配所有声音。 */
fun stopBasicSound(player: ServerPlayer, soundId: String? = null, category: SoundSource? = null): CompletableFuture<Unit> {
    val id = soundId?.let(Identifier::parse)
    return scheduled(player) { player.connection.send(ClientboundStopSoundPacket(id, category)) }
}
