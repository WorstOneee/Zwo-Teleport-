package br.com.zwo.zwoteleport.command;

import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.util.NameMatcher;
import br.com.zwo.zwoteleport.util.Permissions;
import br.com.zwo.zwoteleport.warp.WarpPoint;
import br.com.zwo.zwoteleport.warp.WarpService;
import br.com.zwo.zwoteleport.warp.WarpTeleporter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /warp [nome] e /warps. Sem nome, lista as warps que o jogador pode usar.
 */
public final class WarpCommand implements TabExecutor {

    private final WarpService warps;
    private final WarpTeleporter teleporter;
    private final Messages messages;
    private final boolean listOnly;

    public WarpCommand(WarpService warps, WarpTeleporter teleporter, Messages messages, boolean listOnly) {
        this.warps = warps;
        this.teleporter = teleporter;
        this.messages = messages;
        this.listOnly = listOnly;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandSupport.requirePlayer(sender, messages);
        if (player == null || !CommandSupport.require(player, Permissions.WARP_USE, messages)) {
            return true;
        }
        if (listOnly || args.length == 0) {
            sendList(player);
            return true;
        }
        if (args.length != 1) {
            messages.send(player, "general.invalid-usage", "usage", "/warp [nome]");
            return true;
        }
        teleporter.teleport(player, args[0]);
        return true;
    }

    private void sendList(Player player) {
        List<String> names = new ArrayList<>();
        for (WarpPoint warp : warps.all()) {
            if (teleporter.canUse(player, warp)) {
                names.add(warp.displayName());
            }
        }
        if (names.isEmpty()) {
            messages.send(player, "warp.list-empty");
            return;
        }
        String separator = messages.catalog().raw("warp.list-separator");
        messages.send(player, "warp.list", "warps", String.join(separator, names));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (listOnly || args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (WarpPoint warp : warps.all()) {
            if (teleporter.canUse(player, warp)) {
                names.add(warp.name());
            }
        }
        return NameMatcher.suggest(names, args[0]);
    }
}
