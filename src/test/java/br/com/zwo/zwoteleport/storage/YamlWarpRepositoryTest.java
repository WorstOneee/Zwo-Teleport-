package br.com.zwo.zwoteleport.storage;

import br.com.zwo.zwoteleport.TestResources;
import br.com.zwo.zwoteleport.config.YamlTree;
import br.com.zwo.zwoteleport.warp.WarpPoint;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class YamlWarpRepositoryTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path legacyFile() throws IOException {
        Path file = folder.getRoot().toPath().resolve("warps.yml");
        Files.writeString(file, TestResources.read("/legacy/warps-2.0.4.yml"), StandardCharsets.UTF_8);
        return file;
    }

    @Test
    public void loadsTheVersion2FormatWithPositionAndRotation() throws IOException {
        YamlWarpRepository repository = new YamlWarpRepository(legacyFile());
        WarpLoadResult result = repository.load();

        assertEquals(WarpLoadResult.Source.PRIMARY, result.source());
        WarpPoint spawn = result.warps().get("spawn");
        assertEquals("world", spawn.world());
        assertEquals(0.5, spawn.x(), 1e-9);
        assertEquals(64.0, spawn.y(), 1e-9);
        assertEquals(-90.0f, spawn.yaw(), 1e-6);
        assertEquals(12.5f, spawn.pitch(), 1e-6);
        assertEquals("&aSpawn", spawn.displayName());
        assertEquals("serpens.vip", result.warps().get("vip").permission());
        assertEquals("warp com mundo não carregado continua listada", "world_vip", result.warps().get("vip").world());
    }

    @Test
    public void invalidEntryIsReportedAndPreservedOnSave() throws Exception {
        Path file = legacyFile();
        YamlWarpRepository repository = new YamlWarpRepository(file);
        WarpLoadResult result = repository.load();
        assertFalse(result.warps().containsKey("quebrada"));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("quebrada")));

        repository.upsert(new WarpPoint("minas", "Minas", "world", 1, 2, 3, 4f, 5f, ""));

        Map<String, Object> saved = YamlTree.parse(Files.readString(file));
        Map<?, ?> warps = (Map<?, ?>) saved.get("warps");
        assertTrue("entrada inválida não pode ser apagada", warps.containsKey("quebrada"));
        assertTrue(warps.containsKey("minas"));
        assertEquals("Staff", ((Map<?, ?>) warps.get("vip")).get("criado-por"));
    }

    @Test
    public void saveKeepsAPreviousCopyAndReloadsTheSameData() throws Exception {
        Path file = legacyFile();
        String before = Files.readString(file);
        YamlWarpRepository repository = new YamlWarpRepository(file);
        repository.load();
        repository.upsert(new WarpPoint("spawn", "&aSpawn", "world", 10, 70, 10, 45f, -10f, ""));

        assertEquals(before, Files.readString(AtomicFiles.backupOf(file)));
        WarpPoint reloaded = new YamlWarpRepository(file).load().warps().get("spawn");
        assertEquals(10.0, reloaded.x(), 1e-9);
        assertEquals(45f, reloaded.yaw(), 1e-6);
        assertEquals(-10f, reloaded.pitch(), 1e-6);
        try (Stream<Path> files = Files.list(folder.getRoot().toPath())) {
            assertTrue("não deve sobrar arquivo temporário", files.noneMatch(p -> p.toString().endsWith(".tmp")));
        }
    }

    @Test
    public void deleteRemovesOnlyThatWarp() throws Exception {
        Path file = legacyFile();
        YamlWarpRepository repository = new YamlWarpRepository(file);
        repository.load();
        assertTrue(repository.delete("LOJA"));
        assertFalse(repository.delete("loja"));
        Map<String, WarpPoint> reloaded = new YamlWarpRepository(file).load().warps();
        assertFalse(reloaded.containsKey("loja"));
        assertTrue(reloaded.containsKey("spawn"));
    }

    @Test
    public void corruptedFileFallsBackToBackupWithoutDestroyingIt() throws Exception {
        Path file = legacyFile();
        YamlWarpRepository first = new YamlWarpRepository(file);
        first.load();
        first.upsert(new WarpPoint("minas", "Minas", "world", 1, 2, 3, 0f, 0f, ""));

        String broken = "warps:\n  spawn: [\n";
        Files.writeString(file, broken);

        YamlWarpRepository repository = new YamlWarpRepository(file);
        WarpLoadResult result = repository.load();
        assertEquals(WarpLoadResult.Source.BACKUP, result.source());
        assertTrue(result.warps().containsKey("spawn"));
        assertEquals("o arquivo original continua como estava até a próxima gravação", broken, Files.readString(file));
        try (Stream<Path> files = Files.list(folder.getRoot().toPath())) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("warps.yml.corrompido-")));
        }
    }

    @Test
    public void unreadableWithoutBackupBecomesReadOnly() throws Exception {
        Path file = folder.getRoot().toPath().resolve("warps.yml");
        Files.writeString(file, "warps: [isto, nao, e, um, mapa\n");
        YamlWarpRepository repository = new YamlWarpRepository(file);
        WarpLoadResult result = repository.load();
        assertEquals(WarpLoadResult.Source.UNREADABLE, result.source());
        assertFalse(repository.writable());
        try {
            repository.upsert(new WarpPoint("x", "x", "world", 0, 0, 0, 0f, 0f, ""));
            fail("gravação deveria ser bloqueada");
        } catch (StorageException expected) {
            assertEquals("warps: [isto, nao, e, um, mapa\n", Files.readString(file));
        }
    }

    @Test
    public void missingFileStartsEmptyAndWritable() throws Exception {
        Path file = folder.getRoot().toPath().resolve("novo").resolve("warps.yml");
        YamlWarpRepository repository = new YamlWarpRepository(file);
        assertEquals(WarpLoadResult.Source.NEW, repository.load().source());
        repository.upsert(new WarpPoint("spawn", "Spawn", "world", 0, 64, 0, 0f, 0f, ""));
        assertTrue(Files.exists(file));
    }

    @Test
    public void sectionWithWrongTypeIsTreatedAsCorrupted() throws Exception {
        Path file = folder.getRoot().toPath().resolve("warps.yml");
        Files.writeString(file, "warps: 5\n");
        assertEquals(WarpLoadResult.Source.UNREADABLE, new YamlWarpRepository(file).load().source());
    }
}
