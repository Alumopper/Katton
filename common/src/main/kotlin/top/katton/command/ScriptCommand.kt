package top.katton.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.IdentifierArgument
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import top.katton.Katton
import top.katton.api.ClientItemRenderAnimationMode
import top.katton.api.ClientItemRenderAnimationSetBuilder
import top.katton.api.ClientItemRenderEasing
import top.katton.config.KattonConfigManager
import top.katton.engine.ScriptReloadManager
import top.katton.registry.KattonRegistry
import top.katton.api.clearItemRenderMarkersInRange
import top.katton.api.itemRenderMarker
import top.katton.api.showItemRenderMarker

object ScriptCommand {

    private val packSuggestion: SuggestionProvider<CommandSourceStack> = SuggestionProvider { _, builder ->
        SharedSuggestionProvider.suggest(KattonConfigManager.knownPackIds(), builder)
    }

    private fun keySuggestion(packArg: String): SuggestionProvider<CommandSourceStack> = SuggestionProvider { ctx, builder ->
        val pack = StringArgumentType.getString(ctx, packArg)
        SharedSuggestionProvider.suggest(KattonConfigManager.all(pack).keys, builder)
    }

    private val itemSuggestion: SuggestionProvider<CommandSourceStack> = SuggestionProvider { _, builder ->
        SharedSuggestionProvider.suggest(BuiltInRegistries.ITEM.keySet().map { it.toString() }, builder)
    }

