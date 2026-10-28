package br.com.zwo.zwoteleport.command;

import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.util.NameMatcher;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utilidades comuns aos comandos.
 */
final class CommandSupport {

    private CommandSupport() {
    }

    static Player requirePlayer(CommandSender sender, Messages messages) {
        if (sender instanceof Player player) {
            return player;
        }
        messages.send(sender, "general.player-only");
        return null;
    }

    static boolean require(CommandSender sender, String permission, Messages messages) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        messages.send(sender, "general.no-permission");
        return false;
    }

    /**
     * Encontra um jogador online por nome exato ou prefixo único. Com {@code respectVisibility},
     * jogadores que o remetente não pode ver (vanish) são tratados como offline.
     */
    static Optional<Player> findPlayer(CommandSender sender, String input, Messages messages, boolean respectVisibility) {
        NameMatcher.Result<Player> result = NameMatcher.resolve(visiblePlayers(sender, respectVisibility), Player::getName, input);
        if (result.match().isPresent()) {
            return result.match();
        }
        if (result.isAmbiguous()) {
            messages.send(sender, "general.player-ambiguous", "player", input, "matches", String.join(", ", result.ambiguous()));
        } else {
            messages.send(sender, "general.player-not-found", "player", input);
        }
        return Optional.empty();
    }

    static List<String> playerNames(CommandSender sender, String typed, boolean respectVisibility) {
        List<String> names = new ArrayList<>();
        for (Player player : visiblePlayers(sender, respectVisibility)) {
            names.add(player.getName());
        }
        return NameMatcher.suggest(names, typed);
    }

    private static List<Player> visiblePlayers(CommandSender sender, boolean respectVisibility) {
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!respectVisibility || !(sender instanceof Player viewer) || viewer.canSee(player)) {
                players.add(player);
            }
        }
        return players;
    }
}
