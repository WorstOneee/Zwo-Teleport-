package br.com.zwo.zwoteleport.teleport;

/**
 * Limite de deslocamento durante o warmup. Só considera posição: girar a câmera nunca cancela.
 *
 * @param maxHorizontal distância máxima no plano X/Z, em blocos
 * @param maxVertical distância máxima no eixo Y, em blocos
 */
public record MovementTolerance(double maxHorizontal, double maxVertical) {

    public MovementTolerance {
        if (!Double.isFinite(maxHorizontal) || maxHorizontal < 0) {
            throw new IllegalArgumentException("maxHorizontal inválido: " + maxHorizontal);
        }
        if (!Double.isFinite(maxVertical) || maxVertical < 0) {
            throw new IllegalArgumentException("maxVertical inválido: " + maxVertical);
        }
    }

    public boolean exceeded(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double horizontalSquared = dx * dx + dz * dz;
        return horizontalSquared > maxHorizontal * maxHorizontal || Math.abs(toY - fromY) > maxVertical;
    }
}
