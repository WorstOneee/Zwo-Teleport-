package br.com.zwo.zwoteleport.behavior;

import br.com.zwo.zwoteleport.api.SafetyMode;
import br.com.zwo.zwoteleport.api.TeleportCause;
import br.com.zwo.zwoteleport.api.TeleportResult;
import br.com.zwo.zwoteleport.api.TeleportResult.Reason;
import br.com.zwo.zwoteleport.api.TeleportResult.Status;
import br.com.zwo.zwoteleport.api.event.ZwoPreTeleportEvent;
import br.com.zwo.zwoteleport.api.event.ZwoTeleportCancelledEvent;
import br.com.zwo.zwoteleport.api.event.ZwoTeleportCompletedEvent;
import br.com.zwo.zwoteleport.api.event.ZwoTeleportFailedEvent;
import br.com.zwo.zwoteleport.fake.FakePlayer;
import br.com.zwo.zwoteleport.fake.Fixture;
import br.com.zwo.zwoteleport.teleport.Destination;
import br.com.zwo.zwoteleport.teleport.TeleportPlan;
import br.com.zwo.zwoteleport.util.Permissions;
import org.bukkit.Location;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Comportamento do serviço central de teleporte no servidor simulado.
 */
public class TeleportServiceBehaviorTest {

    private static TeleportResult result(CompletableFuture<TeleportResult> future) {
        assertTrue("o resultado já deveria estar definido", future.isDone());
        return future.join();
    }

    @Test
    public void warmupWaitsTheConfiguredTimeThenTeleports() {
        Fixture f = new Fixture();
        FakePlayer player = f.join("Ana");
        CompletableFuture<TeleportResult> future = f.teleports.start(player.handle, f.warpPlan(f.at(100, 70, 100)));

        assertTrue(player.received("Aguarde 10s"));
        f.seconds(9);
        assertFalse(future.isDone());
        assertTrue(player.teleports.isEmpty());
        f.seconds(1);
        assertEquals(Status.COMPLETED, result(future).status());
        assertEquals(100.0, player.location.getX(), 1e-9);
        assertEquals(90f, player.location.getYaw(), 1e-6);
        assertEquals(1, f.server.events(ZwoTeleportCompletedEvent.class).size());
        assertEquals("Teleporte concluído.", player.lastMessage());
    }

    @Test
    public void successIsAnnouncedOnlyAfterTheServerConfirms() {
        Fixture f = new Fixture();
        FakePlayer player = f.join("Ana");
        player.teleportMode = FakePlayer.TeleportMode.MANUAL;
        CompletableFuture<TeleportResult> future = f.teleports.start(player.handle, f.adminPlan(f.at(5, 64, 5)));

        assertTrue(player.hasPendingTeleport());
        assertFalse(future.isDone());
        assertFalse(player.received("Teleporte concluído"));
        assertTrue(f.server.events(ZwoTeleportCompletedEvent.class).isEmpty());

        player.completeTeleport(true);
        assertEquals(Status.COMPLETED, result(future).status());
        assertTrue(player.received("Teleporte concluído"));
    }

    @Test
    public void rejectedTeleportFailsWithoutSuccessOrCooldown() {
        Fixture f = new Fixture("teleport.warmup.enabled: false");
        FakePlayer player = f.join("Ana");
        player.teleportMode = FakePlayer.TeleportMode.REJECT;
        TeleportResult outcome = result(f.teleports.start(player.handle, f.warpPlan(f.at(5, 64, 5))));

        assertEquals(Status.FAILED, outcome.status());
        assertEquals(Reason.TELEPORT_REJECTED, outcome.reason());
        assertFalse(player.received("Teleporte concluído"));
        assertTrue(player.received("Não foi possível completar o teleporte."));
        assertEquals("falha não consome cooldown", 0, f.teleports.cooldowns().remainingSeconds(player.uuid, System.currentTimeMillis()));
        assertEquals(1, f.server.events(ZwoTeleportFailedEvent.class).size());
    }

