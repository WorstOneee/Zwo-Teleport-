package br.com.zwo.zwoteleport.request;

import br.com.zwo.zwoteleport.api.TeleportCause;
import br.com.zwo.zwoteleport.api.TeleportRequestInfo;
import br.com.zwo.zwoteleport.config.Settings;
import br.com.zwo.zwoteleport.message.Messages;
import br.com.zwo.zwoteleport.teleport.BypassRules;
import br.com.zwo.zwoteleport.teleport.Destination;
import br.com.zwo.zwoteleport.teleport.TeleportPlan;
import br.com.zwo.zwoteleport.teleport.TeleportService;
import br.com.zwo.zwoteleport.util.NameMatcher;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Regras de /tpa, /tpahere, /tpaccept, /tpdeny e /tpacancel. Sempre locais: nenhum pedido leva um
 * jogador para outro servidor da rede.
 */
public final class TpaService {

    /** Nome informado sem pedido correspondente: nenhum pedido usa este UUID, então take() devolve vazio. */
    private static final UUID NO_MATCH = new UUID(0L, 0L);

    private final RequestManager requests = new RequestManager();
    private final TeleportService teleports;
    private final Messages messages;
    private final Supplier<Settings> settings;

    public TpaService(TeleportService teleports, Messages messages, Supplier<Settings> settings) {
        this.teleports = teleports;
        this.messages = messages;
        this.settings = settings;
    }

    public void send(Player requester, Player target, RequestType type) {
        Settings config = settings.get();
        Settings.Tpa tpa = config.tpa();
        long now = System.currentTimeMillis();

        if (!BypassRules.tpaRestrictions(requester::hasPermission)) {
            for (String blocked : tpa.blockedPermissions()) {
                if (requester.hasPermission(blocked)) {
                    messages.send(requester, "tpa.blocked");
                    return;
                }
            }
            if (inBlockedWorld(requester, tpa.blockedWorlds())) {
                messages.send(requester, "tpa.blocked-world");
                return;
            }
            if (inBlockedWorld(target, tpa.blockedWorlds())) {
                messages.send(requester, "tpa.blocked-target-world", "target", target.getName());
                return;
            }
        }
        if (!tpa.allowSelfRequest() && requester.getUniqueId().equals(target.getUniqueId())) {
            messages.send(requester, "tpa.self-request");
            return;
        }

        boolean bypassCooldown = type == RequestType.TPAHERE
            ? BypassRules.tpahereCooldown(requester::hasPermission, config.cooldown().allowBypassPermission())
            : BypassRules.cooldown(requester::hasPermission, config.cooldown().allowBypassPermission());
        if (!bypassCooldown) {
            long left = teleports.cooldowns().remainingSeconds(requester.getUniqueId(), now);
            if (left > 0) {
                messages.send(requester, "teleport.cooldown", "seconds", String.valueOf(left));
                return;
            }
        }

        RequestManager.CreateResult result = requests.create(
            requester.getUniqueId(), requester.getName(), target.getUniqueId(), target.getName(), type,
            now, tpa.expireSeconds() * 1000L, tpa.allowSelfRequest(), tpa.replaceExistingRequest());
        switch (result.status()) {
            case SELF_REQUEST -> {
                messages.send(requester, "tpa.self-request");
                return;
            }
            case ALREADY_PENDING -> {
                messages.send(requester, "tpa.already-pending", "target", target.getName());
                return;
            }
            case CREATED, REPLACED -> {
                // segue
            }
        }
        if (!bypassCooldown) {
            teleports.cooldowns().apply(requester.getUniqueId(), now, config.cooldown().seconds());
        }

        messages.send(requester, "tpa.sent", "target", target.getName(), "seconds", String.valueOf(tpa.expireSeconds()));
        String name = requester.getName();
        String incomingKey = type == RequestType.TPA ? "tpa.incoming-to-target" : "tpa.incoming-here";
        Component line = messages.component(incomingKey, "player", name)
            .append(Component.space())
            .append(messages.button("tpa.accept-button", "tpa.accept-hover", "/tpaccept " + name, "player", name))
            .append(Component.space())
            .append(messages.button("tpa.deny-button", "tpa.deny-hover", "/tpdeny " + name, "player", name));
        target.sendMessage(line);
        messages.send(target, "tpa.incoming-hint", "player", name);
    }

    public void accept(Player target, String requesterName) {
        long now = System.currentTimeMillis();
        Optional<TeleportRequest> taken = requests.take(target.getUniqueId(), resolveRequester(target, requesterName, now), now);
        if (taken.isEmpty()) {
            messages.send(target, "tpa.none");
            return;
        }
        TeleportRequest request = taken.get();
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null || !requester.isOnline()) {
            messages.send(target, "tpa.requester-offline");
            return;
        }
        Settings config = settings.get();
        Set<String> blockedWorlds = BypassRules.tpaRestrictions(requester::hasPermission)
            ? Set.of()
            : config.tpa().blockedWorlds();
        Player mover = request.type() == RequestType.TPA ? requester : target;
        Player anchor = request.type() == RequestType.TPA ? target : requester;
        if (inBlockedWorld(anchor, blockedWorlds) || inBlockedWorld(mover, blockedWorlds)) {
            messages.send(target, "tpa.blocked-world");
            messages.send(requester, "tpa.blocked-world");
            return;
        }

