package br.com.zwo.zwoteleport.message;

import br.com.zwo.zwoteleport.config.YamlTree;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Textos das mensagens: os do messages.yml do servidor, com os do plugin como reserva para chaves
 * ausentes. Imutável; o reload cria um novo catálogo e só troca se ele for válido.
 */
public final class MessageCatalog {

    /** Mensagem própria de uma warp, como na 2.x: "minas.teleported" é usada por /warp minas. */
    private static final Pattern WARP_MESSAGE = Pattern.compile("[a-z0-9_-]{1,32}\\.teleported");

    private final Map<String, String> values;
    private final List<String> warnings;

    private MessageCatalog(Map<String, String> values, List<String> warnings) {
        this.values = Map.copyOf(values);
        this.warnings = List.copyOf(warnings);
    }

    /**
     * @throws IllegalArgumentException se o texto do servidor não for um YAML válido
     */
    public static MessageCatalog load(String userText, String defaultText) {
        Map<String, Object> defaults = YamlTree.flatten(YamlTree.parse(defaultText), Set.of());
        Map<String, Object> user;
        try {
            user = YamlTree.flatten(YamlTree.parse(userText), Set.of());
        } catch (YAMLException invalid) {
            throw new IllegalArgumentException("messages.yml não é um YAML válido: " + firstLine(invalid.getMessage()), invalid);
        }
        Map<String, String> merged = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            merged.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        for (Map.Entry<String, Object> entry : user.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map || value instanceof List) {
                warnings.add("Mensagem " + entry.getKey() + " deve ser um texto; usando o padrão.");
                continue;
            }
            if (!defaults.containsKey(entry.getKey()) && !entry.getKey().equals("prefix") && !WARP_MESSAGE.matcher(entry.getKey()).matches()) {
                warnings.add("Mensagem desconhecida ignorada: " + entry.getKey());
                continue;
            }
            merged.put(entry.getKey(), String.valueOf(value));
        }
        return new MessageCatalog(merged, warnings);
    }

    /** Texto bruto da chave, já com {prefix} aplicado; vazio se a chave não existir. */
    public String raw(String key) {
        String value = values.getOrDefault(key, "");
        if (value.contains("{prefix}")) {
            value = value.replace("{prefix}", values.getOrDefault("prefix", ""));
        }
        return value;
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public List<String> warnings() {
        return warnings;
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "erro desconhecido";
        }
        String trimmed = message.strip();
        int newline = trimmed.indexOf('\n');
        return newline < 0 ? trimmed : trimmed.substring(0, newline);
    }
}
