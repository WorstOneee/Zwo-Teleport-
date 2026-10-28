package br.com.zwo.zwoteleport.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Assume aliases já registrados por outros plugins (ex.: /tpa do Essentials), usando apenas a API
 * pública do mapa de comandos do Paper. Cada troca é registrada no console, a versão anterior é
 * guardada e restaurada quando o alias sai da lista ou o plugin é desligado. Os comandos com
 * namespace de cada plugin ({@code /essentials:tpa}, {@code /zwoteleport:tpa}) não são afetados.
 */
public final class CommandTakeover {

    private final JavaPlugin plugin;
    /** alias -> comando que estava registrado antes (nulo se não havia nenhum) */
    private final Map<String, Command> previous = new HashMap<>();

    public CommandTakeover(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void apply(List<String> labels) {
        Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
        boolean changed = false;

        for (String label : List.copyOf(previous.keySet())) {
            if (!labels.contains(label)) {
                changed |= restore(known, label);
            }
        }
        for (String label : labels) {
            PluginCommand ours = plugin.getCommand(label);
            if (ours == null) {
                plugin.getLogger().warning("commands.takeover: /" + label + " não é um comando do ZwoTeleport; ignorado.");
                continue;
            }
            Command current = known.get(label);
            if (current == ours) {
                continue;
            }
            known.put(label, ours);
            previous.putIfAbsent(label, current);
            changed = true;
            if (current != null) {
                plugin.getLogger().info("Comando /" + label + " assumido pelo ZwoTeleport (antes: " + owner(current)
                    + "). Use /" + ownerNamespace(current) + label + " para o original.");
            }
        }
        if (changed) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.updateCommands();
            }
        }
    }

    /** Devolve os aliases aos donos anteriores. Chamado ao desligar. */
    public void restoreAll() {
        Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
        for (String label : List.copyOf(previous.keySet())) {
            restore(known, label);
        }
    }

    public List<String> active() {
        return List.copyOf(previous.keySet());
    }

    private boolean restore(Map<String, Command> known, String label) {
        Command before = previous.remove(label);
        PluginCommand ours = plugin.getCommand(label);
        if (ours == null || known.get(label) != ours) {
            return false;
        }
        if (before != null) {
            known.put(label, before);
        } else {
            known.remove(label);
        }
        return true;
    }

    private static String owner(Command command) {
        if (command instanceof PluginIdentifiableCommand identifiable) {
            return identifiable.getPlugin().getName();
        }
        return command.getClass().getSimpleName();
    }

    private static String ownerNamespace(Command command) {
        if (command instanceof PluginIdentifiableCommand identifiable) {
            return identifiable.getPlugin().getName().toLowerCase(Locale.ROOT) + ":";
        }
        return "minecraft:";
    }
}
