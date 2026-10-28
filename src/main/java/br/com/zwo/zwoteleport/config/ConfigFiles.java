package br.com.zwo.zwoteleport.config;

import br.com.zwo.zwoteleport.storage.AtomicFiles;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prepara config.yml e messages.yml na pasta do plugin.
 *
 * <ul>
 *   <li>Arquivo ausente: copia o modelo do plugin.</li>
 *   <li>config.yml sem {@code config-version} (versão 2.x): antes de qualquer alteração, copia
 *       config.yml, messages.yml e warps.yml para {@code backups/pre-3.0.0-<data>/}.</li>
 *   <li>Opções e mensagens novas são acrescentadas ao final, sem mexer no que já existe.</li>
 * </ul>
 * Rodar várias vezes não muda nada depois da primeira (idempotente).
 */
public final class ConfigFiles {

    public static final String CONFIG = "config.yml";
    public static final String MESSAGES = "messages.yml";
    public static final String WARPS = "warps.yml";

    private static final String CONFIG_HEADER = "Opções adicionadas automaticamente pela atualização do ZwoTeleport.\n"
        + "Veja a descrição de cada uma no config.yml padrão do plugin.";
    private static final String MESSAGES_HEADER = "Mensagens adicionadas automaticamente pela atualização do ZwoTeleport.";

    public record Report(
        boolean createdConfig,
        boolean upgradedFromLegacy,
        Path backupFolder,
        List<String> configAdded,
        List<String> messagesAdded,
        List<String> problems
    ) {
    }

    private ConfigFiles() {
    }

    public static Report prepare(Path dataFolder, String configTemplate, String messagesTemplate) throws IOException {
        Files.createDirectories(dataFolder);
        Path config = dataFolder.resolve(CONFIG);
        Path messages = dataFolder.resolve(MESSAGES);
        List<String> problems = new ArrayList<>();
        boolean createdConfig = false;
        boolean legacy = false;
        Path backupFolder = null;
        List<String> configAdded = List.of();
        List<String> messagesAdded = List.of();

        if (!Files.exists(config)) {
            AtomicFiles.write(config, configTemplate, false);
            createdConfig = true;
        } else {
            String text = read(config);
            Map<String, Object> root;
            try {
                root = YamlTree.parse(text);
            } catch (YAMLException | IllegalArgumentException invalid) {
                root = null;
                problems.add("config.yml não pôde ser lido para atualização: " + SettingsParser.firstLine(invalid.getMessage()));
            }
            if (root != null) {
                legacy = !root.containsKey("config-version");
                if (legacy) {
                    backupFolder = backup(dataFolder);
                }
                YamlMigrator.Result migrated = YamlMigrator.appendMissing(
                    text, configTemplate, SettingsParser.OPAQUE, SettingsParser.LEGACY_ALIASES, CONFIG_HEADER);
                if (migrated.changed()) {
                    AtomicFiles.write(config, migrated.text(), !legacy);
                    configAdded = migrated.added();
                }
            }
        }

        if (!Files.exists(messages)) {
            AtomicFiles.write(messages, messagesTemplate, false);
        } else {
            String text = read(messages);
            try {
                YamlMigrator.Result migrated = YamlMigrator.appendMissing(text, messagesTemplate, Set.of(), Map.of(), MESSAGES_HEADER);
                if (migrated.changed()) {
                    AtomicFiles.write(messages, migrated.text(), !legacy);
                    messagesAdded = migrated.added();
                }
            } catch (YAMLException | IllegalArgumentException invalid) {
                problems.add("messages.yml não pôde ser lido para atualização: " + SettingsParser.firstLine(invalid.getMessage()));
            }
        }
        return new Report(createdConfig, legacy, backupFolder, configAdded, messagesAdded, problems);
    }

    public static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static Path backup(Path dataFolder) throws IOException {
        Path folder = dataFolder.resolve("backups").resolve("pre-3.0.0-" + AtomicFiles.timestamp());
        Files.createDirectories(folder);
        for (String name : List.of(CONFIG, MESSAGES, WARPS)) {
            Path source = dataFolder.resolve(name);
            if (Files.exists(source)) {
                Files.copy(source, folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return folder;
    }
}
