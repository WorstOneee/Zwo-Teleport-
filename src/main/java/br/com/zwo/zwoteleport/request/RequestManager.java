package br.com.zwo.zwoteleport.request;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Guarda os pedidos de /tpa e /tpahere em memória.
 *
 * <p>Existe no máximo um pedido por par (quem pediu, alvo). Toda operação que finaliza um pedido
 * (aceitar, negar, cancelar, expirar, sair do servidor) remove-o do mapa antes de devolvê-lo, então
 * um mesmo pedido nunca é finalizado duas vezes. Não é thread-safe: use apenas na thread principal.</p>
 */
public final class RequestManager {

    public enum CreateStatus {
        CREATED,
        REPLACED,
        ALREADY_PENDING,
        SELF_REQUEST
    }

    public record CreateResult(CreateStatus status, TeleportRequest request, TeleportRequest previous) {

        public boolean success() {
            return status == CreateStatus.CREATED || status == CreateStatus.REPLACED;
        }
    }

    /** alvo -> (quem pediu -> pedido), em ordem de criação. */
    private final Map<UUID, LinkedHashMap<UUID, TeleportRequest>> byTarget = new HashMap<>();
    private long nextId = 1;

    public CreateResult create(
        UUID requester,
        String requesterName,
        UUID target,
        String targetName,
        RequestType type,
        long nowMillis,
        long ttlMillis,
        boolean allowSelf,
        boolean replaceExisting
    ) {
        if (!allowSelf && requester.equals(target)) {
            return new CreateResult(CreateStatus.SELF_REQUEST, null, null);
        }
        LinkedHashMap<UUID, TeleportRequest> incoming = byTarget.computeIfAbsent(target, ignored -> new LinkedHashMap<>());
        TeleportRequest existing = incoming.get(requester);
        if (existing != null && !existing.expired(nowMillis) && !replaceExisting) {
            return new CreateResult(CreateStatus.ALREADY_PENDING, existing, null);
        }
        TeleportRequest previous = null;
        if (existing != null) {
            incoming.remove(requester);
            previous = existing.expired(nowMillis) ? null : existing;
        }
        TeleportRequest created = new TeleportRequest(
            nextId++, requester, requesterName, target, targetName, type, nowMillis, nowMillis + Math.max(0L, ttlMillis));
        incoming.put(requester, created);
        return new CreateResult(previous == null ? CreateStatus.CREATED : CreateStatus.REPLACED, created, previous);
    }

    /**
     * Remove e devolve um pedido válido recebido por {@code target}. Com {@code requester} nulo,
     * escolhe o mais recente. Pedidos já expirados são ignorados aqui e ficam para {@link #expire(long)}
     * notificar.
     */
    public Optional<TeleportRequest> take(UUID target, UUID requester, long nowMillis) {
        Optional<TeleportRequest> found = peek(target, requester, nowMillis);
        found.ifPresent(this::remove);
        return found;
    }

    public Optional<TeleportRequest> peek(UUID target, UUID requester, long nowMillis) {
        LinkedHashMap<UUID, TeleportRequest> incoming = byTarget.get(target);
        if (incoming == null || incoming.isEmpty()) {
            return Optional.empty();
        }
        if (requester != null) {
            TeleportRequest request = incoming.get(requester);
            return request == null || request.expired(nowMillis) ? Optional.empty() : Optional.of(request);
        }
        TeleportRequest latest = null;
        for (TeleportRequest request : incoming.values()) {
            if (request.expired(nowMillis)) {
                continue;
            }
            if (latest == null || request.createdAtMillis() >= latest.createdAtMillis()) {
                latest = request;
            }
        }
        return Optional.ofNullable(latest);
    }

    /** Procura, entre os pedidos recebidos, quem pediu com este nome (sem diferenciar maiúsculas). */
    public Optional<UUID> findRequesterByName(UUID target, String name, long nowMillis) {
        LinkedHashMap<UUID, TeleportRequest> incoming = byTarget.get(target);
        if (incoming == null || name == null) {
            return Optional.empty();
        }
        for (TeleportRequest request : incoming.values()) {
            if (!request.expired(nowMillis) && request.requesterName().equalsIgnoreCase(name.trim())) {
                return Optional.of(request.requester());
            }
        }
        return Optional.empty();
    }

    /** Cancela todos os pedidos enviados por {@code requester}. */
    public List<TeleportRequest> cancelOutgoing(UUID requester) {
        List<TeleportRequest> removed = new ArrayList<>();
        Iterator<LinkedHashMap<UUID, TeleportRequest>> targets = byTarget.values().iterator();
        while (targets.hasNext()) {
            LinkedHashMap<UUID, TeleportRequest> incoming = targets.next();
            TeleportRequest request = incoming.remove(requester);
            if (request != null) {
                removed.add(request);
            }
            if (incoming.isEmpty()) {
                targets.remove();
            }
        }
        return removed;
    }

    /** Remove todos os pedidos em que o jogador participa, como quem pediu ou como alvo. */
    public List<TeleportRequest> removeInvolving(UUID player) {
        List<TeleportRequest> removed = new ArrayList<>();
        LinkedHashMap<UUID, TeleportRequest> incoming = byTarget.remove(player);
        if (incoming != null) {
            removed.addAll(incoming.values());
        }
        removed.addAll(cancelOutgoing(player));
        return removed;
    }

    /** Remove e devolve os pedidos vencidos. */
    public List<TeleportRequest> expire(long nowMillis) {
        List<TeleportRequest> expired = new ArrayList<>();
        Iterator<LinkedHashMap<UUID, TeleportRequest>> targets = byTarget.values().iterator();
        while (targets.hasNext()) {
            LinkedHashMap<UUID, TeleportRequest> incoming = targets.next();
            Iterator<TeleportRequest> requests = incoming.values().iterator();
            while (requests.hasNext()) {
                TeleportRequest request = requests.next();
                if (request.expired(nowMillis)) {
                    requests.remove();
                    expired.add(request);
                }
            }
            if (incoming.isEmpty()) {
                targets.remove();
            }
        }
        return expired;
    }

    public List<TeleportRequest> incoming(UUID target, long nowMillis) {
        LinkedHashMap<UUID, TeleportRequest> incoming = byTarget.get(target);
        if (incoming == null) {
            return List.of();
        }
        List<TeleportRequest> result = new ArrayList<>();
        for (TeleportRequest request : incoming.values()) {
            if (!request.expired(nowMillis)) {
                result.add(request);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<TeleportRequest> outgoing(UUID requester, long nowMillis) {
        List<TeleportRequest> result = new ArrayList<>();
        for (LinkedHashMap<UUID, TeleportRequest> incoming : byTarget.values()) {
            TeleportRequest request = incoming.get(requester);
            if (request != null && !request.expired(nowMillis)) {
                result.add(request);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public int size() {
        int total = 0;
        for (LinkedHashMap<UUID, TeleportRequest> incoming : byTarget.values()) {
            total += incoming.size();
        }
        return total;
    }

    public void clear() {
        byTarget.clear();
    }

    private void remove(TeleportRequest request) {
        LinkedHashMap<UUID, TeleportRequest> incoming = byTarget.get(request.target());
        if (incoming == null) {
            return;
        }
        incoming.remove(request.requester(), request);
        if (incoming.isEmpty()) {
            byTarget.remove(request.target());
        }
    }
}
