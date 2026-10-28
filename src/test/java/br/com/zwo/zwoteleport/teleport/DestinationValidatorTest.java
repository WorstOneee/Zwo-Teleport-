package br.com.zwo.zwoteleport.teleport;

import org.junit.Test;

import static br.com.zwo.zwoteleport.teleport.DestinationValidator.Check;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DestinationValidatorTest {

    @Test
    public void acceptsNormalCoordinates() {
        assertEquals(Check.OK, DestinationValidator.check(100.5, 64, -20.5, 90f, 0f, -64, 320));
    }

    @Test
    public void rejectsNonFiniteValues() {
        assertEquals(Check.NOT_FINITE, DestinationValidator.check(Double.NaN, 64, 0, 0f, 0f, -64, 320));
        assertEquals(Check.NOT_FINITE, DestinationValidator.check(0, Double.POSITIVE_INFINITY, 0, 0f, 0f, -64, 320));
        assertEquals(Check.NOT_FINITE, DestinationValidator.check(0, 64, 0, Float.NaN, 0f, -64, 320));
    }

    @Test
    public void rejectsOutsideWorldAndHeight() {
        assertEquals(Check.OUTSIDE_WORLD_LIMITS, DestinationValidator.check(30_000_000, 64, 0, 0f, 0f, -64, 320));
        assertEquals(Check.OUTSIDE_HEIGHT_LIMITS, DestinationValidator.check(0, -65, 0, 0f, 0f, -64, 320));
        assertEquals(Check.OUTSIDE_HEIGHT_LIMITS, DestinationValidator.check(0, 320 + 257, 0, 0f, 0f, -64, 320));
    }

    @Test
    public void parseRejectsNaNAndInfinity() {
        for (String bad : new String[] {"NaN", "Infinity", "-Infinity", "1e999", "", "abc", "0x10"}) {
            try {
                DestinationValidator.parseFinite(bad);
                fail("deveria rejeitar " + bad);
            } catch (NumberFormatException expected) {
                // esperado
            }
        }
        assertEquals(-12.5, DestinationValidator.parseFinite("-12,5"), 0.0001);
    }

    @Test
    public void relativeCoordinates() {
        assertEquals(10.0, DestinationValidator.parseCoordinate("~", 10), 0.0001);
        assertEquals(15.0, DestinationValidator.parseCoordinate("~5", 10), 0.0001);
        assertEquals(7.5, DestinationValidator.parseCoordinate("~-2.5", 10), 0.0001);
        assertEquals(3.0, DestinationValidator.parseCoordinate("3", 10), 0.0001);
    }

    @Test(expected = NumberFormatException.class)
    public void localCoordinatesAreRejected() {
        DestinationValidator.parseCoordinate("^1", 0);
    }
}
