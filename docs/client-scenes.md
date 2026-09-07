# 客户端镜头与世界特效 / Client scenes

Fabric 和 NeoForge，Minecraft 26.1.2 / 26.2。所有本地接口在客户端线程调用；服务端接口在服务端线程调用。Paper 不提供这些表现能力。

Fabric and NeoForge, Minecraft 26.1.2 / 26.2. Call local APIs on the client thread and server APIs on the server thread. Paper has no scene renderer.

## 快速开始 / Quick start

将 `examples/client-scenes` 复制到世界的 `kattonpacks` 目录，然后执行 `/katton reload`。客户端须安装匹配版本的 Katton 并接受脚本包同步。

Copy `examples/client-scenes` into the world's `kattonpacks` directory and run `/katton reload`. Clients need a matching Katton installation and must accept pack synchronization.

| 命令 / Command | 效果 / Result |
|---|---|
| `/kattonscene impact` | 冲击震屏与粒子环 / Camera shake and particle ring |
| `/kattonscene trail` | 跑动时的实体拖尾与光束 / Entity trail and beam; move to see the trail |
| `/kattonscene cutscene` | 关键帧镜头与法阵；Esc 跳过 / Camera path and magic circle; Esc skips |
| `/kattonscene geometry` | 曲线、盒体、球体、圆锥 / Curve, box, sphere, cone |

## 本地接口 / Local APIs

```kotlin
import top.katton.api.scene.*
import net.minecraft.world.phys.Vec3

val shake = shakeCamera(durationTicks = 12, strength = 2f)
shake.onEnd { reason -> println(reason) }
shake.cancel()

val line = playClientEffect(GeometryEffect(
    EffectGeometry.Line(Vec3(3.0, 0.0, 0.0)),
    anchor = EffectAnchor.Position(Vec3(0.0, 70.0, 0.0)),
    material = EffectMaterial(color = 0xCC55CCFF.toInt())
))
```

`playCameraPath`、`followCamera`、`shakeCamera`、`transitionCameraFov` 均返回 `EffectHandle`。`playClientEffect` 接受任何 `SceneEffect`。完整过场通过 `CameraOptions(lockInput = false)` 保留玩家操作；Esc 始终可以跳过。

All camera helpers return `EffectHandle`. `playClientEffect` accepts any `SceneEffect`. Full cutscenes can retain player input with `CameraOptions(lockInput = false)`; Esc always skips the current cutscene.

位置单位为方块；角度为度；时间为 tick（正常运行时每秒 20 tick）。镜头路径使用绝对世界坐标，几何顶点相对于锚点。需要相对镜头路径时，在工厂中加上 `SceneContext.origin`。

Positions are in blocks, angles in degrees, and time in ticks (normally 20 ticks/second). Camera paths use absolute world positions; geometry is local to its anchor. Add `SceneContext.origin` in a factory to author a relative camera path.

| 类型 / Type | 参数与行为 / Parameters and behavior |
|---|---|
| `CameraPath` | 从 tick 0 开始的递增关键帧；线性或 Catmull–Rom 位置插值、四元数旋转 / Increasing keyframes starting at tick 0; linear or Catmull–Rom positions, quaternion rotation |
| `CameraFollow` | 锚点、时长、可选注视目标 / Anchor, duration, optional look-at target |
| `CameraShake` | 时长、角度强度、频率；逐渐衰减 / Duration, angular strength, frequency; decays over time |
| `CameraFov` | 起止 FOV（1–179 度）、时长、缓动 / Start/end FOV (1–179 degrees), duration, easing |
| `ParticleEmitter` | 原版或已注册 `ParticleOptions`，形状、锚点、每 tick 数量与速度 / Existing particle options, shape, anchor, count per tick, velocity |
| `GeometryEffect` | 几何、锚点、时长、材质、yaw/pitch/roll / Geometry, anchor, duration, material, orientation |
| `BeamEffect` | 两个锚点之间的光束 / Beam between two anchors |
| `TrailEffect` | 固定 tick 采样，点数有界，传送时断开 / Tick-sampled bounded trail; breaks on teleport |

`EffectAnchor.Position` 为固定世界坐标；`Origin` 为演出原点加偏移；`Entity` 为实体 UUID 加偏移，省略 UUID 时使用 `SceneContext.target`。目标不可用时结束演出，不加载区块。

Anchors can refer to a fixed world position, the scene origin plus an offset, or an entity UUID plus an offset. An entity anchor without a UUID uses `SceneContext.target`. Unavailable targets end the scene without loading chunks.

几何包括 `Line`、Catmull–Rom `Curve`、`Ring`、水平 `Plane`、共面凸多边形 `Face`、实心／线框 `Box`、`Sphere`、`Cylinder`；将 `Cylinder.topRadius` 设为 0 可绘制圆锥。使用旋转参数改变平面或圆环方向。

Geometry includes lines, Catmull–Rom curves, rings, horizontal planes, convex planar faces, solid/wireframe boxes, spheres, and cylinders. Set `Cylinder.topRadius` to zero for a cone. Use rotation parameters to orient planes and rings.

材质支持 ARGB、方块单位宽度、贴图 ID、渐隐、Alpha／加法混合和 `throughWalls`。默认深度遮挡。内置贴图为 `katton:textures/effect/white.png` 和 `katton:textures/effect/circle.png`；自定义贴图放在脚本包 `assets/<namespace>/textures/` 下。

