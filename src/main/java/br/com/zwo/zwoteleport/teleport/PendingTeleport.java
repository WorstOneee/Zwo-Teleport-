package br.com.zwo.zwoteleport.teleport;

import br.com.zwo.zwoteleport.api.TeleportResult;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Estado de um teleporte iniciado. Acessado apenas na thread principal; guarda o UUID e o número
 * da sessão do jogador, nunca o objeto Player.
 */
final class PendingTeleport {

    enum State {
        /** Contando o warmup; movimento, dano e combate podem cancelar. */
        WARMUP,
        /** Validando destino e carregando chunk. Ainda pode ser interrompido. */
        EXECUTING,
        /** teleportAsync chamado; não há mais como impedir o movimento. */
        DISPATCHED,
        /** Finalizado; nenhuma tarefa atrasada pode agir sobre ele. */
        DONE
    }

    final long id;
    final UUID player;
    final long session;
    final TeleportPlan plan;
    final CompletableFuture<TeleportResult> future = new CompletableFuture<>();
    final String originWorld;
    final double originX;
    final double originY;
    final double originZ;

    State state = State.WARMUP;
    int secondsLeft;
    BukkitTask task;

    PendingTeleport(long id, UUID player, long session, TeleportPlan plan, String originWorld, double x, double y, double z) {
        this.id = id;
        this.player = player;
        this.session = session;
        this.plan = plan;
        this.originWorld = originWorld;
        this.originX = x;
        this.originY = y;
        this.originZ = z;
    }

    boolean interruptible() {
        return state == State.WARMUP || state == State.EXECUTING;
    }
}
