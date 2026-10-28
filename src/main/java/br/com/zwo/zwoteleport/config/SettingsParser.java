package br.com.zwo.zwoteleport.config;

import br.com.zwo.zwoteleport.api.SafetyMode;
import br.com.zwo.zwoteleport.spawn.JoinSpawnMode;
import br.com.zwo.zwoteleport.teleport.MovementTolerance;
import br.com.zwo.zwoteleport.warp.WarpNames;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Converte o config.yml em {@link Settings}, acumulando todos os erros em vez de parar no primeiro.
 * Opções ausentes usam o valor do config.yml embutido no plugin.
 */
public final class SettingsParser {

    /** Caminhos cujo valor é um mapa livre. */
    public static final Set<String> OPAQUE = Set.of("shortcuts.commands");

    /** Chaves aceitas pela versão 2.x como alternativa: antiga -> atual. */
    static final Map<String, String> LEGACY_ALIASES = Map.of(
        "teleport.warmup-seconds", "teleport.warmup.seconds",
        "teleport.cancel-on-move", "teleport.warmup.cancel-on-move",
        "teleport.cancel-on-damage", "teleport.warmup.cancel-on-damage",
        "teleport.cooldown-seconds", "teleport.cooldown.seconds",
        "teleport.allow-cooldown-bypass-permission", "teleport.cooldown.allow-bypass-permission"
    );

    public record Result(Settings settings, List<String> errors, List<String> warnings) {

        public boolean valid() {
            return settings != null && errors.isEmpty();
        }
    }

    private final Map<String, Object> user;
    private final Map<String, Object> defaults;
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    private SettingsParser(Map<String, Object> user, Map<String, Object> defaults) {
        this.user = user;
        this.defaults = defaults;
    }

    public static Result parse(String userText, String defaultText) {
        Map<String, Object> defaults = YamlTree.flatten(YamlTree.parse(defaultText), OPAQUE);
        Map<String, Object> user;
        try {
            user = YamlTree.flatten(YamlTree.parse(userText), OPAQUE);
        } catch (YAMLException | IllegalArgumentException invalid) {
            return new Result(null, List.of("config.yml não é um YAML válido: " + firstLine(invalid.getMessage())), List.of());
        }
        return new SettingsParser(user, defaults).build();
    }

