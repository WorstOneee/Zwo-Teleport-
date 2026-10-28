package br.com.zwo.zwoteleport.api;

/**
 * Origem de um teleporte. Define as regras aplicadas (warmup, cooldown, combate e segurança).
 */
public enum TeleportCause {

    /** /tp administrativo. Sem warmup, cooldown ou bloqueio de combate. */
    ADMIN_COMMAND,

    /** Pedido /tpa aceito: quem pediu vai até o alvo. */
    TPA,

    /** Pedido /tpahere aceito: o alvo vai até quem pediu. */
    TPAHERE,

    /** /warp, /loja e atalhos de warp. */
    WARP,

    /** /spawn usado pelo próprio jogador. */
    SPAWN,

    /** /spawn &lt;jogador&gt; usado pela equipe. Sem warmup, cooldown ou bloqueio de combate. */
    SPAWN_OTHER,

    /** Spawn automático ao entrar no servidor. Sem warmup nem cooldown. */
    JOIN_SPAWN,

    /** Teleporte solicitado por outro plugin pela API. */
    PLUGIN
}
