package br.com.zwo.zwoteleport.behavior;

import br.com.zwo.zwoteleport.TestResources;
import br.com.zwo.zwoteleport.fake.FakeServer;
import br.com.zwo.zwoteleport.storage.AtomicFiles;
import br.com.zwo.zwoteleport.storage.WarpLoadResult;
import br.com.zwo.zwoteleport.storage.YamlWarpRepository;
import br.com.zwo.zwoteleport.warp.WarpPoint;
import br.com.zwo.zwoteleport.warp.WarpService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Gravação assíncrona de warps com retorno na thread principal.
 */
public class WarpServiceBehaviorTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static <T> T await(FakeServer server, CompletableFuture<T> future) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!future.isDone() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
            server.tick(1);
        }
        assertTrue("a operação não terminou", future.isDone());
        return future.join();
    }

    @Test
    public void savedWarpIsVisibleImmediatelyAndOnDiskAfterTheCallback() throws Exception {
        FakeServer server = FakeServer.install();
        Path file = folder.getRoot().toPath().resolve("warps.yml");
        Files.writeString(file, TestResources.read("/legacy/warps-2.0.4.yml"));
        WarpService warps = new WarpService(server.plugin, new YamlWarpRepository(file));
        assertEquals(3, warps.loadNow().warps().size());

        CompletableFuture<WarpService.SaveStatus> saved = warps.save(new WarpPoint("minas", "&7Minas", "world", 1, 2, 3, 4f, 5f, ""));
        assertTrue("memória atualizada na hora", warps.get("minas").isPresent());
        assertEquals(WarpService.SaveStatus.SAVED, await(server, saved));
        assertTrue(Files.readString(file).contains("minas"));

        assertEquals(WarpService.SaveStatus.SAVED, await(server, warps.delete("loja")));
        assertFalse(warps.get("loja").isPresent());
        assertFalse(new YamlWarpRepository(file).load().warps().containsKey("loja"));
        warps.shutdown();
    }

    @Test
    public void unreadableFileBlocksChangesWithoutTouchingMemoryOrDisk() throws Exception {
        FakeServer server = FakeServer.install();
        Path file = folder.getRoot().toPath().resolve("warps.yml");
        Files.writeString(file, "warps: [quebrado\n");
        WarpService warps = new WarpService(server.plugin, new YamlWarpRepository(file));
        assertEquals(WarpLoadResult.Source.UNREADABLE, warps.loadNow().source());

        assertEquals(WarpService.SaveStatus.READ_ONLY, warps.save(new WarpPoint("x", "x", "world", 0, 0, 0, 0f, 0f, "")).join());
        assertFalse(warps.get("x").isPresent());
        assertEquals("warps: [quebrado\n", Files.readString(file));
        warps.shutdown();
    }

    @Test
    public void reloadOfABrokenFileKeepsTheWarpsInMemory() throws Exception {
        FakeServer server = FakeServer.install();
        Path file = folder.getRoot().toPath().resolve("warps.yml");
        Files.writeString(file, TestResources.read("/legacy/warps-2.0.4.yml"));
        WarpService warps = new WarpService(server.plugin, new YamlWarpRepository(file));
        warps.loadNow();
        Files.deleteIfExists(AtomicFiles.backupOf(file));
        Files.writeString(file, "warps: [quebrado\n");

        WarpLoadResult result = await(server, warps.reload());
        assertEquals(WarpLoadResult.Source.UNREADABLE, result.source());
        assertEquals("warps anteriores continuam disponíveis", 3, warps.size());
        assertFalse(warps.writable());
        warps.shutdown();
    }
}
