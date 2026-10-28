package br.com.zwo.zwoteleport.listener;

import br.com.zwo.zwoteleport.config.Settings;
import br.com.zwo.zwoteleport.warp.WarpNames;
import br.com.zwo.zwoteleport.warp.WarpService;
import br.com.zwo.zwoteleport.warp.WarpTeleporter;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Atalhos de warp como comando (/minas). Só age quando nenhum plugin registrou um comando com esse
 * nome, então nunca sobrescreve comandos de outros plugins. Comandos já cancelados por outro plugin
 * (ex.: CommandWhitelist) são ignorados.
 */
public final class ShortcutListener implements Listener {

    private final WarpService warps;
    private final WarpTeleporter teleporter;
    private final Supplier<Settings> settings;

    public ShortcutListener(WarpService warps, WarpTeleporter teleporter, Supplier<Settings> settings) {
        this.warps = warps;
        this.teleporter = teleporter;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Settings.Shortcuts shortcuts = settings.get().shortcuts();
        if (!shortcuts.enabled()) {
            return;
        }
        String message = event.getMessage();
        if (message.length() < 2 || message.charAt(0) != '/') {
            return;
        }
        String[] parts = message.substring(1).trim().split("\\s+", 2);
        if (parts.length > 1 && !parts[1].isBlank()) {
            return;
        }
        String label = parts[0].toLowerCase(Locale.ROOT);
        if (label.isEmpty() || Bukkit.getCommandMap().getCommand(label) != null) {
            return;
        }
        String warp = shortcuts.commands().get(label);
        if (warp == null && shortcuts.autoWarpCommands() && WarpNames.isValid(label) && warps.get(label).isPresent()) {
            warp = label;
        }
        if (warp == null) {
            return;
        }
        event.setCancelled(true);
        teleporter.teleport(event.getPlayer(), warp);
    }
}
