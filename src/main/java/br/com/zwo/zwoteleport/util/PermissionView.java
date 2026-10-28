package br.com.zwo.zwoteleport.util;

/**
 * Visão mínima de quem possui permissões. Permite testar as regras de bypass sem um servidor.
 */
@FunctionalInterface
public interface PermissionView {

    boolean has(String node);
}
