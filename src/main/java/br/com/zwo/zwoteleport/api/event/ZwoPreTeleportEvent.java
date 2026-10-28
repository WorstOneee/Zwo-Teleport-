package br.com.zwo.zwoteleport.api.event;

import br.com.zwo.zwoteleport.api.TeleportCause;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Permite que outros plugins bloqueiem um teleporte. Disparado ao iniciar ({@link Phase#START}) e
 * de novo imediatamente antes de mover o jogador ({@link Phase#EXECUTE}), para que condições que
 * mudaram durante o warmup (combate, região, evento) sejam revalidadas.
 */
public final class ZwoPreTeleportEvent extends ZwoTeleportEvent implements Cancellable {

    public enum Phase {
        START,
        EXECUTE
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Phase phase;
    private final Location destination;
    private boolean cancelled;
    private Component cancelMessage;

    public ZwoPreTeleportEvent(Player player, long teleportId, TeleportCause cause, Phase phase, Location destination) {
        super(player, teleportId, cause);
        this.phase = phase;
        this.destination = destination.clone();
    }

    public Phase getPhase() {
        return phase;
    }

    /** Cópia do destino previsto. Na fase START pode mudar até a execução (ex.: /tpa segue o alvo). */
    public Location getDestination() {
        return destination.clone();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    /** Mensagem enviada ao jogador quando o evento for cancelado; nula usa a mensagem padrão. */
    public Component getCancelMessage() {
        return cancelMessage;
    }

    public void setCancelMessage(Component message) {
        this.cancelMessage = message;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