    private Result build() {
        reportUnknownKeys();

        Settings.Warmup warmup = new Settings.Warmup(
            bool("teleport.warmup.enabled"),
            integer("teleport.warmup.seconds", 0, 3600),
            bool("teleport.warmup.cancel-on-move"),
            bool("teleport.warmup.cancel-on-damage"),
            bool("teleport.warmup.allow-bypass-permission"),
            new MovementTolerance(
                decimal("teleport.warmup.max-horizontal-distance", 0.0D, 64.0D),
                decimal("teleport.warmup.max-vertical-distance", 0.0D, 64.0D)),
            bool("teleport.warmup.actionbar-countdown"));

        Settings.Cooldown cooldown = new Settings.Cooldown(
            integer("teleport.cooldown.seconds", 0, 86_400),
            bool("teleport.cooldown.allow-bypass-permission"));

        Settings.Combat combat = new Settings.Combat(
            bool("teleport.combat.enabled"),
            integer("teleport.combat.tag-seconds", 0, 3600),
            bool("teleport.combat.block-while-tagged"),
            bool("teleport.combat.cancel-warmup-on-combat"),
            bool("teleport.combat.tag-attacker"),
            bool("teleport.combat.tag-victim"));

        Settings.Tpa tpa = new Settings.Tpa(
            integer("tpa.expire-seconds", 5, 3600),
            bool("tpa.allow-self-request"),
            bool("tpa.replace-existing-request"),
            stringList("tpa.blocked-permissions"),
            new LinkedHashSet<>(stringList("tpa.blocked-worlds")));

        String permissionFormat = string("warps.per-warp-permission-format");
        if (!permissionFormat.contains("{warp}")) {
            errors.add("warps.per-warp-permission-format precisa conter {warp} (atual: '" + permissionFormat + "').");
        }
        Settings.Warps warps = new Settings.Warps(
            bool("warps.use-warmup"),
            bool("warps.require-per-warp-permission"),
            permissionFormat);

        Settings.Shortcuts shortcuts = new Settings.Shortcuts(
            bool("shortcuts.enabled"),
            bool("shortcuts.auto-warp-commands"),
            shortcutMap("shortcuts.commands"));

        String joinWarp = WarpNames.normalize(string("join-spawn.warp"));
        if (!WarpNames.isValid(joinWarp)) {
            errors.add("join-spawn.warp precisa ser um nome de warp válido (atual: '" + joinWarp + "').");
        }
        Settings.JoinSpawn joinSpawn = new Settings.JoinSpawn(
            enumValue("join-spawn.mode", JoinSpawnMode::parse, JoinSpawnMode.DISABLED, "DISABLED, FIRST_JOIN ou EVERY_JOIN"),
            joinWarp,
            integer("join-spawn.delay-ticks", 1, 200),
            bool("join-spawn.bypass-permission-enabled"),
            bool("join-spawn.skip-if-quit-in-combat"),
            bool("join-spawn.notify-player"));

        Settings.Safety safety = new Settings.Safety(
            safety("safety.tpa"),
            safety("safety.warp"),
            safety("safety.spawn"),
            safety("safety.join-spawn"),
            safety("safety.admin"),
            safety("safety.plugin"),
            bool("safety.allow-water"),
            integer("safety.search-radius", 0, 7),
            integer("safety.search-height", 0, 32),
            bool("safety.respect-world-border"));

        List<String> takeover = new ArrayList<>();
        for (String label : stringList("commands.takeover")) {
            String normalized = label.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            if (normalized.isEmpty() || normalized.contains(" ") || normalized.contains(":")) {
                errors.add("commands.takeover contém um alias inválido: '" + label + "'.");
            } else if (!takeover.contains(normalized)) {
                takeover.add(normalized);
            }
        }
        Settings.Commands commands = new Settings.Commands(takeover, bool("commands.hide-namespaced-from-players"));

        String storage = string("storage.type").trim().toUpperCase(Locale.ROOT);
        if (!storage.equals("YAML")) {
            errors.add("storage.type '" + storage + "' não é suportado nesta versão; use YAML.");
        }

        boolean showSignature = bool("startup.show-signature");

        if (!errors.isEmpty()) {
            return new Result(null, List.copyOf(errors), List.copyOf(warnings));
        }
        Settings settings = new Settings(warmup, cooldown, combat, tpa, warps, shortcuts, joinSpawn, safety, commands, showSignature);
        return new Result(settings, List.of(), List.copyOf(warnings));
    }

    private void reportUnknownKeys() {
        for (String key : user.keySet()) {
            if (key.equals("config-version") || defaults.containsKey(key) || LEGACY_ALIASES.containsKey(key)) {
                continue;
            }
            warnings.add("Opção desconhecida ignorada: " + key);
        }
        for (Map.Entry<String, String> alias : LEGACY_ALIASES.entrySet()) {
            if (user.containsKey(alias.getKey()) && !user.containsKey(alias.getValue())) {
                warnings.add("Opção antiga " + alias.getKey() + " em uso; prefira " + alias.getValue() + ".");
            }
        }
    }

    private Object raw(String path) {
        if (user.containsKey(path)) {
            return user.get(path);
        }
        for (Map.Entry<String, String> alias : LEGACY_ALIASES.entrySet()) {
            if (alias.getValue().equals(path) && user.containsKey(alias.getKey())) {
                return user.get(alias.getKey());
            }
        }
        return defaults.get(path);
    }

