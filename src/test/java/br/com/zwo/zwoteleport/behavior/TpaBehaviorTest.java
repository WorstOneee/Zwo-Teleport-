package br.com.zwo.zwoteleport.behavior;

import br.com.zwo.zwoteleport.fake.FakePlayer;
import br.com.zwo.zwoteleport.fake.Fixture;
import br.com.zwo.zwoteleport.request.RequestType;
import br.com.zwo.zwoteleport.util.Permissions;
import org.bukkit.Location;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Fluxo completo de /tpa e /tpahere no servidor simulado.
 */
public class TpaBehaviorTest {

    /** O servidor simulado não tem chunks; a política ADJUST é coberta em TeleportServiceBehaviorTest. */
    private static final String NO_WARMUP = "teleport.warmup.enabled: false\nsafety.tpa: NONE";

    @Test
    public void tpaMovesTheRequesterToTheTarget() {
        Fixture f = new Fixture(NO_WARMUP);
        FakePlayer ana = f.join("Ana");
        FakePlayer bruno = f.join("Bruno");
        bruno.moveTo(40, 64, 40);

        f.tpa.send(ana.handle, bruno.handle, RequestType.TPA);
        assertTrue(ana.received("Pedido enviado para Bruno"));
        assertTrue(bruno.received("Ana quer teleportar até você. [ACEITAR] [NEGAR]"));
        assertTrue(bruno.received("Use /tpaccept Ana ou /tpdeny Ana."));

        f.tpa.accept(bruno.handle, null);
        assertEquals(40.0, ana.location.getX(), 1e-9);
        assertTrue("o alvo não se move", bruno.teleports.isEmpty());
    }

    @Test
    public void tpahereMovesTheTargetToTheRequester() {
        Fixture f = new Fixture(NO_WARMUP);
        FakePlayer ana = f.join("Ana");
        FakePlayer bruno = f.join("Bruno");
        ana.moveTo(-15, 64, 22);

        f.tpa.send(ana.handle, bruno.handle, RequestType.TPAHERE);
        assertTrue(bruno.received("Ana quer que você teleporte até ele."));
        f.tpa.accept(bruno.handle, "an");
        assertEquals(-15.0, bruno.location.getX(), 1e-9);
        assertTrue(ana.teleports.isEmpty());
    }

    @Test
    public void acceptingTwiceTeleportsOnce() {
        Fixture f = new Fixture(NO_WARMUP);
        FakePlayer ana = f.join("Ana");
        FakePlayer bruno = f.join("Bruno");
        f.tpa.send(ana.handle, bruno.handle, RequestType.TPA);
        f.tpa.accept(bruno.handle, null);
        f.tpa.accept(bruno.handle, null);
        assertEquals(1, ana.teleports.size());
        assertEquals("Você não tem pedidos de teleporte pendentes.", bruno.lastMessage());
    }

    @Test
    public void denyAfterAcceptHasNoEffect() {
        Fixture f = new Fixture(NO_WARMUP);
        FakePlayer ana = f.join("Ana");
        FakePlayer bruno = f.join("Bruno");
        f.tpa.send(ana.handle, bruno.handle, RequestType.TPA);
        f.tpa.accept(bruno.handle, "Ana");
        f.tpa.deny(bruno.handle, "Ana");
        assertFalse("não pode haver aviso de negado depois de aceito", ana.received("foi negado"));
    }

    @Test
    public void requesterCannotAcceptTheirOwnRequest() {
        Fixture f = new Fixture(NO_WARMUP);
        FakePlayer ana = f.join("Ana");
        FakePlayer bruno = f.join("Bruno");
        f.tpa.send(ana.handle, bruno.handle, RequestType.TPAHERE);
        f.tpa.accept(ana.handle, null);
        assertTrue(bruno.teleports.isEmpty());
        assertTrue(ana.teleports.isEmpty());
        assertEquals("Você não tem pedidos de teleporte pendentes.", ana.lastMessage());
    }

    @Test
    public void selfRequestIsRefused() {
        Fixture f = new Fixture(NO_WARMUP);
        FakePlayer ana = f.join("Ana");
        f.tpa.send(ana.handle, ana.handle, RequestType.TPA);
        assertEquals("Você não pode enviar pedido para si mesmo.", ana.lastMessage());
    }

    @Test
    public void sendingConsumesTheRequestCooldown() {
        Fixture f = new Fixture(NO_WARMUP);
        FakePlayer ana = f.join("Ana");
        FakePlayer bruno = f.join("Bruno");
        FakePlayer carla = f.join("Carla");
        f.tpa.send(ana.handle, bruno.handle, RequestType.TPA);
        f.tpa.send(ana.handle, carla.handle, RequestType.TPA);
        assertTrue(ana.lastMessage().startsWith("Aguarde"));
        assertFalse(carla.received("quer teleportar"));
    }

