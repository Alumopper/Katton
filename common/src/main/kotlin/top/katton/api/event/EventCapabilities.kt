package top.katton.api.event

import top.katton.Katton
import top.katton.engine.ScriptDependencyManager
import top.katton.pack.ScriptPlatform

enum class EventCapabilityStatus {
    SUPPORTED,
    PARTIAL,
    UNSUPPORTED
}

enum class EventExecutionContext {
    PLATFORM_THREAD,
    ASYNC_THREAD,
    ENTITY_REGION,
    GLOBAL_REGION,
    NOT_APPLICABLE
}

data class EventCapability(
    val id: String,
    val platform: ScriptPlatform,
    val status: EventCapabilityStatus,
    val executionContext: EventExecutionContext,
    val detail: String
) {
    val supported: Boolean
        get() = status != EventCapabilityStatus.UNSUPPORTED
}

/** Runtime capability query for event APIs whose platform parity is not exact. */
object EventCapabilities {
    private const val EVENT_PACKAGE = "top.katton.api.event"

    private data class Override(
        val status: EventCapabilityStatus,
        val context: EventExecutionContext = EventExecutionContext.PLATFORM_THREAD,
        val detail: String
    )

    private val paperOverrides = buildMap {
        put("LivingUseItemEvent.onUseItemTick", unsupported("Paper 没有逐 tick 的物品使用事件"))
        put("LivingUseItemEvent.onUseItemStart", partial("由 PlayerItemConsumeEvent 在消耗时合成，不代表真实开始使用"))
        put("LivingUseItemEvent.onUseItemFinish", partial("仅覆盖玩家物品消耗完成"))
        put("LivingUseItemEvent.onUseItemStop", partial("使用 Paper 专用参数，不能提供完整通用语义"))
        put("ItemComponentEvent.onModifyComponent", unsupported("Paper API 没有等价的任意数据组件修改钩子"))
        put("ItemComponentEvent.onAllowEnchanting", partial("公开原始 PrepareItemEnchantEvent"))
        put("ItemComponentEvent.onModifyEnchantment", partial("公开原始 EnchantItemEvent"))
        put("LootTableEvent.onLootTableReplace", unsupported("Paper API 没有等价的加载期战利品表替换钩子"))
        put("LootTableEvent.onLootTableModify", unsupported("Paper API 没有等价的加载期战利品表修改钩子"))
        put("LootTableEvent.onLootTableAllLoad", unsupported("Paper API 没有等价的全部战利品表加载钩子"))
        put("LootTableEvent.onLootTableModifyDrops", partial("公开原始 LootGenerateEvent，仅覆盖运行时掉落生成"))
        put("LivingBehaviorEvent.onAllowNearbyMonsters", unsupported("Paper 没有可安全替代原版睡眠判定的事件"))
        put("LivingBehaviorEvent.onModifySleepingDirection", unsupported("Paper 没有可修改睡眠方向的等价事件"))
        put("LivingBehaviorEvent.onSetBedOccupationState", unsupported("Paper 没有可替代床占用状态写入的等价事件"))
        put("LivingBehaviorEvent.onModifyWakeUpPosition", unsupported("Paper 没有可修改起床位置的等价事件"))
        put("ChunkAndBlockEvent.onChunkLevelTypeChange", unsupported("Paper 不公开区块 level type 变化事件"))
        put("ServerEvent.onSyncDatapackContents", unsupported("Paper 服务端没有模组客户端数据包同步阶段"))
        put("ServerEvent.onStartDatapackReload", partial("Paper 只在资源重载完成后提供通知，开始事件为兼容性合成"))
        put("ServerEvent.onEndDatapackReload", partial("Paper 通知不提供通用实现所需的完整失败语义"))
        put("ServerMessageEvent.onAllowChatMessage", partial("在 AsyncChatEvent 线程同步执行以保留取消语义", EventExecutionContext.ASYNC_THREAD))
        put("ServerMessageEvent.onChatMessage", partial("在 AsyncChatEvent 线程同步执行", EventExecutionContext.ASYNC_THREAD))
        put("ServerMessageEvent.onServerChat", partial("在 AsyncChatEvent 线程同步执行", EventExecutionContext.ASYNC_THREAD))
        put("ServerPlayerEvent.onPickFromBlock", partial("Paper 事件不能替换最终 ItemStack"))
        put("ServerPlayerEvent.onPickFromEntity", partial("Paper 事件不能替换最终 ItemStack"))
        put("ServerPlayerEvent.onAfterPlayerRespawn", partial("Paper 不公开旧 ServerPlayer，旧值与新值指向同一实例"))
        put("ServerPlayerEvent.onPlayerCopy", partial("Paper 不公开原版玩家复制阶段，重生事件只提供当前玩家实例"))
        put("ServerPlayerEvent.onPlayerXpLevelChange", partial("Paper 的通知发生在等级变化后，取消标记不能撤销变化"))
        put("ServerEntityCombatEvent.onShieldBlock", partial("Paper 仅能报告已发生的格挡，返回值不能修改格挡结果"))
        put("ServerEvent.onServerStarting", partial("由 ServerLoadEvent.STARTUP 合成，触发时服务端已完成启动"))
        put(
            "ServerEntityEvent.onAfterEntityChangeLevel",
            partial("跨世界完成通知在实体调度器的下一 tick 触发", EventExecutionContext.ENTITY_REGION)
        )
    }

