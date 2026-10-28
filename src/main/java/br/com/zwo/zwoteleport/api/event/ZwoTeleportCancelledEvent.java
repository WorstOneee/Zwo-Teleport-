package br.com.zwo.zwoteleport.api.event;

import br.com.zwo.zwoteleport.api.TeleportCause;
import br.com.zwo.zwoteleport.api.TeleportResult;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Disparado quando um teleporte iniciado é interrompido (movimento, dano, combate, saída,
 * substituição, outro plugin, desligamento). O jogador não foi movido por este teleporte.
 *
 * <p>Na saída do servidor o jogador pode já estar offline quando o evento é disparado.</p>
 */
public final class ZwoTeleportCancelledEvent extends ZwoTeleportEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final TeleportResult.Reason reason;

    public ZwoTeleportCancelledEvent(Player player, long teleportId, TeleportCause cause, TeleportResult.Reason reason) {
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
