package br.com.zwo.zwoteleport.api;

import org.bukkit.Location;

import java.util.Optional;

/**
 * Resultado final de um teleporte. O future devolvido pela API só é concluído com este valor
 * depois que o desfecho é conhecido; {@link Status#COMPLETED} significa que o jogador já está no destino.
 */
public record TeleportResult(Status status, Reason reason, Location location) {

    public enum Status {
        /** O jogador foi teleportado. */
        COMPLETED,
        /** Recusado antes de começar (cooldown, combate, outro plugin). */
        BLOCKED,
        /** Começou e foi interrompido (movimento, dano, saída, substituição). */
        CANCELLED,
        /** O destino ou o servidor impediram o teleporte. */
        FAILED
    }

    public enum Reason {
        COMPLETED,
        COOLDOWN,
        COMBAT_TAGGED,
        CANCELLED_BY_PLUGIN,
        MOVED,
        DAMAGED,
        COMBAT,
        EXTERNAL_TELEPORT,
        REPLACED,
        QUIT,
        ANCHOR_OFFLINE,
        CANCELLED_BY_API,
        SHUTDOWN,
        PLAYER_OFFLINE,
        PLAYER_DEAD,
        INVALID_DESTINATION,
        WORLD_UNAVAILABLE,
        OUTSIDE_LIMITS,
        RESTRICTED_DESTINATION,
        UNSAFE_DESTINATION,
        CHUNK_LOAD_FAILED,
        TELEPORT_REJECTED
    }

    public TeleportResult {
        location = location == null ? null : location.clone();
    }

    public boolean completed() {
        return status == Status.COMPLETED;
    }

    /** Posição final do jogador, presente apenas quando o teleporte foi concluído. */
    public Optional<Location> finalLocation() {
        return Optional.ofNullable(location).map(Location::clone);
    }
}
