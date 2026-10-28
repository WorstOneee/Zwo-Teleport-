package br.com.zwo.zwoteleport.api.event;

import br.com.zwo.zwoteleport.api.TeleportCause;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

/**
 * Base dos eventos do ZwoTeleport. Todos são disparados na thread principal.
 *
 * <p>Ciclo de um teleporte que passa das verificações iniciais:</p>
 * <ol>
 *   <li>{@link ZwoPreTeleportEvent} com fase {@code START} (cancelável);</li>
 *   <li>warmup, se houver;</li>
 *   <li>{@link ZwoPreTeleportEvent} com fase {@code EXECUTE} (cancelável), logo antes de mover o jogador;</li>
 *   <li>exatamente um entre {@link ZwoTeleportCompletedEvent}, {@link ZwoTeleportCancelledEvent} e
 *       {@link ZwoTeleportFailedEvent}.</li>
 * </ol>
 * <p>O {@link #getTeleportId() id} é o mesmo em todos os eventos de um teleporte.</p>
 */
public abstract class ZwoTeleportEvent extends PlayerEvent {

    private final long teleportId;
    private final TeleportCause cause;

    protected ZwoTeleportEvent(Player player, long teleportId, TeleportCause cause) {
        super(player);
        this.teleportId = teleportId;
        this.cause = cause;
    }

    public long getTeleportId() {
        return teleportId;
    }

    public TeleportCause getCause() {
        return cause;
    }
}
