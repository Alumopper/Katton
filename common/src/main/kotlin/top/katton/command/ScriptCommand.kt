package top.katton.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import top.katton.Katton
import top.katton.config.KattonConfigManager
import top.katton.engine.ScriptReloadManager
import top.katton.registry.KattonRegistry

object ScriptCommand {

    private val packSuggestion: SuggestionProvider<CommandSourceStack> = SuggestionProvider { _, builder ->
        SharedSuggestionProvider.suggest(KattonConfigManager.knownPackIds(), builder)
    }

    private fun keySuggestion(packArg: String): SuggestionProvider<CommandSourceStack> = SuggestionProvider { ctx, builder ->
        val pack = StringArgumentType.getString(ctx, packArg)
        SharedSuggestionProvider.suggest(KattonConfigManager.all(pack).keys, builder)
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
        source.sendSuccess({ Component.literal("[Katton] /katton help | status | registry | registry stale | reload | debug registryLogging [on|off] | config list [pack] | config get <pack> <key> | config set <pack> <key> <value> | config reset <pack> <key>") }, false)
        return 1
    }

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
