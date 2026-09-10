package top.katton.paper;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;
import top.katton.Katton;
import top.katton.command.ScriptCommand;
import top.katton.api.event.EventCapabilities;
import top.katton.api.inject.InjectionCapabilities;
import top.katton.engine.ScriptIssueReporter;
import top.katton.engine.ScriptReloadManager;
import top.katton.pack.ScriptPackManager;
import top.katton.pack.ScriptPackScope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Implements the /katton command for Paper, providing subcommands for checking status and reloading scripts.
 */
public class KattonPaperCommand implements BasicCommand {

    /**
     * Creates the Paper command handler.
     */
    public KattonPaperCommand() {
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sender.sendMessage(PaperMessages.tr(sender, "commands.katton.paper.help"));
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "dev" -> {
                if (!sender.hasPermission("katton.admin") && !sender.isOp()) {
                    sender.sendMessage("Katton administrator permission required.");
                    return;
                }
                if (args.length == 2 && "enable".equalsIgnoreCase(args[1])) {
                    sender.sendMessage(top.katton.dev.KattonDevBridge.enable());
                } else if (args.length == 2 && "disable".equalsIgnoreCase(args[1])) {
                    sender.sendMessage(top.katton.dev.KattonDevBridge.disable());
                } else sender.sendMessage("Usage: /katton dev enable|disable");
            }
            case "status" -> {
                sender.sendMessage(PaperMessages.tr(
                    sender,
                    "commands.katton.paper.status",
                    Katton.globalState,
                    Katton.server != null,
                    ScriptReloadManager.isServerReloadRunning()
                ));
            }
            case "reload" -> {
                if (!sender.hasPermission("katton.admin") && !sender.isOp()) {
                    sender.sendMessage(PaperMessages.tr(sender, "commands.katton.paper.reload.no_permission"));
                    return;
                }
                if (Katton.server == null) {
                    sender.sendMessage(PaperMessages.tr(sender, "commands.katton.paper.server_not_ready"));
                    return;
                }
                boolean ok = ScriptCommand.reloadScript(Katton.server, success -> {
                    if (success) {
                        sender.sendMessage("[Katton] Script pack reload completed.");
                    } else {
                        sender.sendMessage(PaperMessages.tr(sender, "commands.katton.reload.failed.logs"));
                    }
                });
                if (ok) {
                    sender.sendMessage(PaperMessages.tr(sender, "commands.katton.reload.started"));
                } else {
                    sender.sendMessage(PaperMessages.tr(sender, "commands.katton.reload.failed"));
                }
            }
            case "errors" -> {
                var issues = ScriptIssueReporter.history();
                if (issues.isEmpty()) {
                    sender.sendMessage("[Katton] No recorded script errors.");
                } else {
                    issues.stream().skip(Math.max(0, issues.size() - 10L)).forEach(issue ->
                        sender.sendMessage("[Katton] " + issue.getTitle() + ": " + issue.getDetail())
                    );
                }
            }
            case "capabilities" -> {
                if (args.length >= 2 && "injection".equalsIgnoreCase(args[1])) {
                    InjectionCapabilities.query().diagnosticLines().forEach(line ->
                        sender.sendMessage("[Katton] " + line)
                    );
                    return;
                }
                if (args.length < 2 || !"events".equalsIgnoreCase(args[1])) {
                    sender.sendMessage("[Katton] Usage: /katton capabilities events [EventObject.onEvent] | injection");
                    return;
                }
                if (args.length >= 3) {
                    var capability = EventCapabilities.query(args[2]);
                    sender.sendMessage("[Katton] " + capability.getId() + ": " + capability.getStatus() +
                        " (" + capability.getExecutionContext() + ") - " + capability.getDetail());
                } else {
                    var capabilities = EventCapabilities.notable();
                    if (capabilities.isEmpty()) sender.sendMessage("[Katton] No partial or unsupported event capabilities.");
                    capabilities.forEach(capability -> sender.sendMessage(
                        "[Katton] " + capability.getId() + ": " + capability.getStatus() + " - " + capability.getDetail()
                    ));
                }
            }
            case "packs" -> handlePacks(sender, args);
            default -> sender.sendMessage(PaperMessages.tr(sender, "commands.katton.paper.unknown_subcommand"));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(@NonNull CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            List<String> base = new ArrayList<>(List.of("help", "status", "reload", "errors", "capabilities", "packs", "dev"));
            // Filter by typed prefix
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return base.stream()
                .filter(s -> s.startsWith(prefix))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && "capabilities".equalsIgnoreCase(args[0])) {
            return List.of("events", "injection");
        }
        if (args.length == 2 && "packs".equalsIgnoreCase(args[0])) {
            return List.of("list", "enable", "disable");
        }
        if (args.length == 2 && "dev".equalsIgnoreCase(args[0])) return List.of("enable", "disable");
        if (args.length == 3 && "packs".equalsIgnoreCase(args[0])) {
            return ScriptPackManager.INSTANCE.listLocalPacksForGui(false).stream()
                .map(view -> view.getSyncId())
                .filter(id -> id.startsWith(args[2]))
                .toList();
        }
        if (args.length == 3 && "capabilities".equalsIgnoreCase(args[0]) && "events".equalsIgnoreCase(args[1])) {
            return EventCapabilities.notable().stream()
                .map(capability -> capability.getId())
                .filter(id -> id.startsWith(args[2]))
                .toList();
        }
        return List.of();
    }

    private void handlePacks(CommandSender sender, String[] args) {
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            var packs = ScriptPackManager.INSTANCE.listLocalPacksForGui(false);
            if (packs.isEmpty()) sender.sendMessage("[Katton] No local script packs.");
            packs.forEach(pack -> sender.sendMessage(
                "[Katton] " + pack.getSyncId() + " " + pack.getVersion() + " enabled=" + pack.getEnabled()
            ));
            return;
        }
        if ((!"enable".equalsIgnoreCase(args[1]) && !"disable".equalsIgnoreCase(args[1])) || args.length < 3) {
            sender.sendMessage("[Katton] Usage: /katton packs list|enable|disable <syncId>");
            return;
        }
        if (!sender.hasPermission("katton.admin") && !sender.isOp()) {
            sender.sendMessage(PaperMessages.tr(sender, "commands.katton.paper.reload.no_permission"));
            return;
        }
        String syncId = args[2];
        var pack = ScriptPackManager.INSTANCE.getPackBySyncId(syncId);
        boolean enabled = "enable".equalsIgnoreCase(args[1]);
        if (pack == null || !ScriptPackManager.INSTANCE.setPackEnabled(syncId, enabled)) {
            sender.sendMessage("[Katton] Could not update pack '" + syncId + "'.");
            return;
        }
        if (pack.getScope() == ScriptPackScope.GLOBAL) {
            sender.sendMessage("[Katton] Updated '" + syncId + "'; restart is required for a global pack.");
            return;
        }
        if (Katton.server == null) {
            sender.sendMessage(PaperMessages.tr(sender, "commands.katton.paper.server_not_ready"));
            return;
        }
        sender.sendMessage("[Katton] Updated '" + syncId + "'; reload started.");
        ScriptCommand.reloadScript(Katton.server, success -> sender.sendMessage(
            success ? "[Katton] Pack state applied for '" + syncId + "'." :
                "[Katton] Pack state was saved, but reload failed for '" + syncId + "'."
        ));
    }

    @Override
    public String permission() {
        return null; // Base command has no permission requirement
    }

    @Override
    public boolean canUse(@NonNull CommandSender sender) {
        return true;
    }
}
