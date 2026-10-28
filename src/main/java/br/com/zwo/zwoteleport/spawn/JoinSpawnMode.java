package br.com.zwo.zwoteleport.spawn;

import java.util.Locale;
import java.util.Optional;

/**
 * Quando enviar o jogador ao spawn ao entrar neste servidor.
 */
public enum JoinSpawnMode {

    /** Nunca. */
    DISABLED,

    /** Só na primeira entrada do jogador neste servidor (sem dados salvos no mundo). */
    FIRST_JOIN,

    /**
     * Em toda entrada neste servidor. Inclui vir do Hub, voltar de outra instância e reconectar,
     * porque o backend não sabe de onde o jogador veio.
     */
    EVERY_JOIN;

    public static Optional<JoinSpawnMode> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT).replace('-', '_')) {
            case "DISABLED", "DESATIVADO", "OFF", "FALSE" -> Optional.of(DISABLED);
            case "FIRST_JOIN", "PRIMEIRA_ENTRADA" -> Optional.of(FIRST_JOIN);
            case "EVERY_JOIN", "TODA_ENTRADA" -> Optional.of(EVERY_JOIN);
            default -> Optional.empty();
        };
    }
}
