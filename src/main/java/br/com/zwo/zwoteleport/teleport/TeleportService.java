package br.com.zwo.zwoteleport.teleport;

import br.com.zwo.zwoteleport.api.PendingTeleportInfo;
import br.com.zwo.zwoteleport.api.SafetyMode;
import br.com.zwo.zwoteleport.api.TeleportCause;
import br.com.zwo.zwoteleport.api.TeleportResult;
import br.com.zwo.zwoteleport.api.TeleportResult.Reason;
import br.com.zwo.zwoteleport.api.TeleportResult.Status;
import br.com.zwo.zwoteleport.api.event.ZwoPreTeleportEvent;
import br.com.zwo.zwoteleport.api.event.ZwoTeleportCancelledEvent;
import br.com.zwo.zwoteleport.api.event.ZwoTeleportCompletedEvent;
import br.com.zwo.zwoteleport.api.event.ZwoTeleportFailedEvent;
import br.com.zwo.zwoteleport.config.Settings;
import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.teleport.safety.SafeLocationFinder;
import br.com.zwo.zwoteleport.teleport.safety.SnapshotBlockGrid;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Serviço central de teleporte. Todo teleporte do plugin passa por aqui.
 *
 * <p>Garantias:</p>
 * <ul>
 *   <li>no máximo um teleporte ativo por jogador; um novo substitui o anterior;</li>
 *   <li>destino resolvido e validado de novo no fim do warmup (mundo, coordenadas, limites, combate,
 *       outros plugins);</li>
 *   <li>tarefas atrasadas verificam o id do teleporte e a sessão do jogador, então não agem depois
 *       de um cancelamento nem em uma reconexão;</li>
 *   <li>a busca de posição segura roda fora da thread principal sobre um snapshot do chunk carregado
 *       de forma assíncrona; tudo que toca jogador e mundo roda na thread principal;</li>
 *   <li>sucesso só é anunciado depois que o servidor confirma o teleporte.</li>
 * </ul>
 */
public final class TeleportService {

    private final Plugin plugin;
    private final Messages messages;
    private final Supplier<Settings> settings;
    private final CooldownTracker cooldowns = new CooldownTracker();
    private final CombatTracker combat = new CombatTracker();
    private final SessionRegistry sessions = new SessionRegistry();
    private final Map<UUID, PendingTeleport> active = new HashMap<>();
    private long nextId = 1;
    private boolean shuttingDown;

    public TeleportService(Plugin plugin, Messages messages, Supplier<Settings> settings) {
        this.plugin = plugin;
        this.messages = messages;
        this.settings = settings;
    }

    // ------------------------------------------------------------------ sessões

    public long openSession(Player player) {
        return sessions.open(player.getUniqueId());
    }

    public boolean isSessionCurrent(UUID player, long token) {
        return sessions.isCurrent(player, token);
    }

    // ------------------------------------------------------------------ início