    @Test
    public void tpahereCooldownBypassOnlyCoversTpahere() {
        Fixture f = new Fixture(NO_WARMUP);
        FakePlayer ana = f.join("Ana");
        ana.permissions.add(Permissions.TPAHERE_BYPASS_COOLDOWN);
        FakePlayer bruno = f.join("Bruno");
        FakePlayer carla = f.join("Carla");
        f.tpa.send(ana.handle, bruno.handle, RequestType.TPAHERE);
        f.tpa.send(ana.handle, carla.handle, RequestType.TPAHERE);
        assertTrue(carla.received("quer que você teleporte"));
    }

    @Test
    public void blockedPermissionKeepsTheOriginalMessage() {
        Fixture f = new Fixture(NO_WARMUP);
        FakePlayer ana = f.join("Ana");
        ana.permissions.add("serpens.yoff.active");
        FakePlayer bruno = f.join("Bruno");
        f.tpa.send(ana.handle, bruno.handle, RequestType.TPA);
        assertEquals("Não foi dessa vez, tente amanhã. :/", ana.lastMessage());
        assertFalse(bruno.received("quer teleportar"));
    }

    @Test
    public void blockedWorldsApplyToBothSides() {
        Fixture f = new Fixture(NO_WARMUP, "tpa.blocked-worlds: [arena]");
        FakePlayer ana = f.join("Ana");
        FakePlayer bruno = f.join("Bruno");
        bruno.location = new Location(f.server.world("arena"), 0, 64, 0);
        f.tpa.send(ana.handle, bruno.handle, RequestType.TPA);
        assertEquals("Bruno está em um mundo onde pedidos de teleporte não funcionam.", ana.lastMessage());
    }

    @Test
    public void disconnectClearsRequestsAndNotifiesOnlyTheOtherSide() {
        Fixture f = new Fixture(NO_WARMUP);
        FakePlayer ana = f.join("Ana");
        FakePlayer bruno = f.join("Bruno");
        FakePlayer carla = f.join("Carla");
        f.tpa.send(ana.handle, bruno.handle, RequestType.TPA);
        f.teleports.cooldowns().clear(ana.uuid);
        int carlaMessages = carla.messages.size();

        f.quit(ana);
        assertTrue(bruno.received("O pedido de Ana foi cancelado porque ele saiu do servidor."));
        assertEquals(carlaMessages, carla.messages.size());
        assertEquals(0, f.tpa.size());
    }

    @Test
    public void replacedRequestKeepsOnlyTheNewDirection() {
        Fixture f = new Fixture(NO_WARMUP, "teleport.cooldown.seconds: 0");
        FakePlayer ana = f.join("Ana");
        FakePlayer bruno = f.join("Bruno");
        bruno.moveTo(30, 64, 30);
        ana.moveTo(-30, 64, -30);
        f.tpa.send(ana.handle, bruno.handle, RequestType.TPA);
        f.tpa.send(ana.handle, bruno.handle, RequestType.TPAHERE);
        assertEquals(1, f.tpa.size());
        f.tpa.accept(bruno.handle, null);
        assertTrue("vale o pedido mais novo (/tpahere)", ana.teleports.isEmpty());
        assertEquals(-30.0, bruno.location.getX(), 1e-9);
    }

    @Test
    public void cancelNotifiesTargets() {
        Fixture f = new Fixture(NO_WARMUP);
        FakePlayer ana = f.join("Ana");
        FakePlayer bruno = f.join("Bruno");
        f.tpa.send(ana.handle, bruno.handle, RequestType.TPA);
        f.tpa.cancelOutgoing(ana.handle);
        assertTrue(bruno.received("O pedido de teleporte de Ana foi cancelado."));
        assertTrue(ana.received("Você cancelou 1 pedido(s) pendente(s)."));
        f.tpa.cancelOutgoing(ana.handle);
        assertEquals("Você não tem pedidos enviados.", ana.lastMessage());
    }

    @Test
    public void acceptedRequestStillRespectsWarmupAndMovement() {
        Fixture f = new Fixture("safety.tpa: NONE");
        FakePlayer ana = f.join("Ana");
        FakePlayer bruno = f.join("Bruno");
        f.tpa.send(ana.handle, bruno.handle, RequestType.TPA);
        f.tpa.accept(bruno.handle, null);
        f.teleports.handleMove(ana.handle, ana.location.clone().add(3, 0, 0));
        f.seconds(10);
        assertTrue(ana.teleports.isEmpty());
        assertTrue(ana.received("você se moveu"));
    }
}
