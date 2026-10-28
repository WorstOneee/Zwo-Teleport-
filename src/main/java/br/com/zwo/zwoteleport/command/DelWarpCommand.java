package br.com.zwo.zwoteleport.command;

import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.util.NameMatcher;
import br.com.zwo.zwoteleport.util.Permissions;
import br.com.zwo.zwoteleport.warp.WarpNames;
import br.com.zwo.zwoteleport.warp.WarpPoint;
import br.com.zwo.zwoteleport.warp.WarpService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * /delwarp &lt;nome&gt;.
 */
public final class DelWarpCommand implements TabExecutor {

    private final WarpService warps;
    private final Messages messages;

    public DelWarpCommand(WarpService warps, Messages messages) {
        this.warps = warps;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!CommandSupport.require(sender, Permissions.WARP_ADMIN, messages)) {
            return true;
        }
        if (args.length != 1) {
            messages.send(sender, "general.invalid-usage", "usage", "/delwarp <nome>");
            return true;
        }
        String name = WarpNames.normalize(args[0]);
        Optional<WarpPoint> existing = warps.get(name);
        if (existing.isEmpty()) {
            messages.send(sender, "warp.not-found", "warp", args[0]);
            return true;
        }
        String shownName = existing.get().displayName();
        warps.delete(name).thenAccept(status -> {
            switch (status) {
                case SAVED -> {
                    messages.send(sender, "warp.deleted", "warp", shownName);
                    if (name.equals(WarpNames.SPAWN)) {
                        messages.send(sender, "warp.spawn-deleted");
                    }
                }
                case FAILED -> messages.send(sender, "warp.save-failed");
                case READ_ONLY -> messages.send(sender, "warp.read-only");
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(Permissions.WARP_ADMIN)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (WarpPoint warp : warps.all()) {
            names.add(warp.name());
        }
        return NameMatcher.suggest(names, args[0]);
    }
}
