package br.com.zwo.zwoteleport.api;

/**
 * Dados públicos de uma warp.
 *
 * @param available se o mundo da warp está carregado neste momento
 */
public record WarpInfo(
    String name,
    String displayName,
    String world,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    String permission,
    boolean available
) {
}