    @Test
    public void cooldownStartsOnlyAfterSuccessAndBlocksTheNextTeleport() {
        Fixture f = new Fixture("teleport.warmup.enabled: false");
        FakePlayer player = f.join("Ana");
        assertEquals(Status.COMPLETED, result(f.teleports.start(player.handle, f.warpPlan(f.at(5, 64, 5)))).status());
        TeleportResult second = result(f.teleports.start(player.handle, f.warpPlan(f.at(9, 64, 9))));
        assertEquals(Status.BLOCKED, second.status());
        assertEquals(Reason.COOLDOWN, second.reason());
        assertTrue(player.lastMessage().startsWith("Aguarde 10s"));
    }

    @Test
    public void adminAndBypassSkipCooldownAndWarmup() {
        Fixture f = new Fixture();
        FakePlayer admin = f.join("Staff");
        admin.permissions.add(Permissions.ADMIN);
        assertEquals(Status.COMPLETED, result(f.teleports.start(admin.handle, f.warpPlan(f.at(5, 64, 5)))).status());
        assertEquals(Status.COMPLETED, result(f.teleports.start(admin.handle, f.warpPlan(f.at(6, 64, 6)))).status());
    }

    @Test
    public void warmupBypassFlagOffMakesAdminsWait() {
        Fixture f = new Fixture("teleport.warmup.allow-bypass-permission: false");
        FakePlayer admin = f.join("Staff");
        admin.permissions.add(Permissions.ADMIN);
        CompletableFuture<TeleportResult> future = f.teleports.start(admin.handle, f.warpPlan(f.at(5, 64, 5)));
        assertFalse(future.isDone());
        f.seconds(10);
        assertTrue(future.isDone());
    }

    @Test
    public void walkingAwayCancelsButRotationAndJumpingDoNot() {
        Fixture f = new Fixture();
        FakePlayer player = f.join("Ana");
        CompletableFuture<TeleportResult> future = f.teleports.start(player.handle, f.warpPlan(f.at(100, 70, 100)));

        Location rotated = player.location.clone();
        rotated.setYaw(180f);
        rotated.setPitch(-45f);
        f.teleports.handleMove(player.handle, rotated);
        Location jump = player.location.clone().add(0.1, 1.2, 0.0);
        f.teleports.handleMove(player.handle, jump);
        assertFalse(future.isDone());

        f.teleports.handleMove(player.handle, player.location.clone().add(1.0, 0, 0));
        TeleportResult outcome = result(future);
        assertEquals(Reason.MOVED, outcome.reason());
        assertTrue(player.received("você se moveu"));

        f.seconds(15);
        assertTrue("tarefa cancelada não pode teleportar depois", player.teleports.isEmpty());
    }

    @Test
    public void damageCancelsWarmup() {
        Fixture f = new Fixture();
        FakePlayer player = f.join("Ana");
        CompletableFuture<TeleportResult> future = f.teleports.start(player.handle, f.warpPlan(f.at(1, 64, 1)));
        f.teleports.handleDamage(player.handle);
        assertEquals(Reason.DAMAGED, result(future).reason());
        assertEquals(1, f.server.events(ZwoTeleportCancelledEvent.class).size());
    }

    @Test
    public void combatCancelsWarmupAndBlocksNewTeleports() {
        Fixture f = new Fixture();
        FakePlayer victim = f.join("Ana");
        FakePlayer attacker = f.join("Bruno");
        CompletableFuture<TeleportResult> future = f.teleports.start(victim.handle, f.warpPlan(f.at(1, 64, 1)));

        f.teleports.handleCombat(victim.handle, attacker.handle);
        assertEquals(Reason.COMBAT, result(future).reason());

        TeleportResult blocked = result(f.teleports.start(attacker.handle, f.warpPlan(f.at(1, 64, 1))));
        assertEquals(Reason.COMBAT_TAGGED, blocked.reason());
        assertTrue(attacker.lastMessage().startsWith("Você está em combate"));
    }

    @Test
    public void combatBypassIsNeverTagged() {
        Fixture f = new Fixture();
        FakePlayer staff = f.join("Staff");
        staff.permissions.add(Permissions.BYPASS_COMBAT);
        FakePlayer other = f.join("Bruno");
        f.teleports.handleCombat(staff.handle, other.handle);
        assertFalse(f.teleports.combat().isTagged(staff.uuid, System.currentTimeMillis()));
        assertTrue(f.teleports.combat().isTagged(other.uuid, System.currentTimeMillis()));
    }