Materials support ARGB, width in blocks, texture IDs, fade-out, Alpha/additive blending, and `throughWalls`. Depth testing is enabled by default. Built-in textures are `katton:textures/effect/white.png` and `katton:textures/effect/circle.png`; custom textures belong under the pack's `assets/<namespace>/textures/` directory.

## 时间线 / Timeline

```kotlin
registerClientScene("demo:sequence") {
    repeats = 2
    effect(CameraShake(10))
    parallel {
        effect(CameraFov(75f, 60f, 20))
        sequential {
            waitTicks(5)
            effect(CameraShake(15))
        }
    }
    at(40) { effect(CameraFov(60f, 75f, 10)) }
}
val scene = playClientScene("demo:sequence", SceneContext(origin = player.position()))
scene.pause()
scene.resume()
scene.cancel()
```

默认顺序，`parallel` 同时启动直接子项，`at` 相对于当前构建块起点。动态工厂 `effect(durationTicks) { context -> ... }` 的效果时长必须等于声明时长。每个实例有自己的时钟与上下文；单机暂停时停止推进。首版最长累计时长 72,000 tick，最多 4,096 条轨道、1,000 次有限重复。

Tracks are sequential by default. `parallel` starts its direct branches together; `at` uses an offset relative to its builder block. A dynamic factory's effect duration must match its declared track duration. Instances have independent clocks and contexts; the integrated game pause stops advancement. Total duration is capped at 72,000 ticks, with up to 4,096 tracks and 1,000 finite repeats.

新完整镜头替换旧镜头时取消旧镜头所属演出及其子效果；独立 FOV 使用相同替换规则。震动可以叠加，总角度限制为每轴 45 度。回调在所属脚本上下文中执行；回调抛错会记录日志，清理仍然完成。

A new full camera cancels the scene that owned the previous full camera, including its children. Standalone FOV tracks use the same replacement rule. Shakes combine with a per-axis 45-degree cap. Callbacks run in their script context; callback exceptions are logged without preventing cleanup.

## 服务端触发 / Server triggers

```kotlin
import top.katton.util.ScriptExecutionContext

// Capture inside the server entrypoint if the later callback does not propagate ownership.
val revision = requireNotNull(ScriptExecutionContext.currentScriptRevision())
val remote = playPlayerScene(player, "demo:sequence", SceneContext(player.position(), player.uuid), revision)
val nearby = playNearbyScene(level, player.position(), 32.0, "demo:sequence",
    SceneContext(player.position(), player.uuid), revision)
remote.cancel()
```

定义默认以注册脚本包的代码哈希作为版本。服务端与客户端必须使用相同定义 ID 和版本。没有脚本上下文时默认版本为 `"1"`，也可以两侧显式指定版本；命令等未传播 owner 的回调需要在入口捕获版本并传入。

Definitions default to their script pack's code hash. Server and client must agree on ID and revision. Calls outside script context default to `"1"`; both sides may specify an explicit revision. Callbacks without owner propagation, such as command handlers, should receive a revision captured at the entrypoint.

只传输开始／停止命令与上下文，定义和资源沿用脚本包同步。客户端收到后开始，不承诺不同客户端逐帧同步；后来进入范围者不补播。过期定义、错误维度、不可用目标及重复实例不会播放。远程句柄仅能向原接收者发送停止消息，不提供完成确认。Esc 只跳过当前观众。

Only start/stop commands and contexts travel over the play channel; definitions and assets use pack synchronization. Playback starts on receipt, without frame-accurate synchronization across clients or replay for late arrivals. Stale definitions, wrong dimensions, missing targets, and duplicate instances are rejected. Remote handles send stops to the original recipients and do not acknowledge completion. Esc skips only the current viewer's scene.

## 生命周期、预算与诊断 / Lifecycle, budgets, diagnostics

预编译失败保持当前演出；进入客户端重载替换阶段会取消 world/server-cache 演出并清理定义。回滚重建旧定义，运行时不恢复被取消的播放实例；脚本应避免在重载入口中无条件自动播放。断线、死亡、世界变化、取消和目标丢失解除镜头接管。停止粒子效果只停止发射，已有粒子自然消失。

Precompile failures preserve active playback. Client runtime replacement cancels world/server-cache scenes and removes their definitions. Rollback rebuilds definitions without restoring cancelled playback instances; scripts should avoid unconditional auto-play in reload entrypoints. Disconnect, death, world changes, cancellation, and target loss release the camera. Stopping a particle effect stops emission; existing particles expire naturally.

`setClientEffectBudgets(EffectBudgets(...))` 设置客户端全局预算：默认 256 个活动效果、每 tick 4,096 个粒子、每帧 65,536 个顶点。启动时预算不足则拒绝新演出；后续片段超限则跳过该效果并继续时间线。粒子和顶点超额部分跳过。日志限频，不影响时钟和恢复。`ClientSceneManager.stats()` 提供定义、演出、效果和绘制缓冲数量，供开发诊断。

Global defaults are 256 active effects, 4,096 particles per tick, and 65,536 vertices per frame. Insufficient startup capacity rejects the new scene; later tracks that exceed the limit are skipped while the timeline continues. Excess emission and vertices are skipped. Diagnostics are rate-limited and do not block clocks or restoration. `ClientSceneManager.stats()` exposes definition, scene, effect, and draw-buffer counts for diagnostics.

镜头和输入限制均为客户端表现，不提供免伤、服务端冻结或路径区块加载。

Camera and input handling are client presentation features, not invulnerability, server freezing, or chunk loading.
