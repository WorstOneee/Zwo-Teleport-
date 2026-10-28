package br.com.zwo.zwoteleport.command;

import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.request.RequestType;
import br.com.zwo.zwoteleport.request.TpaService;
import br.com.zwo.zwoteleport.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /tpa &lt;jogador&gt; e /tpahere &lt;jogador&gt;.
 */
public final class TpaCommand implements TabExecutor {

    private final TpaService tpa;
    private final Messages messages;
    private final RequestType type;

    public TpaCommand(TpaService tpa, Messages messages, RequestType type) {
        this.tpa = tpa;
        this.messages = messages;
        this.type = type;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = CommandSupport.requirePlayer(sender, messages);
        if (player == null || !CommandSupport.require(player, permission(), messages)) {
            return true;
        }
        if (args.length != 1) {
            messages.send(player, "general.invalid-usage", "usage", type == RequestType.TPA ? "/tpa <jogador>" : "/tpahere <jogador>");
            return true;
        }
        CommandSupport.findPlayer(player, args[0], messages, true).ifPresent(target -> tpa.send(player, target, type));
        return true;
    }

    private String permission() {
        return type == RequestType.TPA ? Permissions.TPA : Permissions.TPAHERE;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(permission())) {
            return List.of();
        }
        List<String> names = CommandSupport.playerNames(sender, args[0], true);
        names.remove(sender.getName());
        return names;
    }
}
