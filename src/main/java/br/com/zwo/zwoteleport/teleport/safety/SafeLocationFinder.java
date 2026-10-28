package br.com.zwo.zwoteleport.teleport.safety;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Busca limitada de uma posição segura perto do destino. Não acessa o servidor: trabalha sobre
 * um {@link BlockGrid}, o que permite rodar fora da thread principal e testar sem Paper.
 */
public final class SafeLocationFinder {

    public record Position(int x, int y, int z) {
    }

    private SafeLocationFinder() {
    }

    /**
     * Uma posição é segura quando o bloco abaixo sustenta o jogador, os blocos dos pés e da cabeça
     * estão livres e nenhum dos três é perigoso. Com {@code allowWater}, água conta como livre e
     * também como apoio.
     */
    public static boolean isSafe(BlockGrid grid, int x, int y, int z, boolean allowWater) {
        if (!grid.contains(x, z) || y - 1 < grid.minY() || y + 1 >= grid.maxY()) {
            return false;
        }
        BlockKind below = grid.kind(x, y - 1, z);
        BlockKind feet = grid.kind(x, y, z);
        BlockKind head = grid.kind(x, y + 1, z);
        if (below == BlockKind.HAZARD || feet == BlockKind.HAZARD || head == BlockKind.HAZARD) {
            return false;
        }
        boolean support = below == BlockKind.SOLID || (allowWater && below == BlockKind.WATER);
        return support && free(feet, allowWater) && free(head, allowWater);
    }

    /**
     * Procura a posição segura mais próxima, primeiro pela distância total e, em empate, preferindo
     * subir a descer. Devolve vazio se nada for encontrado dentro dos limites.
     */
    public static Optional<Position> findNearest(
        BlockGrid grid, int x, int y, int z, int horizontalRadius, int verticalRange, boolean allowWater
    ) {
        int radius = Math.max(0, horizontalRadius);
        int range = Math.max(0, verticalRange);
        for (int[] offset : offsets(radius, range)) {
            int cx = x + offset[0];
            int cy = y + offset[1];
            int cz = z + offset[2];
            if (isSafe(grid, cx, cy, cz, allowWater)) {
                return Optional.of(new Position(cx, cy, cz));
            }
        }
        return Optional.empty();
    }

    static List<int[]> offsets(int radius, int range) {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -range; dy <= range; dy++) {
                    offsets.add(new int[] {dx, dy, dz});
                }
            }
        }
        offsets.sort(Comparator
            .comparingInt((int[] o) -> o[0] * o[0] + o[1] * o[1] + o[2] * o[2])
            .thenComparingInt(o -> o[0] * o[0] + o[2] * o[2])
            .thenComparingInt(o -> o[1] >= 0 ? 0 : 1));
        return offsets;
    }

    private static boolean free(BlockKind kind, boolean allowWater) {
        return kind == BlockKind.PASSABLE || (allowWater && kind == BlockKind.WATER);
    }
}
