package top.katton.paper;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NonNull;
import top.katton.Katton;
import top.katton.command.ScriptCommand;
import top.katton.registry.KattonRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class KattonPaperCommand implements BasicCommand {

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sender.sendMessage(Component.text(
                "[Katton] /katton help | status | reload"
            ));
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "status" -> {
                sender.sendMessage(Component.text(
                    "[Katton] state=" + Katton.globalState +
                    ", serverBound=" + (Katton.server != null)
                ));
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
            default -> sender.sendMessage(Component.text(
                "[Katton] Unknown subcommand. Try /katton help."
            ));
        }
    }

    @Override
    public @NonNull Collection<String> suggest(@NonNull CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            List<String> base = new ArrayList<>(List.of("help", "status", "registry", "reload", "debug"));
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
