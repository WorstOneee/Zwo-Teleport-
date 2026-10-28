package br.com.zwo.zwoteleport.storage;

import br.com.zwo.zwoteleport.warp.WarpNames;
import br.com.zwo.zwoteleport.warp.WarpPoint;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Warps em {@code warps.yml}, no mesmo formato da versão 2.x:
 *
 * <pre>
 * warps:
 *   spawn:
 *     world: world
 *     x: 0.5
 *     y: 64.0
 *     z: 0.5
 *     yaw: 90.0
 *     pitch: 0.0
 *     display-name: '&amp;aSpawn'
 *     permission: ''
 * </pre>
 *
 * <p>Chaves desconhecidas e entradas inválidas são preservadas ao gravar. A gravação é atômica e a
 * versão anterior fica em {@code warps.yml.bak}. Se o arquivo principal estiver corrompido, ele é
 * copiado para análise e o backup é usado; sem backup legível, o repositório fica somente leitura.</p>
 */
public final class YamlWarpRepository implements WarpRepository {

    private static final String SECTION = "warps";

    private final Path file;
    private Map<String, Object> root = new LinkedHashMap<>();
    /** nome normalizado -> chave exatamente como está no arquivo */
    private final Map<String, String> rawKeys = new HashMap<>();
    private boolean writable = true;

    public YamlWarpRepository(Path file) {
        this.file = file;
    }

    @Override
    public synchronized WarpLoadResult load() {
        List<String> warnings = new ArrayList<>();
        if (!Files.exists(file)) {
            root = new LinkedHashMap<>();
            rawKeys.clear();
            writable = true;
            return new WarpLoadResult(Map.of(), warnings, WarpLoadResult.Source.NEW);
        }
        try {
            Map<String, Object> parsed = readRoot(file);
            Map<String, WarpPoint> warps = extract(parsed, warnings);
            root = parsed;
            writable = true;
            return new WarpLoadResult(warps, warnings, WarpLoadResult.Source.PRIMARY);
        } catch (IOException | YAMLException | IllegalStateException primaryError) {
            warnings.add("warps.yml ilegível: " + primaryError.getMessage());
            try {
                Path copy = AtomicFiles.preserveCopy(file, "corrompido");
                warnings.add("Cópia do arquivo problemático guardada em " + copy.getFileName());
            } catch (IOException copyError) {
                warnings.add("Não foi possível copiar o arquivo problemático: " + copyError.getMessage());
            }
            Path backup = AtomicFiles.backupOf(file);
            if (Files.exists(backup)) {
                try {
                    Map<String, Object> parsed = readRoot(backup);
                    Map<String, WarpPoint> warps = extract(parsed, warnings);
                    root = parsed;
                    writable = true;
                    warnings.add("Warps recuperadas de " + backup.getFileName() + ".");
                    return new WarpLoadResult(warps, warnings, WarpLoadResult.Source.BACKUP);
                } catch (IOException | YAMLException | IllegalStateException backupError) {
                    warnings.add("Backup também ilegível: " + backupError.getMessage());
                }
            }
            root = new LinkedHashMap<>();
            rawKeys.clear();
            writable = false;
            return new WarpLoadResult(Map.of(), warnings, WarpLoadResult.Source.UNREADABLE);
        }
    }

    @Override
    public synchronized void upsert(WarpPoint warp) throws StorageException {
        requireWritable();
        Map<String, Object> next = deepCopy(root);
        Map<String, Object> section = section(next);
        String key = rawKeys.getOrDefault(warp.name(), warp.name());
        Map<String, Object> entry = new LinkedHashMap<>();
        Object previous = section.get(key);
        if (previous instanceof Map<?, ?> previousMap) {
            previousMap.forEach((k, v) -> entry.put(String.valueOf(k), v));
        }
        entry.put("world", warp.world());
        entry.put("x", warp.x());
        entry.put("y", warp.y());
        entry.put("z", warp.z());
        entry.put("yaw", (double) warp.yaw());
        entry.put("pitch", (double) warp.pitch());
        entry.put("display-name", warp.displayName());
        entry.put("permission", warp.permission());
        section.put(key, entry);
        persist(next);
        rawKeys.put(warp.name(), key);
    }

    @Override
    public synchronized boolean delete(String name) throws StorageException {
        requireWritable();
        String normalized = WarpNames.normalize(name);
        String key = rawKeys.get(normalized);
        Map<String, Object> next = deepCopy(root);
        Map<String, Object> section = section(next);
        if (key == null || !section.containsKey(key)) {
            return false;
        }
        section.remove(key);
        persist(next);
        rawKeys.remove(normalized);
        return true;
    }

