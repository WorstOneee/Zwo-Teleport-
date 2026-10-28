package br.com.zwo.zwoteleport.config;

import br.com.zwo.zwoteleport.TestResources;
import br.com.zwo.zwoteleport.message.MessageCatalog;
import br.com.zwo.zwoteleport.storage.YamlWarpRepository;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConfigFilesTest {

    private static final String CONFIG = TestResources.read("/config.yml");
    private static final String MESSAGES = TestResources.read("/messages.yml");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void freshInstallCopiesTheTemplates() throws Exception {
        Path data = folder.getRoot().toPath().resolve("ZwoTeleport");
        ConfigFiles.Report report = ConfigFiles.prepare(data, CONFIG, MESSAGES);
        assertTrue(report.createdConfig());
        assertFalse(report.upgradedFromLegacy());
        assertEquals(CONFIG, Files.readString(data.resolve("config.yml")));
        assertEquals(MESSAGES, Files.readString(data.resolve("messages.yml")));
    }

    @Test
    public void upgradeFromVersion2BacksUpEverythingOnceAndKeepsWarps() throws Exception {
        Path data = folder.getRoot().toPath();
        String legacyConfig = TestResources.read("/legacy/config-2.0.4.yml");
        String legacyMessages = TestResources.read("/legacy/messages-2.0.4.yml");
        String legacyWarps = TestResources.read("/legacy/warps-2.0.4.yml");
        Files.writeString(data.resolve("config.yml"), legacyConfig);
        Files.writeString(data.resolve("messages.yml"), legacyMessages);
        Files.writeString(data.resolve("warps.yml"), legacyWarps);

        ConfigFiles.Report first = ConfigFiles.prepare(data, CONFIG, MESSAGES);
        assertTrue(first.upgradedFromLegacy());
        assertNotNull(first.backupFolder());
        assertEquals(legacyConfig, Files.readString(first.backupFolder().resolve("config.yml")));
        assertEquals(legacyMessages, Files.readString(first.backupFolder().resolve("messages.yml")));
        assertEquals(legacyWarps, Files.readString(first.backupFolder().resolve("warps.yml")));
        assertEquals("warps.yml não é alterado pela migração", legacyWarps, Files.readString(data.resolve("warps.yml")));
        assertTrue(Files.readString(data.resolve("config.yml")).startsWith(legacyConfig));

        SettingsParser.Result parsed = SettingsParser.parse(Files.readString(data.resolve("config.yml")), CONFIG);
        assertTrue(parsed.errors().toString(), parsed.valid());
        MessageCatalog catalog = MessageCatalog.load(Files.readString(data.resolve("messages.yml")), MESSAGES);
        assertTrue(catalog.warnings().toString(), catalog.warnings().isEmpty());
        assertEquals(3, new YamlWarpRepository(data.resolve("warps.yml")).load().warps().size());

        String configAfterFirst = Files.readString(data.resolve("config.yml"));
        String messagesAfterFirst = Files.readString(data.resolve("messages.yml"));
        ConfigFiles.Report second = ConfigFiles.prepare(data, CONFIG, MESSAGES);
        assertFalse(second.upgradedFromLegacy());
        assertNull(second.backupFolder());
        assertTrue(second.configAdded().isEmpty());
        assertTrue(second.messagesAdded().isEmpty());
        assertEquals(configAfterFirst, Files.readString(data.resolve("config.yml")));
        assertEquals(messagesAfterFirst, Files.readString(data.resolve("messages.yml")));
        try (Stream<Path> backups = Files.list(data.resolve("backups"))) {
            assertEquals("backup feito uma única vez", 1, backups.count());
        }
    }

    @Test
    public void brokenConfigIsLeftUntouchedAndReported() throws Exception {
        Path data = folder.getRoot().toPath();
        String broken = "teleport:\n  warmup: [\n";
        Files.writeString(data.resolve("config.yml"), broken);
        ConfigFiles.Report report = ConfigFiles.prepare(data, CONFIG, MESSAGES);
        assertEquals(1, report.problems().size());
        assertEquals(broken, Files.readString(data.resolve("config.yml")));
    }
}
