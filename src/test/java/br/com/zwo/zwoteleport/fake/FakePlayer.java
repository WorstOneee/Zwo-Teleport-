package br.com.zwo.zwoteleport.fake;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Jogador simulado. O objeto {@link #handle} é o {@link Player} entregue ao plugin.
 */
public final class FakePlayer {

    public enum TeleportMode {
        /** Teleporta e confirma na hora. */
        SUCCEED,
        /** O servidor recusa (ex.: outro plugin cancelou o PlayerTeleportEvent). */
        REJECT,
        /** Fica pendente até o teste chamar {@link #completeTeleport(boolean)}. */
        MANUAL
    }

    public final UUID uuid;
    public final String name;
    public final Player handle;
    public final Set<String> permissions = new HashSet<>();
    public final List<String> messages = new ArrayList<>();
    public final List<String> actionBars = new ArrayList<>();
    public final List<Location> teleports = new ArrayList<>();
    public Location location;
    public boolean online = true;
    public boolean dead;
    public boolean playedBefore;
    public TeleportMode teleportMode = TeleportMode.SUCCEED;
    private CompletableFuture<Boolean> pendingTeleport;
    private Location pendingDestination;

    FakePlayer(FakeServer server, UUID uuid, String name, Location location) {
        this.uuid = uuid;
        this.name = name;
        this.location = location;
        this.handle = FakeServer.proxy(Player.class, (self, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> uuid;
            case "getName" -> name;
            case "isOnline" -> online;
            case "isDead" -> dead;
            case "hasPlayedBefore" -> playedBefore;
            case "getLocation" -> this.location.clone();
            case "getWorld" -> this.location.getWorld();
            case "hasPermission" -> args[0] instanceof String node && permissions.contains(node);
            case "canSee" -> true;
            case "isInsideVehicle" -> false;
            case "sendMessage" -> {
                messages.add(FakeServer.plain(args[args.length - 1]));
                yield null;
            }
            case "sendActionBar" -> {
                actionBars.add(FakeServer.plain(args[0]));
                yield null;
            }
            case "teleportAsync" -> teleport((Location) args[0]);
            default -> FakeServer.objectMethod(self, method, args);
        });
    }

    private CompletableFuture<Boolean> teleport(Location destination) {
        if (!Double.isFinite(destination.getX()) || !Double.isFinite(destination.getY()) || !Double.isFinite(destination.getZ())) {
            throw new IllegalArgumentException("x not finite");
        }
        switch (teleportMode) {
            case SUCCEED -> {
                location = destination.clone();
                teleports.add(destination.clone());
                return CompletableFuture.completedFuture(true);
            }
            case REJECT -> {
                return CompletableFuture.completedFuture(false);
            }
            default -> {
                pendingTeleport = new CompletableFuture<>();
                pendingDestination = destination.clone();
                return pendingTeleport;
            }
        }
    }

    /** Conclui um teleporte em modo MANUAL. */
    public void completeTeleport(boolean success) {
        if (success) {
            location = pendingDestination.clone();
            teleports.add(pendingDestination.clone());
        }
        pendingTeleport.complete(success);
    }

    public boolean hasPendingTeleport() {
        return pendingTeleport != null && !pendingTeleport.isDone();
    }

    public void moveTo(double x, double y, double z) {
        location = new Location(location.getWorld(), x, y, z, location.getYaw(), location.getPitch());
    }

    public String lastMessage() {
        return messages.isEmpty() ? "" : messages.get(messages.size() - 1);
    }

    public boolean received(String text) {
        for (String message : messages) {
            if (message.contains(text)) {
                return true;
            }
        }
        return false;
    }
}
