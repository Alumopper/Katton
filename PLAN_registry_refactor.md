# Katton 注册系统重构与扩展计划

## 当前架构分析

### 现有注册类型

| 类型 | Minecraft Registry | 解冻需求 | 当前状态 |
|---|---|---|---|
| Item | `BuiltInRegistries.ITEM` | `withUnfrozenAndHolders` (unfreeze + intrusive holders) | ✅ 已实现 |
| Block | `BuiltInRegistries.BLOCK` | `withUnfrozenAndHolders` (unfreeze + intrusive holders) | ✅ 已实现 |
| MobEffect | `BuiltInRegistries.MOB_EFFECT` | `withUnfrozenRegistry` (仅解冻) | ✅ 已实现 |
| DataComponentType | `BuiltInRegistries.DATA_COMPONENT_TYPE` | `withUnfrozenRegistry` (仅解冻) | ⚠️ 仅内部使用 |
| Command | Brigadier Dispatcher | 不需要 | ✅ 已实现 |
| Recipe | RecipeManager 内部字段 | 不需要 | ✅ 通过 datapack |
| Advancement | AdvancementManager 内部字段 | 不需要 | ✅ 通过 datapack |
| Tag | TagLoader | 不需要 | ✅ 通过 datapack |

### 核心问题

#### 1. 严重代码重复

`ITEMS`、`BLOCKS`、`EFFECTS` 三个对象遵循几乎完全相同的模式，但代码完全复制粘贴：

- `registerGlobalX()` — 仅 registry 常量不同
- `beginReload()` — 仅 registry 常量和 key factory 不同
- `ensureGlobalXRegistered()` — 仅 registry 常量不同
- `registerNewX()` — 仅 registry 常量不同
- `registerXWithMode()` — 相同的 `when` 逻辑
- `OwnershipTracker` 实例 — 每个 registry 各一个

**预估重复代码量：~60%**

#### 2. ITEMS 独占延迟注册逻辑

只有 `ITEMS` 有 `pendingNativeRegistrations` 队列来处理 `LoadState.INIT` 之前的注册。`BLOCKS` 和 `EFFECTS` 缺少此逻辑，导致不一致。

#### 3. 缺少泛型抽象

没有 `ReloadableBuiltInRegistry<T>` 这样的通用基类。每新增一种注册类型都需要复制 100+ 行代码。

#### 4. 大量常用注册类型缺失

Katton 目前只支持 Item、Block、Effect 三种核心类型。Mod 开发中常用的 EntityType、SoundEvent、Enchantment、ParticleType 等都没有注册 API。

---

## 优化方案

### Phase 1: 提取泛型注册基类

**目标：** 将 ITEMS/BLOCKS/EFFECTS 的重复逻辑提取到一个泛型类中。

```kotlin
// 伪代码
class ReloadableBuiltInRegistry<T : Any, E : Identifiable>(
    val kattonId: Identifier,
    val builtInRegistry: Registry<T>,
    val registryKey: ResourceKey<out Registry<T>>,
    val requiresIntrusiveHolders: Boolean,
    val entryFactory: (id: Identifier, value: T) -> E
) : KattonRegistries<E>(kattonId) {
    private val tracker = OwnershipTracker()
    private val pendingRegistrations = mutableListOf<Pair<Identifier, () -> T>>()
    
    fun registerGlobal(id: Identifier, value: T): T = 
        Registry.register(builtInRegistry, id, value)
    
    fun beginReload() { /* 通用实现 */ }
    fun newNative(id: Identifier, registerMode: RegisterMode, factory: () -> T): E { /* 通用实现 */ }
}
```

**关键设计决策：**
- `requiresIntrusiveHolders` 参数区分 Items/Blocks (需要) 和 Effects/Components (不需要)
- `entryFactory` 参数创建每种类型特有的 entry 对象 (KattonItemEntry/KattonBlockEntry/...)
- 延迟注册队列统一处理
- **类可见性：`ReloadableBuiltInRegistry` 为 `internal`，仅限 Katton 内部使用**，不暴露给脚本作者

