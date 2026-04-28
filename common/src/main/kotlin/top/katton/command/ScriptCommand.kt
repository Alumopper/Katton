package top.katton.command

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import top.katton.Katton
import top.katton.registry.KattonRegistry

object ScriptCommand {

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
                                        "[Katton] state=${Katton.globalState}, serverBound=${Katton.server != null}, clientReloadRunning=${Katton.isClientReloadRunning()}"
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
        source.sendSuccess({ Component.literal("[Katton] /katton help | status | registry | registry stale | reload | debug registryLogging [on|off]") }, false)
        return 1
    }

    fun reloadScript(server: MinecraftServer): Boolean {
        val serverReloaded = Katton.reloadScripts(server)
        val clientReloaded = if (!server.isDedicatedServer) {
            Katton.reloadClientScriptsAsync()
        } else {
            true
        }
        val reloaded = serverReloaded && clientReloaded
        if (serverReloaded) {
            syncCommandTree(server)
        }
        return reloaded
    }

    @JvmStatic
    fun syncCommandTree(server: MinecraftServer) {
        server.playerList.players.forEach(server.commands::sendCommands)
    }
}
