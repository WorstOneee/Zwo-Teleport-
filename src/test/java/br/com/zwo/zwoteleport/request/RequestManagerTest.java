package br.com.zwo.zwoteleport.request;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RequestManagerTest {

    private static final long TTL = 60_000L;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private RequestManager manager;

    @Before
    public void setUp() {
        manager = new RequestManager();
    }

    private RequestManager.CreateResult send(UUID from, UUID to, RequestType type, long now, boolean replace) {
        return manager.create(from, name(from), to, name(to), type, now, TTL, false, replace);
    }

    private String name(UUID id) {
        if (id.equals(alice)) {
            return "Alice";
        }
        return id.equals(bob) ? "Bob" : "Carol";
    }

    @Test
    public void tpaMovesRequesterToTargetAndTpahereMovesTargetToRequester() {
        TeleportRequest tpa = send(alice, bob, RequestType.TPA, 0, true).request();
        assertEquals(alice, tpa.mover());
        assertEquals(bob, tpa.anchor());

        TeleportRequest here = send(carol, bob, RequestType.TPAHERE, 0, true).request();
        assertEquals(bob, here.mover());
        assertEquals(carol, here.anchor());
    }

    @Test
    public void selfRequestIsRejectedUnlessAllowed() {
        assertEquals(RequestManager.CreateStatus.SELF_REQUEST, send(alice, alice, RequestType.TPA, 0, true).status());
        RequestManager.CreateResult allowed = manager.create(alice, "Alice", alice, "Alice", RequestType.TPA, 0, TTL, true, true);
        assertEquals(RequestManager.CreateStatus.CREATED, allowed.status());
    }

    @Test
    public void acceptingConsumesTheRequestExactlyOnce() {
        send(alice, bob, RequestType.TPA, 0, true);
        Optional<TeleportRequest> first = manager.take(bob, null, 1_000);
        Optional<TeleportRequest> second = manager.take(bob, null, 1_000);
        assertTrue(first.isPresent());
        assertFalse(second.isPresent());
        assertEquals(0, manager.size());
    }

    @Test
    public void onlyTheTargetCanTakeTheRequest() {
        send(alice, bob, RequestType.TPA, 0, true);
        assertFalse("quem pediu não pode aceitar o próprio pedido", manager.take(alice, null, 0).isPresent());
        assertTrue(manager.take(bob, null, 0).isPresent());
    }

    @Test
    public void replacementPolicyIsRespected() {
        send(alice, bob, RequestType.TPA, 0, true);
        RequestManager.CreateResult blocked = send(alice, bob, RequestType.TPAHERE, 10, false);
        assertEquals(RequestManager.CreateStatus.ALREADY_PENDING, blocked.status());
        assertEquals(RequestType.TPA, manager.peek(bob, alice, 10).orElseThrow().type());

        RequestManager.CreateResult replaced = send(alice, bob, RequestType.TPAHERE, 20, true);
        assertEquals(RequestManager.CreateStatus.REPLACED, replaced.status());
        assertNotNull(replaced.previous());
        assertEquals(1, manager.size());
        assertEquals(RequestType.TPAHERE, manager.peek(bob, alice, 20).orElseThrow().type());
    }

    @Test
    public void expiredRequestIsNotAcceptedAndIsReportedOnce() {
        send(alice, bob, RequestType.TPA, 0, true);
        assertFalse(manager.take(bob, null, TTL).isPresent());
        List<TeleportRequest> expired = manager.expire(TTL);
        assertEquals(1, expired.size());
        assertTrue(manager.expire(TTL + 1).isEmpty());
    }

    @Test
    public void expiredRequestDoesNotBlockANewOne() {
        send(alice, bob, RequestType.TPA, 0, false);
        RequestManager.CreateResult again = send(alice, bob, RequestType.TPA, TTL + 1, false);
        assertEquals(RequestManager.CreateStatus.CREATED, again.status());
    }

    @Test
    public void takeWithoutNamePicksTheMostRecent() {
        send(alice, bob, RequestType.TPA, 0, true);
        send(carol, bob, RequestType.TPA, 5, true);
        assertEquals(carol, manager.take(bob, null, 10).orElseThrow().requester());
        assertEquals(alice, manager.take(bob, null, 10).orElseThrow().requester());
    }

    @Test
    public void takeByRequesterSelectsThatOne() {
        send(alice, bob, RequestType.TPA, 0, true);
        send(carol, bob, RequestType.TPA, 5, true);
        UUID found = manager.findRequesterByName(bob, "alice", 10).orElseThrow();
        assertEquals(alice, manager.take(bob, found, 10).orElseThrow().requester());
        assertEquals(1, manager.size());
    }

    @Test
    public void simultaneousCrossRequestsAreIndependent() {
        send(alice, bob, RequestType.TPA, 0, true);
        send(bob, alice, RequestType.TPA, 0, true);
        assertEquals(2, manager.size());
        assertEquals(alice, manager.take(bob, null, 1).orElseThrow().requester());
        assertEquals(bob, manager.take(alice, null, 1).orElseThrow().requester());
    }

    @Test
    public void denyAfterAcceptFindsNothing() {
        send(alice, bob, RequestType.TPA, 0, true);
        assertTrue(manager.take(bob, alice, 1).isPresent());
        assertFalse("negar depois de aceitar não pode encontrar o mesmo pedido", manager.take(bob, alice, 1).isPresent());
    }

    @Test
    public void disconnectRemovesEveryRequestInvolvingThePlayer() {
        send(alice, bob, RequestType.TPA, 0, true);
        send(bob, carol, RequestType.TPAHERE, 0, true);
        send(carol, alice, RequestType.TPA, 0, true);
        List<TeleportRequest> removed = manager.removeInvolving(bob);
        assertEquals(2, removed.size());
        assertEquals(1, manager.size());
        assertTrue(manager.peek(alice, carol, 0).isPresent());
    }

    @Test
    public void cancelOutgoingOnlyAffectsTheRequester() {
        send(alice, bob, RequestType.TPA, 0, true);
        send(alice, carol, RequestType.TPA, 0, true);
        send(carol, bob, RequestType.TPA, 0, true);
        assertEquals(2, manager.cancelOutgoing(alice).size());
        assertEquals(1, manager.size());
        assertTrue(manager.cancelOutgoing(alice).isEmpty());
    }

    @Test
    public void incomingAndOutgoingSkipExpired() {
        send(alice, bob, RequestType.TPA, 0, true);
        assertEquals(1, manager.incoming(bob, 1).size());
        assertEquals(1, manager.outgoing(alice, 1).size());
        assertTrue(manager.incoming(bob, TTL).isEmpty());
        assertTrue(manager.outgoing(alice, TTL).isEmpty());
    }
}
