package br.com.zwo.zwoteleport.warp;

import br.com.zwo.zwoteleport.storage.StorageException;
import br.com.zwo.zwoteleport.storage.WarpLoadResult;
import br.com.zwo.zwoteleport.storage.WarpRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Warps em memória (lidas na thread principal) com gravação em uma thread dedicada, na ordem
 * em que as alterações foram feitas. A memória é atualizada na hora; o resultado da gravação
 * chega depois, na thread principal.
 */
public final class WarpService {

    public enum SaveStatus {
        SAVED,
        FAILED,
        READ_ONLY
    }

    private final Plugin plugin;
    private final WarpRepository repository;
    private final ExecutorService writer;
    private Map<String, WarpPoint> warps = new LinkedHashMap<>();
    private WarpLoadResult.Source source = WarpLoadResult.Source.NEW;

    public WarpService(Plugin plugin, WarpRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ZwoTeleport-Warps");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Carga inicial, síncrona (antes de existir qualquer gravação pendente). */
    public WarpLoadResult loadNow() {
        WarpLoadResult result = repository.load();
        apply(result);
        return result;
    }

    /**
     * Recarrega do disco depois das gravações pendentes. Se o arquivo ficou ilegível, mantém as
     * warps atuais em memória e o armazenamento passa a recusar gravações.
     */
    public CompletableFuture<WarpLoadResult> reload() {
        CompletableFuture<WarpLoadResult> done = new CompletableFuture<>();
        writer.execute(() -> {
            WarpLoadResult result = repository.load();
            onMain(() -> {
                if (result.source() != WarpLoadResult.Source.UNREADABLE) {
                    apply(result);
                } else {
                    source = result.source();
                }
                done.complete(result);
            });
        });
        return done;
    }

    private void apply(WarpLoadResult result) {
        Map<String, WarpPoint> loaded = new LinkedHashMap<>(result.warps());
        warps = loaded;
        source = result.source();
        for (String warning : result.warnings()) {
            plugin.getLogger().warning(warning);
        }
    }

    public Optional<WarpPoint> get(String name) {
        return Optional.ofNullable(warps.get(WarpNames.normalize(name)));
    }

    public List<WarpPoint> all() {
        List<WarpPoint> list = new ArrayList<>(warps.values());
        list.sort(Comparator.comparing(WarpPoint::name));
        return Collections.unmodifiableList(list);
    }

    public int size() {
        return warps.size();
    }

    public boolean writable() {
        return repository.writable();
    }

    public WarpLoadResult.Source source() {
        return source;
    }

    public String describe() {
        return repository.describe();
    }

    /** Mundo carregado e local montado, ou vazio se o mundo da warp não estiver disponível. */
    public Optional<Location> location(WarpPoint warp) {
        World world = Bukkit.getWorld(warp.world());
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch()));
    }

    public CompletableFuture<SaveStatus> save(WarpPoint warp) {
        if (!repository.writable()) {
            return CompletableFuture.completedFuture(SaveStatus.READ_ONLY);
        }
        warps.put(warp.name(), warp);
        return write(() -> repository.upsert(warp), "gravar a warp " + warp.name());
    }

    public CompletableFuture<SaveStatus> delete(String name) {
        if (!repository.writable()) {
            return CompletableFuture.completedFuture(SaveStatus.READ_ONLY);
        }
        String normalized = WarpNames.normalize(name);
        warps.remove(normalized);
        return write(() -> repository.delete(normalized), "remover a warp " + normalized);
    }

    private interface StorageAction {
        void run() throws StorageException;
    }

    private CompletableFuture<SaveStatus> write(StorageAction action, String description) {
        CompletableFuture<SaveStatus> done = new CompletableFuture<>();
        writer.execute(() -> {
            SaveStatus status;
            try {
                action.run();
                status = SaveStatus.SAVED;
            } catch (StorageException error) {
                plugin.getLogger().log(Level.SEVERE, "Não foi possível " + description + ": " + error.getMessage());
                status = SaveStatus.FAILED;
            } catch (RuntimeException unexpected) {
                plugin.getLogger().log(Level.SEVERE, "Erro inesperado ao " + description, unexpected);
                status = SaveStatus.FAILED;
            }
            SaveStatus result = status;
            onMain(() -> done.complete(result));
        });
        return done;
    }

    /** Retorno à thread principal. Durante o desligamento os retornos são descartados. */
    private void onMain(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Aguarda as gravações pendentes (até 10 segundos) e encerra a thread de gravação. */
    public void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Gravações de warps ainda pendentes após 10s; encerrando mesmo assim.");
                writer.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }
}
