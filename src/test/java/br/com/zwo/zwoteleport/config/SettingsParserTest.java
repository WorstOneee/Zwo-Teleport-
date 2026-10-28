package br.com.zwo.zwoteleport.config;

import br.com.zwo.zwoteleport.TestResources;
import br.com.zwo.zwoteleport.api.SafetyMode;
import br.com.zwo.zwoteleport.api.TeleportCause;
import br.com.zwo.zwoteleport.spawn.JoinSpawnMode;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SettingsParserTest {

    private static final String DEFAULTS = TestResources.read("/config.yml");

    @Test
    public void bundledConfigIsValid() {
        SettingsParser.Result result = SettingsParser.parse(DEFAULTS, DEFAULTS);
        assertTrue(result.errors().toString(), result.valid());
        assertTrue(result.warnings().toString(), result.warnings().isEmpty());
        Settings settings = result.settings();
        assertEquals(10, settings.warmup().seconds());
        assertEquals(JoinSpawnMode.EVERY_JOIN, settings.joinSpawn().mode());
        assertEquals("spawn", settings.joinSpawn().warp());
        assertEquals(SafetyMode.ADJUST, settings.safety().modeFor(TeleportCause.TPAHERE));
        assertEquals(SafetyMode.NONE, settings.safety().modeFor(TeleportCause.WARP));
        assertEquals(List.of("serpens.yoff.active"), settings.tpa().blockedPermissions());
        assertTrue(settings.commands().takeover().contains("tpaceitar"));
        assertEquals("spawn", settings.shortcuts().commands().get("spawn"));
    }

    @Test
    public void version2ConfigLoadsWithItsValuesAndDefaultsForNewOptions() {
        String legacy = TestResources.read("/legacy/config-2.0.4.yml")
            .replace("seconds: 10\n    cancel-on-move", "seconds: 3\n    cancel-on-move");
        SettingsParser.Result result = SettingsParser.parse(legacy, DEFAULTS);
        assertTrue(result.errors().toString(), result.valid());
        assertEquals(3, result.settings().warmup().seconds());
        assertEquals(0.6, result.settings().warmup().movement().maxHorizontal(), 1e-9);
    }

    @Test
    public void legacyAliasesAreHonouredWithAWarning() {
        String text = "teleport:\n  warmup-seconds: 4\n  cooldown-seconds: 7\n";
        SettingsParser.Result result = SettingsParser.parse(text, DEFAULTS);
        assertTrue(result.valid());
        assertEquals(4, result.settings().warmup().seconds());
        assertEquals(7, result.settings().cooldown().seconds());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("teleport.warmup-seconds")));
    }

    @Test
    public void invalidValuesAreAllReportedAndNothingIsApplied() {
        String text = String.join("\n",
            "teleport:",
            "  warmup:",
            "    seconds: dez",
            "    enabled: talvez",
            "join-spawn:",
            "  mode: SEMPRE",
            "safety:",
            "  tpa: SEGURO",
            "warps:",
            "  per-warp-permission-format: zwo.warp",
            "");
        SettingsParser.Result result = SettingsParser.parse(text, DEFAULTS);
        assertFalse(result.valid());
        assertNull(result.settings());
        assertEquals(result.errors().toString(), 5, result.errors().size());
        assertTrue(result.errors().stream().anyMatch(e -> e.startsWith("teleport.warmup.seconds")));
        assertTrue(result.errors().stream().anyMatch(e -> e.startsWith("join-spawn.mode")));
    }

    @Test
    public void outOfRangeNumbersAreRejected() {
        SettingsParser.Result result = SettingsParser.parse("teleport:\n  warmup:\n    seconds: -5\n", DEFAULTS);
        assertFalse(result.valid());
        assertTrue(result.errors().get(0).contains("entre 0 e 3600"));
    }

    @Test
    public void brokenYamlIsReportedInOneLine() {
        SettingsParser.Result result = SettingsParser.parse("teleport:\n  warmup: [\n", DEFAULTS);
        assertFalse(result.valid());
        assertEquals(1, result.errors().size());
        assertFalse(result.errors().get(0).contains("\n"));
    }

    @Test
    public void unknownKeysAreWarnedNotFatal() {
        SettingsParser.Result result = SettingsParser.parse("teleport:\n  warmup:\n    secondz: 5\n", DEFAULTS);
        assertTrue(result.valid());
        assertTrue(result.warnings().contains("Opção desconhecida ignorada: teleport.warmup.secondz"));
    }

    @Test
    public void dottedKeysAppendedByTheMigrationAreRead() {
        String text = TestResources.read("/legacy/config-2.0.4.yml") + "\nteleport.warmup.max-horizontal-distance: 2.5\n";
        SettingsParser.Result result = SettingsParser.parse(text, DEFAULTS);
        assertTrue(result.valid());
        assertEquals(2.5, result.settings().warmup().movement().maxHorizontal(), 1e-9);
    }

    @Test
    public void storageOtherThanYamlIsRejected() {
        SettingsParser.Result result = SettingsParser.parse("storage:\n  type: MYSQL\n", DEFAULTS);
        assertFalse(result.valid());
    }
}