    private val itemRenderPresetSuggestion: SuggestionProvider<CommandSourceStack> = SuggestionProvider { _, builder ->
        SharedSuggestionProvider.suggest(listOf("still", "spin", "float", "pulse", "showcase"), builder)
    }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            literal("katton")
                .executes {
                    sendHelp(it.source)
                }
                .then(
                    literal("help")
                        .executes {
                            sendHelp(it.source)
                        }
                )
                .then(
                    literal("status")
                        .executes {
                            val source = it.source
                            source.sendSuccess(
                                {
                                    Component.literal(
                                        "[Katton] state=${Katton.globalState}, serverBound=${Katton.server != null}, clientReloadRunning=${ScriptReloadManager.isClientReloadRunning()}"
                                    )
                                },
                                false
                            )
                            1
                        }
                )
                .then(
                    literal("registry")
                        .executes {
                            val source = it.source
                            val rows = KattonRegistry.registryHealthSnapshot()
                            val summary = rows.joinToString(" | ") { row ->
                                "${row.key}: entries=${row.kattonEntries}, managed=${row.managedTracked}, stale=${row.staleRetained}"
                            }
                            source.sendSuccess({ Component.literal("[Katton] $summary") }, false)
                            1
                        }
                        .then(
                            literal("stale")
                                .executes {
                                    val source = it.source
                                    val staleRows = KattonRegistry.registryHealthSnapshot().filter { it.staleRetained > 0 }
                                    if (staleRows.isEmpty()) {
                                        source.sendSuccess({ Component.literal("[Katton] No stale retained registry entries.") }, false)
                                        return@executes 1
                                    }
                                    val text = staleRows.joinToString(" | ") { "${it.key}=${it.staleRetained}" }
                                    source.sendSuccess({ Component.literal("[Katton] Stale retained entries: $text") }, false)
                                    1
                                }
                        )
                )
                .then(
                    literal("reload")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes {
                            val source = it.source
                            if (reloadScript(source.server)) {
                                source.sendSuccess({ Component.literal("[Katton] Reload started.") }, true)
                                1
                            } else {
                                source.sendFailure(Component.literal("[Katton] Failed to reload script packs."))
                                0
                            }
                        }
                )
                .then(
                    literal("itemrender")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(
                            literal("spawn")
                                .then(
                                    Commands.argument("item", IdentifierArgument.id())
                                        .suggests(itemSuggestion)
                                        .executes {
                                            spawnItemRenderMarker(it.source, IdentifierArgument.getId(it, "item"), "showcase", 1.0f, -1)
                                        }
                                        .then(
                                            Commands.argument("preset", StringArgumentType.word())
                                                .suggests(itemRenderPresetSuggestion)
                                                .executes {
                                                    spawnItemRenderMarker(
                                                        it.source,
                                                        IdentifierArgument.getId(it, "item"),
                                                        StringArgumentType.getString(it, "preset"),
                                                        1.0f,
                                                        -1
                                                    )
                                                }
                                                .then(
                                                    Commands.argument("scale", FloatArgumentType.floatArg(0.001f))
                                                        .executes {
                                                            spawnItemRenderMarker(
                                                                it.source,
                                                                IdentifierArgument.getId(it, "item"),
                                                                StringArgumentType.getString(it, "preset"),
                                                                FloatArgumentType.getFloat(it, "scale"),
                                                                -1
                                                            )
                                                        }
                                                        .then(
                                                            Commands.argument("lifetimeTicks", IntegerArgumentType.integer(-1))
                                                                .executes {
                                                                    spawnItemRenderMarker(
                                                                        it.source,
                                                                        IdentifierArgument.getId(it, "item"),
                                                                        StringArgumentType.getString(it, "preset"),
                                                                        FloatArgumentType.getFloat(it, "scale"),
                                                                        IntegerArgumentType.getInteger(it, "lifetimeTicks")
                                                                    )
                                                                }
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            literal("clear")
                                .then(
                                    Commands.argument("radius", DoubleArgumentType.doubleArg(0.0))
                                        .executes {
                                            val source = it.source
                                            val radius = DoubleArgumentType.getDouble(it, "radius")
                                            val removed = clearItemRenderMarkersInRange(source.level.dimension(), source.position, radius)
                                            source.sendSuccess({ Component.literal("[Katton] Removed $removed item render marker(s) within $radius blocks.") }, true)
                                            1
                                        }
                                )
                        )
                )
                .then(
                    literal("config")
                        .then(
                            literal("list")
                                .executes {
                                    val source = it.source
                                    val packIds = KattonConfigManager.knownPackIds()
                                    if (packIds.isEmpty()) {
                                        source.sendSuccess({ Component.literal("[Katton] No configs loaded.") }, false)
                                        return@executes 1
                                    }
                                    val lines = packIds.sorted().joinToString("\n") { packId ->
                                        val entries = KattonConfigManager.all(packId)
                                        if (entries.isEmpty()) {
                                            "  [$packId] (empty)"
                                        } else {
                                            "  [$packId] " + entries.entries.joinToString(", ") { (k, v) -> "$k=$v" }
                                        }
                                    }
                                    source.sendSuccess({ Component.literal("[Katton] Configs:\n$lines") }, false)
                                    1
                                }
                                .then(
                                    Commands.argument("pack", StringArgumentType.word())
                                        .suggests(packSuggestion)
                                        .executes {
                                            val source = it.source
                                            val packId = StringArgumentType.getString(it, "pack")
                                            val entries = KattonConfigManager.all(packId)
                                            if (entries.isEmpty()) {
                                                source.sendSuccess({ Component.literal("[Katton] [$packId] (empty)") }, false)
                                            } else {
                                                val text = entries.entries.joinToString(" | ") { (k, v) -> "$k=$v" }
                                                source.sendSuccess({ Component.literal("[Katton] [$packId] $text") }, false)
                                            }
                                            1
                                        }
                                )
                        )
                        .then(
                            literal("get")
                                .then(
                                    Commands.argument("pack", StringArgumentType.word())
                                        .suggests(packSuggestion)
                                        .then(
                                            Commands.argument("key", StringArgumentType.word())
                                                .suggests(keySuggestion("pack"))
                                                .executes {
                                                    val source = it.source
                                                    val packId = StringArgumentType.getString(it, "pack")
                                                    val key = StringArgumentType.getString(it, "key")
                                                    val value = KattonConfigManager.get(packId, key)
                                                    if (value != null) {
                                                        source.sendSuccess({ Component.literal("[Katton] [$packId] $key = $value") }, false)
                                                        1
                                                    } else {
                                                        source.sendFailure(Component.literal("[Katton] Key '$key' not found in pack '$packId'"))
                                                        0
                                                    }
                                                }
                                        )
                                )
                        )
                        .then(
                            literal("set")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(
                                    Commands.argument("pack", StringArgumentType.word())
                                        .suggests(packSuggestion)
                                        .then(
                                            Commands.argument("key", StringArgumentType.word())
                                                .then(
                                                    Commands.argument("value", StringArgumentType.greedyString())
                                                        .executes {
                                                            val source = it.source
                                                            val packId = StringArgumentType.getString(it, "pack")
                                                            val key = StringArgumentType.getString(it, "key")
                                                            val rawValue = StringArgumentType.getString(it, "value")
                                                            val value = parseConfigValue(rawValue)
                                                            if (KattonConfigManager.set(packId, key, value)) {
                                                                source.sendSuccess({ Component.literal("[Katton] [$packId] $key = $value") }, true)
                                                            } else {
                                                                source.sendFailure(Component.literal("[Katton] Failed to set '$key' in pack '$packId'"))
                                                            }
                                                            1
                                                        }
                                                )
                                        )
                                )
                        )
                        .then(
                            literal("reset")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(
                                    Commands.argument("pack", StringArgumentType.word())
                                        .suggests(packSuggestion)
                                        .then(
                                            Commands.argument("key", StringArgumentType.word())
                                                .suggests(keySuggestion("pack"))
                                                .executes {
                                                    val source = it.source
                                                    val packId = StringArgumentType.getString(it, "pack")
                                                    val key = StringArgumentType.getString(it, "key")
                                                    if (KattonConfigManager.remove(packId, key)) {
                                                        source.sendSuccess({ Component.literal("[Katton] [$packId] removed '$key'") }, true)
                                                    } else {
                                                        source.sendFailure(Component.literal("[Katton] Failed to remove '$key' in pack '$packId'"))
                                                    }
                                                    1
                                                }
                                        )
                                )
                        )
                )
                .then(
                    literal("debug")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(
                            literal("registryLogging")
                                .executes {
                                    it.source.sendSuccess({ Component.literal("[Katton] debugRegistryLogging=${Katton.debugRegistryLogging}") }, false)
                                    1
                                }
                                .then(
                                    literal("on")
                                        .executes {
                                            Katton.debugRegistryLogging = true
                                            it.source.sendSuccess({ Component.literal("[Katton] debugRegistryLogging=true") }, true)
                                            1
                                        }
                                )
                                .then(
                                    literal("off")
                                        .executes {
                                            Katton.debugRegistryLogging = false
                                            it.source.sendSuccess({ Component.literal("[Katton] debugRegistryLogging=false") }, true)
                                            1
                                        }
                                )
                        )
                )
        )
    }

    private fun sendHelp(source: CommandSourceStack): Int {
        source.sendSuccess({ Component.literal("[Katton] /katton help | status | registry | registry stale | reload | itemrender spawn <item> [still|spin|float|pulse|showcase] [scale] [lifetimeTicks] | itemrender clear <radius> | debug registryLogging [on|off] | config ...") }, false)
        return 1
    }

    private fun spawnItemRenderMarker(
        source: CommandSourceStack,
        itemId: Identifier,
        presetName: String,
        scale: Float,
        lifetimeTicks: Int
    ): Int {
        val item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null)
        if (item == null) {
            source.sendFailure(Component.literal("[Katton] Unknown item: $itemId"))
            return 0
        }

        val animations = itemRenderPreset(presetName)
        if (animations == null) {
            source.sendFailure(Component.literal("[Katton] Unknown itemrender preset: $presetName"))
            return 0
        }

        val pos = itemRenderSpawnPosition(source)
        val marker = itemRenderMarker(
            level = source.level.dimension(),
            pos = pos,
            stack = ItemStack(item),
            displayContext = ItemDisplayContext.FIXED,
            scale = scale,
            fullBright = false,
            maxDistance = 96.0,
            lifetimeTicks = lifetimeTicks,
            animations = animations,
            playingAnimationID = animations.keys.toList()
        )
        val markerId = showItemRenderMarker(marker)
        source.sendSuccess({ Component.literal("[Katton] Spawned item render marker $markerId at ${formatVec3(pos)}.") }, true)
        return 1
    }

    private fun itemRenderSpawnPosition(source: CommandSourceStack): Vec3 {
        val entity = source.entity
        if (entity != null) {
            return entity.getEyePosition().add(entity.lookAngle.scale(2.0))
        }
        return source.position
    }

    private fun itemRenderPreset(name: String): Map<String, (ClientItemRenderAnimationSetBuilder) -> Unit>? {
        val spin: Pair<String, (ClientItemRenderAnimationSetBuilder) -> Unit> = "spin" to { set ->
            set.durationTicks = 80
            set.rotate(
                mode = ClientItemRenderAnimationMode.RELATIVE,
            ) {
                keyframe(0.0f, 0.0, 0.0, 0.0)
                keyframe(1.0f, 0.0, 360.0, 0.0)
            }
        }
        val float: Pair<String, (ClientItemRenderAnimationSetBuilder) -> Unit> = "float" to { set ->
            set.durationTicks = 60
            set.translate(
                mode = ClientItemRenderAnimationMode.RELATIVE,
            ) {
                keyframe(0.0f, 0.0, 0.0, 0.0)
                keyframe(0.5f, 0.0, 0.25, 0.0, ClientItemRenderEasing.EASE_IN_OUT)
                keyframe(1.0f, 0.0, 0.0, 0.0, ClientItemRenderEasing.EASE_IN_OUT)
            }
        }
        val pulse: Pair<String, (ClientItemRenderAnimationSetBuilder) -> Unit> = "pulse" to { set ->
            set.durationTicks = 50
            set.scale(
                mode = ClientItemRenderAnimationMode.ABSOLUTE,
            ) {
                keyframe(0.0f, 1.0, 1.0, 1.0)
                keyframe(0.5f, 1.15, 1.15, 1.15, ClientItemRenderEasing.EASE_OUT)
                keyframe(1.0f, 1.0, 1.0, 1.0, ClientItemRenderEasing.EASE_IN)
            }
        }

        return when (name.lowercase()) {
            "still" -> emptyMap()
            "spin" -> mapOf(spin)
            "float" -> mapOf(float)
            "pulse" -> mapOf(pulse)
            "showcase" -> linkedMapOf(spin, float, pulse)
            else -> null
        }
    }

    private fun formatVec3(pos: Vec3): String = "%.2f %.2f %.2f".format(pos.x, pos.y, pos.z)

    private fun parseConfigValue(raw: String): Any {
        // Boolean
        when (raw.lowercase()) {
            "true" -> return true
            "false" -> return false
        }
        // Int
        raw.toIntOrNull()?.let { return it }
        // Double
        raw.toDoubleOrNull()?.let { return it }
        // Fallback: string
        return raw
    }

    @JvmStatic
    fun reloadScript(server: MinecraftServer): Boolean {
        val isDedicated = server.isDedicatedServer

        ScriptReloadManager.reloadScriptsAsync(server) { serverOk ->
            server.execute {
                if (serverOk) {
                    syncCommandTree(server)
                }
                if (!isDedicated && Katton.hasClient) {
                    ScriptReloadManager.reloadClientScriptsAsync()
                }
            }
        }

        return true
    }

    @JvmStatic
    fun syncCommandTree(server: MinecraftServer) {
        server.playerList.players.forEach(server.commands::sendCommands)
    }
}
