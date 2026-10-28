package br.com.zwo.zwoteleport.message;

import java.util.Locale;

/**
 * Texto de uma mensagem com o canal de exibição.
 *
 * <p>Por padrão a mensagem vai para o chat. Prefixos no início do texto mudam o canal:</p>
 * <ul>
 *   <li>{@code [actionbar] texto}</li>
 *   <li>{@code [title] título | subtítulo}</li>
 *   <li>{@code [chat] texto} (explícito)</li>
 * </ul>
 * <p>Uma mensagem vazia não é enviada, o que permite desativá-la.</p>
 */
public record MessageSpec(Channel channel, String text, String subtitle) {

    public enum Channel {
        NONE,
        CHAT,
        ACTIONBAR,
        TITLE
    }

    public static MessageSpec parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new MessageSpec(Channel.NONE, "", "");
        }
        String text = raw.strip();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("[actionbar]")) {
            return of(Channel.ACTIONBAR, text.substring("[actionbar]".length()).strip(), "");
        }
        if (lower.startsWith("[title]")) {
            String body = text.substring("[title]".length()).strip();
            int bar = body.indexOf('|');
            if (bar < 0) {
                return of(Channel.TITLE, body, "");
            }
            return of(Channel.TITLE, body.substring(0, bar).strip(), body.substring(bar + 1).strip());
        }
        if (lower.startsWith("[chat]")) {
            return of(Channel.CHAT, text.substring("[chat]".length()).strip(), "");
        }
        return new MessageSpec(Channel.CHAT, raw, "");
    }

    private static MessageSpec of(Channel channel, String text, String subtitle) {
        if (text.isEmpty() && subtitle.isEmpty()) {
            return new MessageSpec(Channel.NONE, "", "");
        }
        return new MessageSpec(channel, text, subtitle);
    }
}
