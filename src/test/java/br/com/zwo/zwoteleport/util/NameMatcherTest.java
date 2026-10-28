package br.com.zwo.zwoteleport.util;

import org.junit.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NameMatcherTest {

    private static final List<String> ONLINE = List.of("Gabriel", "Gab", "Gabi", "Ana", ".BedrockUser", "Carlos");

    private static NameMatcher.Result<String> resolve(String input) {
        return NameMatcher.resolve(ONLINE, Function.identity(), input);
    }

    @Test
    public void exactMatchWinsOverPrefix() {
        assertEquals("Gab", resolve("gab").match().orElseThrow());
    }

    @Test
    public void uniquePrefixResolves() {
        assertEquals("Carlos", resolve("car").match().orElseThrow());
        assertEquals(".BedrockUser", resolve(".bed").match().orElseThrow());
    }

    @Test
    public void ambiguousPrefixReturnsCandidates() {
        NameMatcher.Result<String> result = resolve("gabr");
        assertEquals("Gabriel", result.match().orElseThrow());
        NameMatcher.Result<String> ambiguous = NameMatcher.resolve(List.of("Gabriel", "Gabi"), Function.identity(), "ga");
        assertTrue(ambiguous.isAmbiguous());
        assertEquals(List.of("Gabi", "Gabriel"), ambiguous.ambiguous());
    }

    @Test
    public void substringInTheMiddleDoesNotMatch() {
        assertFalse("evita pedido para o jogador errado", resolve("los").match().isPresent());
    }

    @Test
    public void suggestionsFilterByPrefix() {
        assertEquals(List.of("Gab", "Gabi", "Gabriel"), NameMatcher.suggest(ONLINE, "GA"));
    }
}
