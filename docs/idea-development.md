# IDEA 开发连接

配套独立仓库为同级 `Katton-IDEA`。其中包含 IDEA 插件、`top.katton.dev` Gradle 插件、模板、三包示例和协议 v1 文档。游戏侧位于 `common/src/main/kotlin/top/katton/dev`。

默认关闭。已有客户端可在脚本包界面右上角打开 **IDE**；服务器管理员可执行 `/katton dev enable` 和 `/katton dev disable`。Fabric、NeoForge、Paper 共用回环 HTTP 接口。客户端连接远端服务器时不会获得其部署权限。

IDEA 从当前用户的 `~/.katton/dev/instances` 发现进程，选择当前本地世界，发送完整脚本快照。令牌不写入共享工程。写入和游戏内重载共用 ScriptReloadManager 的串行协调机制；Kotlin/Java 编译器提供结构化诊断，原有日志继续输出。

世界包支持热应用；全局代码变更需要用户在外部安装并重启，不会被开发桥提交。已加载全局包的纯资源变更可使用既有资源重载。Paper／Folia 的能力限制由实例查询报告并在预检验证。

桥接测试：

```powershell
.\gradlew.bat :common:26.1.2:test :common:26.2:test --tests 'top.katton.dev.*'
```

这验证传输和快照事务，不替代实际 IDEA + Minecraft 的连接、世界切换和断点验收。完整步骤见 Katton-IDEA 的 `docs/verification.md`。
