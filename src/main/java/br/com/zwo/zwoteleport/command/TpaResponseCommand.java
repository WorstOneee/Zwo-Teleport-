package br.com.zwo.zwoteleport.command;

import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.request.TpaService;
import br.com.zwo.zwoteleport.util.NameMatcher;
import br.com.zwo.zwoteleport.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /tpaccept [jogador] (alias /tpaceitar) e /tpdeny [jogador] (alias /tpnegar). Sem nome, responde
 * ao pedido mais recente.
 */
public final class TpaResponseCommand implements TabExecutor {

    private final TpaService tpa;
    private final Messages messages;
    private final boolean accept;

    public TpaResponseCommand(TpaService tpa, Messages messages, boolean accept) {
        this.tpa = tpa;
        this.messages = messages;
        this.accept = accept;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandSupport.requirePlayer(sender, messages);
        if (player == null || !CommandSupport.require(player, Permissions.TPA_RESPOND, messages)) {
            return true;
        }
        if (args.length > 1) {
            messages.send(player, "general.invalid-usage", "usage", accept ? "/tpaccept [jogador]" : "/tpdeny [jogador]");
            return true;
        }
        String name = args.length == 1 ? args[0] : null;
        if (accept) {
            tpa.accept(player, name);
        } else {
            tpa.deny(player, name);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        return NameMatcher.suggest(tpa.incomingNames(player), args[0]);
    }
}
