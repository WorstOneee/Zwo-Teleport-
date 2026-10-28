package br.com.zwo.zwoteleport.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolve um nome digitado contra uma lista de nomes candidatos.
 *
 * <p>Ordem: nome exato (sem diferenciar maiúsculas) e, se não houver, prefixo único. Diferente da
 * versão 2.x, trechos no meio do nome não são aceitos, evitando enviar pedidos ao jogador errado.</p>
 */
public final class NameMatcher {

    public record Result<T>(Optional<T> match, List<String> ambiguous) {

        public boolean isAmbiguous() {
            return match.isEmpty() && ambiguous.size() > 1;
        }
    }

    private NameMatcher() {
    }

    public static <T> Result<T> resolve(Collection<T> candidates, Function<T, String> nameOf, String input) {
        String wanted = normalize(input);
        if (wanted.isEmpty()) {
            return new Result<>(Optional.empty(), List.of());
        }
        List<T> prefixed = new ArrayList<>();
        List<String> prefixedNames = new ArrayList<>();
        for (T candidate : candidates) {
            String name = nameOf.apply(candidate);
            if (name == null) {
                continue;
            }
            String normalized = normalize(name);
            if (normalized.equals(wanted)) {
                return new Result<>(Optional.of(candidate), List.of());
            }
            if (normalized.startsWith(wanted)) {
                prefixed.add(candidate);
                prefixedNames.add(name);
            }
        }
        if (prefixed.size() == 1) {
            return new Result<>(Optional.of(prefixed.get(0)), List.of());
        }
        prefixedNames.sort(String.CASE_INSENSITIVE_ORDER);
        return new Result<>(Optional.empty(), List.copyOf(prefixedNames));
    }

    /** Filtra sugestões de tab pelo prefixo digitado, em ordem alfabética. */
    public static List<String> suggest(Collection<String> options, String typed) {
        String prefix = normalize(typed);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option != null && normalize(option).startsWith(prefix)) {
                result.add(option);
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
