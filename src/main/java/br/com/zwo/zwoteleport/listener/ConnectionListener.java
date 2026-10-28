package br.com.zwo.zwoteleport.listener;

import br.com.zwo.zwoteleport.request.TpaService;
import br.com.zwo.zwoteleport.spawn.JoinSpawnService;
import br.com.zwo.zwoteleport.teleport.TeleportService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Entrada e saída, em ordem fixa: a sessão é aberta antes de qualquer agendamento e fechada
 * depois que pedidos e teleportes do jogador foram encerrados.
 */
public final class ConnectionListener implements Listener {

    private final TeleportService teleports;
    private final TpaService tpa;
    private final JoinSpawnService joinSpawn;

    public ConnectionListener(TeleportService teleports, TpaService tpa, JoinSpawnService joinSpawn) {
        this.teleports = teleports;
        this.tpa = tpa;
        this.joinSpawn = joinSpawn;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long session = teleports.openSession(player);
        joinSpawn.handleJoin(player, session);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        joinSpawn.handleQuit(player.getUniqueId());
        tpa.handleQuit(player);
        teleports.handleQuit(player.getUniqueId());
    }
}
