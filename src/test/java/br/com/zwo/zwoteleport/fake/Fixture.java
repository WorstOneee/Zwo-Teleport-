package br.com.zwo.zwoteleport.fake;

import br.com.zwo.zwoteleport.TestResources;
import br.com.zwo.zwoteleport.api.SafetyMode;
import br.com.zwo.zwoteleport.api.TeleportCause;
import br.com.zwo.zwoteleport.config.Settings;
import br.com.zwo.zwoteleport.config.SettingsParser;
import br.com.zwo.zwoteleport.message.MessageCatalog;
import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.request.TpaService;
import br.com.zwo.zwoteleport.spawn.JoinSpawnService;
import br.com.zwo.zwoteleport.storage.YamlWarpRepository;
import br.com.zwo.zwoteleport.teleport.Destination;
import br.com.zwo.zwoteleport.teleport.TeleportPlan;
import br.com.zwo.zwoteleport.teleport.TeleportService;
import br.com.zwo.zwoteleport.warp.WarpService;
import org.bukkit.Location;
import org.bukkit.World;

import java.nio.file.Path;

/**
 * Monta os serviços reais do plugin sobre o {@link FakeServer}.
 */
public final class Fixture {

    public static final String CONFIG = TestResources.read("/config.yml");
    public static final String MESSAGES = TestResources.read("/messages.yml");

    public final FakeServer server = FakeServer.install();
    public final World world = server.world("world");
    public Settings settings;
    public final Messages messages = new Messages(MessageCatalog.load(MESSAGES, MESSAGES));
    public final TeleportService teleports;
    public final TpaService tpa;
    public WarpService warps;
    public JoinSpawnService joinSpawn;

    /** @param overrides linhas "caminho.da.opcao: valor" aplicadas sobre o config.yml padrão */
    public Fixture(String... overrides) {
        configure(overrides);
        teleports = new TeleportService(server.plugin, messages, () -> settings);
        tpa = new TpaService(teleports, messages, () -> settings);
    }

    public void configure(String... overrides) {
        String text = CONFIG + "\n" + String.join("\n", overrides) + "\n";
        SettingsParser.Result result = SettingsParser.parse(text, CONFIG);
        if (!result.valid()) {
            throw new IllegalArgumentException(result.errors().toString());
        }
        settings = result.settings();
    }

    public Fixture withWarps(Path warpsFile) {
        warps = new WarpService(server.plugin, new YamlWarpRepository(warpsFile));
        warps.loadNow();
        joinSpawn = new JoinSpawnService(server.plugin, teleports, warps, messages, () -> settings);
        return this;
    }

    public FakePlayer join(String name) {
        FakePlayer player = server.join(name, world);
        teleports.openSession(player.handle);
        return player;
    }

    public FakePlayer rejoin(FakePlayer old) {
        FakePlayer player = server.rejoin(old);
        teleports.openSession(player.handle);
        return player;
    }

    public void quit(FakePlayer player) {
        if (joinSpawn != null) {
            joinSpawn.handleQuit(player.uuid);
        }
        tpa.handleQuit(player.handle);
        teleports.handleQuit(player.uuid);
        server.quit(player);
    }

    public Location at(double x, double y, double z) {
        return new Location(world, x, y, z, 90f, 10f);
    }

    /** Plano equivalente a uma warp comum: warmup, combate e cooldown. */
    public TeleportPlan warpPlan(Location destination) {
        return new TeleportPlan(TeleportCause.WARP, Destination.of(destination), null, null,
            true, true, true, SafetyMode.NONE, true, "teleport.success", new String[0], true);
    }

    /** Plano administrativo: sem warmup, combate ou cooldown. */
    public TeleportPlan adminPlan(Location destination) {
        return new TeleportPlan(TeleportCause.ADMIN_COMMAND, Destination.of(destination), null, null,
            false, false, false, SafetyMode.NONE, false, "teleport.success", new String[0], true);
    }

    public void seconds(int seconds) {
        server.tick(seconds * 20);
    }
}
