package br.com.zwo.zwoteleport.api.event;

import br.com.zwo.zwoteleport.api.TeleportCause;
import br.com.zwo.zwoteleport.api.TeleportResult;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Disparado quando o teleporte não pôde ser executado: destino inválido ou inseguro, mundo
 * indisponível, chunk que não carregou ou recusa do servidor/de outro plugin no momento de mover.
 */
public final class ZwoTeleportFailedEvent extends ZwoTeleportEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final TeleportResult.Reason reason;

    public ZwoTeleportFailedEvent(Player player, long teleportId, TeleportCause cause, TeleportResult.Reason reason) {
        super(player, teleportId, cause);
        this.reason = reason;
    }

    public TeleportResult.Reason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
