package top.katton.paper;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;
import top.katton.Katton;
import top.katton.command.ScriptCommand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "status" -> {
                sender.sendMessage(PaperMessages.tr(
                    sender,
                    "commands.katton.paper.status",
                    Katton.globalState,
                    Katton.server != null
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
                boolean ok = ScriptCommand.reloadScript(Katton.server);
                if (ok) {
                    sender.sendMessage(PaperMessages.tr(sender, "commands.katton.reload.started"));
                } else {
                    sender.sendMessage(PaperMessages.tr(sender, "commands.katton.reload.failed"));
                }
            }
            default -> sender.sendMessage(PaperMessages.tr(sender, "commands.katton.paper.unknown_subcommand"));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(@NonNull CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            List<String> base = new ArrayList<>(List.of("help", "status", "reload"));
            // Filter by typed prefix
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return base.stream()
                .filter(s -> s.startsWith(prefix))
                .collect(Collectors.toList());
        }
        return List.of();
    }

    @Override
    public String permission() {
        return null; // Base command has no permission requirement
    }

    @Override
    public boolean canUse(@NonNull CommandSender sender) {
        return sender.isOp();
    }
}
