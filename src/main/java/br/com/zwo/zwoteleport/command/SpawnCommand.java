package br.com.zwo.zwoteleport.command;

import br.com.zwo.zwoteleport.api.TeleportCause;
import br.com.zwo.zwoteleport.config.Settings;
import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.teleport.BypassRules;
import br.com.zwo.zwoteleport.teleport.Destination;
import br.com.zwo.zwoteleport.teleport.TeleportPlan;
import br.com.zwo.zwoteleport.teleport.TeleportService;
import br.com.zwo.zwoteleport.util.Permissions;
import br.com.zwo.zwoteleport.warp.WarpNames;
import br.com.zwo.zwoteleport.warp.WarpPoint;
import br.com.zwo.zwoteleport.warp.WarpService;
import br.com.zwo.zwoteleport.warp.WarpTeleporter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * /spawn: o jogador vai ao spawn com as regras de warp (warmup, cooldown, combate).
 * /spawn &lt;jogador&gt;: a equipe envia outro jogador, sem warmup, cooldown ou bloqueio de combate.
 */
public final class SpawnCommand implements TabExecutor {

    private final WarpService warps;
    private final WarpTeleporter teleporter;
    private final TeleportService teleports;
    private final Messages messages;
    private final Supplier<Settings> settings;

    public SpawnCommand(WarpService warps, WarpTeleporter teleporter, TeleportService teleports, Messages messages, Supplier<Settings> settings) {
        this.warps = warps;
        this.teleporter = teleporter;
        this.teleports = teleports;
        this.messages = messages;
        this.settings = settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            Player player = CommandSupport.requirePlayer(sender, messages);
            if (player != null && CommandSupport.require(player, Permissions.WARP_USE, messages)) {
                teleporter.teleport(player, WarpNames.SPAWN, TeleportCause.SPAWN, "spawn.teleported");
            }
            return true;
        }
        if (args.length != 1) {
            messages.send(sender, "general.invalid-usage", "usage", "/" + label + " [jogador]");
            return true;
        }
        if (!BypassRules.spawnOthers(sender::hasPermission)) {
            messages.send(sender, "general.no-permission");
            return true;
        }
        Optional<Player> target = CommandSupport.findPlayer(sender, args[0], messages, false);
        if (target.isEmpty()) {
            return true;
        }
        Optional<WarpPoint> spawn = warps.get(WarpNames.SPAWN);
        if (spawn.isEmpty()) {
            messages.send(sender, "spawn.not-set");
            return true;
        }
        Player player = target.get();
        WarpPoint warp = spawn.get();
        Settings config = settings.get();
        TeleportPlan plan = new TeleportPlan(
            TeleportCause.SPAWN_OTHER,
            Destination.ofWarp(warp, warp.displayName()),
            null,
            null,
            false,
            false,
            false,
            config.safety().modeFor(TeleportCause.SPAWN_OTHER),
            config.safety().respectWorldBorder(),
            "spawn.teleported",
            new String[] {"warp", warp.displayName()},
            true);
        teleports.start(player, plan).thenAccept(result -> {
            if (result.completed()) {
                messages.send(sender, "spawn.teleported-other", "player", player.getName());
                return;
            }
            String reasonKey = TeleportService.failureKey(result.reason());
            String reason = reasonKey == null ? result.reason().name() : messages.plain(reasonKey, "warp", warp.displayName(), "player", player.getName());
            messages.send(sender, "tp.failed-other", "player", player.getName(), "reason", reason);
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !BypassRules.spawnOthers(sender::hasPermission)) {
            return List.of();
        }
        return CommandSupport.playerNames(sender, args[0], false);
    }
}
