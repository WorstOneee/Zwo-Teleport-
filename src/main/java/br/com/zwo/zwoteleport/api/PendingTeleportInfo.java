package br.com.zwo.zwoteleport.api;

import java.util.UUID;

/**
 * Teleporte em andamento.
 *
 * @param id identificador repetido nos eventos do mesmo teleporte
 * @param secondsLeft segundos restantes de warmup (0 quando já está executando)
 * @param executing se o warmup terminou e o teleporte está sendo executado
 */
public record PendingTeleportInfo(UUID player, long id, TeleportCause cause, int secondsLeft, boolean executing) {
}
