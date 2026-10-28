package br.com.zwo.zwoteleport.command;

import br.com.zwo.zwoteleport.api.TeleportCause;
import br.com.zwo.zwoteleport.api.TeleportResult;
import br.com.zwo.zwoteleport.config.Settings;
import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.teleport.Destination;
import br.com.zwo.zwoteleport.teleport.DestinationValidator;
import br.com.zwo.zwoteleport.teleport.TeleportPlan;
import br.com.zwo.zwoteleport.teleport.TeleportService;
import br.com.zwo.zwoteleport.util.Permissions;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * /tp administrativo, com a mesma sintaxe da versão 2.x:
 * <ul>
 *   <li>/tp &lt;jogador&gt; — você até o jogador</li>
 *   <li>/tp &lt;x&gt; &lt;y&gt; &lt;z&gt; [yaw pitch] — você até as coordenadas (aceita ~)</li>
 *   <li>/tp &lt;jogador&gt; &lt;alvo&gt; — jogador até o alvo</li>
 *   <li>/tp &lt;jogador&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt; [yaw pitch] — jogador até as coordenadas</li>
 * </ul>
 * Sem warmup, cooldown ou bloqueio de combate. Coordenadas não finitas são recusadas.
 */
public final class TpCommand implements TabExecutor {

    private static final String USAGE = "/tp <jogador> | /tp <x> <y> <z> | /tp <jogador> <alvo|x y z>";

    private final TeleportService teleports;
    private final Messages messages;
    private final Supplier<Settings> settings;

    public TpCommand(TeleportService teleports, Messages messages, Supplier<Settings> settings) {
        this.teleports = teleports;
        this.messages = messages;
        this.settings = settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!CommandSupport.require(sender, Permissions.TP_ADMIN, messages)) {
            return true;
        }
        switch (args.length) {
            case 1 -> {
                Player self = CommandSupport.requirePlayer(sender, messages);
                if (self == null) {
                    return true;
                }
                resolveTarget(sender, args[0]).ifPresent(target -> teleportToPlayer(sender, self, target));
            }
            case 3, 5 -> {
                Player self = CommandSupport.requirePlayer(sender, messages);
                if (self == null) {
                    return true;
                }
                Location destination = parse(self.getLocation(), args, 0);
                if (destination == null) {
                    messages.send(sender, "tp.invalid-coordinates");
                    return true;
                }
                teleports.start(self, plan(Destination.of(destination), "tp.teleported-coordinates", coordinates(destination)));
            }
            case 2 -> {
                Optional<Player> moving = resolveTarget(sender, args[0]);
                if (moving.isEmpty()) {
                    return true;
                }
                Optional<Player> target = resolveTarget(sender, args[1]);
                if (target.isEmpty()) {
                    return true;
                }
                if (moving.get().equals(sender)) {
                    teleportToPlayer(sender, moving.get(), target.get());
                    return true;
                }
                Player who = moving.get();
                Player to = target.get();
                teleports.start(who, plan(Destination.ofPlayer(to.getUniqueId(), to.getName(), Set.of()),
                        "tp.received-admin", new String[] {"sender", sender.getName()}))
                    .thenAccept(result -> report(sender, who, result, "tp.teleported-other", "player", who.getName(), "target", to.getName()));
            }
            case 4, 6 -> {
                Optional<Player> moving = resolveTarget(sender, args[0]);
                if (moving.isEmpty()) {
                    return true;
                }
                Player who = moving.get();
                Location destination = parse(who.getLocation(), args, 1);
                if (destination == null) {
                    messages.send(sender, "tp.invalid-coordinates");
                    return true;
                }
                String[] coords = coordinates(destination);
                teleports.start(who, plan(Destination.of(destination), "tp.received-admin", new String[] {"sender", sender.getName()}))
                    .thenAccept(result -> report(sender, who, result, "tp.teleported-other-coordinates",
                        "player", who.getName(), "x", coords[1], "y", coords[3], "z", coords[5]));
            }
            default -> messages.send(sender, "general.invalid-usage", "usage", USAGE);
        }
        return true;
    }

    private void teleportToPlayer(CommandSender sender, Player self, Player target) {
        teleports.start(self, plan(Destination.ofPlayer(target.getUniqueId(), target.getName(), Set.of()),
            "tp.teleported-self", new String[] {"target", target.getName()}));
    }

    private TeleportPlan plan(Destination destination, String successKey, String[] placeholders) {
        Settings config = settings.get();
        return new TeleportPlan(
            TeleportCause.ADMIN_COMMAND,
            destination,
            null,
            null,
            false,
            false,
            false,
            config.safety().modeFor(TeleportCause.ADMIN_COMMAND),
            false,
            successKey,
            placeholders,
            true);
    }

    private void report(CommandSender sender, Player who, TeleportResult result, String successKey, String... placeholders) {
        if (result.completed()) {
            messages.send(sender, successKey, placeholders);
            return;
        }
        String reasonKey = TeleportService.failureKey(result.reason());
        String reason = reasonKey == null ? result.reason().name() : messages.plain(reasonKey, "seconds", "?", "player", who.getName());
        messages.send(sender, "tp.failed-other", "player", who.getName(), "reason", reason);
    }

    private Optional<Player> resolveTarget(CommandSender sender, String input) {
        if ((input.equalsIgnoreCase("@s") || input.equalsIgnoreCase("@p")) && sender instanceof Player player) {
            return Optional.of(player);
        }
        return CommandSupport.findPlayer(sender, input, messages, false);
    }

    /** Lê x y z [yaw pitch] a partir de {@code offset}; nulo se algum valor for inválido. */
    static Location parse(Location base, String[] args, int offset) {
        try {
            double x = DestinationValidator.parseCoordinate(args[offset], base.getX());
            double y = DestinationValidator.parseCoordinate(args[offset + 1], base.getY());
            double z = DestinationValidator.parseCoordinate(args[offset + 2], base.getZ());
            float yaw = base.getYaw();
            float pitch = base.getPitch();
            if (args.length >= offset + 5) {
                yaw = (float) DestinationValidator.parseCoordinate(args[offset + 3], base.getYaw());
                pitch = (float) DestinationValidator.parseCoordinate(args[offset + 4], base.getPitch());
            }
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90f || pitch > 90f) {
                return null;
            }
            return new Location(base.getWorld(), x, y, z, yaw, pitch);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static String[] coordinates(Location location) {
        return new String[] {"x", format(location.getX()), "y", format(location.getY()), "z", format(location.getZ())};
    }

    private static String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-5) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.US, "%.2f", value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(Permissions.TP_ADMIN) || args.length > 2) {
            return List.of();
        }
        return CommandSupport.playerNames(sender, args[args.length - 1], false);
    }
}
