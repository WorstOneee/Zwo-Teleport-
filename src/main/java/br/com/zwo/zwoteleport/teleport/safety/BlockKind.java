package br.com.zwo.zwoteleport.teleport.safety;

/**
 * Classificação simplificada de um bloco para a busca de posição segura.
 */
public enum BlockKind {

    /** Ar, plantas baixas, tochas, placas e outros blocos atravessáveis sem dano. */
    PASSABLE,

    /** Bloco em que se pode ficar em pé. */
    SOLID,

    /** Água e blocos que a contêm. */
    WATER,

    /** Lava, fogo, magma, cacto, teia, pó de neve, portais e afins. */
    HAZARD
}
