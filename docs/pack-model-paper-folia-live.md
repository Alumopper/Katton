# Paper / Folia 依赖重构实机验证

测试时间：2026-09-08 23:56 至 2026-09-09 00:00（Asia/Shanghai）。
目标提交：`b0c40a8886beabac54a0c99a0387fc0c933a05ba`，包含 `e182080` 的依赖重构及补齐实现。
环境：Windows、Oracle OpenJDK 25.0.1、Gradle 9.3.0、Minecraft 26.1.2。

## 结果

| 服务端 | 包格式 | 结果 | 原始日志 |
|---|---|---|---|
| Paper 26.1.2 build 74 (`e4e17fc`) | 目录 | 通过 | [smoke.log](../build/live-paper-b0c40a8/smoke.log) |
| Folia 26.1.2 build 8 (`62dc0f2`) | 目录 | 通过 | [smoke.log](../build/live-folia-b0c40a8/smoke.log) |
| Paper 26.1.2 build 74 | ZIP | 通过 | [smoke.log](../build/live-paper-zip-b0c40a8/smoke.log) |
| Folia 26.1.2 build 8 | ZIP | 通过 | [smoke.log](../build/live-folia-zip-b0c40a8/smoke.log) |

四个测试进程退出码均为 0，均输出 `SMOKE_PASSED`。日志均包含停止服务器及禁用
Katton 的记录；结束后未发现测试服务器 Java 进程。服务端绑定 localhost 临时端口，
使用独立世界，没有客户端加入。

每轮实际验证：

1. 初始加载：消费者调用共享依赖，计数为 **1**，V1 监听器与延迟全局任务执行。
2. 仅修改消费者：`katton reload` 后计数为 **2**，证明依赖实例状态保留；V2 监听器与任务执行。
3. 入口故障：候选注册监听器和延迟任务后抛出 `SMOKE_ENTRY_FAILURE`，事务被拒绝；
   旧 V2 监听器恢复，旧入口未被重新执行，失败候选的监听器和延迟任务均未执行。
4. 修改共享依赖并修复消费者：重载后计数归 **1**，证明依赖及消费者一起替换。
5. 每轮 V1 监听器响应 **1 次**，V2 响应 **3 次**；V1 延迟任务执行 **1 次**，V2 **2 次**。
   失败候选监听器及延迟任务响应均为 **0 次**。ZIP 模式在重载前替换真实 ZIP 文件。

冷编译期间，启动后的控制台 `list` 在日志的下一秒响应；脚本入口在其后约 10–17 秒执行。
未观察到 watchdog 或额外的 ERROR 日志。每轮唯一 ERROR 是主动注入的事务失败。
`smoke_ping` 通过 Bukkit 事件触发探针，没有注册成 Brigadier 命令，因此同时出现
“Unknown or incomplete command”提示；共享库无入口的警告也符合该测试包的设计。

## 构建与复现

`:paper:26.1.2:shadowJar` 构建成功。实际构建使用已校验的 Gradle 9.3.0 分发包、
可用的 Java 25 路径及 `D:\dps-gradle-home` 缓存，以绕过本机失效的 JAVA_HOME 和 G 盘链接。
见[构建日志](../build/dependency-live-build-retry2.log)。没有修改插件生产源码。

插件：`build/paper-katton-0.5.0-build1+mc26.1.2.jar`。
SHA-256：`f73bfad74b5fdf8e6073fbd96772f93958a89b9c084a4ef8b5b5e34481cb273d`。
四个服务端实际加载的插件副本均与此哈希一致。

设置有效的 `JAVA_HOME` 后：

```sh
./gradlew :paper:26.1.2:shadowJar
python examples/pack-dependencies/paper-smoke.py --server-jar build/paper-server.jar --eula-file build/test-eula.txt --vanilla-jar build/mojang_26.1.2.jar --output build/new-paper-test
```

将 `--server-jar` 换为 `build/folia-server.jar` 即测试 Folia；增加 `--zip` 即测试 ZIP 包。
`--output` 必须为空目录，EULA 文件须已由使用者接受。上述下载文件和用户授权生成的
EULA 文件保留在本次本地 `build/` 下，不进入 Git。

脚本修改包括 Windows 启动支持、服务端路径参数、ZIP 热重载、全局调度探针，以及
按命令发出位置匹配新日志，避免消费启动日志时遗漏入口结果或误用上轮回调。

完整计数与插件哈希见[机器核验结果](../build/dependency-live-results.json)。
上述原始日志及 JSON 位于 Git 忽略的 `build/`，执行 clean 后会被删除。

## 验证边界

本次验证真实 Paper/Folia 进程中的依赖实例共享、替换、入口失败回滚、Bukkit 托管监听器、
延迟全局调度和 DIR/ZIP 加载。没有验证玩家联机、实体调度、Folia 多区域并发与持续负载，
也没有在实机中穷举 export、私有库、依赖环及版本约束矩阵；不能据此断言所有线程安全场景通过。
没有重新运行完整 JVM 单元测试套件。任意脚本副作用（如失败入口对共享计数的递增）不属于回滚保证。
