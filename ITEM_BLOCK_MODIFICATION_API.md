# 物品和方块属性修改API

Katton现在支持类似KubeJS的物品和方块属性修改功能。你可以在运行时修改已有物品和方块的属性，无需重启服务器。

## 物品修改API

### 基本用法

使用 `modifyItem` 函数来修改物品属性：

```kotlin
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Rarity
import top.katton.api.*

modifyItem("minecraft:diamond") {
    maxStackSize = 16
    rarity = Rarity.EPIC
    name(Component.literal("超级钻石"))
    addTooltip(Component.literal("这是一个非常稀有的钻石！"))
}
```

### 可修改的物品属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `maxStackSize` | Int | 最大堆叠数量 |
| `maxDamage` | Int | 最大耐久度（用于工具） |
| `rarity` | Rarity | 稀有度（COMMON, UNCOMMON, RARE, EPIC） |
| `fireResistant` | Boolean | 是否防火 |
| `name` | Component | 物品显示名称 |
| `foodProperties` | FoodProperties | 食物属性 |
| `customTooltip` | MutableList<Component> | 自定义提示文本 |

### 物品修改示例

```kotlin
// 修改钻石堆叠大小和稀有度
modifyItem("minecraft:diamond") {
    maxStackSize = 16
    rarity = Rarity.EPIC
}

// 修改苹果为超级食物
modifyItem("minecraft:apple") {
    maxStackSize = 32
    foodProperties = FoodProperties.Builder()
        .nutrition(8)
        .saturationModifier(0.8f)
        .alwaysEat()
        .build()
}

// 强化工具耐久度
modifyItem("minecraft:iron_pickaxe") {
    maxDamage = 500
    rarity = Rarity.RARE
    name(Component.literal("强化铁镐"))
}

// 让物品防火
modifyItem("minecraft:blaze_powder") {
    fireResistant = true
}

// 添加自定义提示
modifyItem("minecraft:gold_ingot") {
    name(Component.literal("金锭"))
    addTooltip(Component.literal("珍贵的金属"))
    addTooltip(Component.literal("可用于合成"))
}
```

## 方块修改API

### 基本用法

使用 `modifyBlock` 函数来修改方块属性：

```kotlin
import top.katton.api.*

modifyBlock("minecraft:stone") {
    hardness = 1.0f
    resistance = 10.0f
    lightEmission = 5
    friction = 0.8f
}
```

### 可修改的方块属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `hardness` | Float | 硬度（挖掘难度） |
| `resistance` | Float | 爆炸抗性 |
| `strength` | Float | 同时设置硬度和抗性（抗性 = 硬度 * 6.0） |
| `requiresCorrectTool` | Boolean | 是否需要正确工具才能掉落 |
| `friction` | Float | 摩擦力（0.0-1.0） |
| `speedFactor` | Float | 移动速度倍率 |
| `jumpFactor` | Float | 跳跃高度倍率 |
| `lightEmission` | Int | 发光等级（0-15） |
| `mapColor` | MapColor | 地图颜色 |
| `canOcclude` | Boolean | 是否遮挡光线 |
| `isAir` | Boolean | 是否为空气 |
| `hasCollision` | Boolean | 是否有碰撞箱 |
| `isSuffocating` | Boolean | 是否窒息 |
| `isViewBlocking` | Boolean | 是否阻挡视线 |

### 方块修改示例

```kotlin
// 让石头发光
modifyBlock("minecraft:stone") {
    hardness = 1.0f
    resistance = 10.0f
    lightEmission = 5
}

// 让泥土更容易挖掘且移动更快
modifyBlock("minecraft:dirt") {
    hardness = 0.5f
    resistance = 2.0f
    requiresCorrectTool = false
    speedFactor = 1.2f
    jumpFactor = 1.5f
}

// 强化荧石
modifyBlock("minecraft:glowstone") {
    lightEmission = 15
    hardness = 0.5f
    resistance = 1.0f
}

// 超强化黑曜石
modifyBlock("minecraft:obsidian") {
    strength(50.0f)
    lightEmission = 3
}

// 让玻璃无碰撞
modifyBlock("minecraft:glass") {
    hardness = 0.3f
    resistance = 0.5f
    canOcclude = false
    hasCollision = false
}

// 修改草地颜色
modifyBlock("minecraft:grass_block") {
    mapColor = MapColor.PLANT
    hardness = 0.8f
    resistance = 4.0f
}
```

## 辅助函数

### 物品相关

```kotlin
// 获取物品
val diamond = getItem("minecraft:diamond")

// 创建物品堆
val diamondStack = itemStack("minecraft:diamond", 64)
```

### 方块相关

```kotlin
// 获取方块
val stone = getBlock("minecraft:stone")

// 获取方块状态
val stoneState = getBlockState("minecraft:stone")

// 获取方块硬度
val hardness = getBlockHardness("minecraft:stone")

// 获取方块抗性
val resistance = getBlockResistance("minecraft:stone")
```

## 完整示例

```kotlin
import net.minecraft.network.chat.Component
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.Rarity
import net.minecraft.world.level.material.MapColor
import top.katton.api.*

Events.onServerStarting {
    once("modify_items_and_blocks") {
        // 修改物品
        modifyItem("minecraft:diamond") {
            maxStackSize = 16
            rarity = Rarity.EPIC
            name(Component.literal("超级钻石"))
            addTooltip(Component.literal("这是一个非常稀有的钻石！"))
        }
        
        modifyItem("minecraft:apple") {
            maxStackSize = 32
            foodProperties = FoodProperties.Builder()
                .nutrition(8)
                .saturationModifier(0.8f)
                .alwaysEat()
                .build()
        }
        
        // 修改方块
        modifyBlock("minecraft:stone") {
            hardness = 1.0f
            resistance = 10.0f
            lightEmission = 5
        }
        
        modifyBlock("minecraft:dirt") {
            hardness = 0.5f
            resistance = 2.0f
            speedFactor = 1.2f
            jumpFactor = 1.5f
        }
        
        println("物品和方块修改完成！")
    }
}
```

## 注意事项

1. **热重载支持**：这些修改支持热重载，使用 `/katton reload` 可以重新应用修改
2. **全局生效**：修改会影响所有该物品/方块的实例
3. **持久化**：修改会在服务器运行期间保持，重启后需要重新应用
4. **兼容性**：修改Vanilla物品和方块是安全的，修改其他模组的物品/方块可能需要测试
5. **once函数**：建议使用 `once` 函数确保修改只执行一次，避免重复应用

## 与KubeJS的对比

| 功能 | Katton | KubeJS |
|------|--------|--------|
| 修改物品属性 | ✅ | ✅ |
| 修改方块属性 | ✅ | ✅ |
| 热重载支持 | ✅ | ✅ |
| Kotlin语法 | ✅ | ❌（JavaScript） |
| 类型安全 | ✅ | ❌ |

Katton的物品和方块修改API提供了与KubeJS类似的功能，但使用Kotlin编写，具有更好的类型安全性和IDE支持。
