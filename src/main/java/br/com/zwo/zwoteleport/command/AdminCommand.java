package br.com.zwo.zwoteleport.command;

import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.util.NameMatcher;
import br.com.zwo.zwoteleport.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * /zwotp &lt;reload|info|combaton|combatoff&gt;.
 */
public final class AdminCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("reload", "info", "combaton", "combatoff");

    private final AdminActions actions;
    private final Messages messages;

    public AdminCommand(AdminActions actions, Messages messages) {
        this.actions = actions;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!CommandSupport.require(sender, Permissions.ZWOTP_ADMIN, messages)) {
            return true;
        }
        if (args.length != 1) {
            messages.send(sender, "zwotp.usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "info" -> info(sender);
            case "combaton" -> combat(sender, true);
            case "combatoff" -> combat(sender, false);
            default -> messages.send(sender, "zwotp.usage");
        }
        return true;
    }

    private void reload(CommandSender sender) {
        actions.reload().thenAccept(outcome -> {
            if (!outcome.applied()) {
                messages.send(sender, "general.reload-failed");
                for (String error : outcome.errors()) {
                    messages.send(sender, "general.reload-error-line", "error", error);
                }
                return;
            }
            messages.send(sender, "general.reloaded");
            for (String warning : outcome.warnings()) {
                messages.send(sender, "general.reload-error-line", "error", warning);
            }
        });
    }

    private void info(CommandSender sender) {
        messages.send(sender, "zwotp.info-header", "version", actions.version());
        for (Map.Entry<String, String> line : actions.info()) {
            messages.send(sender, "zwotp.info-line", "key", line.getKey(), "value", line.getValue());
        }
    }

    private void combat(CommandSender sender, boolean enabled) {
        Optional<String> failure = actions.setCombatEnabled(enabled);
        messages.send(sender, enabled ? "zwotp.combat-on" : "zwotp.combat-off");
        failure.ifPresent(reason -> messages.send(sender, "zwotp.combat-save-failed", "reason", reason));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(Permissions.ZWOTP_ADMIN)) {
            return List.of();
        }
        return NameMatcher.suggest(SUBCOMMANDS, args[0]);
    }
}
