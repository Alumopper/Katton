# Eltaw MP3 播放示例

适用于带音频 API 的 Katton Fabric / NeoForge 客户端，不适用于 Paper。

1. 将整个 `audio-eltaw` 文件夹复制到测试存档的 `kattonpacks/` 下，
   确保 `kattonpacks/audio-eltaw/manifest.json` 直接存在。
2. 替换旧示例的 `PlayEltaw.kt` 和 `manifest.json`，进入存档或执行
   `/katton reload`（重载需要权限）。本版本不再自动播放。
3. 输入 `/eltaw play` 播放，默认原速、50% 音量，受游戏主音量和音乐音量影响。
4. 若没有声音，查看 `logs/latest.log` 的脚本加载错误，并检查音量设置。

## 命令

```text
/eltaw play          从头播放（重新创建实例，重置倍率和音量）
/eltaw pause         暂停
/eltaw resume        继续；stop 后从头播放，保留倍率和音量
/eltaw stop          停止并归零
/eltaw speed 2       2 倍速，允许 0.25–4，速度与音高联动
/eltaw volume 0.3    设置音量，允许 0–1
/eltaw seek 30       跳到原始音频第 30 秒，暂停时仍保持暂停
/eltaw status        查询客户端确认的状态、进度、倍率和音量
```

命令只控制执行者自己的音频，无需 OP；不能从服务端控制台执行。
单人游戏同样使用内置服务端命令。联机时服务端和客户端都需要 Katton；
在服务端世界的 `kattonpacks/` 安装本包，并允许可信包同步。
首次播放可能显示 PREPARING，表示尚在准备，不表示已经响起。
准备完成后可用 status 查询；加载失败或不兼容客户端会报告错误。

本机示例已复制用户提供的 `D:/CloudMusic/Download/Download/Fl00t - Eltaw.mp3`
到 `audio/Fl00t - Eltaw.mp3`，原文件未修改。API 播放的是包内副本，
不是直接读取磁盘绝对路径；无需转换 Ogg 或放入 Minecraft 的 assets 目录。
单文件限额为 16 MiB。本文件约 6.30 MiB。

音频目录被 Git 忽略。只获取源码的其他用户需要自行提供有权使用的音频。
`clientSync: true` 允许世界脚本包同步到客户端；联机使用前请确认有分发授权。
示例仅创建文件，没有自动安装到任何存档或启动游戏。
