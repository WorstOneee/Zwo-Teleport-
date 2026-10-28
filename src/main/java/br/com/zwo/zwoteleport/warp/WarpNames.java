package br.com.zwo.zwoteleport.warp;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regras de nome de warp (as mesmas da versão 2.x).
 */
public final class WarpNames {

    public static final String SPAWN = "spawn";
    public static final String SHOP = "loja";

    private static final Pattern VALID = Pattern.compile("[a-z0-9_-]{1,32}");

    private WarpNames() {
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String raw) {
        return VALID.matcher(normalize(raw)).matches();
    }
}
