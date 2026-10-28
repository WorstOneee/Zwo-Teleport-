package br.com.zwo.zwoteleport;

import br.com.zwo.zwoteleport.api.SafetyMode;
import br.com.zwo.zwoteleport.api.TeleportOptions;
import br.com.zwo.zwoteleport.api.TeleportResult;
import br.com.zwo.zwoteleport.api.WarpInfo;
import br.com.zwo.zwoteleport.fake.FakePlayer;
import br.com.zwo.zwoteleport.fake.Fixture;
import br.com.zwo.zwoteleport.request.RequestType;
import br.com.zwo.zwoteleport.warp.WarpTeleporter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApiBehaviorTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private ApiImpl api(Fixture f) {
        WarpTeleporter teleporter = new WarpTeleporter(f.warps, f.teleports, f.messages, () -> f.settings);
        return new ApiImpl("3.0.0", f.warps, teleporter, f.teleports, f.tpa, () -> f.settings);
    }

    private Fixture fixture() throws Exception {
        Path file = folder.newFile("warps.yml").toPath();
        Files.writeString(file, TestResources.read("/legacy/warps-2.0.4.yml"));
        return new Fixture("safety.tpa: NONE").withWarps(file);
    }

    @Test
    public void exposesWarpsAndAvailability() throws Exception {
        Fixture f = fixture();
        ApiImpl api = api(f);
        assertEquals(3, api.warps().size());
        WarpInfo spawn = api.spawn().orElseThrow();
        assertEquals("spawn", spawn.name());
        assertTrue(spawn.available());
        assertFalse("mundo world_vip não está carregado", api.warp("vip").orElseThrow().available());
    }

    @Test
    public void pluginTeleportCompletesAndCanBeCancelledDuringWarmup() throws Exception {
        Fixture f = fixture();
        ApiImpl api = api(f);
        FakePlayer player = f.join("Ana");

        TeleportResult done = api.teleport(player.handle, f.at(7, 64, 7), TeleportOptions.plugin().withSafety(SafetyMode.NONE)).join();
        assertTrue(done.completed());
        assertEquals(7.0, done.finalLocation().orElseThrow().getX(), 1e-9);

        CompletableFuture<TeleportResult> pending = api.teleportToWarp(player.handle, "loja",
            TeleportOptions.likePlayerCommand().withSafety(SafetyMode.NONE));
        assertTrue(api.pendingTeleport(player.uuid).isPresent());
        assertTrue(api.cancelTeleport(player.handle));
        assertEquals(TeleportResult.Reason.CANCELLED_BY_API, pending.join().reason());
        assertFalse(api.cancelTeleport(player.handle));
    }

    @Test
    public void reportsRequestsAndCombat() throws Exception {
        Fixture f = fixture();
        ApiImpl api = api(f);
        FakePlayer ana = f.join("Ana");
        FakePlayer bruno = f.join("Bruno");
        f.tpa.send(ana.handle, bruno.handle, RequestType.TPAHERE);
        assertEquals(1, api.incomingRequests(bruno.uuid).size());
        assertTrue(api.incomingRequests(bruno.uuid).get(0).here());
        assertEquals(1, api.outgoingRequests(ana.uuid).size());

        assertFalse(api.isTeleportBlockedByCombat(ana.handle));
        f.teleports.handleCombat(ana.handle, bruno.handle);
        assertTrue(api.isCombatTagged(ana.uuid));
        assertTrue(api.isTeleportBlockedByCombat(ana.handle));
        assertTrue(api.combatSecondsLeft(ana.uuid) > 0);
    }

    @Test
    public void unknownWarpFailsCleanly() throws Exception {
        Fixture f = fixture();
        TeleportResult result = api(f).teleportToWarp(f.join("Ana").handle, "nao-existe", TeleportOptions.plugin()).join();
        assertEquals(TeleportResult.Reason.INVALID_DESTINATION, result.reason());
    }
}
