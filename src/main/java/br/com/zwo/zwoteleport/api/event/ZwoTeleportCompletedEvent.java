package br.com.zwo.zwoteleport.api.event;

import br.com.zwo.zwoteleport.api.TeleportCause;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Disparado depois que o servidor confirmou o teleporte: o jogador já está no destino.
 */
public final class ZwoTeleportCompletedEvent extends ZwoTeleportEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Location destination;

    public ZwoTeleportCompletedEvent(Player player, long teleportId, TeleportCause cause, Location destination) {
        super(player, teleportId, cause);
        this.destination = destination.clone();
    }

    public Location getDestination() {
        return destination.clone();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
