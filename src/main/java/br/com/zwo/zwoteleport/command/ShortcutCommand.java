package br.com.zwo.zwoteleport.command;

import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.util.Permissions;
import br.com.zwo.zwoteleport.warp.WarpTeleporter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Comando registrado que leva a uma warp fixa (ex.: /loja).
 */
public final class ShortcutCommand implements TabExecutor {

    private final WarpTeleporter teleporter;
    private final Messages messages;
    private final String warp;

    public ShortcutCommand(WarpTeleporter teleporter, Messages messages, String warp) {
        this.teleporter = teleporter;
        this.messages = messages;
        this.warp = warp;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandSupport.requirePlayer(sender, messages);
        if (player == null || !CommandSupport.require(player, Permissions.WARP_USE, messages)) {
            return true;
        }
        if (args.length != 0) {
            messages.send(player, "general.invalid-usage", "usage", "/" + label);
            return true;
        }
        teleporter.teleport(player, warp);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
