# 反编译 Minecraft 源码

## 概述

当需要查看 Minecraft 内部实现（比如验证某个 API 的行为、查看类结构、理解内部机制）时，可以通过反编译器从 Fabric/NeoForge 构建缓存中的 mapped jar 提取源码。

## 前置条件

- Java 已安装（`java` 命令可用）
- Fabric/NeoForge 项目已构建过（Gradle 缓存中有 mapped jar）
- 反编译器（CFR、FernFlower 等）

## 步骤

### 1. 找到 Minecraft Mapped Jar

Fabric Loom / NeoForge ModDev 在构建时会下载并 remap Minecraft jar，存放在 Gradle 缓存中：

**Fabric 路径：**
```
~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common/<version>-loom.mappings.../minecraft-common-<version>-loom.mappings....jar
```

**查找方法：**
```powershell
# 搜索缓存中的 Minecraft common jar
Get-ChildItem "$env:USERPROFILE\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-common" -Recurse -Filter "*.jar"
```

### 2. 获取反编译器

**CFR（推荐，最容易获取）：**
```powershell
# 从 Maven Central 下载
$ cfrUrl = "https://repo1.maven.org/maven2/org/benf/cfr/0.152/cfr-0.152.jar"
$ cfrPath = "$env:TEMP\cfr.jar"
Invoke-WebRequest -Uri $cfrUrl -OutFile $cfrPath
```

**FernFlower（JetBrains 出品，IDEA 内置）：**
- 通常随 IntelliJ IDEA 安装，位于 IDEA 安装目录的 `plugins/java-decompiler/lib/fernflower.jar`
- 也可以从 JetBrains GitHub 仓库构建获取

### 3. 提取并反编译类

```powershell
# 设置路径
$mcJar = "path/to/minecraft-common-xxx.jar"  # 第1步找到的路径
$cfrPath = "$env:TEMP\cfr.jar"
$tempDir = "$env:TEMP\mc_decompile"
$className = "net/minecraft/world/item/CreativeModeTab"

# 创建工作目录
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null

# 从 jar 中提取 .class 文件
Push-Location $tempDir
jar xf "$mcJar" "$className.class"
Pop-Location

# 使用 CFR 反编译
java -jar "$cfrPath" "$tempDir\$className.class" --outputdir "$tempDir\decompiled"

# 查看反编译结果
Get-Content "$tempDir\decompiled\$className.java" -TotalCount 50
```

### 4. 使用 javap 查看结构（更快，无需反编译器）

如果只需要查看类的字段和方法签名，不需要完整反编译：

```powershell
# 查看字段和方法
javap -p "$tempDir\net\minecraft\world\item\CreativeModeTab.class"

# 查看字节码（更详细）
javap -c -p "$tempDir\net\minecraft\world\item\CreativeModeTab.class"
```

## 实际示例

### 查看 CreativeModeTab 的构建器模式

反编译后发现：
```java
public class CreativeModeTab {
    static final Identifier DEFAULT_BACKGROUND = CreativeModeTab.createTextureLocation("items");
    private final Component displayName;
    Identifier backgroundTexture = DEFAULT_BACKGROUND;
    boolean canScroll = true;
    boolean showTitle = true;
    boolean alignedRight = false;
    private final Row row;
    private final int column;
    private final Type type;
    private @Nullable ItemStack iconItemStack;
    private Collection<ItemStack> displayItems = ItemStackLinkedSet.createTypeAndComponentsSet();
    private Set<ItemStack> displayItemsSearchTab = ItemStackLinkedSet.createTypeAndComponentsSet();
    private final Supplier<ItemStack> iconGenerator;
    private final DisplayItemsGenerator displayItemsGenerator;

    CreativeModeTab(Row row, int i, Type type, Component component, 
                    Supplier<ItemStack> supplier, DisplayItemsGenerator displayItemsGenerator) {
        // ...
    }

    public static Builder builder(Row row, int i) {
        return new Builder(row, i);
    }
    // ...
}
```

### 查看 CreativeModeTabs 的注册方式

反编译后发现 `CreativeModeTabs.bootstrap(Registry<CreativeModeTab>)` 方法使用 `Registry.register()` 注册所有默认 tabs，并通过 `CreativeModeTab.builder().displayItems(...).build()` 构建每个 tab。

## 注意事项

1. **mapped vs intermediary vs official**: Fabric Loom 缓存中通常是 yarn/mojang mapped 的 jar（使用可读名如 `CreativeModeTab`），不是 intermediary 名（如 `class_1234`）。

2. **反编译质量**: CFR 和 FernFlower 都能很好地处理 Minecraft 的字节码，但某些 lambda、switch表达式可能会有轻微的语法差异。

3. **内嵌类**: 如果类有内部类（如 `CreativeModeTab$Builder`），需要分别提取和反编译每个 `.class` 文件。

4. **版权**: 反编译后的源码仅用于开发和调试目的，受 Minecraft EULA 约束，不要分发。

## 快捷命令模板

```powershell
# 一键反编译指定类
function Decompile-McClass {
    param([string]$ClassPath)
    $mcJar = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-common" -Recurse -Filter "*.jar" | Select-Object -First 1).FullName
    $cfr = "$env:TEMP\cfr.jar"
    $tmp = "$env:TEMP\mc_decompile"
    if (-not (Test-Path $cfr)) { 
        Invoke-WebRequest "https://repo1.maven.org/maven2/org/benf/cfr/0.152/cfr-0.152.jar" -OutFile $cfr 
    }
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
    New-Item -ItemType Directory $tmp | Out-Null
    Push-Location $tmp
    jar xf "$mcJar" "$ClassPath.class"
    Pop-Location
    java -jar "$cfr" "$tmp\$ClassPath.class" --outputdir "$tmp\decompiled" | Out-Null
    Get-Content "$tmp\decompiled\$ClassPath.java" -Raw
}

# 使用示例:
# Decompile-McClass "net/minecraft/world/item/CreativeModeTab"
```
