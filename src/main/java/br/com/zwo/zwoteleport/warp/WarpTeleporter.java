package br.com.zwo.zwoteleport.warp;

import br.com.zwo.zwoteleport.api.TeleportCause;
import br.com.zwo.zwoteleport.api.TeleportResult;
import br.com.zwo.zwoteleport.config.Settings;
import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.teleport.Destination;
import br.com.zwo.zwoteleport.teleport.TeleportPlan;
import br.com.zwo.zwoteleport.teleport.TeleportService;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Teleporte de um jogador para uma warp pelos comandos /warp, /spawn, /loja e atalhos.
 */
public final class WarpTeleporter {

    private final WarpService warps;
    private final TeleportService teleports;
    private final Messages messages;
    private final Supplier<Settings> settings;

    public WarpTeleporter(WarpService warps, TeleportService teleports, Messages messages, Supplier<Settings> settings) {
        this.warps = warps;
        this.teleports = teleports;
        this.messages = messages;
        this.settings = settings;
    }

    /** Usa a mensagem "&lt;warp&gt;.teleported" se existir (ex.: spawn, loja), senão "warp.teleported". */
    public CompletableFuture<TeleportResult> teleport(Player player, String warpName) {
        String name = WarpNames.normalize(warpName);
        String key = messages.has(name + ".teleported") ? name + ".teleported" : "warp.teleported";
        TeleportCause cause = name.equals(WarpNames.SPAWN) ? TeleportCause.SPAWN : TeleportCause.WARP;
        return teleport(player, name, cause, key);
    }

    public CompletableFuture<TeleportResult> teleport(Player player, String warpName, TeleportCause cause, String successKey) {
        Settings config = settings.get();
        String name = WarpNames.normalize(warpName);
        Optional<WarpPoint> found = warps.get(name);
        if (found.isEmpty()) {
            if (name.equals(WarpNames.SPAWN)) {
                messages.send(player, "spawn.not-set");
            } else {
                messages.send(player, "warp.not-found", "warp", warpName);
            }
            return CompletableFuture.completedFuture(new TeleportResult(TeleportResult.Status.FAILED, TeleportResult.Reason.INVALID_DESTINATION, null));
        }
        WarpPoint warp = found.get();
        if (!canUse(player, warp)) {
            messages.send(player, "warp.no-access", "warp", warp.displayName());
            return CompletableFuture.completedFuture(new TeleportResult(TeleportResult.Status.BLOCKED, TeleportResult.Reason.CANCELLED_BY_PLUGIN, null));
        }
        TeleportPlan plan = new TeleportPlan(
            cause,
            Destination.ofWarp(warp, warp.displayName()),
            null,
            null,
            config.warps().useWarmup(),
            true,
            true,
            config.safety().modeFor(cause),
            config.safety().respectWorldBorder(),
            successKey,
            new String[] {"warp", warp.displayName()},
            true);
        return teleports.start(player, plan);
    }

    public boolean canUse(Player player, WarpPoint warp) {
        Settings.Warps config = settings.get().warps();
        return WarpAccess.canUse(player::hasPermission, warp, config.requirePerWarpPermission(), config.perWarpPermissionFormat());
    }
}
