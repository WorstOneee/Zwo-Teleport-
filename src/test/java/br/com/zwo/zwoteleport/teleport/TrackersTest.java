package br.com.zwo.zwoteleport.teleport;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TrackersTest {

    private final UUID player = UUID.randomUUID();

    @Test
    public void cooldownCountsDownAndRoundsUp() {
        CooldownTracker cooldowns = new CooldownTracker();
        cooldowns.apply(player, 0, 10);
        assertEquals(10, cooldowns.remainingSeconds(player, 0));
        assertEquals(10, cooldowns.remainingSeconds(player, 1));
        assertEquals(1, cooldowns.remainingSeconds(player, 9_001));
        assertEquals(0, cooldowns.remainingSeconds(player, 10_000));
    }

    @Test
    public void zeroCooldownIsNeverRecorded() {
        CooldownTracker cooldowns = new CooldownTracker();
        cooldowns.apply(player, 0, 0);
        assertEquals(0, cooldowns.remainingMillis(player, 0));
    }

    @Test
    public void combatTagExtendsButNeverShortens() {
        CombatTracker combat = new CombatTracker();
        combat.tag(player, 0, 15);
        combat.tag(player, 1_000, 5);
        assertTrue(combat.isTagged(player, 14_000));
        assertEquals(1, combat.remainingSeconds(player, 14_500));
        assertFalse(combat.isTagged(player, 15_000));
    }

    @Test
    public void quitWhileTaggedIsRememberedOnlyOnce() {
        CombatTracker combat = new CombatTracker();
        combat.tag(player, 0, 15);
        assertTrue(combat.recordQuit(player, 1_000));
        assertTrue(combat.consumeQuitWhileTagged(player, 120_000));
        assertFalse(combat.consumeQuitWhileTagged(player, 120_000));
    }

    @Test
    public void quitWithoutTagIsNotRemembered() {
        CombatTracker combat = new CombatTracker();
        assertFalse(combat.recordQuit(player, 0));
        assertFalse(combat.consumeQuitWhileTagged(player, 1));
    }

    @Test
    public void tagSurvivesDisconnectUntilItExpires() {
        CombatTracker combat = new CombatTracker();
        combat.tag(player, 0, 15);
        combat.recordQuit(player, 1_000);
        assertTrue("reconectar rápido não limpa a tag", combat.isTagged(player, 5_000));
    }

    @Test
    public void sessionTokenChangesOnReconnect() {
        SessionRegistry sessions = new SessionRegistry();
        long first = sessions.open(player);
        assertTrue(sessions.isCurrent(player, first));
        sessions.close(player);
        assertFalse(sessions.isCurrent(player, first));
        long second = sessions.open(player);
        assertFalse("tarefa da conexão anterior não pode valer para a nova", sessions.isCurrent(player, first));
        assertTrue(sessions.isCurrent(player, second));
    }

    @Test
    public void rotationAloneNeverExceedsTolerance() {
        MovementTolerance tolerance = new MovementTolerance(0.6, 1.5);
        assertFalse(tolerance.exceeded(10, 64, 10, 10, 64, 10));
    }

    @Test
    public void jumpingInPlaceIsTolerated() {
        MovementTolerance tolerance = new MovementTolerance(0.6, 1.5);
        assertFalse(tolerance.exceeded(10, 64, 10, 10.1, 65.25, 10.1));
    }

    @Test
    public void walkingAwayExceedsTolerance() {
        MovementTolerance tolerance = new MovementTolerance(0.6, 1.5);
        assertTrue(tolerance.exceeded(10, 64, 10, 10.5, 64, 10.5));
        assertTrue(tolerance.exceeded(10, 64, 10, 10, 62, 10));
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeToleranceIsRejected() {
        new MovementTolerance(-1, 1);
    }
}
