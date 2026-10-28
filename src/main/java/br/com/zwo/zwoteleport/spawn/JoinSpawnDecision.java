package br.com.zwo.zwoteleport.spawn;

/**
 * Decide, sem tocar no servidor, se a entrada atual deve levar o jogador ao spawn.
 */
public enum JoinSpawnDecision {

    TELEPORT,
    SKIP_DISABLED,
    SKIP_RETURNING_PLAYER,
    SKIP_BYPASS,
    SKIP_QUIT_IN_COMBAT;

    public static JoinSpawnDecision decide(
        JoinSpawnMode mode,
        boolean firstJoin,
        boolean hasBypass,
        boolean quitInCombat,
        boolean skipIfQuitInCombat
    ) {
        if (mode == JoinSpawnMode.DISABLED) {
            return SKIP_DISABLED;
        }
        if (mode == JoinSpawnMode.FIRST_JOIN && !firstJoin) {
            return SKIP_RETURNING_PLAYER;
        }
        if (hasBypass) {
            return SKIP_BYPASS;
        }
        if (skipIfQuitInCombat && quitInCombat && !firstJoin) {
            return SKIP_QUIT_IN_COMBAT;
        }
        return TELEPORT;
    }
}
