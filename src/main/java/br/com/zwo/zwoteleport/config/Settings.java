package br.com.zwo.zwoteleport.config;

import br.com.zwo.zwoteleport.api.SafetyMode;
import br.com.zwo.zwoteleport.api.TeleportCause;
import br.com.zwo.zwoteleport.spawn.JoinSpawnMode;
import br.com.zwo.zwoteleport.teleport.MovementTolerance;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuração validada e imutável. Um reload só troca a instância quando a nova é válida.
 */
public record Settings(
    Warmup warmup,
    Cooldown cooldown,
    Combat combat,
    Tpa tpa,
    Warps warps,
    Shortcuts shortcuts,
    JoinSpawn joinSpawn,
    Safety safety,
    Commands commands,
    boolean showSignature
) {

    public Settings withCombatEnabled(boolean enabled) {
        Combat updated = new Combat(enabled, combat.tagSeconds(), combat.blockWhileTagged(), combat.cancelWarmupOnCombat(),
            combat.tagAttacker(), combat.tagVictim());
        return new Settings(warmup, cooldown, updated, tpa, warps, shortcuts, joinSpawn, safety, commands, showSignature);
    }

    public record Warmup(
        boolean enabled,
        int seconds,
        boolean cancelOnMove,
        boolean cancelOnDamage,
        boolean allowBypassPermission,
        MovementTolerance movement,
        boolean actionbarCountdown
    ) {
    }

    public record Cooldown(int seconds, boolean allowBypassPermission) {
    }

    public record Combat(
        boolean enabled,
        int tagSeconds,
        boolean blockWhileTagged,
        boolean cancelWarmupOnCombat,
        boolean tagAttacker,
        boolean tagVictim
    ) {
    }

    public record Tpa(
        int expireSeconds,
        boolean allowSelfRequest,
        boolean replaceExistingRequest,
        List<String> blockedPermissions,
        Set<String> blockedWorlds
    ) {

        public Tpa {
            blockedPermissions = List.copyOf(blockedPermissions);
            blockedWorlds = Set.copyOf(blockedWorlds);
        }
    }

    public record Warps(boolean useWarmup, boolean requirePerWarpPermission, String perWarpPermissionFormat) {
    }

    /**
     * @param commands comando extra -> warp (ex.: {@code minas: minas})
     */
    public record Shortcuts(boolean enabled, boolean autoWarpCommands, Map<String, String> commands) {

        public Shortcuts {
            commands = Map.copyOf(commands);
        }
    }

    public record JoinSpawn(
        JoinSpawnMode mode,
        String warp,
        int delayTicks,
        boolean bypassPermissionEnabled,
        boolean skipIfQuitInCombat,
        boolean notifyPlayer
    ) {
    }

    public record Safety(
        SafetyMode tpa,
        SafetyMode warp,
        SafetyMode spawn,
        SafetyMode joinSpawn,
        SafetyMode admin,
        SafetyMode plugin,
        boolean allowWater,
        int searchRadius,
        int searchHeight,
        boolean respectWorldBorder
    ) {

        public SafetyMode modeFor(TeleportCause cause) {
            return switch (cause) {
                case TPA, TPAHERE -> tpa;
                case WARP -> warp;
                case SPAWN, SPAWN_OTHER -> spawn;
                case JOIN_SPAWN -> joinSpawn;
                case ADMIN_COMMAND -> admin;
                case PLUGIN -> plugin;
            };
        }
    }

    /**
     * @param takeover aliases que o ZwoTeleport assume mesmo se outro plugin os registrou
     */
    public record Commands(List<String> takeover, boolean hideNamespacedFromPlayers) {

        public Commands {
            takeover = List.copyOf(takeover);
        }
    }
}
