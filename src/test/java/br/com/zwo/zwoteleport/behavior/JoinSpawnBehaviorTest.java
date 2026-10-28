package br.com.zwo.zwoteleport.behavior;

import br.com.zwo.zwoteleport.fake.FakePlayer;
import br.com.zwo.zwoteleport.fake.Fixture;
import br.com.zwo.zwoteleport.util.Permissions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Spawn nativo na entrada no servidor simulado.
 */
public class JoinSpawnBehaviorTest {

    private static final String SPAWN = String.join("\n",
        "warps:",
        "  spawn:",
        "    world: world",
        "    x: 0.5",
        "    y: 100.0",
        "    z: -7.5",
        "    yaw: -90.0",
        "    pitch: 15.0",
        "    display-name: '&aSpawn'",
        "    permission: ''",
        "");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Fixture fixture(String warps, String... overrides) throws IOException {
        Path file = folder.newFile("warps.yml").toPath();
        Files.writeString(file, warps);
        return new Fixture(overrides).withWarps(file);
    }

    private FakePlayer join(Fixture f, String name, boolean playedBefore) {
        FakePlayer player = f.server.join(name, f.world);
        player.playedBefore = playedBefore;
        player.moveTo(250, 70, 250);
        long session = f.teleports.openSession(player.handle);
        f.joinSpawn.handleJoin(player.handle, session);
        return player;
    }

    @Test
    public void everyJoinSendsReturningPlayersToTheExactSpawnWithoutWarmupOrCooldown() throws IOException {
        Fixture f = fixture(SPAWN);
        FakePlayer player = join(f, "Ana", true);
        assertTrue(player.teleports.isEmpty());
        f.server.tick(1);

        assertEquals(1, player.teleports.size());
        assertEquals(0.5, player.location.getX(), 1e-9);
        assertEquals(100.0, player.location.getY(), 1e-9);
        assertEquals(-7.5, player.location.getZ(), 1e-9);
        assertEquals(-90f, player.location.getYaw(), 1e-6);
        assertEquals(15f, player.location.getPitch(), 1e-6);
        assertTrue(player.actionBars.isEmpty());
        assertTrue("spawn na entrada não manda mensagem por padrão", player.messages.isEmpty());
        assertEquals(0, f.teleports.cooldowns().remainingSeconds(player.uuid, System.currentTimeMillis()));
    }

    @Test
    public void firstJoinModeIgnoresReturningPlayers() throws IOException {
        Fixture f = fixture(SPAWN, "join-spawn.mode: FIRST_JOIN");
        FakePlayer veteran = join(f, "Ana", true);
        FakePlayer newcomer = join(f, "Bruno", false);
        f.server.tick(5);
        assertTrue(veteran.teleports.isEmpty());
        assertEquals(1, newcomer.teleports.size());
    }

    @Test
    public void disabledModeDoesNothing() throws IOException {
        Fixture f = fixture(SPAWN, "join-spawn.mode: DISABLED");
        FakePlayer player = join(f, "Ana", false);
        f.server.tick(5);
        assertTrue(player.teleports.isEmpty());
    }

    @Test
    public void bypassPermissionIsRespectedButNotGivenToAdmins() throws IOException {
        Fixture f = fixture(SPAWN);
        FakePlayer admin = f.server.join("Staff", f.world);
        admin.permissions.add(Permissions.ADMIN);
        admin.playedBefore = true;
        f.joinSpawn.handleJoin(admin.handle, f.teleports.openSession(admin.handle));

        FakePlayer exempt = f.server.join("Builder", f.world);
        exempt.permissions.add(Permissions.JOIN_SPAWN_BYPASS);
        exempt.playedBefore = true;
        f.joinSpawn.handleJoin(exempt.handle, f.teleports.openSession(exempt.handle));

        f.server.tick(2);
        assertEquals("zwo.admin não ignora o spawn na entrada", 1, admin.teleports.size());
        assertTrue(exempt.teleports.isEmpty());
    }

    @Test
    public void quickReconnectTeleportsOnlyOnceFromTheNewSession() throws IOException {
        Fixture f = fixture(SPAWN, "join-spawn.delay-ticks: 10");
        FakePlayer first = join(f, "Ana", true);
        f.server.tick(3);
        f.quit(first);
        FakePlayer second = f.server.rejoin(first);
        second.moveTo(250, 70, 250);
        f.joinSpawn.handleJoin(second.handle, f.teleports.openSession(second.handle));

        f.server.tick(30);
        assertTrue("a tarefa da conexão anterior não roda", first.teleports.isEmpty());
        assertEquals(1, second.teleports.size());
    }

    @Test
    public void missingSpawnLeavesThePlayerAndWarnsOnce() throws IOException {
        Fixture f = fixture("warps: {}\n");
        FakePlayer ana = join(f, "Ana", true);
        FakePlayer bruno = join(f, "Bruno", true);
        f.server.tick(2);
        assertTrue(ana.teleports.isEmpty());
        assertTrue(bruno.teleports.isEmpty());
        assertEquals(1, f.server.warnings.stream().filter(w -> w.contains("não existe")).count());
    }

    @Test
    public void unloadedSpawnWorldLeavesThePlayer() throws IOException {
        Fixture f = fixture(SPAWN.replace("world: world", "world: lobby_antigo"));
        FakePlayer player = join(f, "Ana", true);
        f.server.tick(2);
        assertTrue(player.teleports.isEmpty());
        assertTrue(f.server.warnings.stream().anyMatch(w -> w.contains("lobby_antigo")));
    }

    @Test
    public void quittingInCombatIsNotAnEscape() throws IOException {
        Fixture f = fixture(SPAWN);
        FakePlayer ana = join(f, "Ana", true);
        f.server.tick(2);
        assertEquals(1, ana.teleports.size());

        FakePlayer enemy = join(f, "Bruno", true);
        f.server.tick(2);
        ana.moveTo(500, 64, 500);
        f.teleports.handleCombat(ana.handle, enemy.handle);
        f.quit(ana);

        FakePlayer back = f.server.rejoin(ana);
        f.joinSpawn.handleJoin(back.handle, f.teleports.openSession(back.handle));
        f.server.tick(5);
        assertTrue(back.teleports.isEmpty());
        assertEquals(500.0, back.location.getX(), 1e-9);
        assertTrue(back.received("Você saiu em combate"));

        f.teleports.combat().clear(back.uuid);
        f.quit(back);
        FakePlayer later = f.server.rejoin(back);
        f.joinSpawn.handleJoin(later.handle, f.teleports.openSession(later.handle));
        f.server.tick(5);
        assertEquals("a exceção vale só para a entrada seguinte", 1, later.teleports.size());
    }

    @Test
    public void joinSpawnIsNotCancelledByCombat() throws IOException {
        Fixture f = fixture(SPAWN, "join-spawn.delay-ticks: 5");
        FakePlayer ana = join(f, "Ana", true);
        FakePlayer bruno = f.server.join("Bruno", f.world);
        f.teleports.openSession(bruno.handle);
        f.server.tick(1);
        f.teleports.handleCombat(ana.handle, bruno.handle);
        f.server.tick(10);
        assertEquals(1, ana.teleports.size());
    }
}
