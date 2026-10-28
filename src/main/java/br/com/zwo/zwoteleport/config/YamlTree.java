package br.com.zwo.zwoteleport.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Leitura de YAML em um mapa "achatado" de caminhos ({@code teleport.warmup.seconds -> 10}).
 *
 * <p>Chaves literais com ponto no nível superior ({@code "teleport.warmup.x": 1}) produzem o mesmo
 * caminho que a forma aninhada. A migração usa isso para acrescentar opções novas ao final do
 * arquivo sem reescrever o que o usuário já tem.</p>
 */
public final class YamlTree {

    private YamlTree() {
    }

    /**
     * @throws org.yaml.snakeyaml.error.YAMLException se o texto não for YAML válido
     * @throws IllegalArgumentException se a raiz não for um mapa
     */
    public static Map<String, Object> parse(String text) {
        Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(text == null ? "" : text);
        if (loaded == null) {
            return new LinkedHashMap<>();
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("a raiz do arquivo precisa ser um mapa de chaves");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    /**
     * @param opaque caminhos cujo valor é um mapa livre (ex.: {@code shortcuts.commands}) e deve
     *               ser tratado como uma única opção
     */
    public static Map<String, Object> flatten(Map<String, Object> root, Set<String> opaque) {
        Map<String, Object> out = new LinkedHashMap<>();
        walk("", root, opaque, out);
        return out;
    }

    private static void walk(String prefix, Map<?, ?> node, Set<String> opaque, Map<String, Object> out) {
        for (Map.Entry<?, ?> entry : node.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> child && !opaque.contains(path)) {
                walk(path, child, opaque, out);
            } else if (value != null) {
                out.put(path, value);
            }
        }
    }
}
