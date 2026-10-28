package br.com.zwo.zwoteleport.teleport;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tag de combate do próprio ZwoTeleport, em memória. Use apenas na thread principal.
 *
 * <p>A tag não é removida quando o jogador sai: se ele voltar antes de expirar, continua marcado.
 * Além disso, quem sai marcado fica registrado até a próxima entrada para que o spawn na entrada
 * não sirva de fuga de combate.</p>
 */
public final class CombatTracker {

    private static final long QUIT_MEMORY_MILLIS = 24L * 60L * 60L * 1000L;

    private final Map<UUID, Long> taggedUntil = new HashMap<>();
    private final Map<UUID, Long> quitWhileTagged = new HashMap<>();

    public void tag(UUID player, long nowMillis, int seconds) {
        if (seconds <= 0) {
            return;
        }
        taggedUntil.merge(player, nowMillis + seconds * 1000L, Math::max);
    }

    public boolean isTagged(UUID player, long nowMillis) {
        Long end = taggedUntil.get(player);
        if (end == null) {
            return false;
        }
        if (end <= nowMillis) {
            taggedUntil.remove(player);
            return false;
        }
        return true;
    }

    public long remainingSeconds(UUID player, long nowMillis) {
        Long end = taggedUntil.get(player);
        if (end == null || end <= nowMillis) {
            return 0L;
        }
        return Math.max(1L, (end - nowMillis + 999L) / 1000L);
    }

    public void clear(UUID player) {
        taggedUntil.remove(player);
    }

    public void clearAll() {
        taggedUntil.clear();
        quitWhileTagged.clear();
    }

    /** Registra a saída; devolve true se o jogador saiu marcado. */
    public boolean recordQuit(UUID player, long nowMillis) {
        if (isTagged(player, nowMillis)) {
            quitWhileTagged.put(player, nowMillis);
            return true;
        }
        return false;
    }

    /** Consome o registro de saída em combate. Devolve true uma única vez por saída marcada. */
    public boolean consumeQuitWhileTagged(UUID player, long nowMillis) {
        Long when = quitWhileTagged.remove(player);
        return when != null && nowMillis - when < QUIT_MEMORY_MILLIS;
    }

    public void purge(long nowMillis) {
        taggedUntil.values().removeIf(end -> end <= nowMillis);
        quitWhileTagged.values().removeIf(when -> nowMillis - when >= QUIT_MEMORY_MILLIS);
    }

    public int taggedCount(long nowMillis) {
        purge(nowMillis);
        return taggedUntil.size();
    }
}