    @Test
    public void adminTeleportIgnoresCombat() {
        Fixture f = new Fixture();
        FakePlayer player = f.join("Ana");
        f.teleports.handleCombat(player.handle, f.join("Bruno").handle);
        assertEquals(Status.COMPLETED, result(f.teleports.start(player.handle, f.adminPlan(f.at(3, 64, 3)))).status());
    }

    @Test
    public void disconnectDuringWarmupCancelsAndReconnectIsNotAffected() {
        Fixture f = new Fixture();
        FakePlayer player = f.join("Ana");
        Location start = player.location.clone();
        CompletableFuture<TeleportResult> future = f.teleports.start(player.handle, f.warpPlan(f.at(100, 70, 100)));
        f.seconds(3);

        f.quit(player);
        assertEquals(Reason.QUIT, result(future).reason());

        FakePlayer again = f.rejoin(player);
        f.seconds(20);
        assertTrue("teleporte da conexão anterior não pode agir na reconexão", again.teleports.isEmpty());
        assertEquals(start.getX(), again.location.getX(), 1e-9);
        assertEquals(0, f.server.pendingTasks());
    }

    @Test
    public void newTeleportReplacesThePendingOne() {
        Fixture f = new Fixture();
        FakePlayer player = f.join("Ana");
        CompletableFuture<TeleportResult> first = f.teleports.start(player.handle, f.warpPlan(f.at(10, 64, 10)));
        CompletableFuture<TeleportResult> second = f.teleports.start(player.handle, f.warpPlan(f.at(20, 64, 20)));
        assertEquals(Reason.REPLACED, result(first).reason());
        f.seconds(10);
        assertEquals(Status.COMPLETED, result(second).status());
        assertEquals(1, player.teleports.size());
        assertEquals(20.0, player.location.getX(), 1e-9);
    }

    @Test
    public void unloadedWorldIsDetectedWhenTheWarmupEnds() {
        Fixture f = new Fixture();
        FakePlayer player = f.join("Ana");
        Location destination = new Location(f.server.world("eventos_mundo"), 0, 64, 0);
        CompletableFuture<TeleportResult> future = f.teleports.start(player.handle, f.warpPlan(destination));
        f.server.unloadWorld("eventos_mundo");
        f.seconds(10);
        assertEquals(Reason.WORLD_UNAVAILABLE, result(future).reason());
        assertTrue(player.received("mundo indisponível"));
        assertTrue(player.teleports.isEmpty());
    }

    @Test
    public void invalidCoordinatesAreRejectedBeforeAnything() {
        Fixture f = new Fixture();
        FakePlayer player = f.join("Ana");
        TeleportResult nan = result(f.teleports.start(player.handle, f.adminPlan(new Location(f.world, Double.NaN, 64, 0))));
        assertEquals(Reason.INVALID_DESTINATION, nan.reason());
        TeleportResult far = result(f.teleports.start(player.handle, f.adminPlan(new Location(f.world, 40_000_000, 64, 0))));
        assertEquals(Reason.OUTSIDE_LIMITS, far.reason());
        TeleportResult deep = result(f.teleports.start(player.handle, f.adminPlan(new Location(f.world, 0, -200, 0))));
        assertEquals(Reason.OUTSIDE_LIMITS, deep.reason());
        assertTrue(player.teleports.isEmpty());
        assertTrue(f.server.events(ZwoPreTeleportEvent.class).isEmpty());
    }

    @Test
    public void otherPluginsCanBlockAtStartAndAtExecution() {
        Fixture f = new Fixture();
        FakePlayer player = f.join("Ana");
        f.server.eventHooks.add(event -> {
            if (event instanceof ZwoPreTeleportEvent pre && pre.getPhase() == ZwoPreTeleportEvent.Phase.START) {
                pre.setCancelled(true);
            }
        });
        TeleportResult blocked = result(f.teleports.start(player.handle, f.warpPlan(f.at(1, 64, 1))));
        assertEquals(Reason.CANCELLED_BY_PLUGIN, blocked.reason());
        assertEquals(Status.BLOCKED, blocked.status());

        f.server.eventHooks.clear();
        f.server.eventHooks.add(event -> {
            if (event instanceof ZwoPreTeleportEvent pre && pre.getPhase() == ZwoPreTeleportEvent.Phase.EXECUTE) {
                pre.setCancelled(true);
            }
        });
        CompletableFuture<TeleportResult> future = f.teleports.start(player.handle, f.warpPlan(f.at(1, 64, 1)));
        f.seconds(10);
        assertEquals(Status.CANCELLED, result(future).status());
        assertTrue(player.teleports.isEmpty());
    }

