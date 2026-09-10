import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import top.katton.api.ServerPhase
import top.katton.api.ServerScriptEntrypoint
import top.katton.api.audio.*
import top.katton.registry.registerCommand
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

@ServerScriptEntrypoint(ServerPhase.READY)
fun registerEltawCommands() {
    // 在入口捕获包版本，命令执行时不再重新解析脚本上下文。
    val source = AudioSource.packFile("audio/Fl00t - Eltaw.mp3")
    val players = mutableMapOf<UUID, RemoteAudioHandle>()

    fun execute(context: CommandContext<CommandSourceStack>, play: Boolean = false,
                action: (RemoteAudioHandle) -> CompletableFuture<*>): Int {
        val command = context.source
        val player = command.playerOrException
        return try {
            players.entries.removeIf { it.value.snapshot.state == AudioState.CLOSED }
            val handle = if (play) {
                players.remove(player.uuid)?.close()
                playPlayerAudio(player, source, AudioOptions(volume = 0.5f)).also {
                    players[player.uuid] = it
                }
            } else players[player.uuid] ?: error("请先执行 /eltaw play")
            action(handle).whenComplete { _, error ->
                command.server.execute {
                    if (error != null) {
                        command.sendFailure(Component.literal("音频操作失败：" + (error.cause ?: error).message))
                    } else {
                        val state = handle.snapshot
                        val duration = state.duration?.inWholeSeconds?.toString() ?: "?"
                        command.sendSuccess({ Component.literal(
                            "Eltaw: " + state.state + " | " + state.position.inWholeSeconds + "/" + duration +
                                "s | " + state.playbackRate + "x | 音量 " + state.volume +
                                (state.error?.let { " | " + it } ?: "")
                        ) }, false)
                    }
                }
            }
            // 只确认请求已提交，不阻塞服务端线程等待客户端回复。
            1
        } catch (error: Exception) {
            command.sendFailure(Component.literal(error.message ?: "音频操作失败"))
            0
        }
    }

    registerCommand("eltaw") {
        // 普通玩家只控制自己的音频；控制台不能执行。
        requires { it.entity is net.minecraft.server.level.ServerPlayer }
        executes {
            it.source.sendSuccess({ Component.literal(
                "/eltaw play | pause | resume | stop | speed <0.25-4> | volume <0-1> | seek <秒> | status"
            ) }, false)
            1
        }
        literal("play") { executes { execute(it, play = true) { h -> h.refresh() } } }
        literal("pause") { executes { execute(it) { h -> h.pause() } } }
        literal("resume") { executes { execute(it) { h -> h.resume() } } }
        literal("stop") { executes { execute(it) { h -> h.stop() } } }
        literal("status") { executes { execute(it) { h -> h.refresh() } } }
        literal("speed") {
            argument("rate", FloatArgumentType.floatArg(0.25f, 4f)) {
                executes { ctx -> execute(ctx) { it.setPlaybackRate(FloatArgumentType.getFloat(ctx, "rate")) } }
            }
        }
        literal("volume") {
            argument("value", FloatArgumentType.floatArg(0f, 1f)) {
                executes { ctx -> execute(ctx) { it.setVolume(FloatArgumentType.getFloat(ctx, "value")) } }
            }
        }
        literal("seek") {
            argument("seconds", DoubleArgumentType.doubleArg(0.0)) {
                executes { ctx -> execute(ctx) { it.seek(DoubleArgumentType.getDouble(ctx, "seconds").seconds) } }
            }
        }
    }
}