    private val neoForgeOverrides = buildMap {
        listOf(
            "ServerMessageEvent.onAllowChatMessage",
            "ServerMessageEvent.onAllowGameMessage",
            "ServerMessageEvent.onAllowCommandMessage",
            "ServerMessageEvent.onChatMessage",
            "ServerMessageEvent.onGameMessage",
            "ServerMessageEvent.onCommandMessage"
        ).forEach { put(it, unsupported("NeoForge 当前只公开 ServerMessageEvent.onServerChat")) }
        listOf(
            "ServerMobEffectEvent.onAllowAdd",
            "ServerMobEffectEvent.onBeforeAdd",
            "ServerMobEffectEvent.onAfterAdd",
            "ServerMobEffectEvent.onAllowEarlyRemove",
            "ServerMobEffectEvent.onBeforeRemove",
            "ServerMobEffectEvent.onAfterRemove"
        ).forEach { put(it, unsupported("NeoForge 使用平台专用 MobEffectApplicable/Add/Remove/Expire 事件")) }
        listOf(
            "ItemComponentEvent.onModifyComponent",
            "ItemComponentEvent.onAllowEnchanting",
            "ItemComponentEvent.onModifyEnchantment"
        ).forEach { put(it, unsupported("NeoForge 当前仅保留 API 占位字段，没有安装平台触发点")) }
        listOf(
            "LivingBehaviorEvent.onSetBedOccupationState",
            "LivingBehaviorEvent.onModifyWakeUpPosition"
        ).forEach { put(it, unsupported("NeoForge 当前仅保留 API 占位字段，没有安装平台触发点")) }
        listOf(
            "LootTableEvent.onLootTableReplace",
            "LootTableEvent.onLootTableModify",
            "LootTableEvent.onLootTableAllLoad",
            "LootTableEvent.onLootTableModifyDrops"
        ).forEach { put(it, unsupported("NeoForge 当前仅保留 API 占位字段，没有安装平台触发点")) }
    }

    /** Returns a capability record for `EventObject.onEvent` (FQCN is also accepted). */
    @JvmStatic
    fun query(eventId: String): EventCapability {
        val id = normalize(eventId)
        val platform = ScriptDependencyManager.platform
        dynamicOverride(platform, id)?.let { return it.toCapability(id, platform) }
        overrides(platform)[id]?.let { return it.toCapability(id, platform) }

        if (!hasExportedField(id)) {
            return EventCapability(
                id,
                platform,
                EventCapabilityStatus.UNSUPPORTED,
                EventExecutionContext.NOT_APPLICABLE,
                "不支持：当前平台没有导出事件字段 $id"
            )
        }
        return EventCapability(
            id,
            platform,
            EventCapabilityStatus.SUPPORTED,
            EventExecutionContext.PLATFORM_THREAD,
            "支持：由当前平台事件桥直接提供"
        )
    }

    /** Lists every known non-portable event for diagnostics and feature gating. */
    @JvmStatic
    fun notable(): List<EventCapability> {
        val platform = ScriptDependencyManager.platform
        val ids = linkedSetOf<String>().apply {
            addAll(overrides(platform).keys)
            addAll(COMMON_GAP_IDS)
        }
        return ids.map(::query)
            .filter { it.status != EventCapabilityStatus.SUPPORTED }
            .sortedBy { it.id }
    }

    @JvmStatic
    fun isSupported(eventId: String): Boolean = query(eventId).supported