    @Test
    public void anchorLeavingCancelsTheTpaTeleport() {
        Fixture f = new Fixture();
        FakePlayer mover = f.join("Ana");
        FakePlayer anchor = f.join("Bruno");
        anchor.moveTo(50, 64, 50);
        TeleportPlan plan = new TeleportPlan(TeleportCause.TPA,
            Destination.ofPlayer(anchor.uuid, anchor.name, Set.of()), anchor.uuid, anchor.name,
            true, true, false, SafetyMode.NONE, true, "teleport.success", new String[0], true);
        CompletableFuture<TeleportResult> future = f.teleports.start(mover.handle, plan);
        f.quit(anchor);
        assertEquals(Reason.ANCHOR_OFFLINE, result(future).reason());
        assertTrue(mover.received("Bruno saiu do servidor"));
    }

    @Test
    public void tpaFollowsTheAnchorDuringTheWarmup() {
        Fixture f = new Fixture();
        FakePlayer mover = f.join("Ana");
        FakePlayer anchor = f.join("Bruno");
        anchor.moveTo(50, 64, 50);
        TeleportPlan plan = new TeleportPlan(TeleportCause.TPA,
            Destination.ofPlayer(anchor.uuid, anchor.name, Set.of()), anchor.uuid, anchor.name,
            true, true, false, SafetyMode.NONE, true, "teleport.success", new String[0], true);
        CompletableFuture<TeleportResult> future = f.teleports.start(mover.handle, plan);
        anchor.moveTo(80, 64, -30);
        f.seconds(10);
        assertEquals(Status.COMPLETED, result(future).status());
        assertEquals(80.0, mover.location.getX(), 1e-9);
    }

    @Test
    public void shutdownCancelsPendingTeleportsAndLeavesNoTasks() {
        Fixture f = new Fixture();
        FakePlayer player = f.join("Ana");
        CompletableFuture<TeleportResult> future = f.teleports.start(player.handle, f.warpPlan(f.at(1, 64, 1)));
        f.teleports.shutdown();
        assertEquals(Reason.SHUTDOWN, result(future).reason());
        assertEquals(0, f.server.pendingTasks());
        f.seconds(20);
        assertTrue(player.teleports.isEmpty());
    }

    @Test
    public void actionBarCountsDown() {
        Fixture f = new Fixture("teleport.warmup.seconds: 3");
        FakePlayer player = f.join("Ana");
        f.teleports.start(player.handle, f.warpPlan(f.at(1, 64, 1)));
        f.seconds(3);
        assertEquals(List.of("Teleportando em 3s...", "Teleportando em 2s...", "Teleportando em 1s..."), player.actionBars);
    }

    @Test
    public void chunkThatFailsToLoadFailsTheSafetyCheckWithoutTeleporting() {
        Fixture f = new Fixture("teleport.warmup.enabled: false");
        FakePlayer player = f.join("Ana");
        TeleportPlan plan = new TeleportPlan(TeleportCause.PLUGIN, Destination.of(f.at(1, 64, 1)), null, null,
            false, true, false, SafetyMode.VERIFY, true, "teleport.success", new String[0], true);
        CompletableFuture<TeleportResult> future = f.teleports.start(player.handle, plan);
        f.server.runPending();
        assertEquals(Reason.CHUNK_LOAD_FAILED, result(future).reason());
        assertTrue(player.teleports.isEmpty());
        assertTrue(player.received("Não foi possível completar o teleporte."));
    }

    @Test
    public void deadPlayerIsNotTeleported() {
        Fixture f = new Fixture();
        FakePlayer player = f.join("Ana");
        player.dead = true;
        assertEquals(Reason.PLAYER_DEAD, result(f.teleports.start(player.handle, f.adminPlan(f.at(1, 64, 1)))).reason());
    }
}
