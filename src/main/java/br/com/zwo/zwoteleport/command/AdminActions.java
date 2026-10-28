package br.com.zwo.zwoteleport.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Operações administrativas expostas ao /zwotp.
 */
public interface AdminActions {

    /**
     * @param applied false quando a configuração nova foi recusada e a anterior continua ativa
     */
    record ReloadOutcome(boolean applied, List<String> errors, List<String> warnings) {
    }

    CompletableFuture<ReloadOutcome> reload();

    /** Liga/desliga o combate interno. Devolve o motivo se não foi possível gravar no config.yml. */
    Optional<String> setCombatEnabled(boolean enabled);

    /** Linhas de diagnóstico (rótulo, valor) em ordem. */
    List<Map.Entry<String, String>> info();

    String version();
}
