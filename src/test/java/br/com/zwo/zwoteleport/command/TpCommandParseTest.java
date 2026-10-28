package br.com.zwo.zwoteleport.command;

import br.com.zwo.zwoteleport.fake.FakeServer;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TpCommandParseTest {

    private final World world = FakeServer.install().world("world");
    private final Location base = new Location(world, 10, 64, -5, 45f, 10f);

    @Test
    public void absoluteAndRelativeCoordinates() {
        Location parsed = TpCommand.parse(base, new String[] {"~5", "70", "~-2.5"}, 0);
        assertEquals(15.0, parsed.getX(), 1e-9);
        assertEquals(70.0, parsed.getY(), 1e-9);
        assertEquals(-7.5, parsed.getZ(), 1e-9);
        assertEquals("sem yaw/pitch mantém a direção atual", 45f, parsed.getYaw(), 1e-6);
    }

    @Test
    public void yawAndPitchWithOffset() {
        Location parsed = TpCommand.parse(base, new String[] {"Ana", "0", "64", "0", "180", "-30"}, 1);
        assertEquals(180f, parsed.getYaw(), 1e-6);
        assertEquals(-30f, parsed.getPitch(), 1e-6);
    }

    @Test
    public void rejectsNonFiniteAndInvalidValues() {
        assertNull(TpCommand.parse(base, new String[] {"NaN", "64", "0"}, 0));
        assertNull(TpCommand.parse(base, new String[] {"Infinity", "64", "0"}, 0));
        assertNull(TpCommand.parse(base, new String[] {"^1", "64", "0"}, 0));
        assertNull(TpCommand.parse(base, new String[] {"0", "64", "0", "0", "120"}, 0));
    }
}
