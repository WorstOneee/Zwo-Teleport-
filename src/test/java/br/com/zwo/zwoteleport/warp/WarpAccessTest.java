package br.com.zwo.zwoteleport.warp;

import br.com.zwo.zwoteleport.util.PermissionView;
import br.com.zwo.zwoteleport.util.Permissions;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WarpAccessTest {

    private static PermissionView with(String... nodes) {
        Set<String> granted = Set.of(nodes);
        return granted::contains;
    }

    private static WarpPoint warp(String name, String permission) {
        return new WarpPoint(name, name, "world", 0, 64, 0, 0f, 0f, permission);
    }

    @Test
    public void publicWarpNeedsOnlyWarpUse() {
        assertTrue(WarpAccess.canUse(with(Permissions.WARP_USE), warp("loja", ""), false, "zwo.warp.{warp}"));
        assertFalse(WarpAccess.canUse(with(), warp("loja", ""), false, "zwo.warp.{warp}"));
    }

    @Test
    public void customPermissionIsRequired() {
        WarpPoint vip = warp("vip", "serpens.vip");
        assertFalse(WarpAccess.canUse(with(Permissions.WARP_USE), vip, false, "zwo.warp.{warp}"));
        assertTrue(WarpAccess.canUse(with(Permissions.WARP_USE, "serpens.vip"), vip, false, "zwo.warp.{warp}"));
    }

    @Test
    public void perWarpPermissionUsesTheFormat() {
        WarpPoint minas = warp("minas", "");
        assertFalse(WarpAccess.canUse(with(Permissions.WARP_USE), minas, true, "zwo.warp.{warp}"));
        assertTrue(WarpAccess.canUse(with(Permissions.WARP_USE, "zwo.warp.minas"), minas, true, "zwo.warp.{warp}"));
    }

    @Test
    public void warpAdminAlwaysHasAccess() {
        assertTrue(WarpAccess.canUse(with(Permissions.WARP_ADMIN), warp("vip", "serpens.vip"), true, "zwo.warp.{warp}"));
    }

    @Test
    public void namesFollowThePattern() {
        assertTrue(WarpNames.isValid("Minas_2"));
        assertEquals("minas_2", WarpNames.normalize(" Minas_2 "));
        assertFalse(WarpNames.isValid("minas 2"));
        assertFalse(WarpNames.isValid("ação"));
        assertFalse(WarpNames.isValid(""));
        assertFalse(WarpNames.isValid("a".repeat(33)));
    }
}
