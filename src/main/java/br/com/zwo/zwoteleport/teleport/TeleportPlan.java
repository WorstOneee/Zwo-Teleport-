package br.com.zwo.zwoteleport.teleport;

import br.com.zwo.zwoteleport.api.SafetyMode;
import br.com.zwo.zwoteleport.api.TeleportCause;

import java.util.Objects;
import java.util.UUID;

/**
 * Tudo o que o {@link TeleportService} precisa saber sobre um teleporte.
 *
 * @param anchor jogador usado como destino (TPA), para cancelar se ele sair; pode ser nulo
 * @param warmup se o warmup configurado deve ser aplicado (bypass ainda vale)
 * @param checkCombat bloquear/cancelar se o jogador estiver em combate
 * @param applyCooldown verificar o cooldown ao iniciar e registrá-lo no sucesso
 * @param respectWorldBorder recusar destinos fora da borda do mundo
 * @param successKey mensagem enviada ao jogador teleportado no sucesso; nula para nenhuma
 * @param successPlaceholders placeholders da mensagem de sucesso, em pares
 * @param notifyPlayer enviar mensagens de espera, cancelamento e falha ao jogador
 */
public record TeleportPlan(
    TeleportCause cause,
    Destination destination,
    UUID anchor,
    String anchorName,
    boolean warmup,
    boolean checkCombat,
    boolean applyCooldown,
    SafetyMode safety,
    boolean respectWorldBorder,
    String successKey,
    String[] successPlaceholders,
    boolean notifyPlayer
) {

    public TeleportPlan {
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(safety, "safety");
        successPlaceholders = successPlaceholders == null ? new String[0] : successPlaceholders.clone();
        anchorName = anchorName == null ? "" : anchorName;
    }
}