    public CompletableFuture<TeleportResult> start(Player player, TeleportPlan plan) {
        requireMainThread();
        if (shuttingDown || !player.isOnline()) {
            return CompletableFuture.completedFuture(new TeleportResult(Status.FAILED, Reason.PLAYER_OFFLINE, null));
        }
        UUID id = player.getUniqueId();
        OptionalLong current = sessions.current(id);
        long session = current.isPresent() ? current.getAsLong() : sessions.open(id);
        Settings config = settings.get();
        long now = System.currentTimeMillis();

        PendingTeleport previous = active.get(id);
        if (previous != null && previous.state != PendingTeleport.State.DONE) {
            finish(previous, Status.CANCELLED, Reason.REPLACED, false, null, null);
        }

        if (plan.applyCooldown() && !BypassRules.cooldown(player::hasPermission, config.cooldown().allowBypassPermission())) {
            long left = cooldowns.remainingSeconds(id, now);
            if (left > 0) {
                notify(player, plan, "teleport.cooldown", "seconds", String.valueOf(left));
                return blocked(Reason.COOLDOWN);
            }
        }
        if (plan.checkCombat() && blockedByCombat(player, now)) {
            notify(player, plan, "teleport.blocked-combat", "seconds", String.valueOf(combat.remainingSeconds(id, now)));
            return blocked(Reason.COMBAT_TAGGED);
        }
        if (player.isDead()) {
            notify(player, plan, "teleport.dead");
            return CompletableFuture.completedFuture(new TeleportResult(Status.FAILED, Reason.PLAYER_DEAD, null));
        }

        Destination.Resolved resolved = plan.destination().resolve();
        Reason invalid = resolved.failed() ? resolved.failure() : validate(resolved.location(), plan);
        if (invalid != null) {
            notifyFailure(player, plan, invalid, resolved);
            return CompletableFuture.completedFuture(new TeleportResult(Status.FAILED, invalid, null));
        }

        long teleportId = nextId++;
        ZwoPreTeleportEvent startEvent = new ZwoPreTeleportEvent(
            player, teleportId, plan.cause(), ZwoPreTeleportEvent.Phase.START, resolved.location());
        Bukkit.getPluginManager().callEvent(startEvent);
        if (startEvent.isCancelled()) {
            if (plan.notifyPlayer()) {
                if (startEvent.getCancelMessage() != null) {
                    player.sendMessage(startEvent.getCancelMessage());
                } else {
                    messages.send(player, "teleport.blocked");
                }
            }
            return blocked(Reason.CANCELLED_BY_PLUGIN);
        }

        Location origin = player.getLocation();
        PendingTeleport pending = new PendingTeleport(teleportId, id, session, plan,
            origin.getWorld() == null ? "" : origin.getWorld().getName(), origin.getX(), origin.getY(), origin.getZ());
        active.put(id, pending);

        Settings.Warmup warmup = config.warmup();
        boolean needsWarmup = plan.warmup()
            && warmup.enabled()
            && warmup.seconds() > 0
            && !BypassRules.warmup(player::hasPermission, warmup.allowBypassPermission());
        if (needsWarmup) {
            pending.secondsLeft = warmup.seconds();
            notify(player, plan, "teleport.warmup", "seconds", String.valueOf(warmup.seconds()));
            if (warmup.actionbarCountdown()) {
                notify(player, plan, "teleport.warmup-countdown", "seconds", String.valueOf(warmup.seconds()));
            }
            pending.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(pending), 20L, 20L);
        } else {
            execute(pending);
        }
        return pending.future;
    }

    private void tick(PendingTeleport pending) {
        if (pending.state != PendingTeleport.State.WARMUP || active.get(pending.player) != pending) {
            if (pending.task != null) {
                pending.task.cancel();
            }
            return;
        }
        if (!sessions.isCurrent(pending.player, pending.session)) {
            finish(pending, Status.CANCELLED, Reason.QUIT, false, null, null);
            return;
        }
        pending.secondsLeft--;
        if (pending.secondsLeft > 0) {
            if (settings.get().warmup().actionbarCountdown()) {
                Player player = livePlayer(pending);
                if (player != null) {
                    notify(player, pending.plan, "teleport.warmup-countdown", "seconds", String.valueOf(pending.secondsLeft));
                }
            }
            return;
        }
        pending.task.cancel();
        pending.task = null;
        execute(pending);
    }

    // ------------------------------------------------------------------ execução

    private void execute(PendingTeleport pending) {
        pending.state = PendingTeleport.State.EXECUTING;
        Player player = livePlayer(pending);
        if (player == null) {
            finish(pending, Status.CANCELLED, Reason.QUIT, false, null, null);
            return;
        }
        TeleportPlan plan = pending.plan;
        if (player.isDead()) {
            finish(pending, Status.FAILED, Reason.PLAYER_DEAD, true, "teleport.dead", null);
            return;
        }
        if (plan.checkCombat() && blockedByCombat(player, System.currentTimeMillis())) {
            finish(pending, Status.CANCELLED, Reason.COMBAT, true, "teleport.cancelled-combat", null);
            return;
        }
        Destination.Resolved resolved = plan.destination().resolve();
        Reason invalid = resolved.failed() ? resolved.failure() : validate(resolved.location(), plan);
        if (invalid != null) {
            finishFailure(pending, invalid, resolved);
            return;
        }
        Location destination = resolved.location();

        ZwoPreTeleportEvent executeEvent = new ZwoPreTeleportEvent(
            player, pending.id, plan.cause(), ZwoPreTeleportEvent.Phase.EXECUTE, destination);
        Bukkit.getPluginManager().callEvent(executeEvent);
        if (pending.state != PendingTeleport.State.EXECUTING) {
            return;
        }
        if (executeEvent.isCancelled()) {
            if (plan.notifyPlayer() && executeEvent.getCancelMessage() != null) {
                player.sendMessage(executeEvent.getCancelMessage());
                finish(pending, Status.CANCELLED, Reason.CANCELLED_BY_PLUGIN, false, null, null);
            } else {
                finish(pending, Status.CANCELLED, Reason.CANCELLED_BY_PLUGIN, true, "teleport.cancelled-plugin", null);
            }
            return;
        }

        if (plan.safety() == SafetyMode.NONE) {
            dispatch(pending, destination);
            return;
        }
        checkSafetyThenDispatch(pending, destination);
    }

    private void checkSafetyThenDispatch(PendingTeleport pending, Location destination) {
        World world = destination.getWorld();
        Settings.Safety safety = settings.get().safety();
        int blockX = destination.getBlockX();
        int blockY = destination.getBlockY();
        int blockZ = destination.getBlockZ();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        boolean adjust = pending.plan.safety() == SafetyMode.ADJUST;

        world.getChunkAtAsync(blockX >> 4, blockZ >> 4).whenComplete((chunk, error) -> runOnMain(() -> {
            if (!stillRunning(pending)) {
                return;
            }
            if (error != null || chunk == null) {
                if (error != null) {
                    plugin.getLogger().log(Level.WARNING, "Falha ao carregar o chunk do destino de um teleporte.", error);
                }
                finish(pending, Status.FAILED, Reason.CHUNK_LOAD_FAILED, true, "teleport.failed", null);
                return;
            }
            ChunkSnapshot snapshot = snapshotOf(chunk);
            SnapshotBlockGrid grid = new SnapshotBlockGrid(snapshot, minY, maxY);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Optional<SafeLocationFinder.Position> found;
                if (adjust) {
                    found = SafeLocationFinder.findNearest(grid, blockX, blockY, blockZ,
                        safety.searchRadius(), safety.searchHeight(), safety.allowWater());
                } else {
                    found = SafeLocationFinder.isSafe(grid, blockX, blockY, blockZ, safety.allowWater())
                        ? Optional.of(new SafeLocationFinder.Position(blockX, blockY, blockZ))
                        : Optional.empty();
                }
                runOnMain(() -> {
                    if (!stillRunning(pending)) {
                        return;
                    }
                    if (found.isEmpty()) {
                        finish(pending, Status.FAILED, Reason.UNSAFE_DESTINATION, true, "teleport.unsafe-destination", null);
                        return;
                    }
                    SafeLocationFinder.Position position = found.get();
                    Location target = destination.clone();
                    if (position.x() != blockX || position.y() != blockY || position.z() != blockZ) {
                        target = new Location(world, position.x() + 0.5D, position.y(), position.z() + 0.5D,
                            destination.getYaw(), destination.getPitch());
                    }
                    dispatch(pending, target);
                });
            });
        }));
    }

    private static ChunkSnapshot snapshotOf(Chunk chunk) {
        return chunk.getChunkSnapshot(false, false, false, false);
    }

    private void dispatch(PendingTeleport pending, Location destination) {
        Player player = livePlayer(pending);
        if (player == null) {
            finish(pending, Status.CANCELLED, Reason.QUIT, false, null, null);
            return;
        }
        pending.state = PendingTeleport.State.DISPATCHED;
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        PlayerTeleportEvent.TeleportCause cause = pending.plan.cause() == TeleportCause.ADMIN_COMMAND
            ? PlayerTeleportEvent.TeleportCause.COMMAND
            : PlayerTeleportEvent.TeleportCause.PLUGIN;
        CompletableFuture<Boolean> teleport;
        try {
            teleport = player.teleportAsync(destination, cause);
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            finish(pending, Status.FAILED, Reason.TELEPORT_REJECTED, true, "teleport.failed", null);
            return;
        }
        teleport.whenComplete((success, error) -> runOnMain(() -> {
            if (pending.state == PendingTeleport.State.DONE) {
                return;
            }
            if (error != null || !Boolean.TRUE.equals(success)) {
                finish(pending, Status.FAILED, Reason.TELEPORT_REJECTED, true, "teleport.failed", null);
                return;
            }
            Player moved = Bukkit.getPlayer(pending.player);
            if (pending.plan.applyCooldown() && moved != null
                && !BypassRules.cooldown(moved::hasPermission, settings.get().cooldown().allowBypassPermission())) {
                cooldowns.apply(pending.player, System.currentTimeMillis(), settings.get().cooldown().seconds());
            }
            Location finalLocation = moved != null ? moved.getLocation() : destination;
            finish(pending, Status.COMPLETED, Reason.COMPLETED, true, pending.plan.successKey(), finalLocation);
        }));
    }

    // ------------------------------------------------------------------ finalização

    private void finishFailure(PendingTeleport pending, Reason reason, Destination.Resolved resolved) {
        if (resolved != null && resolved.messageKey() != null) {
            Player player = livePlayer(pending);
            if (player != null && pending.plan.notifyPlayer()) {
                messages.send(player, resolved.messageKey(), resolved.placeholders());
            }
            finish(pending, statusOf(reason), reason, false, null, null);
            return;
        }
        finish(pending, statusOf(reason), reason, true, failureKey(reason), null);
    }

    /**
     * Conclui o teleporte uma única vez: cancela a tarefa, libera o jogador, envia a mensagem,
     * dispara exatamente um evento final e completa o future.
     */
    private void finish(PendingTeleport pending, Status status, Reason reason, boolean sendMessage, String messageKey, Location finalLocation) {
        if (pending.state == PendingTeleport.State.DONE) {
            return;
        }
        pending.state = PendingTeleport.State.DONE;
        if (pending.task != null) {
            pending.task.cancel();
            pending.task = null;
        }
        active.remove(pending.player, pending);

        Player player = Bukkit.getPlayer(pending.player);
        boolean sameSession = player != null && sessions.isCurrent(pending.player, pending.session);
        if (sendMessage && messageKey != null && sameSession && (pending.plan.notifyPlayer() || status == Status.COMPLETED)) {
            if (status == Status.COMPLETED) {
                messages.send(player, messageKey, pending.plan.successPlaceholders());
            } else if (reason == Reason.ANCHOR_OFFLINE) {
                messages.send(player, messageKey, "player", pending.plan.anchorName());
            } else {
                messages.send(player, messageKey);
            }
        }

        if (player != null && plugin.isEnabled()) {
            switch (status) {
                case COMPLETED -> Bukkit.getPluginManager().callEvent(
                    new ZwoTeleportCompletedEvent(player, pending.id, pending.plan.cause(), finalLocation));
                case CANCELLED, BLOCKED -> Bukkit.getPluginManager().callEvent(
                    new ZwoTeleportCancelledEvent(player, pending.id, pending.plan.cause(), reason));
                case FAILED -> Bukkit.getPluginManager().callEvent(
                    new ZwoTeleportFailedEvent(player, pending.id, pending.plan.cause(), reason));
            }
        }
        pending.future.complete(new TeleportResult(status, reason, status == Status.COMPLETED ? finalLocation : null));
    }

    // ------------------------------------------------------------------ cancelamentos externos

    /** Movimento do jogador. Só considera posição; rotação é ignorada pelo listener e aqui. */
    public void handleMove(Player player, Location to) {
        PendingTeleport pending = active.get(player.getUniqueId());
        if (pending == null || pending.state != PendingTeleport.State.WARMUP) {
            return;
        }
        Settings.Warmup warmup = settings.get().warmup();
        if (!warmup.cancelOnMove() || to == null || to.getWorld() == null) {
            return;
        }
        boolean changedWorld = !to.getWorld().getName().equals(pending.originWorld);
        if (changedWorld || warmup.movement().exceeded(
            pending.originX, pending.originY, pending.originZ, to.getX(), to.getY(), to.getZ())) {
            finish(pending, Status.CANCELLED, Reason.MOVED, true, "teleport.cancelled-move", null);
        }
    }

    public void handleDamage(Player player) {
        PendingTeleport pending = active.get(player.getUniqueId());
        if (pending != null && pending.state == PendingTeleport.State.WARMUP && settings.get().warmup().cancelOnDamage()) {
            finish(pending, Status.CANCELLED, Reason.DAMAGED, true, "teleport.cancelled-damage", null);
        }
    }

    /** Teleporte feito por outro plugin, portal ou pérola durante o warmup. */
    public void handleExternalTeleport(Player player) {
        PendingTeleport pending = active.get(player.getUniqueId());
        if (pending != null && pending.state == PendingTeleport.State.WARMUP) {
            finish(pending, Status.CANCELLED, Reason.EXTERNAL_TELEPORT, true, "teleport.cancelled-teleported", null);
        }
    }

    /** Marca atacante e vítima. Atacante pode ser nulo (dano sem jogador responsável não marca). */
    public void handleCombat(Player victim, Player attacker) {
        Settings.Combat config = settings.get().combat();
        if (!config.enabled() || config.tagSeconds() <= 0 || attacker == null
            || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (config.tagVictim()) {
            tag(victim, config);
        }
        if (config.tagAttacker()) {
            tag(attacker, config);
        }
    }

    private void tag(Player player, Settings.Combat config) {
        if (BypassRules.combat(player::hasPermission)) {
            combat.clear(player.getUniqueId());
            return;
        }
        combat.tag(player.getUniqueId(), System.currentTimeMillis(), config.tagSeconds());
        PendingTeleport pending = active.get(player.getUniqueId());
        if (config.cancelWarmupOnCombat() && pending != null && pending.state == PendingTeleport.State.WARMUP
            && pending.plan.checkCombat()) {
            finish(pending, Status.CANCELLED, Reason.COMBAT, true, "teleport.cancelled-combat", null);
        }
    }

    /** Saída do servidor: cancela o teleporte do jogador e os que tinham ele como destino. */
    public void handleQuit(UUID player) {
        PendingTeleport own = active.get(player);
        if (own != null) {
            finish(own, Status.CANCELLED, Reason.QUIT, false, null, null);
        }
        for (PendingTeleport pending : new ArrayList<>(active.values())) {
            if (player.equals(pending.plan.anchor()) && pending.interruptible()) {
                finish(pending, Status.CANCELLED, Reason.ANCHOR_OFFLINE, true, "teleport.anchor-left", null);
            }
        }
        combat.recordQuit(player, System.currentTimeMillis());
        sessions.close(player);
    }

    /** Cancelamento pedido pela API ou por comando. */
    public boolean cancel(UUID player, Reason reason, boolean notifyPlayer) {
        PendingTeleport pending = active.get(player);
        if (pending == null || !pending.interruptible()) {
            return false;
        }
        finish(pending, Status.CANCELLED, reason, notifyPlayer, "teleport.cancelled-plugin", null);
        return true;
    }

    // ------------------------------------------------------------------ consultas

    public boolean blockedByCombat(Player player, long now) {
        Settings.Combat config = settings.get().combat();
        if (!config.enabled() || !config.blockWhileTagged()) {
            return false;
        }
        if (BypassRules.combat(player::hasPermission)) {
            combat.clear(player.getUniqueId());
            return false;
        }
        return combat.isTagged(player.getUniqueId(), now);
    }

    public CombatTracker combat() {
        return combat;
    }

    public CooldownTracker cooldowns() {
        return cooldowns;
    }

    public Optional<PendingTeleportInfo> pending(UUID player) {
        PendingTeleport pending = active.get(player);
        if (pending == null || pending.state == PendingTeleport.State.DONE) {
            return Optional.empty();
        }
        return Optional.of(new PendingTeleportInfo(player, pending.id, pending.plan.cause(),
            Math.max(0, pending.secondsLeft), pending.state != PendingTeleport.State.WARMUP));
    }

    public int activeCount() {
        return active.size();
    }

    public void maintenance() {
        long now = System.currentTimeMillis();
        cooldowns.purge(now);
        combat.purge(now);
    }

    public void clearCombatTags() {
        combat.clearAll();
    }

    public void shutdown() {
        shuttingDown = true;
        for (PendingTeleport pending : new ArrayList<>(active.values())) {
            finish(pending, Status.CANCELLED, Reason.SHUTDOWN, false, null, null);
        }
        active.clear();
        sessions.clear();
    }

    // ------------------------------------------------------------------ apoio

    private Reason validate(Location location, TeleportPlan plan) {
        if (location == null) {
            return Reason.INVALID_DESTINATION;
        }
        World world = location.getWorld();
        if (world == null || !location.isWorldLoaded()) {
            return Reason.WORLD_UNAVAILABLE;
        }
        DestinationValidator.Check check = DestinationValidator.check(location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch(), world.getMinHeight(), world.getMaxHeight());
        switch (check) {
            case NOT_FINITE -> {
                return Reason.INVALID_DESTINATION;
            }
            case OUTSIDE_WORLD_LIMITS, OUTSIDE_HEIGHT_LIMITS -> {
                return Reason.OUTSIDE_LIMITS;
            }
            case OK -> {
                // segue
            }
        }
        if (plan.respectWorldBorder() && !world.getWorldBorder().isInside(location)) {
            return Reason.OUTSIDE_LIMITS;
        }
        return null;
    }

    private void notifyFailure(Player player, TeleportPlan plan, Reason reason, Destination.Resolved resolved) {
        if (!plan.notifyPlayer()) {
            return;
        }
        if (resolved != null && resolved.messageKey() != null) {
            messages.send(player, resolved.messageKey(), resolved.placeholders());
            return;
        }
        String key = failureKey(reason);
        if (key != null) {
            messages.send(player, key);
        }
    }

    public static String failureKey(Reason reason) {
        return switch (reason) {
            case PLAYER_DEAD -> "teleport.dead";
            case INVALID_DESTINATION -> "teleport.invalid-destination";
            case WORLD_UNAVAILABLE -> "teleport.world-unavailable";
            case OUTSIDE_LIMITS -> "teleport.outside-limits";
            case UNSAFE_DESTINATION -> "teleport.unsafe-destination";
            case ANCHOR_OFFLINE -> "teleport.anchor-left";
            case RESTRICTED_DESTINATION -> "teleport.blocked";
            case CHUNK_LOAD_FAILED, TELEPORT_REJECTED -> "teleport.failed";
            case MOVED -> "teleport.cancelled-move";
            case DAMAGED -> "teleport.cancelled-damage";
            case COMBAT -> "teleport.cancelled-combat";
            case COMBAT_TAGGED -> "teleport.blocked-combat";
            case EXTERNAL_TELEPORT -> "teleport.cancelled-teleported";
            case COOLDOWN -> "teleport.cooldown";
            case CANCELLED_BY_PLUGIN, CANCELLED_BY_API -> "teleport.cancelled-plugin";
            case COMPLETED, REPLACED, QUIT, SHUTDOWN, PLAYER_OFFLINE -> null;
        };
    }

    private static Status statusOf(Reason reason) {
        return switch (reason) {
            case ANCHOR_OFFLINE, QUIT, REPLACED, SHUTDOWN, MOVED, DAMAGED, COMBAT, EXTERNAL_TELEPORT,
                 CANCELLED_BY_PLUGIN, CANCELLED_BY_API -> Status.CANCELLED;
            case COOLDOWN, COMBAT_TAGGED -> Status.BLOCKED;
            case COMPLETED -> Status.COMPLETED;
            default -> Status.FAILED;
        };
    }

    private void notify(Player player, TeleportPlan plan, String key, String... placeholders) {
        if (plan.notifyPlayer()) {
            messages.send(player, key, placeholders);
        }
    }

    private static CompletableFuture<TeleportResult> blocked(Reason reason) {
        return CompletableFuture.completedFuture(new TeleportResult(Status.BLOCKED, reason, null));
    }

    private Player livePlayer(PendingTeleport pending) {
        if (!sessions.isCurrent(pending.player, pending.session)) {
            return null;
        }
        Player player = Bukkit.getPlayer(pending.player);
        return player != null && player.isOnline() ? player : null;
    }

    private boolean stillRunning(PendingTeleport pending) {
        if (pending.state != PendingTeleport.State.EXECUTING) {
            return false;
        }
        if (livePlayer(pending) == null) {
            finish(pending, Status.CANCELLED, Reason.QUIT, false, null, null);
            return false;
        }
        return true;
    }

    private void runOnMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("ZwoTeleport: teleportes devem ser iniciados na thread principal");
        }
    }
}
