package br.com.zwo.zwoteleport.api;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * API do ZwoTeleport para outros plugins do servidor.
 *
 * <p>Obtenha com {@link #find()} (ou pelo {@code ServicesManager}) depois que o ZwoTeleport
 * habilitar; declare {@code depend} ou {@code softdepend: [ZwoTeleport]} no seu plugin.yml.
 * Todos os métodos devem ser chamados na thread principal e lançam {@link IllegalStateException}
 * fora dela. Os futures são concluídos na thread principal.</p>
 *
 * <p>Para bloquear teleportes (ex.: durante um evento ou em uma região), escute
 * {@link br.com.zwo.zwoteleport.api.event.ZwoPreTeleportEvent} e cancele-o.</p>
 */
public interface ZwoTeleportApi {

    static Optional<ZwoTeleportApi> find() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(ZwoTeleportApi.class));
    }

    String version();

    Optional<WarpInfo> warp(String name);

    /** Warps carregadas, em ordem alfabética. */
    List<WarpInfo> warps();

    /** A warp usada como spawn (a mesma do /setspawn e do spawn na entrada). */
    Optional<WarpInfo> spawn();

    boolean canUseWarp(Player player, String warpName);

    /**
     * Teleporta para um local aplicando as regras indicadas. Um teleporte anterior em andamento do
     * mesmo jogador é cancelado ({@code REPLACED}).
     */
    CompletableFuture<TeleportResult> teleport(Player player, Location destination, TeleportOptions options);

    /** Teleporta para uma warp. Não verifica a permissão da warp; use {@link #canUseWarp} se precisar. */
    CompletableFuture<TeleportResult> teleportToWarp(Player player, String warpName, TeleportOptions options);

    /** Cancela o teleporte em andamento. Devolve false se não havia nenhum ou se ele já foi despachado. */
    boolean cancelTeleport(Player player);

    Optional<PendingTeleportInfo> pendingTeleport(UUID player);

    List<TeleportRequestInfo> incomingRequests(UUID player);

    List<TeleportRequestInfo> outgoingRequests(UUID player);

    boolean isCombatTagged(UUID player);

    long combatSecondsLeft(UUID player);

    long cooldownSecondsLeft(UUID player);

    /**
     * Se o combate do ZwoTeleport impediria este jogador de teleportar agora (tag ativa, bloqueio
     * ligado e sem bypass). Útil para plugins de transferência entre servidores negarem a saída.
     */
    boolean isTeleportBlockedByCombat(Player player);
}