        messages.send(target, "tpa.accepted-target", "player", requester.getName());
        messages.send(requester, "tpa.accepted-requester", "player", target.getName());

        TeleportCause cause = request.type() == RequestType.TPA ? TeleportCause.TPA : TeleportCause.TPAHERE;
        TeleportPlan plan = new TeleportPlan(
            cause,
            Destination.ofPlayer(anchor.getUniqueId(), anchor.getName(), blockedWorlds),
            anchor.getUniqueId(),
            anchor.getName(),
            true,
            true,
            false,
            config.safety().modeFor(cause),
            config.safety().respectWorldBorder(),
            "teleport.success",
            new String[0],
            true);
        teleports.start(mover, plan);
    }

    public void deny(Player target, String requesterName) {
        long now = System.currentTimeMillis();
        Optional<TeleportRequest> taken = requests.take(target.getUniqueId(), resolveRequester(target, requesterName, now), now);
        if (taken.isEmpty()) {
            messages.send(target, "tpa.none");
            return;
        }
        TeleportRequest request = taken.get();
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester != null && requester.isOnline()) {
            messages.send(requester, "tpa.denied-requester", "player", target.getName());
        }
        messages.send(target, "tpa.denied-target", "player", request.requesterName());
    }

    public void cancelOutgoing(Player requester) {
        List<TeleportRequest> cancelled = requests.cancelOutgoing(requester.getUniqueId());
        if (cancelled.isEmpty()) {
            messages.send(requester, "tpa.none-outgoing");
            return;
        }
        for (TeleportRequest request : cancelled) {
            Player target = Bukkit.getPlayer(request.target());
            if (target != null && target.isOnline()) {
                messages.send(target, "tpa.cancelled-target", "player", requester.getName());
            }
        }
        messages.send(requester, "tpa.cancelled-requester", "count", String.valueOf(cancelled.size()));
    }

    /** Remove os pedidos de quem saiu e avisa apenas a outra parte de cada pedido. */
    public void handleQuit(Player quitter) {
        UUID id = quitter.getUniqueId();
        for (TeleportRequest request : requests.removeInvolving(id)) {
            if (request.requester().equals(id)) {
                Player target = Bukkit.getPlayer(request.target());
                if (target != null && target.isOnline() && !target.getUniqueId().equals(id)) {
                    messages.send(target, "tpa.left-requester", "player", request.requesterName());
                }
            } else {
                Player requester = Bukkit.getPlayer(request.requester());
                if (requester != null && requester.isOnline() && !requester.getUniqueId().equals(id)) {
                    messages.send(requester, "tpa.left-target", "target", request.targetName());
                }
            }
        }
    }

    /** Chamado a cada segundo: remove pedidos vencidos e avisa os dois lados. */
    public void expire() {
        for (TeleportRequest request : requests.expire(System.currentTimeMillis())) {
            Player requester = Bukkit.getPlayer(request.requester());
            Player target = Bukkit.getPlayer(request.target());
            if (requester != null && requester.isOnline()) {
                messages.send(requester, "tpa.expired-requester", "target", request.targetName());
            }
            if (target != null && target.isOnline()) {
                messages.send(target, "tpa.expired-target", "player", request.requesterName());
            }
        }
    }

    /** Nomes de quem enviou pedidos ainda válidos, para sugestões do /tpaccept e /tpdeny. */
    public List<String> incomingNames(Player target) {
        return requests.incoming(target.getUniqueId(), System.currentTimeMillis()).stream()
            .map(TeleportRequest::requesterName)
            .toList();
    }

    public List<TeleportRequestInfo> incoming(UUID player) {
        return requests.incoming(player, System.currentTimeMillis()).stream().map(TpaService::info).toList();
    }

    public List<TeleportRequestInfo> outgoing(UUID player) {
        return requests.outgoing(player, System.currentTimeMillis()).stream().map(TpaService::info).toList();
    }

    public int size() {
        return requests.size();
    }

    public void clear() {
        requests.clear();
    }

    private UUID resolveRequester(Player target, String name, long now) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Optional<UUID> exact = requests.findRequesterByName(target.getUniqueId(), name, now);
        if (exact.isPresent()) {
            return exact.get();
        }
        List<TeleportRequest> incoming = requests.incoming(target.getUniqueId(), now);
        return NameMatcher.resolve(incoming, TeleportRequest::requesterName, name).match()
            .map(TeleportRequest::requester)
            .orElse(NO_MATCH);
    }

    private static boolean inBlockedWorld(Player player, Set<String> blockedWorlds) {
        World world = player.getWorld();
        return !blockedWorlds.isEmpty() && blockedWorlds.contains(world.getName());
    }

    private static TeleportRequestInfo info(TeleportRequest request) {
        return new TeleportRequestInfo(request.requester(), request.requesterName(), request.target(),
            request.targetName(), request.type() == RequestType.TPAHERE, request.createdAtMillis(), request.expiresAtMillis());
    }
}
