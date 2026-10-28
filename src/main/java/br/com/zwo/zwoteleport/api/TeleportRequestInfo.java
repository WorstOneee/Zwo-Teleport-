package br.com.zwo.zwoteleport.api;

import java.util.UUID;

/**
 * Pedido de /tpa ou /tpahere pendente.
 *
 * @param here true para /tpahere (o alvo é quem será teleportado)
 */
public record TeleportRequestInfo(
    UUID requester,
    String requesterName,
    UUID target,
    String targetName,
    boolean here,
    long createdAtMillis,
    long expiresAtMillis
) {
}
