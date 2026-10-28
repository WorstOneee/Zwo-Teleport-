package br.com.zwo.zwoteleport.listener;

import br.com.zwo.zwoteleport.teleport.TeleportService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Tag de combate entre jogadores. Dano cancelado (ex.: região protegida) não marca ninguém.
 */
public final class CombatListener implements Listener {

    private final TeleportService teleports;

    public CombatListener(TeleportService teleports) {
        this.teleports = teleports;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = responsiblePlayer(event);
        if (attacker != null) {
            teleports.handleCombat(victim, attacker);
        }
    }

    /**
     * Jogador responsável pelo dano: o causador indicado pelo servidor (atirador da flecha, dono
     * da poção, quem acendeu a TNT) ou, como reserva, o atacante direto ou o atirador do projétil.
     */
    static Player responsiblePlayer(EntityDamageByEntityEvent event) {
        Entity causing = event.getDamageSource().getCausingEntity();
        if (causing instanceof Player player) {
            return player;
        }
        Entity damager = event.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
