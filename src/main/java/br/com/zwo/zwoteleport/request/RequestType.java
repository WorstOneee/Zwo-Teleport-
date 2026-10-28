package br.com.zwo.zwoteleport.request;

/**
 * Direção de um pedido. Em ambos os casos quem aceita é o alvo do pedido.
 */
public enum RequestType {

    /** /tpa: quem pediu vai até o alvo. */
    TPA,

    /** /tpahere: o alvo vai até quem pediu. */
    TPAHERE
}
