package br.com.zwo.zwoteleport.fake;

import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Servidor simulado para testes de comportamento. Implementa, por proxies dinâmicos, apenas o que
 * o plugin usa das interfaces reais da paper-api: jogadores, mundos, agendador por ticks e eventos.
 * Não substitui um teste em Paper real (sem chunks, física ou rede).
 */
public final class FakeServer {

    private static FakeServer current;
    private static boolean installed;

    public final Map<UUID, FakePlayer> players = new LinkedHashMap<>();
    public final Map<String, World> worlds = new LinkedHashMap<>();
    public final List<Event> events = new ArrayList<>();
    public final List<Consumer<Event>> eventHooks = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public final Logger logger;
    public final Plugin plugin;
    public boolean pluginEnabled = true;
    private final List<Scheduled> tasks = new ArrayList<>();
    private long tick;
    private int nextTaskId = 1;

    private FakeServer() {
        logger = Logger.getLogger("ZwoTeleport-Test-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        plugin = proxy(Plugin.class, (self, method, args) -> switch (method.getName()) {
            case "isEnabled" -> pluginEnabled;
            case "getLogger" -> logger;
            case "getName" -> "ZwoTeleport";
            default -> objectMethod(self, method, args);
        });
    }

    /** Cria um servidor limpo e o torna o atual para {@link Bukkit}. */
    public static FakeServer install() {
        current = new FakeServer();
        if (!installed) {
            Server server = proxy(Server.class, (self, method, args) -> current.handleServer(self, method, args));
            try {
                Field field = Bukkit.class.getDeclaredField("server");
                field.setAccessible(true);
                field.set(null, server);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
            installed = true;
        }
        return current;
    }

    public World world(String name) {
        return worlds.computeIfAbsent(name, FakeServer::createWorld);
    }

    public void unloadWorld(String name) {
        worlds.remove(name);
    }

    public FakePlayer join(String name, World world) {
        FakePlayer player = new FakePlayer(this, UUID.nameUUIDFromBytes(name.getBytes()), name, new Location(world, 0.5, 64, 0.5));
        players.put(player.uuid, player);
        return player;
    }

    /** Reconecta: mesmo UUID, novo objeto Player (como acontece no servidor). */
    public FakePlayer rejoin(FakePlayer old) {
        FakePlayer player = new FakePlayer(this, old.uuid, old.name, old.location.clone());
        player.playedBefore = true;
        player.permissions.addAll(old.permissions);
        players.put(player.uuid, player);
        return player;
    }

    public void quit(FakePlayer player) {
        player.online = false;
        players.remove(player.uuid);
    }

    public <T extends Event> List<T> events(Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Event event : events) {
            if (type.isInstance(event)) {
                found.add(type.cast(event));
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ agendador

    /** Avança o relógio do servidor executando as tarefas vencidas, um tick por vez. */
    public void tick(int ticks) {
        for (int i = 0; i < ticks; i++) {
            tick++;
            runDue();
        }
    }

    /** Executa o que já está vencido sem avançar o relógio (tarefas "no próximo tick" e assíncronas). */
    public void runPending() {
        tick(1);
    }

    private void runDue() {
        boolean ranSomething = true;
        while (ranSomething) {
            ranSomething = false;
            for (Scheduled task : List.copyOf(tasks)) {
                if (task.cancelled || task.nextRun > tick) {
                    continue;
                }
                ranSomething = true;
                if (task.period > 0) {
                    task.nextRun = tick + task.period;
                } else {
                    task.cancelled = true;
                    tasks.remove(task);
                }
                task.runnable.run();
            }
        }
        tasks.removeIf(task -> task.cancelled);
    }

    private BukkitTask schedule(Runnable runnable, long delay, long period) {
        if (!pluginEnabled) {
            throw new IllegalStateException("plugin desabilitado");
        }
        Scheduled task = new Scheduled(nextTaskId++, runnable, tick + Math.max(1, delay), period);
        tasks.add(task);
        return proxy(BukkitTask.class, (self, method, args) -> switch (method.getName()) {
            case "cancel" -> {
                task.cancelled = true;
                yield null;
            }
            case "isCancelled" -> task.cancelled;
            case "getTaskId" -> task.id;
            case "isSync" -> true;
            case "getOwner" -> plugin;
            default -> objectMethod(self, method, args);
        });
    }

    public int pendingTasks() {
        int count = 0;
        for (Scheduled task : tasks) {
            if (!task.cancelled) {
                count++;
            }
        }
        return count;
    }

    private static final class Scheduled {
        final int id;
        final Runnable runnable;
        final long period;
        long nextRun;
        boolean cancelled;

        Scheduled(int id, Runnable runnable, long nextRun, long period) {
            this.id = id;
            this.runnable = runnable;
            this.nextRun = nextRun;
            this.period = period;
        }
    }

    // ------------------------------------------------------------------ servidor

    private Object handleServer(Object self, Method method, Object[] args) {
        return switch (method.getName()) {
            case "getLogger" -> logger;
            case "isPrimaryThread" -> true;
            case "getScheduler" -> scheduler();
            case "getPluginManager" -> pluginManager();
            case "getPlayer" -> args[0] instanceof UUID id ? onlinePlayer(id) : byName((String) args[0]);
            case "getPlayerExact" -> byName((String) args[0]);
            case "getOnlinePlayers" -> onlinePlayers();
            case "getWorld" -> args[0] instanceof UUID id ? worldById(id) : worlds.get((String) args[0]);
            case "getName" -> "FakeServer";
            default -> objectMethod(self, method, args);
        };
    }

    private Player onlinePlayer(UUID id) {
        FakePlayer player = players.get(id);
        return player == null || !player.online ? null : player.handle;
    }

    private Player byName(String name) {
        for (FakePlayer player : players.values()) {
            if (player.online && player.name.equalsIgnoreCase(name)) {
                return player.handle;
            }
        }
        return null;
    }

    private Collection<Player> onlinePlayers() {
        List<Player> list = new ArrayList<>();
        for (FakePlayer player : players.values()) {
            if (player.online) {
                list.add(player.handle);
            }
        }
        return list;
    }

    private World worldById(UUID id) {
        for (World world : worlds.values()) {
            if (world.getUID().equals(id)) {
                return world;
            }
        }
        return null;
    }

    private BukkitScheduler scheduler() {
        return proxy(BukkitScheduler.class, (self, method, args) -> switch (method.getName()) {
            case "runTask", "runTaskAsynchronously" -> schedule((Runnable) args[1], 1, 0);
            case "runTaskLater" -> schedule((Runnable) args[1], (Long) args[2], 0);
            case "runTaskTimer" -> schedule((Runnable) args[1], (Long) args[2], Math.max(1, (Long) args[3]));
            default -> objectMethod(self, method, args);
        });
    }

    private PluginManager pluginManager() {
        return proxy(PluginManager.class, (self, method, args) -> {
            if (method.getName().equals("callEvent")) {
                Event event = (Event) args[0];
                events.add(event);
                for (Consumer<Event> hook : List.copyOf(eventHooks)) {
                    hook.accept(event);
                }
                return null;
            }
            return objectMethod(self, method, args);
        });
    }

    private static World createWorld(String name) {
        UUID id = UUID.nameUUIDFromBytes(("world:" + name).getBytes());
        WorldBorder border = proxy(WorldBorder.class, (self, method, args) -> switch (method.getName()) {
            case "isInside" -> true;
            default -> objectMethod(self, method, args);
        });
        return proxy(World.class, (self, method, args) -> switch (method.getName()) {
            case "getName" -> name;
            case "getUID" -> id;
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "getWorldBorder" -> border;
            case "getChunkAtAsync" -> CompletableFuture.failedFuture(new UnsupportedOperationException("sem chunks no servidor simulado"));
            default -> objectMethod(self, method, args);
        });
    }

    // ------------------------------------------------------------------ utilidades

    static String plain(Object message) {
        if (message instanceof ComponentLike like) {
            return PlainTextComponentSerializer.plainText().serialize(like.asComponent());
        }
        return String.valueOf(message);
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(FakeServer.class.getClassLoader(), new Class<?>[] {type}, handler);
    }

    static Object objectMethod(Object self, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> self == args[0];
            case "hashCode" -> System.identityHashCode(self);
            case "toString" -> "Fake" + method.getDeclaringClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(self));
            default -> defaultValue(method.getReturnType());
        };
    }

    static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == void.class) {
            return null;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        return 0;
    }
}
