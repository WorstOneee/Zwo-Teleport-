package br.com.zwo.zwoteleport.command;

import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.request.TpaService;
import br.com.zwo.zwoteleport.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /tpacancel (alias /tpcancelar): cancela os pedidos enviados.
 */
public final class TpaCancelCommand implements TabExecutor {

    private final TpaService tpa;
    private final Messages messages;

    public TpaCancelCommand(TpaService tpa, Messages messages) {
        this.tpa = tpa;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandSupport.requirePlayer(sender, messages);
        if (player != null && CommandSupport.require(player, Permissions.TPA, messages)) {
            tpa.cancelOutgoing(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
