package br.com.zwo.zwoteleport.teleport;

import br.com.zwo.zwoteleport.util.PermissionView;
import br.com.zwo.zwoteleport.util.Permissions;

/**
 * Regras explícitas de bypass. Ser OP não concede nada por si só: OP recebe as permissões cujo
 * padrão no plugin.yml é "op", e uma negação feita pelo plugin de permissões é respeitada.
 *
 * <p>As combinações reproduzem o comportamento da versão 2.x, agora em um único lugar e cobertas por testes.</p>
 */
public final class BypassRules {

    private BypassRules() {
    }

    /** Cooldown de warps, spawn e pedidos /tpa. Administradores sempre ignoram. */
    public static boolean cooldown(PermissionView who, boolean allowBypassPermission) {
        return who.has(Permissions.ADMIN)
            || who.has(Permissions.WARP_ADMIN)
            || (allowBypassPermission && who.has(Permissions.BYPASS_COOLDOWN));
    }

    /** Cooldown ao enviar /tpahere. */
    public static boolean tpahereCooldown(PermissionView who, boolean allowBypassPermission) {
        return who.has(Permissions.ADMIN)
            || who.has(Permissions.TP_ADMIN)
            || who.has(Permissions.WARP_ADMIN)
            || who.has(Permissions.TPAHERE_BYPASS_COOLDOWN)
            || (allowBypassPermission && who.has(Permissions.BYPASS_COOLDOWN));
    }

    /** Warmup. Desligar allow-bypass-permission obriga todos, inclusive administradores, a esperar. */
    public static boolean warmup(PermissionView who, boolean allowBypassPermission) {
        return allowBypassPermission
            && (who.has(Permissions.ADMIN) || who.has(Permissions.WARP_ADMIN) || who.has(Permissions.BYPASS_WARMUP));
    }

    /** Tag e bloqueio de combate do próprio ZwoTeleport. */
    public static boolean combat(PermissionView who) {
        return who.has(Permissions.ADMIN)
            || who.has(Permissions.TP_ADMIN)
            || who.has(Permissions.WARP_ADMIN)
            || who.has(Permissions.BYPASS_COMBAT);
    }

    /** Ignorar o spawn na entrada. Por padrão ninguém tem essa permissão, nem OP. */
    public static boolean joinSpawn(PermissionView who, boolean bypassEnabled) {
        return bypassEnabled && who.has(Permissions.JOIN_SPAWN_BYPASS);
    }

    /** Usar /spawn &lt;jogador&gt;. */
    public static boolean spawnOthers(PermissionView who) {
        return who.has(Permissions.SPAWN_OTHERS)
            || who.has(Permissions.WARP_ADMIN)
            || who.has(Permissions.TP_ADMIN)
            || who.has(Permissions.ADMIN);
    }

    /** Ignorar mundos e permissões bloqueadas para /tpa. */
    public static boolean tpaRestrictions(PermissionView who) {
        return who.has(Permissions.ADMIN) || who.has(Permissions.TPA_BYPASS_RESTRICTIONS);
    }

    /** Ver comandos com namespace (zwoteleport:...) nas sugestões. */
    public static boolean seeNamespacedCommands(PermissionView who) {
        return who.has(Permissions.ADMIN);
    }
}