### Phase 2: 统一注册 API 模式

**目标：** 所有 `registerNativeXxx()` 函数遵循相同的签名模式。

当前不一致：
- `registerNativeItem(id, registerMode, configure, itemFactory)` — `configure` lambda + `KattonItemProperties`
- `registerNativeBlock(id, registerMode, blockFactory)` — 直接 factory
- `registerNativeEffect(id, registerMode, effectFactory)` — 直接 factory

**建议统一为两种模式：**
1. **简单模式：** `registerNative(id, registerMode, factory)` — 适用于 Effect、SoundEvent 等不需要复杂配置的
2. **属性模式：** `registerNative(id, registerMode, configure, factory)` — 适用于 Item、Block 等需要 Properties 配置的

### Phase 3: 添加常用注册类型

#### 需要解冻注册表的类型 (BuiltInRegistries)

| 优先级 | 类型 | Registry | 解冻方式 | 常见用途 |
|---|---|---|---|---|
| P0 | EntityType | `ENTITY_TYPE` | `withUnfrozenAndHolders` | 自定义生物、NPC、投掷物 |
| P0 | SoundEvent | `SOUND_EVENT` | `withUnfrozenRegistry` | 自定义音效 |
| P0 | Enchantment | `ENCHANTMENT` | `withUnfrozenRegistry` | 自定义附魔 |
| P0 | ParticleType | `PARTICLE_TYPE` | `withUnfrozenAndHolders` | 自定义粒子效果 |
| P1 | BlockEntityType | `BLOCK_ENTITY_TYPE` | `withUnfrozenAndHolders` | 方块实体（箱子、熔炉等），**需要组合 API** |
| P1 | MenuType | `MENU` | `withUnfrozenRegistry` | 自定义 GUI |
| P1 | Potion | `POTION` | `withUnfrozenRegistry` | 自定义药水 |
| P1 | CreativeModeTab | `CREATIVE_MODE_TAB` | `withUnfrozenRegistry` | 创造模式物品栏分类，**支持 after/before 排序** |
| P2 | VillagerProfession | `VILLAGER_PROFESSION` | `withUnfrozenRegistry` | 自定义村民职业 |
| P2 | Attribute | `ATTRIBUTE` | `withUnfrozenRegistry` | 自定义属性 |
| P2 | Fluid | `FLUID` | `withUnfrozenAndHolders` | 自定义流体 |
| P2 | Instrument | `INSTRUMENT` | `withUnfrozenRegistry` | 自定义乐器（山羊角等） |
| P3 | GameEvent | `GAME_EVENT` | `withUnfrozenRegistry` | 自定义振动检测事件 |
| P3 | MemoryModuleType | `MEMORY_MODULE_TYPE` | `withUnfrozenRegistry` | AI 记忆模块 |
| P3 | SensorType | `SENSOR_TYPE` | `withUnfrozenRegistry` | AI 传感器 |

#### 不需要解冻注册表的类型 (非注册表/其他机制)

| 优先级 | 类型 | 机制 | 常见用途 |
|---|---|---|---|
| P0 | DataComponentType (公开API) | `withUnfrozenRegistry` | 自定义物品数据组件 |
| P2 | RecipeSerializer | `RECIPE_SERIALIZER` | 自定义配方类型 |
| P2 | LootItemFunctionType | `LOOT_FUNCTION_TYPE` | 自定义战利品函数 |
| P2 | LootItemConditionType | `LOOT_CONDITION_TYPE` | 自定义战利品条件 |

> **注意：** 动态注册表类型（Biome、Structure、DimensionType、ConfiguredFeature、DamageType 等）**不在支持范围内**。它们存储在 level 的 datapack 中，需要完全不同的注册机制。

### Phase 4: 特定类型的设计细节

#### 4.1 BlockEntityType 组合 API

