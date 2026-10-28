package br.com.zwo.zwoteleport.spawn;

import org.junit.Test;

import static br.com.zwo.zwoteleport.spawn.JoinSpawnDecision.SKIP_BYPASS;
import static br.com.zwo.zwoteleport.spawn.JoinSpawnDecision.SKIP_DISABLED;
import static br.com.zwo.zwoteleport.spawn.JoinSpawnDecision.SKIP_QUIT_IN_COMBAT;
import static br.com.zwo.zwoteleport.spawn.JoinSpawnDecision.SKIP_RETURNING_PLAYER;
import static br.com.zwo.zwoteleport.spawn.JoinSpawnDecision.TELEPORT;
import static br.com.zwo.zwoteleport.spawn.JoinSpawnDecision.decide;
import static org.junit.Assert.assertEquals;

public class JoinSpawnDecisionTest {

    @Test
    public void disabledNeverTeleports() {
        assertEquals(SKIP_DISABLED, decide(JoinSpawnMode.DISABLED, true, false, false, true));
    }

    @Test
    public void firstJoinOnlyTeleportsNewPlayers() {
        assertEquals(TELEPORT, decide(JoinSpawnMode.FIRST_JOIN, true, false, false, true));
        assertEquals(SKIP_RETURNING_PLAYER, decide(JoinSpawnMode.FIRST_JOIN, false, false, false, true));
    }

    @Test
    public void everyJoinTeleportsReturningPlayers() {
        assertEquals(TELEPORT, decide(JoinSpawnMode.EVERY_JOIN, false, false, false, true));
        assertEquals(TELEPORT, decide(JoinSpawnMode.EVERY_JOIN, true, false, false, true));
    }

    @Test
    public void bypassSkips() {
        assertEquals(SKIP_BYPASS, decide(JoinSpawnMode.EVERY_JOIN, false, true, false, true));
    }

    @Test
    public void quittingInCombatDoesNotBecomeAnEscape() {
        assertEquals(SKIP_QUIT_IN_COMBAT, decide(JoinSpawnMode.EVERY_JOIN, false, false, true, true));
        assertEquals(TELEPORT, decide(JoinSpawnMode.EVERY_JOIN, false, false, true, false));
    }

    @Test
    public void modeParsingAcceptsPortugueseAliases() {
        assertEquals(JoinSpawnMode.EVERY_JOIN, JoinSpawnMode.parse("toda_entrada").orElseThrow());
        assertEquals(JoinSpawnMode.FIRST_JOIN, JoinSpawnMode.parse("first-join").orElseThrow());
        assertEquals(JoinSpawnMode.DISABLED, JoinSpawnMode.parse("desativado").orElseThrow());
        assertEquals(false, JoinSpawnMode.parse("sempre").isPresent());
    }
}
