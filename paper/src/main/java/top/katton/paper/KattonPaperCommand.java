package top.katton.paper;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import top.katton.Katton;
import top.katton.command.ScriptCommand;
import top.katton.registry.KattonRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Paper implementation of the {@code /katton} command.
 * <p>
 * Subcommands:
 * <ul>
 *   <li>{@code help} — Show usage</li>
 *   <li>{@code status} — Show engine state</li>
 *   <li>{@code registry} — Show registry health snapshot</li>
 *   <li>{@code registry stale} — Show stale retained entries</li>
 *   <li>{@code reload} — Reload script packs (requires admin permission)</li>
 *   <li>{@code debug registryLogging [on|off]} — Toggle verbose registration logging</li>
 * </ul>
 * <p>
 * Note: Paper is server-only — there is no client-side script reload.
 */
public class KattonPaperCommand implements BasicCommand {

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sender.sendMessage(Component.text(
                "[Katton] /katton help | status | registry | registry stale | reload | debug registryLogging [on|off]"
            ));
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "status" -> {
                sender.sendMessage(Component.text(
                    "[Katton] state=" + Katton.globalState +
                    ", serverBound=" + (Katton.server != null) +
                    ", registrationEnabled=" + Katton.registrationEnabled +
                    ", hasClient=" + Katton.hasClient
                ));
            }
            case "registry" -> {
                if (args.length > 1 && "stale".equalsIgnoreCase(args[1])) {
                    handleRegistryStale(sender);
                } else {
                    handleRegistry(sender);
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("katton.admin") && !sender.isOp()) {
                    sender.sendMessage(Component.text("[Katton] You need katton.admin permission to reload."));
                    return;
                }
                if (Katton.server == null) {
                    sender.sendMessage(Component.text("[Katton] Server not ready."));
                    return;
                }
                boolean ok = ScriptCommand.reloadScript(Katton.server);
                if (ok) {
                    sender.sendMessage(Component.text("[Katton] Reload started."));
                } else {
                    sender.sendMessage(Component.text("[Katton] Failed to reload script packs."));
                }
            }
            case "debug" -> {
                if (args.length > 1 && "registryLogging".equalsIgnoreCase(args[1])) {
                    handleDebugRegistryLogging(sender, args);
                } else {
                    sender.sendMessage(Component.text("[Katton] Usage: /katton debug registryLogging [on|off]"));
                }
            }
            default -> sender.sendMessage(Component.text(
                "[Katton] Unknown subcommand. Try /katton help."
            ));
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            List<String> base = new ArrayList<>(List.of("help", "status", "registry", "reload", "debug"));
            // Filter by typed prefix
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return base.stream()
                .filter(s -> s.startsWith(prefix))
                .collect(Collectors.toList());
        }

        String sub = args[0].toLowerCase();
        if ("registry".equals(sub) && args.length == 2) {
            return List.of("stale").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        if ("debug".equals(sub) && args.length == 2) {
            return List.of("registryLogging").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        if ("debug".equals(sub) && "registryLogging".equalsIgnoreCase(args[1]) && args.length == 3) {
            return List.of("on", "off").stream()
                .filter(s -> s.startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }
        return List.of();
    }

    @Override
    public String permission() {
        return null; // Base command has no permission requirement
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return true;
    }

    private void handleRegistry(CommandSender sender) {
        var rows = KattonRegistry.INSTANCE.registryHealthSnapshot();
        String summary = rows.stream()
            .map(row -> row.getKey() + ": entries=" + row.getKattonEntries() +
                ", managed=" + row.getManagedTracked() +
                ", stale=" + row.getStaleRetained())
            .collect(Collectors.joining(" | "));
        sender.sendMessage(Component.text("[Katton] " + summary));
    }

    private void handleRegistryStale(CommandSender sender) {
        var staleRows = KattonRegistry.INSTANCE.registryHealthSnapshot().stream()
            .filter(row -> row.getStaleRetained() > 0)
            .collect(Collectors.toList());
        if (staleRows.isEmpty()) {
            sender.sendMessage(Component.text("[Katton] No stale retained registry entries."));
        } else {
            String text = staleRows.stream()
                .map(row -> row.getKey() + "=" + row.getStaleRetained())
                .collect(Collectors.joining(" | "));
            sender.sendMessage(Component.text("[Katton] Stale retained entries: " + text));
        }
    }

    private void handleDebugRegistryLogging(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("[Katton] debugRegistryLogging=" + Katton.debugRegistryLogging));
            return;
        }
        if ("on".equalsIgnoreCase(args[2])) {
            Katton.debugRegistryLogging = true;
            sender.sendMessage(Component.text("[Katton] debugRegistryLogging=true"));
        } else if ("off".equalsIgnoreCase(args[2])) {
            Katton.debugRegistryLogging = false;
            sender.sendMessage(Component.text("[Katton] debugRegistryLogging=false"));
        } else {
            sender.sendMessage(Component.text("[Katton] Usage: /katton debug registryLogging [on|off]"));
        }
    }
}
