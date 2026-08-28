# 在电脑上调试 FCL 注入兼容性

## 结论

FCL 的 Android Java 25 运行时包含 `java.instrument`，但没有打包 `jdk.attach`。Katton 原先只在脚本首次调用注入 API 时执行 `ByteBuddyAgent.install()`；运行期 agent 安装依赖 Attach，因此会在 FCL 中失败。

本分支让 Fabric/NeoForge 的 Katton 模组 jar 同时成为标准 Java agent。FCL 用户需要把同一个 Katton jar 同时放进 `mods`，并在启动参数中通过 `-javaagent` 提前加载。Katton 会优先使用启动期取得的 `Instrumentation`；桌面 JDK 仍可继续使用 Byte Buddy 动态 Attach。

## 为什么采用启动期 agent

- FCL 官方 Java 25 镜像的 `jlink --add-modules` 列表包含 `java.instrument` 和 `jdk.jdwp.agent`，但不包含 `jdk.attach`：[FCL Android OpenJDK 构建脚本](https://github.com/FCL-Team/Android-OpenJDK-Build/blob/7a0266e745d9b4acf400afa189b58e672900f710/remove_jdk_debug_info.sh#L24-L33)。
- FCL 官方启动代码会把版本设置中的自定义 JVM 参数加入 Java 命令行：[DefaultLauncher.java](https://github.com/FCL-Team/FoldCraftLauncher/blob/692121fcd113f844167b5ce26df311426aedf75e/FCL/src/main/java/com/tungsten/fclcore/launch/DefaultLauncher.java#L85-L93)，界面也提供 JVM 参数编辑项：[VersionSettingAdapter.kt](https://github.com/FCL-Team/FoldCraftLauncher/blob/692121fcd113f844167b5ce26df311426aedf75e/FCL/src/main/java/com/tungsten/fcl/ui/manage/VersionSettingAdapter.kt#L246-L253)。
- Java 25 的标准做法是在启动命令中使用 `-javaagent:<jarpath>`，JVM 会把 `Instrumentation` 传给 agent 的 `premain`：[Java Instrumentation package specification](https://docs.oracle.com/en/java/javase/25/docs/api/java.instrument/java/lang/instrument/package-summary.html)。
- Byte Buddy 1.17.8 说明 Java 9+ 的运行期安装需要可用的 `jdk.attach` 模块：[ByteBuddyAgent 1.17.8 source](https://github.com/raphw/byte-buddy/blob/byte-buddy-1.17.8/byte-buddy-agent/src/main/java/net/bytebuddy/agent/ByteBuddyAgent.java)。

## 在电脑上复现能力边界

以下任务只启动很小的 Java 探针，不启动 Minecraft，也不依赖 Android 图形栈。

```powershell
# 查看电脑当前 JDK 的 Attach / Instrumentation 能力
.\gradlew.bat :common:26.1.2:probeInjectionEnvironment

# 用 --limit-modules 模拟 FCL：有 java.instrument，没有 jdk.attach
.\gradlew.bat :common:26.1.2:probeFclLikeInjectionEnvironment

# 在同一受限环境中用 Katton jar 作为 -javaagent，验证类重定义能力
.\gradlew.bat :common:26.1.2:verifyKattonStartupAgent

# 真正重定义一个已加载类、修改方法参数并回滚处理器
.\gradlew.bat :common:26.1.2:verifyKattonStartupInjection

# 验证桌面 JDK 原有的 Byte Buddy 动态 Attach 路径没有回归
.\gradlew.bat :common:26.1.2:verifyKattonDynamicAttachInjection
```

第二个任务应输出：

```text
fclDetected=true
java.instrument=true
jdk.attach=false
startupAgent=false
```

第三个任务应输出：

```text
jdk.attach=false
startupAgent=true
redefine=true
```

这能稳定复现并验证本次修复针对的 JVM 能力差异，但不会模拟 Android 的 Bionic、FCL 原生启动器、渲染器或 ARM64 原生库。涉及这些部分的故障仍应在真实 Android 设备上复现。

## 在 FCL 中启用并验证

1. 使用 FCL 的内部 Java 25 运行时。
2. 将 Fabric 或 NeoForge 的 Katton jar 放入该游戏版本的 `mods` 目录。
3. 打开该版本的“版本设置 → 高级设置 → JVM 参数”。
4. 添加下面的参数；路径必须是设备上这个 Katton 模组 jar 的绝对路径：

```text
-javaagent:/storage/emulated/0/<实际游戏目录>/mods/fabric-katton-<版本>.jar
```

NeoForge 使用对应的 `neoforge-katton-<版本>.jar`。如果路径或文件名含空格，使用 FCL 的长按完整编辑功能并给完整参数加引号。升级 Katton 后，参数中的文件名也要同步修改。

启动后执行：

```text
/katton capabilities injection
```

成功时关键输出为：

```text
status=SUPPORTED, mode=STARTUP_AGENT, platform=FCL
java.instrument=true, jdk.attach=false, redefine=true
```

未添加启动参数或路径错误时，报告会明确显示 `UNSUPPORTED`、缺失的能力、根异常以及应该添加的 `-javaagent` 参数。FCL 启动日志也会逐项打印 `Java argument:`，应先确认其中存在 Katton 的 `-javaagent` 行。

## 从电脑远程断点调试 Android 上的 FCL

FCL 的 Java 25 镜像包含 JDWP agent。在 FCL JVM 参数中同时加入：

```text
-javaagent:/storage/emulated/0/<实际游戏目录>/mods/fabric-katton-<版本>.jar
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
```

电脑连接设备并转发端口：

```powershell
adb devices
adb forward tcp:5005 tcp:5005
```

随后在 IntelliJ IDEA 创建 **Remote JVM Debug**，主机填 `localhost`、端口填 `5005`。建议先在这些位置下断点：

- `KattonAgent.premain`：验证启动期 agent；要命中它需临时使用 `suspend=y`，FCL 会等待调试器连接。
- `InjectionManager.acquireInstrumentation`：确认选择了 `STARTUP_AGENT`，没有进入动态 Attach。
- `InjectionManager.ensureInstrumented`：观察目标类是否可修改。
- `MethodInjectionTransformer.transform`：检查实际生成的字节码。

普通调试建议保持 `suspend=n`，等游戏进入后再连接，避免把 FCL 等待调试器误判为卡死。

## 收集兼容性报告

报告问题时至少提供：

- `/katton capabilities injection` 的完整输出；
- FCL 导出的完整游戏日志，包含 `Basic Information`、`Env Map` 和 `Java argument`；
- FCL 版本、Java 运行时版本、设备架构、Android 版本、Fabric/NeoForge 版本；
- 最小注入脚本及目标类、方法签名；
- 使用启动期 agent 后是否仍可复现。

不要只提供最后一行异常。Attach 缺失、agent 路径错误、目标类不可重定义以及其他 transformer 冲突的处理方式不同。
