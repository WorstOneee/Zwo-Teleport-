package br.com.zwo.zwoteleport.command;

import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.util.NameMatcher;
import br.com.zwo.zwoteleport.util.Permissions;
import br.com.zwo.zwoteleport.warp.WarpNames;
import br.com.zwo.zwoteleport.warp.WarpPoint;
import br.com.zwo.zwoteleport.warp.WarpService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * /setwarp &lt;nome&gt; [nome bonito] e /setspawn. Grava posição, mundo, yaw e pitch atuais.
 * Atualizar uma warp existente mantém a permissão específica dela.
 */
public final class SetWarpCommand implements TabExecutor {

    private final Plugin plugin;
    private final WarpService warps;
    private final Messages messages;
    private final boolean spawn;

    public SetWarpCommand(Plugin plugin, WarpService warps, Messages messages, boolean spawn) {
        this.plugin = plugin;
        this.warps = warps;
        this.messages = messages;
        this.spawn = spawn;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandSupport.requirePlayer(sender, messages);
        if (player == null || !CommandSupport.require(player, Permissions.WARP_ADMIN, messages)) {
            return true;
        }
        String name;
        String displayName;
        if (spawn) {
            name = WarpNames.SPAWN;
            displayName = "&aSpawn";
        } else {
            if (args.length < 1) {
                messages.send(player, "general.invalid-usage", "usage", "/setwarp <nome> [nome bonito]");
                return true;
            }
            name = WarpNames.normalize(args[0]);
            displayName = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : name;
        }
        if (!WarpNames.isValid(name)) {
            messages.send(player, "warp.invalid-name");
            return true;
        }
        Location location = player.getLocation();
        if (location.getWorld() == null) {
            messages.send(player, "teleport.invalid-destination");
            return true;
        }
        Optional<WarpPoint> existing = warps.get(name);
        if (spawn && existing.isPresent()) {
            displayName = existing.get().displayName();
        }
        WarpPoint warp = new WarpPoint(name, displayName, location.getWorld().getName(),
            location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(),
            existing.map(WarpPoint::permission).orElse(""));
        String shownName = warp.displayName();
        String successKey = existing.isPresent() ? "warp.updated" : "warp.created";
        warps.save(warp).thenAccept(status -> {
            switch (status) {
                case SAVED -> {
                    messages.send(player, successKey, "warp", shownName);
                    if (!spawn && conflictsWithCommand(name)) {
                        messages.send(player, "warp.command-conflict", "warp", name);
                    }
                }
                case FAILED -> messages.send(player, "warp.save-failed");
                case READ_ONLY -> messages.send(player, "warp.read-only");
            }
        });
        return true;
    }

    /** Se /nome já é um comando de outro plugin (o atalho automático não funcionará). */
    private boolean conflictsWithCommand(String name) {
        Command registered = Bukkit.getCommandMap().getCommand(name);
        if (registered == null) {
            return false;
        }
        return !(registered instanceof PluginCommand pluginCommand && pluginCommand.getPlugin() == plugin
            && (name.equals(WarpNames.SHOP) || name.equals(WarpNames.SPAWN)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (spawn || args.length != 1 || !sender.hasPermission(Permissions.WARP_ADMIN)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (WarpPoint warp : warps.all()) {
            names.add(warp.name());
        }
        return NameMatcher.suggest(names, args[0]);
    }
}
