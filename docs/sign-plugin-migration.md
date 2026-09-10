# 签名插件已迁移至 Katton-Sign

`sign-plugin` 现在由独立公共仓库 [Alumopper/Katton-Sign](https://github.com/Alumopper/Katton-Sign)
维护，本仓库不再包含或构建该 Gradle 子模块。游戏侧签名验证逻辑保持在 Katton 中。

插件 ID `top.katton.sign`、版本 `1.0.0` 与实现坐标 `top.katton:sign-plugin:1.0.0`
不变，已有消费工程无需修改已发布依赖。

在新仓库目录运行 `./gradlew build`、`./gradlew publishToMavenLocal` 或
`./gradlew publishSignPluginToPrivateNexus`，取代原 `:sign-plugin:*` 任务。
Katton 的 `verifyLocalMavenPublications` 与 `publishEverythingToPrivateNexus`
现在仅处理 Minecraft 模块，不会跨仓库发布签名插件。

独立源码开发可在消费工程的 `pluginManagement` 内配置
`includeBuild("../Katton-Sign")`，详见新仓库 README。

原有验证记录中的 `:sign-plugin:build` 是迁移前的历史命令。
