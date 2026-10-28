package br.com.zwo.zwoteleport.teleport;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cooldown por jogador, em memória. Use apenas na thread principal.
 */
public final class CooldownTracker {

    private final Map<UUID, Long> until = new HashMap<>();

    public long remainingMillis(UUID player, long nowMillis) {
        Long end = until.get(player);
        if (end == null) {
            return 0L;
        }
        long left = end - nowMillis;
        if (left <= 0L) {
            until.remove(player);
            return 0L;
        }
        return left;
    }

    /** Segundos restantes arredondados para cima, como exibido ao jogador. */
    public long remainingSeconds(UUID player, long nowMillis) {
        long millis = remainingMillis(player, nowMillis);
        return millis <= 0L ? 0L : (millis + 999L) / 1000L;
    }

    public void apply(UUID player, long nowMillis, int seconds) {
        if (seconds <= 0) {
            return;
        }
        until.put(player, nowMillis + seconds * 1000L);
    }

    public void clear(UUID player) {
        until.remove(player);
    }

    public void purge(long nowMillis) {
        until.values().removeIf(end -> end <= nowMillis);
    }

    public void clearAll() {
        until.clear();
    }
}