BlockEntityType 注册必须同时注册对应的 Block 和 BlockEntity。提供组合 API：

```kotlin
fun registerNativeBlockWithEntity(
    id: String,
    registerMode: RegisterMode = RegisterMode.AUTO,
    configure: KattonItemProperties.() -> Unit = {},  // 用于 Block 属性
    blockFactory: (BlockBehaviour.Properties) -> BlockWithEntity,
    blockEntityFactory: () -> BlockEntity
): Pair<KattonBlockEntry, BlockEntityEntry>
```

内部实现：
1. 先注册 Block（使用现有的 BLOCKS 注册流程）
2. 再注册 BlockEntityType，绑定到已注册的 Block

#### 4.2 CreativeModeTab 排序

创造模式物品栏 tab 默认出现在最右侧。提供 `after`/`before` 参数控制顺序：

```kotlin
fun registerNativeCreativeTab(
    id: String,
    title: String,
    icon: ItemStack,
    after: String? = null,      // 放置在该 tab 之后
    before: String? = null,     // 放置在该 tab 之前
    items: List<Item> = emptyList()
)
```

内部实现：
- 使用 `CreativeModeTab.Builder` 构建 tab
- 注册后通过反射调整 `CreativeModeTabs.tabs()` 返回的列表顺序

### Phase 5: 优化现有实现

#### 5.1 删除 `KattonRegistries.register()` 的 @Deprecated

当前 `newNative()` 每次都 `@Suppress("DEPRECATION")` 调用 `register()`。要么取消 deprecated，要么改为 private helper。

#### 5.2 优化 `RegistryMutationUtil.unregister()`

当前是 `O(n)` 的 `toId` map 重建。可以：
- 批量 unregister 时只重建一次（目前 `beginReload()` 对每个 id 单独调用 `unregister()`，每次都重建）
- 或者在批量场景下提供一个 `unregisterAll(registry, keys)` 方法

#### 5.3 统一延迟注册

把 `pendingNativeRegistrations` 逻辑从 ITEMS 提取到泛型基类中，让 BLOCKS 和 EFFECTS 也支持 init 之前的注册。

#### 5.4 `KattonItemProperties` 的错误处理

`buildComponent()` 中 `runCatching { componentInitializer.run() }` 静默忽略 Throwable。应该至少 log 错误。

---

## 实施优先级

### 高优先级 (立即做)

1. **提取 `ReloadableBuiltInRegistry<T, E>` 泛型基类**
   - 消除 ITEMS/BLOCKS/EFFECTS 的重复代码
   - 将 `OwnershipTracker`、`pendingNativeRegistrations`、`beginReload` 等逻辑统一
   
2. **添加 EntityType 注册**
   - Mod 最常用且目前缺失最痛的类型
   - 需要 `withUnfrozenAndHolders`

3. **添加 SoundEvent 注册**
   - 非常简单（只需要 `SoundEvent.createVariableRangeEvent(id)`）
   - 只需要 `withUnfrozenRegistry`

### 中优先级 (下一个版本)

4. **添加 Enchantment 注册**
5. **添加 ParticleType 注册**
6. **公开 DataComponentType 注册 API**
7. **添加 BlockEntityType 注册**

### 低优先级 (未来扩展)

8. **MenuType 注册**
9. **VillagerProfession、Attribute 等**

---

## 已确定的设计决策

| 问题 | 决策 |
|---|---|
| 动态注册表 (Biome/Structure/DimensionType) | **不支持**。这些不是 `BuiltInRegistries`，需要完全不同的机制 |
| BlockEntityType 注册 | **提供组合 API**：`registerNativeBlockWithEntity(id, blockFactory, blockEntityFactory)`，内部同时注册 Block 和 BlockEntityType |
| CreativeModeTab 排序 | **支持 `after`/`before` 参数**，通过反射调整 tab 列表顺序 |
| 泛型基类可见性 | **`internal` 仅限内部使用**，不暴露给脚本作者 |
