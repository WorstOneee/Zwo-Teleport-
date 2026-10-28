package br.com.zwo.zwoteleport.message;

import br.com.zwo.zwoteleport.TestResources;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MessageTest {

    private static final String DEFAULTS = TestResources.read("/messages.yml");

    @Test
    public void plainTextGoesToChat() {
        MessageSpec spec = MessageSpec.parse("&aOlá");
        assertEquals(MessageSpec.Channel.CHAT, spec.channel());
        assertEquals("&aOlá", spec.text());
    }

    @Test
    public void channelPrefixes() {
        MessageSpec bar = MessageSpec.parse("[actionbar] &7Teleportando");
        assertEquals(MessageSpec.Channel.ACTIONBAR, bar.channel());
        assertEquals("&7Teleportando", bar.text());

        MessageSpec title = MessageSpec.parse("[TITLE] &aSpawn | &7Bem-vindo");
        assertEquals(MessageSpec.Channel.TITLE, title.channel());
        assertEquals("&aSpawn", title.text());
        assertEquals("&7Bem-vindo", title.subtitle());
    }

    @Test
    public void emptyMessageIsNotSent() {
        assertEquals(MessageSpec.Channel.NONE, MessageSpec.parse("").channel());
        assertEquals(MessageSpec.Channel.NONE, MessageSpec.parse("[actionbar]   ").channel());
    }

    @Test
    public void missingKeysFallBackToDefaults() {
        MessageCatalog catalog = MessageCatalog.load("general:\n  no-permission: '&cSem permissão'\n", DEFAULTS);
        assertEquals("&cSem permissão", catalog.raw("general.no-permission"));
        assertEquals("&aTeleporte concluído.", catalog.raw("teleport.success"));
    }

    @Test
    public void prefixPlaceholderIsApplied() {
        MessageCatalog catalog = MessageCatalog.load("prefix: '&8[&bZ&8] '\ngeneral:\n  no-permission: '{prefix}&cNão'\n", DEFAULTS);
        assertEquals("&8[&bZ&8] &cNão", catalog.raw("general.no-permission"));
    }

    @Test
    public void bundledMessagesAreAllPortugueseAndParseable() {
        MessageCatalog catalog = MessageCatalog.load(DEFAULTS, DEFAULTS);
        assertTrue(catalog.warnings().isEmpty());
        assertTrue(catalog.has("teleport.warmup-countdown"));
        assertEquals(MessageSpec.Channel.ACTIONBAR, MessageSpec.parse(catalog.raw("teleport.warmup-countdown")).channel());
    }

    @Test
    public void customPerWarpMessagesFromVersion2AreKept() {
        MessageCatalog catalog = MessageCatalog.load("minas:\n  teleported: '&7Bem-vindo às minas'\n", DEFAULTS);
        assertTrue(catalog.warnings().isEmpty());
        assertTrue(catalog.has("minas.teleported"));
        assertEquals("&7Bem-vindo às minas", catalog.raw("minas.teleported"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void brokenYamlIsRejected() {
        MessageCatalog.load("general: [\n", DEFAULTS);
    }
}
