package br.com.zwo.zwoteleport.warp;

import java.util.Objects;

/**
 * Warp persistida. Guarda o nome do mundo em vez de uma referência ao mundo, então uma warp cujo
 * mundo ainda não carregou (ou foi removido) continua salva e é tratada como indisponível.
 *
 * @param name nome normalizado (minúsculas)
 * @param displayName nome exibido, aceita códigos de cor com &amp;
 * @param permission permissão específica da warp; vazio quando não há
 */
public record WarpPoint(
    String name,
    String displayName,
    String world,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    String permission
) {

    public WarpPoint {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(world, "world");
        displayName = displayName == null || displayName.isBlank() ? name : displayName;
        permission = permission == null ? "" : permission.trim();
    }

    public boolean hasCustomPermission() {
        return !permission.isEmpty();
    }

    public WarpPoint withDisplayName(String newDisplayName) {
        return new WarpPoint(name, newDisplayName, world, x, y, z, yaw, pitch, permission);
    }
}
