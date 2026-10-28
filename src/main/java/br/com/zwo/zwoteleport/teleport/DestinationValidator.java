package br.com.zwo.zwoteleport.teleport;

/**
 * Validação numérica de um destino, independente do servidor.
 */
public final class DestinationValidator {

    /** Limite absoluto de X/Z aceito pelo jogo. */
    public static final double MAX_HORIZONTAL = 29_999_984.0D;

    /** Margem acima do limite de construção aceita para teleportes (ex.: warps no céu). */
    public static final int HEIGHT_MARGIN_ABOVE = 256;

    public enum Check {
        OK,
        NOT_FINITE,
        OUTSIDE_WORLD_LIMITS,
        OUTSIDE_HEIGHT_LIMITS
    }

    private DestinationValidator() {
    }

    public static Check check(double x, double y, double z, float yaw, float pitch, int minHeight, int maxHeight) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return Check.NOT_FINITE;
        }
        if (Math.abs(x) > MAX_HORIZONTAL || Math.abs(z) > MAX_HORIZONTAL) {
            return Check.OUTSIDE_WORLD_LIMITS;
        }
        if (y < minHeight || y > (double) maxHeight + HEIGHT_MARGIN_ABOVE) {
            return Check.OUTSIDE_HEIGHT_LIMITS;
        }
        return Check.OK;
    }

    /** Converte texto em número aceitando apenas valores finitos (rejeita NaN e Infinity). */
    public static double parseFinite(String raw) {
        if (raw == null) {
            throw new NumberFormatException("vazio");
        }
        String text = raw.trim().replace(',', '.');
        if (text.isEmpty()) {
            throw new NumberFormatException("vazio");
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean allowed = (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '+';
            if (!allowed) {
                throw new NumberFormatException("caractere inválido: " + raw);
            }
        }
        double value = Double.parseDouble(text);
        if (!Double.isFinite(value)) {
            throw new NumberFormatException("não finito: " + raw);
        }
        return value;
    }

    /**
     * Interpreta uma coordenada no formato do /tp: número absoluto, "~" ou "~deslocamento".
     * Coordenadas locais ("^") não são suportadas.
     */
    public static double parseCoordinate(String raw, double base) {
        if (raw == null || raw.isEmpty() || raw.startsWith("^")) {
            throw new NumberFormatException("coordenada inválida: " + raw);
        }
        if (raw.equals("~")) {
            return base;
        }
        if (raw.startsWith("~")) {
            return base + parseFinite(raw.substring(1));
        }
        return parseFinite(raw);
    }
}
