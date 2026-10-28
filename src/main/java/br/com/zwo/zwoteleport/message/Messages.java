package br.com.zwo.zwoteleport.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Envia as mensagens do messages.yml. Os valores dos placeholders entram como texto literal,
 * então nomes de jogadores nunca alteram cores nem formatação. A exceção são {warp} e {warps},
 * definidos pela equipe, que mantêm as cores do nome de exibição da warp.
 */
public final class Messages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .build();
    private static final Title.Times TITLE_TIMES = Title.Times.times(
        Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(500));
    /** Placeholders definidos pela equipe (nomes de warps), que mantêm as cores. */
    private static final Set<String> FORMATTED_PLACEHOLDERS = Set.of("warp", "warps");

    private MessageCatalog catalog;

    public Messages(MessageCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public void update(MessageCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public MessageCatalog catalog() {
        return catalog;
    }

    public boolean has(String key) {
        return catalog.has(key);
    }

    /** Envia a mensagem pelo canal definido no texto. Placeholders em pares: "player", nome, ... */
    public void send(CommandSender to, String key, String... placeholders) {
        if (to == null) {
            return;
        }
        MessageSpec spec = MessageSpec.parse(catalog.raw(key));
        Map<String, String> values = pairs(placeholders);
        switch (spec.channel()) {
            case NONE -> {
                // mensagem desativada
            }
            case CHAT -> to.sendMessage(format(spec.text(), values));
            case ACTIONBAR -> {
                if (to instanceof Player player) {
                    player.sendActionBar(format(spec.text(), values));
                } else {
                    to.sendMessage(format(spec.text(), values));
                }
            }
            case TITLE -> {
                if (to instanceof Player player) {
                    player.showTitle(Title.title(format(spec.text(), values), format(spec.subtitle(), values), TITLE_TIMES));
                } else {
                    to.sendMessage(format(spec.text(), values));
                }
            }
        }
    }

    /** Texto da chave como componente de chat, ignorando o canal. Vazio se a mensagem estiver desativada. */
    public Component component(String key, String... placeholders) {
        MessageSpec spec = MessageSpec.parse(catalog.raw(key));
        if (spec.channel() == MessageSpec.Channel.NONE) {
            return Component.empty();
        }
        return format(spec.text(), pairs(placeholders));
    }

    public boolean isEnabled(String key) {
        return MessageSpec.parse(catalog.raw(key)).channel() != MessageSpec.Channel.NONE;
    }

    /** Botão clicável que executa um comando, com texto ao passar o mouse. */
    public Component button(String labelKey, String hoverKey, String command, String... placeholders) {
        return component(labelKey, placeholders)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(component(hoverKey, placeholders)));
    }

    /** Texto da chave sem cores, para usar dentro de outra mensagem. */
    public String plain(String key, String... placeholders) {
        return PlainTextComponentSerializer.plainText().serialize(component(key, placeholders));
    }

    public static Component format(String template, Map<String, String> values) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }
        Component component = LEGACY.deserialize(template);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String token = "{" + entry.getKey() + "}";
            if (!template.contains(token)) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue();
            Component replacement = FORMATTED_PLACEHOLDERS.contains(entry.getKey()) ? LEGACY.deserialize(value) : Component.text(value);
            component = component.replaceText(TextReplacementConfig.builder()
                .matchLiteral(token)
                .replacement(replacement)
                .build());
        }
        return component;
    }

    /** Converte texto com códigos &amp; em componente (para nomes de exibição de warps). */
    public static Component legacy(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    /** Remove os códigos de cor de um texto com &amp;. */
    public static String stripColors(String text) {
        return PlainTextComponentSerializer.plainText().serialize(legacy(text));
    }

    private static Map<String, String> pairs(String... placeholders) {
        Map<String, String> values = new LinkedHashMap<>();
        if (placeholders == null) {
            return values;
        }
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            values.put(placeholders[i], placeholders[i + 1]);
        }
        return values;
    }
}
