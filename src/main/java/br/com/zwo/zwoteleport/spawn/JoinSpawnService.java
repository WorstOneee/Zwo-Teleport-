package br.com.zwo.zwoteleport.spawn;

import br.com.zwo.zwoteleport.api.TeleportCause;
import br.com.zwo.zwoteleport.api.TeleportResult;
import br.com.zwo.zwoteleport.config.Settings;
import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.teleport.BypassRules;
import br.com.zwo.zwoteleport.teleport.Destination;
import br.com.zwo.zwoteleport.teleport.TeleportPlan;
import br.com.zwo.zwoteleport.teleport.TeleportService;
import br.com.zwo.zwoteleport.warp.WarpPoint;
import br.com.zwo.zwoteleport.warp.WarpService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Spawn nativo na entrada. Usa o serviço de teleporte diretamente (nunca executa /spawn), sem
 * warmup, sem cooldown e sem conceder permissões. Não altera regras de morte, cama, âncora ou
 * respawn: atua apenas no evento de entrada.
 *
 * <p>Limitação: o backend não sabe de onde o jogador veio. Em {@code EVERY_JOIN}, entrar pelo Hub,
 * voltar de outra instância e reconectar são tratados igualmente.</p>
 */
public final class JoinSpawnService {

    private static final long WARN_INTERVAL_MILLIS = 60_000L;

    private final Plugin plugin;
    private final TeleportService teleports;
    private final WarpService warps;
    private final Messages messages;
    private final Supplier<Settings> settings;
    private final Map<UUID, BukkitTask> scheduled = new HashMap<>();
    private final Map<String, Long> lastWarning = new HashMap<>();

    public JoinSpawnService(Plugin plugin, TeleportService teleports, WarpService warps, Messages messages, Supplier<Settings> settings) {
        this.plugin = plugin;
        this.teleports = teleports;
        this.warps = warps;
        this.messages = messages;
        this.settings = settings;
    }

    /**
     * Chamado na entrada, depois de abrir a sessão. Agenda o teleporte para alguns ticks depois,
     * preso ao número da sessão: se o jogador sair e voltar antes, a tarefa antiga não faz nada.
     */
    public void handleJoin(Player player, long session) {
        Settings.JoinSpawn config = settings.get().joinSpawn();
        UUID id = player.getUniqueId();
        cancelScheduled(id);

        boolean quitInCombat = teleports.combat().consumeQuitWhileTagged(id, System.currentTimeMillis());
        JoinSpawnDecision decision = JoinSpawnDecision.decide(
            config.mode(),
            !player.hasPlayedBefore(),
            BypassRules.joinSpawn(player::hasPermission, config.bypassPermissionEnabled()),
            quitInCombat,
            config.skipIfQuitInCombat());

        if (decision == JoinSpawnDecision.SKIP_QUIT_IN_COMBAT) {
            messages.send(player, "join-spawn.skipped-combat");
            return;
        }
        if (decision != JoinSpawnDecision.TELEPORT) {
            return;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> run(id, session), config.delayTicks());
        scheduled.put(id, task);
    }

    private void run(UUID id, long session) {
        scheduled.remove(id);
        if (!teleports.isSessionCurrent(id, session)) {
            return;
        }
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) {
            return;
        }
        Settings config = settings.get();
        Settings.JoinSpawn joinSpawn = config.joinSpawn();
        Optional<WarpPoint> warp = warps.get(joinSpawn.warp());
        if (warp.isEmpty()) {
            warnOnce("missing", "Spawn na entrada ativo, mas a warp '" + joinSpawn.warp()
                + "' não existe. Use /setspawn. Os jogadores permanecem onde entraram.");
            return;
        }
        WarpPoint spawn = warp.get();
        if (Bukkit.getWorld(spawn.world()) == null) {
            warnOnce("world", "Spawn na entrada: o mundo '" + spawn.world() + "' da warp '" + spawn.name()
                + "' não está carregado. Os jogadores permanecem onde entraram.");
            return;
        }
        TeleportPlan plan = new TeleportPlan(
            TeleportCause.JOIN_SPAWN,
            Destination.ofWarp(spawn, spawn.displayName()),
            null,
            null,
            false,
            false,
            false,
            config.safety().modeFor(TeleportCause.JOIN_SPAWN),
            config.safety().respectWorldBorder(),
            joinSpawn.notifyPlayer() ? "join-spawn.teleported" : null,
            new String[] {"warp", spawn.displayName()},
            joinSpawn.notifyPlayer());
        String playerName = player.getName();
        teleports.start(player, plan).thenAccept(result -> {
            if (result.status() == TeleportResult.Status.FAILED) {
                warnOnce("fail-" + result.reason(), "Spawn na entrada falhou para " + playerName + ": " + result.reason()
                    + ". Verifique a warp '" + spawn.name() + "'.");
            }
        });
    }

    public void handleQuit(UUID id) {
        cancelScheduled(id);
    }

    public void shutdown() {
        for (BukkitTask task : scheduled.values()) {
            task.cancel();
        }
        scheduled.clear();
    }

    private void cancelScheduled(UUID id) {
        BukkitTask previous = scheduled.remove(id);
        if (previous != null) {
            previous.cancel();
        }
    }

    private void warnOnce(String key, String message) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(key);
        if (last == null || now - last >= WARN_INTERVAL_MILLIS) {
            lastWarning.put(key, now);
            plugin.getLogger().warning(message);
        }
    }
}
