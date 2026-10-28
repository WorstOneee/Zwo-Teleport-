package br.com.zwo.zwoteleport.teleport.safety;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

/**
 * {@link BlockGrid} sobre um {@link ChunkSnapshot}. O snapshot é imutável, então a leitura pode
 * acontecer fora da thread principal. Cobre apenas o chunk do destino.
 */
public final class SnapshotBlockGrid implements BlockGrid {

    private static final Set<Material> HAZARDS = EnumSet.of(
        Material.LAVA,
        Material.LAVA_CAULDRON,
        Material.FIRE,
        Material.SOUL_FIRE,
        Material.MAGMA_BLOCK,
        Material.CACTUS,
        Material.CAMPFIRE,
        Material.SOUL_CAMPFIRE,
        Material.SWEET_BERRY_BUSH,
        Material.WITHER_ROSE,
        Material.POWDER_SNOW,
        Material.POINTED_DRIPSTONE,
        Material.COBWEB,
        Material.NETHER_PORTAL,
        Material.END_PORTAL,
        Material.END_GATEWAY);

    private static final Set<Material> WATER = EnumSet.of(
        Material.WATER,
        Material.BUBBLE_COLUMN,
        Material.KELP,
        Material.KELP_PLANT,
        Material.SEAGRASS,
        Material.TALL_SEAGRASS);

    private final ChunkSnapshot snapshot;
    private final int chunkX;
    private final int chunkZ;
    private final int minY;
    private final int maxY;

    public SnapshotBlockGrid(ChunkSnapshot snapshot, int minY, int maxY) {
        this.snapshot = snapshot;
        this.chunkX = snapshot.getX();
        this.chunkZ = snapshot.getZ();
        this.minY = minY;
        this.maxY = maxY;
    }

    @Override
    public boolean contains(int x, int z) {
        return (x >> 4) == chunkX && (z >> 4) == chunkZ;
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }

    @Override
    public BlockKind kind(int x, int y, int z) {
        return classify(snapshot.getBlockType(x & 15, y, z & 15));
    }

    public static BlockKind classify(Material material) {
        if (WATER.contains(material)) {
            return BlockKind.WATER;
        }
        if (HAZARDS.contains(material)) {
            return BlockKind.HAZARD;
        }
        return material.isSolid() ? BlockKind.SOLID : BlockKind.PASSABLE;
    }
}
