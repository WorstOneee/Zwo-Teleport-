package br.com.zwo.zwoteleport;

import br.com.zwo.zwoteleport.api.PendingTeleportInfo;
import br.com.zwo.zwoteleport.api.SafetyMode;
import br.com.zwo.zwoteleport.api.TeleportOptions;
import br.com.zwo.zwoteleport.api.TeleportRequestInfo;
import br.com.zwo.zwoteleport.api.TeleportResult;
import br.com.zwo.zwoteleport.api.WarpInfo;
import br.com.zwo.zwoteleport.api.ZwoTeleportApi;
import br.com.zwo.zwoteleport.config.Settings;
import br.com.zwo.zwoteleport.request.TpaService;
import br.com.zwo.zwoteleport.teleport.Destination;
import br.com.zwo.zwoteleport.teleport.TeleportPlan;
import br.com.zwo.zwoteleport.teleport.TeleportService;
import br.com.zwo.zwoteleport.warp.WarpNames;
import br.com.zwo.zwoteleport.warp.WarpPoint;
import br.com.zwo.zwoteleport.warp.WarpService;
import br.com.zwo.zwoteleport.warp.WarpTeleporter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class ApiImpl implements ZwoTeleportApi {

    private final String version;
    private final WarpService warps;
    private final WarpTeleporter warpTeleporter;
    private final TeleportService teleports;
    private final TpaService tpa;
    private final Supplier<Settings> settings;

    ApiImpl(String version, WarpService warps, WarpTeleporter warpTeleporter, TeleportService teleports, TpaService tpa, Supplier<Settings> settings) {
        this.version = version;
        this.warps = warps;
        this.warpTeleporter = warpTeleporter;
        this.teleports = teleports;
        this.tpa = tpa;
        this.settings = settings;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public Optional<WarpInfo> warp(String name) {
        requireMainThread();
        return warps.get(name).map(this::info);
    }

    @Override
    public List<WarpInfo> warps() {
        requireMainThread();
        return warps.all().stream().map(this::info).toList();
    }

    @Override
    public Optional<WarpInfo> spawn() {
        requireMainThread();
        return warps.get(settings.get().joinSpawn().warp()).map(this::info);
    }

    @Override
    public boolean canUseWarp(Player player, String warpName) {
        requireMainThread();
        return warps.get(warpName).map(warp -> warpTeleporter.canUse(player, warp)).orElse(false);
    }

    @Override
    public CompletableFuture<TeleportResult> teleport(Player player, Location destination, TeleportOptions options) {
        requireMainThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(options, "options");
        return teleports.start(player, plan(Destination.of(destination), options, null));
    }

    @Override
    public CompletableFuture<TeleportResult> teleportToWarp(Player player, String warpName, TeleportOptions options) {
        requireMainThread();
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(options, "options");
        Optional<WarpPoint> warp = warps.get(warpName);
        if (warp.isEmpty()) {
            return CompletableFuture.completedFuture(
                new TeleportResult(TeleportResult.Status.FAILED, TeleportResult.Reason.INVALID_DESTINATION, null));
        }
        WarpPoint point = warp.get();
        return teleports.start(player, plan(Destination.ofWarp(point, point.displayName()), options, point));
    }

    private TeleportPlan plan(Destination destination, TeleportOptions options, WarpPoint warp) {
        Settings config = settings.get();
        SafetyMode safety = options.safety() != null ? options.safety() : config.safety().modeFor(options.cause());
        String successKey = null;
        String[] placeholders = new String[0];
        if (options.notifyPlayer()) {
            if (warp != null) {
                String name = WarpNames.normalize(warp.name());
                successKey = name.equals(WarpNames.SPAWN) ? "spawn.teleported" : "warp.teleported";
                placeholders = new String[] {"warp", warp.displayName()};
            } else {
                successKey = "teleport.success";
            }
        }
        return new TeleportPlan(
            options.cause(),
            destination,
            null,
            null,
            options.warmup(),
            options.checkCombat(),
            options.applyCooldown(),
            safety,
            config.safety().respectWorldBorder(),
            successKey,
            placeholders,
            options.notifyPlayer());
    }

    @Override
    public boolean cancelTeleport(Player player) {
        requireMainThread();
        return teleports.cancel(player.getUniqueId(), TeleportResult.Reason.CANCELLED_BY_API, true);
    }

    @Override
    public Optional<PendingTeleportInfo> pendingTeleport(UUID player) {
        requireMainThread();
        return teleports.pending(player);
    }

    @Override
    public List<TeleportRequestInfo> incomingRequests(UUID player) {
        requireMainThread();
        return tpa.incoming(player);
    }

    @Override
    public List<TeleportRequestInfo> outgoingRequests(UUID player) {
        requireMainThread();
        return tpa.outgoing(player);
    }

    @Override
    public boolean isCombatTagged(UUID player) {
        requireMainThread();
        return teleports.combat().isTagged(player, System.currentTimeMillis());
    }

    @Override
    public long combatSecondsLeft(UUID player) {
        requireMainThread();
        return teleports.combat().remainingSeconds(player, System.currentTimeMillis());
    }

    @Override
    public long cooldownSecondsLeft(UUID player) {
        requireMainThread();
        return teleports.cooldowns().remainingSeconds(player, System.currentTimeMillis());
    }

    @Override
    public boolean isTeleportBlockedByCombat(Player player) {
        requireMainThread();
        return teleports.blockedByCombat(player, System.currentTimeMillis());
    }

    private WarpInfo info(WarpPoint warp) {
        return new WarpInfo(warp.name(), warp.displayName(), warp.world(), warp.x(), warp.y(), warp.z(),
            warp.yaw(), warp.pitch(), warp.permission(), Bukkit.getWorld(warp.world()) != null);
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("A API do ZwoTeleport deve ser usada na thread principal");
        }
    }
}
