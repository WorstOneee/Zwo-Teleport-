package br.com.zwo.zwoteleport.api;

import java.util.Locale;
import java.util.Optional;

/**
 * Política aplicada ao destino antes do teleporte.
 */
public enum SafetyMode {

    /** Usa a posição exata, sem verificar blocos. Indicado para warps e spawn definidos pela equipe. */
    NONE,

    /** Verifica a posição exata e recusa o teleporte se ela não for segura. */
    VERIFY,

    /** Verifica e, se necessário, procura a posição segura mais próxima dentro do raio configurado. */
    ADJUST;

    public static Optional<SafetyMode> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "NONE", "NENHUMA", "EXATO" -> Optional.of(NONE);
            case "VERIFY", "VERIFICAR" -> Optional.of(VERIFY);
            case "ADJUST", "AJUSTAR" -> Optional.of(ADJUST);
            default -> Optional.empty();
        };
    }
}
