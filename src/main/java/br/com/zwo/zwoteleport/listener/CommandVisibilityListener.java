package br.com.zwo.zwoteleport.listener;

import br.com.zwo.zwoteleport.config.Settings;
import br.com.zwo.zwoteleport.teleport.BypassRules;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Esconde "/zwoteleport:..." das sugestões de jogadores comuns. Os comandos continuam funcionando.
 */
public final class CommandVisibilityListener implements Listener {

    private static final String NAMESPACE = "zwoteleport:";

    private final Supplier<Settings> settings;

    public CommandVisibilityListener(Supplier<Settings> settings) {
        this.settings = settings;
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (!settings.get().commands().hideNamespacedFromPlayers() || BypassRules.seeNamespacedCommands(player::hasPermission)) {
            return;
        }
        event.getCommands().removeIf(command -> command.toLowerCase(Locale.ROOT).startsWith(NAMESPACE));
    }
}
