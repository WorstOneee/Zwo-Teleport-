package br.com.zwo.zwoteleport.teleport;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Identifica cada conexão de um jogador com um número único. Tarefas agendadas guardam o número
 * da sessão em que foram criadas e não fazem nada se o jogador saiu ou reconectou nesse meio tempo.
 */
public final class SessionRegistry {

    private final Map<UUID, Long> current = new HashMap<>();
    private long next = 1;

    public long open(UUID player) {
        long token = next++;
        current.put(player, token);
        return token;
    }

    public void close(UUID player) {
        current.remove(player);
    }

    public boolean isCurrent(UUID player, long token) {
        Long active = current.get(player);
        return active != null && active == token;
    }

    public OptionalLong current(UUID player) {
        Long active = current.get(player);
        return active == null ? OptionalLong.empty() : OptionalLong.of(active);
    }

    public void clear() {
        current.clear();
    }
}
