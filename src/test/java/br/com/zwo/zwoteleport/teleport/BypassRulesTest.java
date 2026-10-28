package br.com.zwo.zwoteleport.teleport;

import br.com.zwo.zwoteleport.util.PermissionView;
import br.com.zwo.zwoteleport.util.Permissions;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BypassRulesTest {

    private static PermissionView with(String... nodes) {
        Set<String> granted = Set.of(nodes);
        return granted::contains;
    }

    private static final PermissionView PLAYER = with(Permissions.TPA, Permissions.WARP_USE);
    private static final PermissionView ADMIN = with(Permissions.ADMIN);

    @Test
    public void ordinaryPlayerHasNoBypass() {
        assertFalse(BypassRules.cooldown(PLAYER, true));
        assertFalse(BypassRules.warmup(PLAYER, true));
        assertFalse(BypassRules.combat(PLAYER));
        assertFalse(BypassRules.joinSpawn(PLAYER, true));
        assertFalse(BypassRules.spawnOthers(PLAYER));
        assertFalse(BypassRules.tpaRestrictions(PLAYER));
    }

    @Test
    public void adminAlwaysSkipsCooldownButWarmupFollowsTheFlag() {
        assertTrue(BypassRules.cooldown(ADMIN, false));
        assertTrue(BypassRules.warmup(ADMIN, true));
        assertFalse("allow-bypass-permission=false obriga todos a esperar", BypassRules.warmup(ADMIN, false));
    }

    @Test
    public void specificBypassNodesRespectTheFlag() {
        PermissionView cooldownOnly = with(Permissions.BYPASS_COOLDOWN);
        assertTrue(BypassRules.cooldown(cooldownOnly, true));
        assertFalse(BypassRules.cooldown(cooldownOnly, false));

        PermissionView warmupOnly = with(Permissions.BYPASS_WARMUP);
        assertTrue(BypassRules.warmup(warmupOnly, true));
        assertFalse(BypassRules.warmup(warmupOnly, false));
    }

    @Test
    public void tpahereBypassIsIndependentOfTheGlobalFlag() {
        assertTrue(BypassRules.tpahereCooldown(with(Permissions.TPAHERE_BYPASS_COOLDOWN), false));
        assertTrue(BypassRules.tpahereCooldown(with(Permissions.TP_ADMIN), false));
        assertFalse(BypassRules.tpahereCooldown(with(Permissions.BYPASS_COOLDOWN), false));
    }

    @Test
    public void adminDoesNotSkipJoinSpawnWithoutTheExplicitNode() {
        assertFalse(BypassRules.joinSpawn(ADMIN, true));
        assertTrue(BypassRules.joinSpawn(with(Permissions.JOIN_SPAWN_BYPASS), true));
        assertFalse(BypassRules.joinSpawn(with(Permissions.JOIN_SPAWN_BYPASS), false));
    }

    @Test
    public void staffNodesSkipCombat() {
        assertTrue(BypassRules.combat(with(Permissions.TP_ADMIN)));
        assertTrue(BypassRules.combat(with(Permissions.WARP_ADMIN)));
        assertTrue(BypassRules.combat(with(Permissions.BYPASS_COMBAT)));
    }
}