    private fun dynamicOverride(platform: ScriptPlatform, id: String): Override? {
        if (platform == ScriptPlatform.NEOFORGE && id.startsWith("LivingUseItemEvent.")) {
            val server = Katton.server
            return when {
                server == null -> partial("仅注册在专用服务端；服务端类型确定后可再次查询")
                !server.isDedicatedServer -> unsupported("NeoForge 的 LivingUseItemEvent 仅在专用服务端注册")
                else -> null
            }
        }
        if (platform == ScriptPlatform.PAPER &&
            id in setOf("ServerEvent.onStartWorldTick", "ServerEvent.onEndWorldTick") &&
            isFoliaRuntime()
        ) {
            return unsupported("Folia 没有单一世界线程，无法提供通用 WorldTick 线程语义")
        }
        return null
    }

    private fun overrides(platform: ScriptPlatform): Map<String, Override> = when (platform) {
        ScriptPlatform.FABRIC -> emptyMap()
        ScriptPlatform.NEOFORGE -> neoForgeOverrides
        ScriptPlatform.PAPER -> paperOverrides
        ScriptPlatform.UNKNOWN -> emptyMap()
    }

    private fun hasExportedField(id: String): Boolean {
        val owner = id.substringBefore('.', missingDelimiterValue = "")
        val field = id.substringAfter('.', missingDelimiterValue = "")
        if (owner.isBlank() || field.isBlank()) return false
        return runCatching {
            val type = Class.forName("$EVENT_PACKAGE.$owner", false, EventCapabilities::class.java.classLoader)
            runCatching { type.getField(field) }.isSuccess || type.methods.any { method ->
                method.name == "get${field.replaceFirstChar { it.uppercaseChar() }}" && method.parameterCount == 0
            }
        }.getOrDefault(false)
    }

    private fun normalize(value: String): String {
        val trimmed = value.trim().removePrefix("$EVENT_PACKAGE.")
        val ownerAndField = trimmed.split('.').takeLast(2)
        return ownerAndField.joinToString(".")
    }

    private fun isFoliaRuntime(): Boolean = runCatching {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, EventCapabilities::class.java.classLoader)
    }.isSuccess

    private fun Override.toCapability(id: String, platform: ScriptPlatform): EventCapability =
        EventCapability(id, platform, status, context, detail)

    private fun unsupported(detail: String) = Override(
        EventCapabilityStatus.UNSUPPORTED,
        EventExecutionContext.NOT_APPLICABLE,
        "不支持：$detail"
    )

    private fun partial(
        detail: String,
        context: EventExecutionContext = EventExecutionContext.PLATFORM_THREAD
    ) = Override(EventCapabilityStatus.PARTIAL, context, "部分支持：$detail")

    private val COMMON_GAP_IDS = setOf(
        "ChunkAndBlockEvent.onBlockPlace",
        "ChunkAndBlockEvent.onChunkLevelTypeChange",
        "ItemComponentEvent.onModifyComponent",
        "LivingBehaviorEvent.onAllowNearbyMonsters",
        "LivingBehaviorEvent.onAnimalTame",
        "LivingBehaviorEvent.onBabySpawn",
        "LivingBehaviorEvent.onModifySleepingDirection",
        "LivingBehaviorEvent.onModifyWakeUpPosition",
        "LivingBehaviorEvent.onSetBedOccupationState",
        "LivingUseItemEvent.onUseItemFinish",
        "LivingUseItemEvent.onUseItemStart",
        "LivingUseItemEvent.onUseItemStop",
        "LivingUseItemEvent.onUseItemTick",
        "LootTableEvent.onLootTableAllLoad",
        "LootTableEvent.onLootTableModify",
        "LootTableEvent.onLootTableModifyDrops",
        "LootTableEvent.onLootTableReplace",
        "ServerEntityCombatEvent.onCriticalHit",
        "ServerEntityEvent.onEntityTeleport",
        "ServerEvent.onLevelLoad",
        "ServerEvent.onLevelSave",
        "ServerEvent.onLevelUnload",
        "ServerEvent.onSyncDatapackContents",
        "ServerMessageEvent.onAllowChatMessage",
        "ServerMessageEvent.onAllowCommandMessage",
        "ServerMessageEvent.onAllowGameMessage",
        "ServerMessageEvent.onChatMessage",
        "ServerMessageEvent.onCommandMessage",
        "ServerMessageEvent.onGameMessage",
        "ServerMobEffectEvent.onAfterAdd",
        "ServerMobEffectEvent.onAfterRemove",
        "ServerMobEffectEvent.onAllowAdd",
        "ServerMobEffectEvent.onAllowEarlyRemove",
        "ServerMobEffectEvent.onBeforeAdd",
        "ServerMobEffectEvent.onBeforeRemove"
    )
}
