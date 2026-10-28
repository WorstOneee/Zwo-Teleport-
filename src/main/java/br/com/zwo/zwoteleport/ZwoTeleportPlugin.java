package br.com.zwo.zwoteleport;

import br.com.zwo.zwoteleport.api.ZwoTeleportApi;
import br.com.zwo.zwoteleport.command.AdminActions;
import br.com.zwo.zwoteleport.command.AdminCommand;
import br.com.zwo.zwoteleport.command.CommandTakeover;
import br.com.zwo.zwoteleport.command.DelWarpCommand;
import br.com.zwo.zwoteleport.command.SetWarpCommand;
import br.com.zwo.zwoteleport.command.ShortcutCommand;
import br.com.zwo.zwoteleport.command.SpawnCommand;
import br.com.zwo.zwoteleport.command.TpCommand;
import br.com.zwo.zwoteleport.command.TpaCancelCommand;
import br.com.zwo.zwoteleport.command.TpaCommand;
import br.com.zwo.zwoteleport.command.TpaResponseCommand;
import br.com.zwo.zwoteleport.command.WarpCommand;
import br.com.zwo.zwoteleport.config.ConfigFiles;
import br.com.zwo.zwoteleport.config.Settings;
import br.com.zwo.zwoteleport.config.SettingsParser;
import br.com.zwo.zwoteleport.config.YamlMigrator;
import br.com.zwo.zwoteleport.config.YamlTree;
import br.com.zwo.zwoteleport.listener.CombatListener;
import br.com.zwo.zwoteleport.listener.CommandVisibilityListener;
import br.com.zwo.zwoteleport.listener.ConnectionListener;
import br.com.zwo.zwoteleport.listener.ShortcutListener;
import br.com.zwo.zwoteleport.listener.WarmupListener;
import br.com.zwo.zwoteleport.message.MessageCatalog;
import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.request.RequestType;
import br.com.zwo.zwoteleport.request.TpaService;
import br.com.zwo.zwoteleport.spawn.JoinSpawnService;
import br.com.zwo.zwoteleport.storage.AtomicFiles;
import br.com.zwo.zwoteleport.storage.WarpLoadResult;
import br.com.zwo.zwoteleport.storage.YamlWarpRepository;
import br.com.zwo.zwoteleport.teleport.TeleportService;
import br.com.zwo.zwoteleport.warp.WarpPoint;
import br.com.zwo.zwoteleport.warp.WarpService;
import br.com.zwo.zwoteleport.warp.WarpTeleporter;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class ZwoTeleportPlugin extends JavaPlugin implements AdminActions {

    private String configTemplate;
    private String messagesTemplate;
    private Settings settings;
    private Messages messages;
    private WarpService warps;
    private TeleportService teleports;
    private TpaService tpa;
    private JoinSpawnService joinSpawn;
    private CommandTakeover takeover;
    private ApiImpl api;

    @Override
    public void onEnable() {
        configTemplate = resource(ConfigFiles.CONFIG);
        messagesTemplate = resource(ConfigFiles.MESSAGES);
        Path data = getDataFolder().toPath();

        prepareFiles(data);
        settings = loadSettingsAtStartup(data);
        messages = new Messages(loadMessagesAtStartup(data));

        warps = new WarpService(this, new YamlWarpRepository(data.resolve(ConfigFiles.WARPS)));
        WarpLoadResult loaded = warps.loadNow();
        if (loaded.source() == WarpLoadResult.Source.UNREADABLE) {
            getLogger().severe("warps.yml está ilegível e não há backup legível. As warps ficam indisponíveis e "
                + "gravações estão bloqueadas até o arquivo ser corrigido (depois use /zwotp reload).");
        }

        teleports = new TeleportService(this, messages, this::settings);
        tpa = new TpaService(teleports, messages, this::settings);
        joinSpawn = new JoinSpawnService(this, teleports, warps, messages, this::settings);
        WarpTeleporter warpTeleporter = new WarpTeleporter(warps, teleports, messages, this::settings);

        registerCommands(warpTeleporter);
        register(new ConnectionListener(teleports, tpa, joinSpawn));
        register(new WarmupListener(teleports));
        register(new CombatListener(teleports));
        register(new ShortcutListener(warps, warpTeleporter, this::settings));
        register(new CommandVisibilityListener(this::settings));

        for (Player online : Bukkit.getOnlinePlayers()) {
            teleports.openSession(online);
        }

        takeover = new CommandTakeover(this);
        // Depois que todos os plugins habilitaram, para enxergar os comandos deles.
        Bukkit.getScheduler().runTask(this, () -> takeover.apply(settings.commands().takeover()));

        Bukkit.getScheduler().runTaskTimer(this, tpa::expire, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, teleports::maintenance, 1200L, 1200L);

        api = new ApiImpl(getPluginMeta().getVersion(), warps, warpTeleporter, teleports, tpa, this::settings);
        Bukkit.getServicesManager().register(ZwoTeleportApi.class, api, this, ServicePriority.Normal);

        if (settings.showSignature()) {
            getLogger().info("ZwoTeleport " + getPluginMeta().getVersion() + " habilitado. Assinatura by zwo.");
        }
        getLogger().info("Warps: " + warps.size() + " (" + warps.describe() + "). Spawn na entrada: "
            + settings.joinSpawn().mode() + ".");
    }

    @Override
    public void onDisable() {
        if (takeover != null) {
            takeover.restoreAll();
        }
        if (joinSpawn != null) {
            joinSpawn.shutdown();
        }
        if (teleports != null) {
            teleports.shutdown();
        }
        if (tpa != null) {
            tpa.clear();
        }
        if (warps != null) {
            warps.shutdown();
        }
        if (api != null) {
            Bukkit.getServicesManager().unregister(ZwoTeleportApi.class, api);
        }
    }

    public Settings settings() {
        return settings;
    }

    // ------------------------------------------------------------------ inicialização

    private void prepareFiles(Path data) {
        try {
            ConfigFiles.Report report = ConfigFiles.prepare(data, configTemplate, messagesTemplate);
            for (String problem : report.problems()) {
                getLogger().severe(problem);
            }
            if (report.upgradedFromLegacy()) {
                getLogger().warning("Arquivos da versão 2.x atualizados. Cópia dos originais em "
                    + data.relativize(report.backupFolder()) + ".");
            }
            if (!report.configAdded().isEmpty()) {
                getLogger().info("config.yml: " + report.configAdded().size() + " opção(ões) nova(s) adicionada(s) ao final do arquivo.");
                if (report.configAdded().contains("join-spawn.mode")) {
                    getLogger().warning("Spawn na entrada (join-spawn) foi adicionado com o modo EVERY_JOIN. "
                        + "Se o servidor usa Skript para enviar ao spawn, remova esse script para evitar teleporte duplicado, "
                        + "ou ajuste join-spawn.mode.");
                }
            }
            if (!report.messagesAdded().isEmpty()) {
                getLogger().info("messages.yml: " + report.messagesAdded().size() + " mensagem(ns) nova(s) adicionada(s) ao final do arquivo.");
            }
        } catch (IOException error) {
            getLogger().log(Level.SEVERE, "Não foi possível preparar os arquivos de configuração.", error);
        }
    }

    private Settings loadSettingsAtStartup(Path data) {
        SettingsParser.Result result = parseSettings(data);
        result.warnings().forEach(getLogger()::warning);
        if (result.valid()) {
            return result.settings();
        }
        getLogger().severe("config.yml tem erros; usando os valores padrão do plugin até que seja corrigido (/zwotp reload):");
        result.errors().forEach(error -> getLogger().severe(" - " + error));
        return SettingsParser.parse(configTemplate, configTemplate).settings();
    }

    private MessageCatalog loadMessagesAtStartup(Path data) {
        try {
            MessageCatalog catalog = MessageCatalog.load(ConfigFiles.read(data.resolve(ConfigFiles.MESSAGES)), messagesTemplate);
            catalog.warnings().forEach(getLogger()::warning);
            return catalog;
        } catch (IOException | IllegalArgumentException error) {
            getLogger().severe("messages.yml não pôde ser carregado (" + error.getMessage() + "); usando as mensagens padrão.");
            return MessageCatalog.load(messagesTemplate, messagesTemplate);
        }
    }

    private SettingsParser.Result parseSettings(Path data) {
        try {
            return SettingsParser.parse(ConfigFiles.read(data.resolve(ConfigFiles.CONFIG)), configTemplate);
        } catch (IOException error) {
            return new SettingsParser.Result(null, List.of("config.yml não pôde ser lido: " + error.getMessage()), List.of());
        }
    }

    private void registerCommands(WarpTeleporter warpTeleporter) {
        bind("tp", new TpCommand(teleports, messages, this::settings));
        bind("tpa", new TpaCommand(tpa, messages, RequestType.TPA));
        bind("tpahere", new TpaCommand(tpa, messages, RequestType.TPAHERE));
        bind("tpaccept", new TpaResponseCommand(tpa, messages, true));
        bind("tpdeny", new TpaResponseCommand(tpa, messages, false));
        bind("tpacancel", new TpaCancelCommand(tpa, messages));
        bind("warp", new WarpCommand(warps, warpTeleporter, messages, false));
        bind("warps", new WarpCommand(warps, warpTeleporter, messages, true));
        bind("setwarp", new SetWarpCommand(this, warps, messages, false));
        bind("setspawn", new SetWarpCommand(this, warps, messages, true));
        bind("delwarp", new DelWarpCommand(warps, messages));
        bind("spawn", new SpawnCommand(warps, warpTeleporter, teleports, messages, this::settings));
        bind("loja", new ShortcutCommand(warpTeleporter, messages, "loja"));
        bind("zwotp", new AdminCommand(this, messages));
    }

    private void bind(String name, TabExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Comando /" + name + " ausente no plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void register(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, this);
    }

    private String resource(String name) {
        try (InputStream in = getResource(name)) {
            if (in == null) {
                throw new IllegalStateException(name + " ausente dentro do JAR");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Não foi possível ler " + name + " do JAR", error);
        }
    }

    // ------------------------------------------------------------------ AdminActions

    @Override
    public CompletableFuture<ReloadOutcome> reload() {
        Path data = getDataFolder().toPath();
        prepareFiles(data);
        SettingsParser.Result parsed = parseSettings(data);
        List<String> errors = new ArrayList<>(parsed.errors());
        MessageCatalog catalog = null;
        try {
            catalog = MessageCatalog.load(ConfigFiles.read(data.resolve(ConfigFiles.MESSAGES)), messagesTemplate);
        } catch (IOException | IllegalArgumentException error) {
            errors.add(error.getMessage());
        }
        if (!errors.isEmpty() || catalog == null) {
            errors.forEach(error -> getLogger().severe("Reload recusado: " + error));
            return CompletableFuture.completedFuture(new ReloadOutcome(false, errors, List.of()));
        }
        List<String> warnings = new ArrayList<>(parsed.warnings());
        warnings.addAll(catalog.warnings());
        warnings.forEach(getLogger()::warning);

        settings = parsed.settings();
        messages.update(catalog);
        takeover.apply(settings.commands().takeover());

        return warps.reload().thenApply(result -> {
            List<String> all = new ArrayList<>(warnings);
            all.addAll(result.warnings());
            if (result.source() == WarpLoadResult.Source.UNREADABLE) {
                all.add("warps.yml ilegível: warps anteriores mantidas em memória e gravações bloqueadas.");
            }
            return new ReloadOutcome(true, List.of(), all);
        });
    }

    @Override
    public Optional<String> setCombatEnabled(boolean enabled) {
        settings = settings.withCombatEnabled(enabled);
        if (!enabled) {
            teleports.clearCombatTags();
        }
        Path config = getDataFolder().toPath().resolve(ConfigFiles.CONFIG);
        try {
            String text = ConfigFiles.read(config);
            String path = "teleport.combat.enabled";
            String updated;
            Map<String, Object> flat = YamlTree.flatten(YamlTree.parse(text), SettingsParser.OPAQUE);
            if (flat.containsKey(path)) {
                updated = YamlMigrator.setScalar(text, path, String.valueOf(enabled));
            } else {
                updated = (text.endsWith("\n") ? text : text + "\n") + path + ": " + enabled + "\n";
            }
            AtomicFiles.write(config, updated, true);
            return Optional.empty();
        } catch (IOException | YAMLException | IllegalArgumentException error) {
            getLogger().warning("Não foi possível gravar teleport.combat.enabled no config.yml: " + error.getMessage());
            return Optional.of(String.valueOf(error.getMessage()));
        }
    }

    @Override
    public List<Map.Entry<String, String>> info() {
        List<Map.Entry<String, String>> lines = new ArrayList<>();
        String storage = warps.describe() + (warps.writable() ? "" : " - somente leitura");
        if (warps.source() == WarpLoadResult.Source.BACKUP) {
            storage += " - recuperado do backup";
        }
        lines.add(Map.entry("Armazenamento", storage));
        lines.add(Map.entry("Warps", String.valueOf(warps.size())));
        Optional<WarpPoint> spawn = warps.get(settings.joinSpawn().warp());
        lines.add(Map.entry("Spawn", spawn.map(warp -> warp.world()
            + (Bukkit.getWorld(warp.world()) == null ? " (mundo indisponível)" : "")).orElse("não definido")));
        lines.add(Map.entry("Spawn na entrada", settings.joinSpawn().mode().name()));
        lines.add(Map.entry("Combate interno", settings.combat().enabled()
            ? "ligado (" + teleports.combat().taggedCount(System.currentTimeMillis()) + " marcados)"
            : "desligado"));
        lines.add(Map.entry("Teleportes em andamento", String.valueOf(teleports.activeCount())));
        lines.add(Map.entry("Pedidos pendentes", String.valueOf(tpa.size())));
        List<String> taken = takeover.active();
        lines.add(Map.entry("Comandos assumidos", taken.isEmpty() ? "nenhum" : "/" + String.join(", /", taken)));
        return lines;
    }

    @Override
    public String version() {
        return getPluginMeta().getVersion();
    }
}