    private boolean bool(String path) {
        Object value = raw(path);
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value != null) {
            String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
            if (text.equals("true") || text.equals("sim")) {
                return true;
            }
            if (text.equals("false") || text.equals("nao") || text.equals("não")) {
                return false;
            }
        }
        errors.add(path + " deve ser true ou false (atual: " + describe(value) + ").");
        return false;
    }

    private int integer(String path, int min, int max) {
        Object value = raw(path);
        Long parsed = null;
        if (value instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())) {
            parsed = number.longValue();
        } else if (value instanceof String text) {
            try {
                parsed = Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                parsed = null;
            }
        }
        if (parsed == null) {
            errors.add(path + " deve ser um número inteiro (atual: " + describe(value) + ").");
            return min;
        }
        if (parsed < min || parsed > max) {
            errors.add(path + " deve estar entre " + min + " e " + max + " (atual: " + parsed + ").");
            return min;
        }
        return parsed.intValue();
    }

    private double decimal(String path, double min, double max) {
        Object value = raw(path);
        Double parsed = null;
        if (value instanceof Number number) {
            parsed = number.doubleValue();
        } else if (value instanceof String text) {
            try {
                parsed = Double.parseDouble(text.trim().replace(',', '.'));
            } catch (NumberFormatException ignored) {
                parsed = null;
            }
        }
        if (parsed == null || !Double.isFinite(parsed)) {
            errors.add(path + " deve ser um número (atual: " + describe(value) + ").");
            return min;
        }
        if (parsed < min || parsed > max) {
            errors.add(path + " deve estar entre " + min + " e " + max + " (atual: " + parsed + ").");
            return min;
        }
        return parsed;
    }

    private String string(String path) {
        Object value = raw(path);
        if (value == null || value instanceof Map || value instanceof List) {
            errors.add(path + " deve ser um texto (atual: " + describe(value) + ").");
            return "";
        }
        return String.valueOf(value);
    }

    private List<String> stringList(String path) {
        Object value = raw(path);
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item == null || item instanceof Map || item instanceof List) {
                    errors.add(path + " deve ser uma lista de textos.");
                    return List.of();
                }
                String text = String.valueOf(item).trim();
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
            return result;
        }
        if (value instanceof String text) {
            if (!text.isBlank()) {
                result.add(text.trim());
            }
            return result;
        }
        errors.add(path + " deve ser uma lista (atual: " + describe(value) + ").");
        return List.of();
    }

    private Map<String, String> shortcutMap(String path) {
        Object value = raw(path);
        Map<String, String> result = new LinkedHashMap<>();
        if (value == null) {
            return result;
        }
        if (!(value instanceof Map<?, ?> map)) {
            errors.add(path + " deve ser um mapa 'comando: warp'.");
            return result;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String command = String.valueOf(entry.getKey()).trim().toLowerCase(Locale.ROOT);
            String warp = entry.getValue() == null ? "" : WarpNames.normalize(String.valueOf(entry.getValue()));
            if (command.isEmpty() || command.contains(" ") || command.contains(":")) {
                errors.add(path + " tem um comando inválido: '" + entry.getKey() + "'.");
                continue;
            }
            if (!WarpNames.isValid(warp)) {
                errors.add(path + "." + command + " aponta para um nome de warp inválido: '" + entry.getValue() + "'.");
                continue;
            }
            result.put(command, warp);
        }
        return result;
    }

    private SafetyMode safety(String path) {
        return enumValue(path, SafetyMode::parse, SafetyMode.NONE, "NONE, VERIFY ou ADJUST");
    }

    private <T> T enumValue(String path, Function<String, Optional<T>> parser, T fallback, String expected) {
        Object value = raw(path);
        Optional<T> parsed = value == null ? Optional.empty() : parser.apply(String.valueOf(value));
        if (parsed.isEmpty()) {
            errors.add(path + " deve ser " + expected + " (atual: " + describe(value) + ").");
            return fallback;
        }
        return parsed.get();
    }

    private static String describe(Object value) {
        if (value == null) {
            return "vazio";
        }
        String text = String.valueOf(value);
        return text.length() > 60 ? text.substring(0, 57) + "..." : "'" + text + "'";
    }

    static String firstLine(String message) {
        if (message == null) {
            return "erro desconhecido";
        }
        String trimmed = message.strip();
        int newline = trimmed.indexOf('\n');
        return newline < 0 ? trimmed : trimmed.substring(0, newline);
    }
}
