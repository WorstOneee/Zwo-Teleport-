package br.com.zwo.zwoteleport.config;

import br.com.zwo.zwoteleport.TestResources;
import br.com.zwo.zwoteleport.message.MessageCatalog;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class YamlMigratorTest {

    private static final String TEMPLATE = TestResources.read("/config.yml");
    private static final String MESSAGES = TestResources.read("/messages.yml");
    private static final String LEGACY_CONFIG = TestResources.read("/legacy/config-2.0.4.yml");
    private static final String LEGACY_MESSAGES = TestResources.read("/legacy/messages-2.0.4.yml");

    private static YamlMigrator.Result migrateConfig(String text) {
        return YamlMigrator.appendMissing(text, TEMPLATE, SettingsParser.OPAQUE, SettingsParser.LEGACY_ALIASES, "Opções novas");
    }

    @Test
    public void legacyConfigKeepsEveryOriginalLineAndBecomesValid() {
        YamlMigrator.Result result = migrateConfig(LEGACY_CONFIG);
        assertTrue(result.changed());
        assertTrue("o conteúdo original é mantido intacto no início", result.text().startsWith(LEGACY_CONFIG));
        assertTrue(result.added().contains("config-version"));
        assertTrue(result.added().contains("join-spawn.mode"));
        assertTrue(result.added().contains("teleport.warmup.max-horizontal-distance"));

        SettingsParser.Result parsed = SettingsParser.parse(result.text(), TEMPLATE);
        assertTrue(parsed.errors().toString(), parsed.valid());
        assertTrue(parsed.warnings().toString(), parsed.warnings().isEmpty());
    }

    @Test
    public void migrationIsIdempotent() {
        String once = migrateConfig(LEGACY_CONFIG).text();
        YamlMigrator.Result twice = migrateConfig(once);
        assertFalse(twice.changed());
        assertEquals(once, twice.text());
    }

    @Test
    public void newSectionsCarryTheirComments() {
        String migrated = migrateConfig(LEGACY_CONFIG).text();
        assertTrue(migrated.contains("# EVERY_JOIN = em toda entrada neste servidor."));
        assertTrue(migrated.contains("join-spawn:\n  # DISABLED"));
    }

    @Test
    public void userValuesAreNeverOverwritten() {
        String custom = LEGACY_CONFIG.replace("expire-seconds: 60", "expire-seconds: 25");
        String migrated = migrateConfig(custom).text();
        SettingsParser.Result parsed = SettingsParser.parse(migrated, TEMPLATE);
        assertEquals(25, parsed.settings().tpa().expireSeconds());
    }

    @Test
    public void legacyAliasCountsAsPresent() {
        String text = "teleport:\n  warmup-seconds: 4\n";
        YamlMigrator.Result result = migrateConfig(text);
        assertFalse(result.added().contains("teleport.warmup.seconds"));
        assertEquals(4, SettingsParser.parse(result.text(), TEMPLATE).settings().warmup().seconds());
    }

    @Test
    public void opaqueShortcutMapIsNotMergedEntryByEntry() {
        String text = LEGACY_CONFIG.replace("    loja: \"loja\"\n", "");
        YamlMigrator.Result result = migrateConfig(text);
        assertFalse(result.added().contains("shortcuts.commands"));
        Map<String, String> shortcuts = SettingsParser.parse(result.text(), TEMPLATE).settings().shortcuts().commands();
        assertEquals(Map.of("spawn", "spawn"), shortcuts);
    }

    @Test
    public void legacyMessagesGainTheNewKeysAndKeepCustomTexts() {
        String custom = LEGACY_MESSAGES.replace("&aTeleportado para o spawn", "&bBem-vindo ao spawn");
        YamlMigrator.Result result = YamlMigrator.appendMissing(custom, MESSAGES, Set.of(), Map.of(), "Mensagens novas");
        assertTrue(result.text().startsWith(custom));
        assertTrue(result.added().contains("tpa.blocked"));
        assertTrue(result.added().contains("join-spawn.teleported"));

        MessageCatalog catalog = MessageCatalog.load(result.text(), MESSAGES);
        assertEquals("&bBem-vindo ao spawn", catalog.raw("spawn.teleported"));
        assertEquals("&cNão foi dessa vez, tente amanhã. &7:/", catalog.raw("tpa.blocked"));
        assertTrue(catalog.warnings().toString(), catalog.warnings().isEmpty());
        assertFalse(YamlMigrator.appendMissing(result.text(), MESSAGES, Set.of(), Map.of(), "x").changed());
    }

    @Test
    public void setScalarChangesOnlyTheTargetAndKeepsComments() {
        String text = "teleport:\n  combat:\n    # liga o combate\n    enabled: true # comentario\n  warmup:\n    enabled: true\n";
        String changed = YamlMigrator.setScalar(text, "teleport.combat.enabled", "false");
        assertEquals("teleport:\n  combat:\n    # liga o combate\n    enabled: false # comentario\n  warmup:\n    enabled: true\n", changed);
    }

    @Test
    public void setScalarAlsoUpdatesDottedKeys() {
        String text = "teleport:\n  warmup:\n    enabled: true\nteleport.combat.enabled: true\n";
        String changed = YamlMigrator.setScalar(text, "teleport.combat.enabled", "false");
        assertTrue(changed.endsWith("teleport.combat.enabled: false\n"));
    }

    @Test
    public void setScalarOnMissingPathReturnsTheSameText() {
        String text = "a:\n  b: 1\n";
        assertEquals(text, YamlMigrator.setScalar(text, "a.c", "2"));
    }

    @Test
    public void topLevelBlocksAttachOnlyAdjacentComments() {
        Map<String, String> blocks = YamlMigrator.topLevelBlocks("# cabeçalho\n\n# sobre a\na: 1\n\n# sobre b\nb:\n  c: 2\n");
        assertEquals("# sobre a\na: 1", blocks.get("a"));
        assertEquals("# sobre b\nb:\n  c: 2", blocks.get("b"));
    }
}
