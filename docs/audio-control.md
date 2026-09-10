# Audio control / 音频控制

Import `top.katton.api.audio.*`. Fabric and NeoForge provide the full player on
Minecraft 26.1.2 and 26.2. Paper/Folia provides `playBasicSound` and
`stopBasicSound` for sound IDs already available to the player's client.

Fabric / NeoForge 支持完整播放器；Paper / Folia 只提供基础声音播放与停止，
不能直接播放包内文件，也不模拟暂停、跳转、倍速或精确实例停止。

## Sources / 音频来源

- `AudioSource.sound("minecraft:music_disc.cat")`: resolves one weighted sound
  using the active resource pack. Looping and seeking keep that selected sound.
- `AudioSource.resource("minecraft:sounds/music/game/calm1.ogg")`: exact resource.
- `AudioSource.packFile("custom/music.blob")`: current pack's relative path,
  captured from a managed script context, including during candidate entrypoints.

音频按文件内容识别，支持 MP3、PCM WAV、Ogg Vorbis 和 FLAC，无需指定扩展名。
支持单／双声道、8–192 kHz；FLAC 支持 8/16/24 位。任意包内普通文件随包分发，
不需要新增 manifest 字段。`.kt` / `.java` 仍按现有规则作为源码，`libs/` 下的
直接 JAR 仍作为库；不要给音频使用这些保留用途的路径。越界路径和符号链接不支持。
本地 `.kattonpack.state.json` 不分发。单文件 16 MiB、单包及同步批次 64 MiB 限制不变。
原先被忽略的附加文件现在纳入签名；含此类文件的旧签名包需要重新签名。
客户端与服务端应升级到同一支持音频的 Katton 版本。

## Playback / 播放

```kotlin
val music = playClientAudio(
    AudioSource.packFile("custom/music.blob"),
    AudioOptions(volume = 0.5f, playbackRate = 1.5f, loop = true)
)
music.setPlaybackRate(2f)
music.pause()
music.seek(30.seconds)
music.resume()
music.fadeTo(0.2f, 3.seconds)
music.refresh().thenAccept { println(it) }
// music.stop()  // rewind, keep the handle for replay
// music.close() // release the handle
```

Import `kotlin.time.Duration.Companion.seconds` for these time literals.
Create client players on the client thread (`runOnClient`); create remote
players on the server thread. Handle controls can be called from other threads.
Do not block the client/server thread waiting for preparation or remote replies;
use future chaining as shown in `examples/audio/AudioExamples.kt`.

`playbackRate` 接受 0.25–4 的有限数值，默认 1；速度和音高联动，不保持原音高。
播放中修改速度不重置进度。进度、跳转和总时长均表示原始音频时间：60 秒曲目
以 2 倍速播放约需 30 秒，总时长仍为 60 秒。淡变按实际时间进行，暂停时冻结。

Defaults: stereo background audio, MUSIC category, volume 1, no loop.
Position/entity options enable mono spatial audio with linear attenuation
(16 blocks by default). Game master/category volume and output device apply.
Singleplayer pause freezes playback; manual pause remains in effect on resume.

Preparation is asynchronous: state starts at PREPARING, and duration is null
until decoding finishes. Unsupported/corrupt audio reports FAILED with an error.
Stop rewinds for replay; close invalidates the handle. Closed/failed handles
reject further controls. Keep no more than 64 handles open; close unused ones.

PCM is decoded on two workers into a bounded disk cache (512 MiB per file,
1 GiB total). Active handles pin their cache entries. Memory uses streaming
buffers rather than retaining entire decoded songs. Idle cache entries can be
evicted; disconnect closes playback and removes idle PCM files.

## Remote playback and lifecycle / 远程播放与生命周期

`playPlayerAudio(player, source, options)` requires a compatible Fabric/NeoForge
Katton client. Full playback is unavailable on Paper. Remote snapshots are the
last client-confirmed state; call `refresh()` for current progress. A sent packet
is not a playback confirmation, and different players are not sample-synchronized.
Remote commands are serialized; errors/timeouts complete their futures exceptionally.

包文件复用现有可信脚本包同步，不另行下载 HTTP 音频。缺少对应包版本时等待同步，
超时明确失败。包音频内容改变后重新创建 `AudioSource.packFile` 使用新版本，已有
播放保持原内容快照。脚本重载暂停旧实例，失败恢复，成功释放旧实例；断线、世界／
维度离开或跟随实体失效时关闭实例。声音设备或资源重载保留播放状态。

Paper 基础 API 返回的 future 只确认声音已发送，不确认玩家听到声音。
`stopBasicSound` 按 ID／分类匹配，会影响相同 ID 的多次播放；空筛选匹配全部声音。

## Verification / 验证

Recorded results and remaining integration checks: [audio-verification.md](audio-verification.md).

- `:common:26.1.2:test`, `:common:26.2:test`: deterministic decoder/queue/API/pack tests.
- `:common:<version>:verifyAudioExamples`: compile the shipped Kotlin examples.
- `:common:<version>:verifyAudioNative`: opt-in muted OpenAL device test; requires a working audio device.

In-game checks: play a looping track, change rate while playing/paused, seek,
move around a spatial source, change game volume, reload resources, switch audio
device, reload a script successfully/unsuccessfully, disconnect, and exercise
the same controls through a second modded client. Verify Paper basic playback on
the player's entity region. Device probes do not replace these integration checks.