    @Override
    public synchronized boolean writable() {
        return writable;
    }

    @Override
    public String describe() {
        return "YAML (" + file.getFileName() + ")";
    }

    private void requireWritable() throws StorageException {
        if (!writable) {
            throw new StorageException("warps.yml está ilegível; gravação bloqueada para não sobrescrever o arquivo");
        }
    }

    private void persist(Map<String, Object> next) throws StorageException {
        String text = dump(next);
        try {
            // Confere se o conteúdo gerado é legível antes de substituir o arquivo.
            Object reparsed = loader().load(text);
            if (!(reparsed instanceof Map)) {
                throw new StorageException("conteúdo gerado inválido");
            }
            AtomicFiles.write(file, text, true);
            root = next;
        } catch (IOException | YAMLException error) {
            throw new StorageException("falha ao gravar warps.yml: " + error.getMessage(), error);
        }
    }

    private Map<String, WarpPoint> extract(Map<String, Object> parsed, List<String> warnings) {
        rawKeys.clear();
        Map<String, WarpPoint> warps = new LinkedHashMap<>();
        Object sectionObject = parsed.get(SECTION);
        if (sectionObject == null) {
            return warps;
        }
        if (!(sectionObject instanceof Map<?, ?> section)) {
            throw new IllegalStateException("a seção 'warps' não é um mapa");
        }
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            String rawKey = String.valueOf(entry.getKey());
            String name = WarpNames.normalize(rawKey);
            if (warps.containsKey(name)) {
                warnings.add("Warp '" + rawKey + "' duplicada (mesmo nome de '" + rawKeys.get(name) + "'); mantida a primeira.");
                continue;
            }
            if (!(entry.getValue() instanceof Map<?, ?> data)) {
                warnings.add("Warp '" + rawKey + "' ignorada: formato inválido (mantida no arquivo).");
                continue;
            }
            try {
                WarpPoint warp = parseWarp(name, data);
                if (!WarpNames.isValid(name)) {
                    warnings.add("Warp '" + rawKey + "' tem nome fora do padrão [a-z0-9_-]; carregada mesmo assim.");
                }
                warps.put(name, warp);
                rawKeys.put(name, rawKey);
            } catch (IllegalArgumentException invalid) {
                warnings.add("Warp '" + rawKey + "' ignorada: " + invalid.getMessage() + " (mantida no arquivo).");
            }
        }
        return warps;
    }

    static WarpPoint parseWarp(String name, Map<?, ?> data) {
        Object worldValue = data.get("world");
        String world = worldValue == null ? "" : String.valueOf(worldValue).trim();
        if (world.isEmpty()) {
            throw new IllegalArgumentException("sem mundo");
        }
        double x = number(data, "x", null);
        double y = number(data, "y", null);
        double z = number(data, "z", null);
        double yaw = number(data, "yaw", 0.0D);
        double pitch = number(data, "pitch", 0.0D);
        Object display = data.get("display-name");
        Object permission = data.get("permission");
        return new WarpPoint(
            name,
            display == null ? name : String.valueOf(display),
            world,
            x, y, z,
            (float) yaw, (float) pitch,
            permission == null ? "" : String.valueOf(permission));
    }

    private static double number(Map<?, ?> data, String key, Double fallback) {
        Object value = data.get(key);
        if (value == null) {
            if (fallback == null) {
                throw new IllegalArgumentException("sem '" + key + "'");
            }
            return fallback;
        }
        double parsed;
        if (value instanceof Number number) {
            parsed = number.doubleValue();
        } else {
            try {
                parsed = Double.parseDouble(String.valueOf(value).trim());
            } catch (NumberFormatException notNumber) {
                throw new IllegalArgumentException("'" + key + "' não é número");
            }
        }
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException("'" + key + "' não é finito");
        }
        return parsed;
    }

    private static Map<String, Object> readRoot(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Object loaded = loader().load(text);
        if (loaded == null) {
            return new LinkedHashMap<>();
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalStateException("o conteúdo não é um mapa YAML");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root) {
        Object existing = root.get(SECTION);
        if (existing instanceof Map<?, ?>) {
            return (Map<String, Object>) existing;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        root.put(SECTION, created);
        return created;
    }

    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((k, v) -> copy.put(k, copyValue(v)));
        return copy;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), copyValue(v)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(copyValue(item));
            }
            return copy;
        }
        return value;
    }

    private static Yaml loader() {
        return new Yaml(new SafeConstructor(new LoaderOptions()));
    }

    private static String dump(Map<String, Object> data) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setAllowUnicode(true);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(data);
    }
}
