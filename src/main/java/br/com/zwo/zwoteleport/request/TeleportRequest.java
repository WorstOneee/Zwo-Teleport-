package br.com.zwo.zwoteleport.request;

import java.util.Objects;
import java.util.UUID;

/**
 * Pedido de teleporte entre dois jogadores do mesmo servidor. Imutável.
 *
 * @param id identificador único dentro desta execução do servidor
 * @param requester quem enviou o pedido
 * @param target quem pode aceitar ou negar
 */
public record TeleportRequest(
    long id,
    UUID requester,
    String requesterName,
    UUID target,
    String targetName,
    RequestType type,
    long createdAtMillis,
    long expiresAtMillis
) {

    public TeleportRequest {
        Objects.requireNonNull(requester, "requester");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(type, "type");
        requesterName = requesterName == null ? "" : requesterName;
        targetName = targetName == null ? "" : targetName;
    }

    public boolean expired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    /** Jogador que será teleportado se o pedido for aceito. */
    public UUID mover() {
        return type == RequestType.TPA ? requester : target;
    }

    /** Jogador que serve de destino. */
    public UUID anchor() {
        return type == RequestType.TPA ? target : requester;
    }

    public boolean involves(UUID player) {
        return requester.equals(player) || target.equals(player);
    }
}
