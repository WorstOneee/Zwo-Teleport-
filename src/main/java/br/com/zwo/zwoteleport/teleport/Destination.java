package br.com.zwo.zwoteleport.teleport;

import br.com.zwo.zwoteleport.api.TeleportResult;
import br.com.zwo.zwoteleport.warp.WarpPoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Destino resolvido no momento da execução. Assim um /tpa segue o alvo durante o warmup e uma warp
 * cujo mundo foi descarregado é recusada com uma mensagem clara.
 */
@FunctionalInterface
public interface Destination {

    Resolved resolve();

    /**
     * @param messageKey mensagem específica para a falha; nula usa a padrão do motivo
     */
    record Resolved(Location location, TeleportResult.Reason failure, String messageKey, String[] placeholders) {

        public static Resolved ok(Location location) {
            return new Resolved(location, null, null, new String[0]);
        }

        public static Resolved fail(TeleportResult.Reason reason) {
            return new Resolved(null, reason, null, new String[0]);
        }

        public static Resolved fail(TeleportResult.Reason reason, String messageKey, String... placeholders) {
            return new Resolved(null, reason, messageKey, placeholders);
        }

        public boolean failed() {
            return location == null;
        }
    }

    static Destination of(Location location) {
        Location copy = location.clone();
        return () -> {
            if (!copy.isWorldLoaded()) {
                return Resolved.fail(TeleportResult.Reason.WORLD_UNAVAILABLE);
            }
            return Resolved.ok(copy.clone());
        };
    }

    static Destination ofWarp(WarpPoint warp, String displayName) {
        return () -> {
            World world = Bukkit.getWorld(warp.world());
            if (world == null) {
                return Resolved.fail(TeleportResult.Reason.WORLD_UNAVAILABLE, "warp.unavailable", "warp", displayName);
            }
            return Resolved.ok(new Location(world, warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch()));
        };
    }

    /**
     * Posição atual de outro jogador. Falha se ele saiu ou se estiver em um mundo bloqueado.
     */
    static Destination ofPlayer(UUID anchor, String anchorName, Set<String> blockedWorlds) {
        return () -> {
            Player target = Bukkit.getPlayer(anchor);
            if (target == null || !target.isOnline()) {
                return Resolved.fail(TeleportResult.Reason.ANCHOR_OFFLINE, "teleport.anchor-left", "player", anchorName);
            }
            Location location = target.getLocation();
            if (location.getWorld() != null && blockedWorlds.contains(location.getWorld().getName())) {
                return Resolved.fail(TeleportResult.Reason.RESTRICTED_DESTINATION, "tpa.blocked-target-world", "target", anchorName);
            }
            return Resolved.ok(location);
        };
    }
}
