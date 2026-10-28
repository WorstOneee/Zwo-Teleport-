package br.com.zwo.zwoteleport.listener;

import br.com.zwo.zwoteleport.teleport.TeleportService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Cancela o warmup por movimento, dano ou teleporte externo. Só observa (MONITOR) e ignora eventos
 * já cancelados por outros plugins.
 */
public final class WarmupListener implements Listener {

    private final TeleportService teleports;

    public WarmupListener(TeleportService teleports) {
        this.teleports = teleports;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Girar a câmera não muda a posição e não chega a ser avaliado.
        if (event.hasChangedPosition()) {
            teleports.handleMove(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        teleports.handleExternalTeleport(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            teleports.handleDamage(player);
        }
    }
}
