package br.com.zwo.zwoteleport.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Atualiza arquivos YAML do usuário sem reescrevê-los: nada do que já existe é alterado, apenas
 * acrescentado ao final. Seções novas inteiras são copiadas do modelo com seus comentários; opções
 * novas dentro de seções existentes entram como chaves com ponto ({@code "a.b.c": valor}), que o
 * plugin lê como se estivessem aninhadas. Rodar de novo não acrescenta nada (idempotente).
 */
public final class YamlMigrator {

    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^([A-Za-z0-9_][A-Za-z0-9_.-]*)\\s*:(\\s.*)?$");
    private static final Pattern KEY_LINE = Pattern.compile("^(\\s*)([A-Za-z0-9_\"'][^:#]*?)\\s*:(\\s.*)?$");

    public record Result(String text, List<String> added) {

        public boolean changed() {
            return !added.isEmpty();
        }
    }

    private YamlMigrator() {
    }

    /**
     * @param aliases chaves antigas aceitas no lugar das novas (antiga -> nova); a nova não é
     *                acrescentada se a antiga existir
     * @param header comentário colocado antes das opções acrescentadas
     */
    public static Result appendMissing(
        String userText, String templateText, Set<String> opaque, Map<String, String> aliases, String header
    ) {
        Map<String, Object> userFlat = YamlTree.flatten(YamlTree.parse(userText), opaque);
        Map<String, Object> templateFlat = YamlTree.flatten(YamlTree.parse(templateText), opaque);

        Set<String> present = new LinkedHashSet<>(userFlat.keySet());
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            if (userFlat.containsKey(alias.getKey())) {
                present.add(alias.getValue());
            }
        }
        Set<String> userSections = new LinkedHashSet<>();
        for (String key : present) {
            userSections.add(firstSegment(key));
        }
        for (String key : YamlTree.parse(userText).keySet()) {
            userSections.add(firstSegment(key));
        }

        Map<String, String> blocks = topLevelBlocks(templateText);
        StringBuilder appended = new StringBuilder();
        List<String> added = new ArrayList<>();

        for (Map.Entry<String, String> block : blocks.entrySet()) {
            if (userSections.contains(block.getKey())) {
                continue;
            }
            appended.append('\n').append(block.getValue()).append('\n');
            for (String leaf : templateFlat.keySet()) {
                if (firstSegment(leaf).equals(block.getKey())) {
                    added.add(leaf);
                }
            }
            userSections.add(block.getKey());
        }

        Map<String, Object> missingLeaves = new LinkedHashMap<>();
        for (Map.Entry<String, Object> leaf : templateFlat.entrySet()) {
            if (!present.contains(leaf.getKey()) && !added.contains(leaf.getKey())) {
                missingLeaves.put(leaf.getKey(), leaf.getValue());
            }
        }
        if (!missingLeaves.isEmpty()) {
            appended.append('\n');
            if (header != null && !header.isBlank()) {
                for (String line : header.strip().split("\n")) {
                    appended.append("# ").append(line.strip()).append('\n');
                }
            }
            for (Map.Entry<String, Object> leaf : missingLeaves.entrySet()) {
                appended.append(dumpEntry(leaf.getKey(), leaf.getValue()));
                added.add(leaf.getKey());
            }
        }

        if (added.isEmpty()) {
            return new Result(userText, List.of());
        }
        String base = userText == null ? "" : userText;
        if (!base.isEmpty() && !base.endsWith("\n")) {
            base = base + "\n";
        }
        return new Result(base + appended, List.copyOf(added));
    }

    /**
     * Troca o valor escalar de todas as ocorrências de {@code path}, preservando indentação e
     * comentário na mesma linha. Devolve o texto original se o caminho não existir como escalar.
     */
    public static String setScalar(String text, String path, String literal) {
        String[] lines = text.split("\n", -1);
        List<int[]> stack = new ArrayList<>();
        List<String> names = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
                continue;
            }
            Matcher matcher = KEY_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            int indent = matcher.group(1).length();
            String key = unquote(matcher.group(2).strip());
            while (!stack.isEmpty() && stack.get(stack.size() - 1)[0] >= indent) {
                stack.remove(stack.size() - 1);
                names.remove(names.size() - 1);
            }
            stack.add(new int[] {indent});
            names.add(key);
            String current = String.join(".", names);
            String rest = matcher.group(3) == null ? "" : matcher.group(3);
            String valuePart = rest.strip();
            if (!current.equals(path) || valuePart.isEmpty() || valuePart.startsWith("|") || valuePart.startsWith(">")) {
                continue;
            }
            String comment = "";
            int hash = rest.indexOf(" #");
            if (hash >= 0 && !valuePart.startsWith("\"") && !valuePart.startsWith("'")) {
                comment = rest.substring(hash);
            }
            lines[i] = matcher.group(1) + matcher.group(2).strip() + ": " + literal + comment;
            changed = true;
        }
        return changed ? String.join("\n", lines) : text;
    }

    /** Divide o modelo em blocos por chave de primeiro nível, levando os comentários colados acima. */
    static Map<String, String> topLevelBlocks(String text) {
        String[] lines = text.split("\n", -1);
        List<Integer> keyLines = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = TOP_LEVEL_KEY.matcher(lines[i]);
            if (matcher.matches()) {
                keyLines.add(i);
                keys.add(matcher.group(1));
            }
        }
        int[] starts = new int[keyLines.size()];
        for (int k = 0; k < keyLines.size(); k++) {
            int lowerBound = k == 0 ? 0 : keyLines.get(k - 1) + 1;
            int start = keyLines.get(k);
            while (start - 1 >= lowerBound && lines[start - 1].startsWith("#")) {
                start--;
            }
            starts[k] = start;
        }
        Map<String, String> blocks = new LinkedHashMap<>();
        for (int k = 0; k < keyLines.size(); k++) {
            int end = k + 1 < keyLines.size() ? starts[k + 1] : lines.length;
            while (end - 1 > keyLines.get(k) && lines[end - 1].isBlank()) {
                end--;
            }
            StringBuilder block = new StringBuilder();
            for (int i = starts[k]; i < end; i++) {
                if (i > starts[k]) {
                    block.append('\n');
                }
                block.append(lines[i]);
            }
            blocks.put(keys.get(k), block.toString());
        }
        return blocks;
    }

    private static String dumpEntry(String key, Object value) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        options.setAllowUnicode(true);
        options.setWidth(Integer.MAX_VALUE);
        Map<String, Object> single = new LinkedHashMap<>();
        single.put(key, value);
        String dumped = new Yaml(options).dump(single).strip();
        // Em estilo FLOW o SnakeYAML envolve o mapa em chaves: {key: valor}
        if (dumped.startsWith("{") && dumped.endsWith("}")) {
            dumped = dumped.substring(1, dumped.length() - 1).strip();
        }
        return dumped + "\n";
    }

    private static String firstSegment(String key) {
        int dot = key.indexOf('.');
        return dot < 0 ? key : key.substring(0, dot);
    }

    private static String unquote(String key) {
        if (key.length() >= 2 && ((key.startsWith("\"") && key.endsWith("\"")) || (key.startsWith("'") && key.endsWith("'")))) {
            return key.substring(1, key.length() - 1);
        }
        return key;
    }
}
