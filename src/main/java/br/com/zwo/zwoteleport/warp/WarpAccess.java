package br.com.zwo.zwoteleport.warp;

import br.com.zwo.zwoteleport.util.PermissionView;
import br.com.zwo.zwoteleport.util.Permissions;

/**
 * Quem pode usar uma warp. Mesma ordem de verificação da versão 2.x.
 */
public final class WarpAccess {

    private WarpAccess() {
    }

    public static boolean canUse(PermissionView who, WarpPoint warp, boolean requirePerWarpPermission, String permissionFormat) {
        if (who.has(Permissions.WARP_ADMIN)) {
            return true;
        }
        if (!who.has(Permissions.WARP_USE)) {
            return false;
        }
        if (warp.hasCustomPermission() && !who.has(warp.permission())) {
            return false;
        }
        if (!requirePerWarpPermission) {
            return true;
        }
        String format = permissionFormat == null || permissionFormat.isBlank() ? "zwo.warp.{warp}" : permissionFormat;
        return who.has(format.replace("{warp}", warp.name()));
    }
}
