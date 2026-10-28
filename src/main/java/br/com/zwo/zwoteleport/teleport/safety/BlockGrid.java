package br.com.zwo.zwoteleport.teleport.safety;

/**
 * Região de blocos já carregada que pode ser lida fora da thread principal.
 */
public interface BlockGrid {

    /** Se a coluna X/Z está dentro da região lida. */
    boolean contains(int x, int z);

    int minY();

    /** Altura máxima exclusiva. */
    int maxY();

    BlockKind kind(int x, int y, int z);
}
