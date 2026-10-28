package br.com.zwo.zwoteleport.api;

import java.util.Objects;

/**
 * Como um teleporte pedido pela API deve se comportar.
 *
 * @param cause origem usada nas regras e nos eventos
 * @param warmup aplicar o warmup configurado (respeitando bypass)
 * @param checkCombat bloquear se o jogador estiver em combate
 * @param applyCooldown verificar e registrar o cooldown do jogador
 * @param safety política de destino; {@code null} usa a configurada para a causa
 * @param notifyPlayer enviar ao jogador as mensagens de espera, cancelamento, falha e sucesso
 */
public record TeleportOptions(
    TeleportCause cause,
    boolean warmup,
    boolean checkCombat,
    boolean applyCooldown,
    SafetyMode safety,
    boolean notifyPlayer
) {

    public TeleportOptions {
        Objects.requireNonNull(cause, "cause");
    }

    /** Teleporte imediato e silencioso de plugin, com verificação de combate e política "plugin". */
    public static TeleportOptions plugin() {
        return new TeleportOptions(TeleportCause.PLUGIN, false, true, false, null, false);
    }

    /** Comporta-se como um comando de jogador: warmup, combate, cooldown e mensagens. */
    public static TeleportOptions likePlayerCommand() {
        return new TeleportOptions(TeleportCause.PLUGIN, true, true, true, null, true);
    }

    public TeleportOptions withWarmup(boolean value) {
        return new TeleportOptions(cause, value, checkCombat, applyCooldown, safety, notifyPlayer);
    }

    public TeleportOptions withSafety(SafetyMode value) {
        return new TeleportOptions(cause, warmup, checkCombat, applyCooldown, value, notifyPlayer);
    }

    public TeleportOptions withNotify(boolean value) {
        return new TeleportOptions(cause, warmup, checkCombat, applyCooldown, safety, value);
    }
}
