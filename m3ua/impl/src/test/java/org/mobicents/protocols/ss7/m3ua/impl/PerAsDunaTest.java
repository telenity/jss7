package org.mobicents.protocols.ss7.m3ua.impl;

import org.junit.Before;
import org.junit.Test;
import org.mobicents.protocols.ss7.m3ua.message.MessageType;
import org.mobicents.protocols.ss7.m3ua.message.MessageClass;
import org.mobicents.protocols.ss7.m3ua.ExchangeType;
import org.mobicents.protocols.ss7.m3ua.Functionality;
import org.mobicents.protocols.ss7.m3ua.impl.message.ssnm.DestinationAvailableImpl;
import org.mobicents.protocols.ss7.m3ua.impl.message.ssnm.DestinationUnavailableImpl;
import org.mobicents.protocols.ss7.mtp.Mtp3PausePrimitive;
import org.mobicents.protocols.ss7.mtp.Mtp3ResumePrimitive;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * DUNA/DAVA are tracked per (DPC, AS): a DUNA only makes the DPC unreachable via the AS that sent it.
 */
public class PerAsDunaTest {
    private static final int DPC = 1003;
    private RecordingManagement management;
    private M3UARouteManagement routes;
    private AsImpl as1;
    private AsImpl as2;
    private SignalingNetworkManagementHandler handler1;
    private SignalingNetworkManagementHandler handler2;
    private final java.util.List<org.mobicents.protocols.ss7.m3ua.message.M3UAMessage> sent = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Before
    public void setUp() throws Exception {
        management = new RecordingManagement();
        routes = management.getRouteManagement();
        as1 = newAs("AS_1");
        as2 = newAs("AS_2");
    }

    /**
     * DUNA via AS1 and DAVA via AS2 on two SCTP threads: the user must get PAUSE/RESUME in the order they were
     * decided, or it stays paused while route selection uses AS2.
     */
    @Test
    public void pauseAndResumeReachTheUserInDecisionOrder() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_1");
        routes.addRoute(DPC, -1, -1, "AS_2");
        activateAll();
        handler2.handleDestinationUnavailable(duna(DPC));          // AS1 still reaches it: no PAUSE yet
        management.reset();
        final CountDownLatch pausing = new CountDownLatch(1);
        management.beforePause = () -> {                            // slow PAUSE enqueue
            pausing.countDown();
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
        };

        Thread sctp1 = new Thread(() -> handler1.handleDestinationUnavailable(duna(DPC)));
        sctp1.start();
        assertTrue(pausing.await(5, java.util.concurrent.TimeUnit.SECONDS));
        handler2.handleDestinationAvailable(dava(DPC));              // decided after the PAUSE
        sctp1.join();

        assertEquals(java.util.Arrays.asList("PAUSE", "RESUME"), management.events);
        assertSame(as2, routes.getAsForRoute(DPC, -1, -1, 0));
    }

    /** Routes come from config before the ASs go active, as in production. */
    private void activateAll() throws Exception {
        handler1 = activate(as1);
        handler2 = activate(as2);
    }

    @Test
    public void singleAsDpcKeepsTodaysBehaviour() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_1");
        activateAll();
        management.reset();

        handler1.handleDestinationUnavailable(duna(DPC));
        assertEquals(1, management.pauseCount);
        assertEquals(DPC, management.lastPause.getAffectedDpc());
        assertNull(routes.getAsForRoute(DPC, -1, -1, 0));

        // RFC 4666 1.3.2.5: a repeated DUNA (e.g. the answer to a DAUD) changes nothing, the user is not told again
        handler1.handleDestinationUnavailable(duna(DPC));
        assertEquals(1, management.pauseCount);

        handler1.handleDestinationAvailable(dava(DPC));
        assertEquals(1, management.resumeCount);
        assertSame(as1, routes.getAsForRoute(DPC, -1, -1, 0));

        handler1.handleDestinationUnavailable(duna(DPC));
        assertEquals("a DUNA after DAVA pauses again", 2, management.pauseCount);
    }

    @Test
    public void twoAsDpcPausesOnlyWhenNoAsReachesIt() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_1");
        routes.addRoute(DPC, -1, -1, "AS_2");
        activateAll();
        management.reset();

        handler1.handleDestinationUnavailable(duna(DPC));
        assertEquals(0, management.pauseCount);
        for (int sls = 0; sls < 256; sls++) {
            assertSame(as2, routes.getAsForRoute(DPC, -1, -1, sls));
        }

        handler2.handleDestinationUnavailable(duna(DPC));
        assertEquals(1, management.pauseCount);
        assertNull(routes.getAsForRoute(DPC, -1, -1, 0));

        handler1.handleDestinationAvailable(dava(DPC));
        assertEquals(1, management.resumeCount);
        for (int sls = 0; sls < 256; sls++) {
            assertSame(as1, routes.getAsForRoute(DPC, -1, -1, sls));
        }
    }

    @Test
    public void dunaFromAsNotInRouteIsIgnored() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_1");
        routes.addRoute(1002, -1, -1, "AS_2");
        activateAll();
        management.reset();

        handler2.handleDestinationUnavailable(duna(DPC));

        assertEquals(0, management.pauseCount);
        assertSame(as1, routes.getAsForRoute(DPC, -1, -1, 0));
    }

    @Test
    public void inactiveAsDropsItsDunaMarks() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_1");
        routes.addRoute(DPC, -1, -1, "AS_2");
        activateAll();
        handler1.handleDestinationUnavailable(duna(DPC));
        assertSame(as2, routes.getAsForRoute(DPC, -1, -1, 0));

        // AS1 goes PENDING, T(r) expires -> DOWN, then comes back ACTIVE
        as1.getPeerFSM().signal(TransitionState.AS_STATE_CHANGE_PENDING);
        new AsStatePenTimeout(as1, as1.getPeerFSM()).onEvent(null);
        assertEquals(AsState.DOWN.toString(), as1.getPeerFSM().getState().getName());
        as1.getPeerFSM().signal(TransitionState.AS_STATE_CHANGE_ACTIVE);

        boolean as1Selected = false;
        for (int sls = 0; sls < 256; sls++) {
            as1Selected |= routes.getAsForRoute(DPC, -1, -1, sls) == as1;
        }
        assertTrue("stale DUNA mark must not survive AS1 going down", as1Selected);
    }

    @Test
    public void otherAsGoingInactiveWhileDunaMarkedPauses() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_1");
        routes.addRoute(DPC, -1, -1, "AS_2");
        activateAll();
        handler1.handleDestinationUnavailable(duna(DPC));
        management.reset();

        as2.getPeerFSM().signal(TransitionState.AS_STATE_CHANGE_PENDING);
        new AsStatePenTimeout(as2, as2.getPeerFSM()).onEvent(null);

        assertEquals("AS1 is up but cannot reach the DPC", 1, management.pauseCount);
    }

    @Test
    public void removingTheLastUsablePathPauses() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_1");
        routes.addRoute(DPC, -1, -1, "AS_2");
        routes.addRoute(1002, -1, -1, "AS_1");
        routes.addRoute(1002, -1, -1, "AS_2");
        activateAll();
        management.reset();
        handler2.handleDestinationUnavailable(duna(DPC));   // AS1 still reaches DPC: no PAUSE
        assertEquals(0, management.pauseCount);

        routes.removeRoute(1002, -1, -1, "AS_1");          // AS2 still reaches 1002: no PAUSE
        assertEquals(0, management.pauseCount);

        routes.removeRoute(DPC, -1, -1, "AS_1");           // only AS2 left, and it sent DUNA
        assertEquals(java.util.Collections.singletonList(DPC), management.pausedDpcs);
        assertNull(routes.getAsForRoute(DPC, -1, -1, 0));
    }

    @Test
    public void routeRemovalClearsMarks() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_1");
        routes.addRoute(DPC, -1, -1, "AS_2");
        activateAll();
        handler1.handleDestinationUnavailable(duna(DPC));

        routes.removeRoute(DPC, -1, -1, "AS_1");
        routes.addRoute(DPC, -1, -1, "AS_1");

        boolean as1Selected = false;
        for (int sls = 0; sls < 256; sls++) {
            as1Selected |= routes.getAsForRoute(DPC, -1, -1, sls) == as1;
        }
        assertTrue(as1Selected);
    }

    @Test
    public void dunaDavaRaceWithRouteChangesStaysConsistent() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_2");
        activateAll();
        final int rounds = 2000;
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread admin = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < rounds; i++) {
                    routes.addRoute(DPC, -1, -1, "AS_1");
                    routes.removeRoute(DPC, -1, -1, "AS_1");
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        });
        Thread ssnm = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < rounds; i++) {
                    handler1.handleDestinationUnavailable(duna(DPC));
                    handler1.handleDestinationAvailable(dava(DPC));
                    handler1.handleDestinationUnavailable(duna(DPC));
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        });
        admin.start();
        ssnm.start();
        start.countDown();
        admin.join(30000);
        ssnm.join(30000);
        assertNull(failure.get());

        // AS2 never sent DUNA, so the DPC was never unreachable: no PAUSE may have leaked out of the race
        assertEquals(0, management.pauseCount);
        RouteRow row = management.routeRow(DPC);
        assertFalse("AS1 is no longer routed, its mark must be gone", row.getUnreachableVia().contains(as1));
        routes.addRoute(DPC, -1, -1, "AS_1");
        boolean as1Selected = false;
        for (int sls = 0; sls < 256; sls++) {
            as1Selected |= routes.getAsForRoute(DPC, -1, -1, sls) == as1;
        }
        assertTrue(as1Selected);
    }

    @Test
    public void maskedDunaAndDavaApplyToMatchingRouteDpcsOnly() throws Exception {
        routes.addRoute(1002, -1, -1, "AS_1");
        routes.addRoute(1003, -1, -1, "AS_1");
        routes.addRoute(1004, -1, -1, "AS_1");
        activateAll();
        management.reset();

        // mask 1: 1002/1003 share all bits but the lowest; 1004 does not
        handler1.handleDestinationUnavailable(duna(1002, (short) 1));
        assertEquals(java.util.Arrays.asList(1002, 1003), management.pausedDpcs);
        assertNull(routes.getAsForRoute(1003, -1, -1, 0));
        assertSame(as1, routes.getAsForRoute(1004, -1, -1, 0));

        DestinationAvailableImpl dava = new DestinationAvailableImpl();
        dava.setAffectedPointCodes(management.parameterFactory.createAffectedPointCode(new int[]{1003}, new short[]{1}));
        handler1.handleDestinationAvailable(dava);
        assertEquals(2, management.resumeCount);
        assertSame(as1, routes.getAsForRoute(1002, -1, -1, 0));
    }

    @Test
    public void dupuWithAMaskIsRejected() throws Exception {
        routes.addRoute(1002, -1, -1, "AS_1");
        routes.addRoute(1003, -1, -1, "AS_1");
        activateAll();

        // RFC 4666 3.4.5: DUPU carries one DPC without a mask; a mask (here 1) draws ERR(Invalid Parameter Value)
        handler1.handleDestinationUPUnavailable(dupu(1002, (short) 1));
        assertTrue(management.statusDpcs.isEmpty());

        handler1.handleDestinationUPUnavailable(dupu(1002, (short) 0));
        assertEquals(java.util.Collections.singletonList(1002), management.statusDpcs);
    }

    private org.mobicents.protocols.ss7.m3ua.impl.message.ssnm.DestinationUPUnavailableImpl dupu(int pc, short mask) {
        org.mobicents.protocols.ss7.m3ua.impl.message.ssnm.DestinationUPUnavailableImpl dupu =
                new org.mobicents.protocols.ss7.m3ua.impl.message.ssnm.DestinationUPUnavailableImpl();
        dupu.setAffectedPointCode(management.parameterFactory.createAffectedPointCode(new int[]{pc}, new short[]{mask}));
        dupu.setUserCause(management.parameterFactory.createUserCause(3, 0));
        return dupu;
    }

    @Test
    public void isolationMaskAffectsEveryDpcRoutedViaTheSender() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_1");
        routes.addRoute(1001, -1, -1, "AS_1");
        routes.addRoute(1002, -1, -1, "AS_2");
        activateAll();
        management.reset();

        // RFC 4666 3.4.1: a mask >= the PC width means the whole network appearance (SG isolation)
        handler1.handleDestinationUnavailable(duna(0, (short) 200));

        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(DPC, 1001)),
                new java.util.HashSet<>(management.pausedDpcs));
        assertSame("AS2 did not report isolation", as2, routes.getAsForRoute(1002, -1, -1, 0));
    }

    @Test
    public void dunaBeforeAspActiveAckIsKept() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_1");
        routes.addRoute(DPC, -1, -1, "AS_2");
        handler2 = activate(as2);
        AspImpl asp1 = upAsp(as1);
        handler1 = new SignalingNetworkManagementHandler(asp1.getAspFactory());

        asp1.getAspFactory().sendAspActive(as1);
        asp1.getLocalFSM().signal(TransitionState.ASP_ACTIVE_SENT);
        handler1.handleDestinationUnavailable(duna(DPC));      // SGP reports before the Ack (RFC 4666 4.5.1)
        java.lang.reflect.Field sent = AspFactoryImpl.class.getDeclaredField("aspactiveSentTime");
        sent.setAccessible(true);
        ((java.util.Map<Object, Long>) sent.get(asp1.getAspFactory())).put(as1, 0L);
        asp1.getAspFactory().sendAspActive(as1);               // T(ack) retry must not drop the mark
        asp1.getLocalFSM().signal(TransitionState.ASP_ACTIVE_ACK);
        as1.getPeerFSM().signal(TransitionState.AS_STATE_CHANGE_ACTIVE);

        for (int sls = 0; sls < 256; sls++) {
            assertSame(as2, routes.getAsForRoute(DPC, -1, -1, sls));
        }
    }

    @Test
    public void marksSurviveReactivationWithinTr() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_1");
        routes.addRoute(DPC, -1, -1, "AS_2");
        activateAll();
        handler1.handleDestinationUnavailable(duna(DPC));
        AspImpl asp1 = (AspImpl) as1.appServerProcs.get(0);

        // AS1 leaves ACTIVE and comes back before T(r) expires: the SG's destination state has not changed
        as1.getPeerFSM().signal(TransitionState.AS_STATE_CHANGE_PENDING);
        asp1.getLocalFSM().signal(TransitionState.ASP_INACTIVE_ACK);
        asp1.getAspFactory().sendAspActive(as1);
        asp1.getLocalFSM().signal(TransitionState.ASP_ACTIVE_SENT);
        asp1.getLocalFSM().signal(TransitionState.ASP_ACTIVE_ACK);
        as1.getPeerFSM().signal(TransitionState.AS_STATE_CHANGE_ACTIVE);

        assertTrue(management.routeRow(DPC).getUnreachableVia().contains(as1));
        for (int sls = 0; sls < 256; sls++) {
            assertSame(as2, routes.getAsForRoute(DPC, -1, -1, sls));
        }
    }

    /** AS2 comes up on the FSM thread while a DUNA via AS1 is queueing its PAUSE: RESUME must follow the PAUSE. */
    @Test
    public void asActivationAndDunaReachTheUserInDecisionOrder() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_1");
        routes.addRoute(DPC, -1, -1, "AS_2");
        handler1 = activate(as1);
        AspImpl asp2 = upAsp(as2);
        asp2.getLocalFSM().signal(TransitionState.ASP_ACTIVE_SENT);
        asp2.getLocalFSM().signal(TransitionState.ASP_ACTIVE_ACK);
        management.reset();
        final CountDownLatch pausing = new CountDownLatch(1);
        management.beforePause = () -> {                            // slow PAUSE enqueue
            pausing.countDown();
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
        };

        Thread sctp1 = new Thread(() -> handler1.handleDestinationUnavailable(duna(DPC)));
        sctp1.start();
        assertTrue(pausing.await(5, java.util.concurrent.TimeUnit.SECONDS));
        as2.getPeerFSM().signal(TransitionState.AS_STATE_CHANGE_ACTIVE);    // AS2 up, decided after the PAUSE
        sctp1.join();

        assertEquals(java.util.Arrays.asList("PAUSE", "RESUME"), management.events);
        assertSame(as2, routes.getAsForRoute(DPC, -1, -1, 0));
    }

    /** NTFY(AS-ACTIVE) before our Ack, then DUNA and DAVA, then the Ack: the user must end up resumed. */
    @Test
    public void davaBeforeTheAckResumesOnTheAck() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_1");
        AspImpl asp1 = upAsp(as1);
        asp1.getAspFactory().started = true;
        handler1 = new SignalingNetworkManagementHandler(asp1.getAspFactory());
        asp1.getLocalFSM().signal(TransitionState.ASP_ACTIVE_SENT);
        as1.getPeerFSM().signal(TransitionState.AS_STATE_CHANGE_ACTIVE);   // what the NTFY does
        handler1.handleDestinationUnavailable(duna(DPC));
        handler1.handleDestinationAvailable(dava(DPC));
        management.reset();

        asp1.getAspFactory().read(management.messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE,
                MessageType.ASP_ACTIVE_ACK));

        assertSame(as1, routes.getAsForRoute(DPC, -1, -1, 0));
        assertEquals(1, management.resumeCount);
    }

    @Test
    public void activationDoesNotResumeADpcReportedUnavailableBeforeTheAck() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_1");
        AspImpl asp1 = upAsp(as1);
        handler1 = new SignalingNetworkManagementHandler(asp1.getAspFactory());
        management.reset();

        asp1.getAspFactory().sendAspActive(as1);
        asp1.getLocalFSM().signal(TransitionState.ASP_ACTIVE_SENT);
        handler1.handleDestinationUnavailable(duna(DPC));
        asp1.getLocalFSM().signal(TransitionState.ASP_ACTIVE_ACK);
        as1.getPeerFSM().signal(TransitionState.AS_STATE_CHANGE_ACTIVE);

        assertEquals(1, management.pauseCount);
        assertEquals("route selection cannot use AS1, so the user must stay paused", 0, management.resumeCount);
        assertNull(routes.getAsForRoute(DPC, -1, -1, 0));

        handler1.handleDestinationAvailable(dava(DPC));
        assertEquals(1, management.resumeCount);
        assertSame(as1, routes.getAsForRoute(DPC, -1, -1, 0));
    }

    @Test
    public void dpcPausedByDunaResumesWhenAnotherAsComesUp() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_1");
        routes.addRoute(DPC, -1, -1, "AS_2");
        handler1 = activate(as1);
        management.reset();

        handler1.handleDestinationUnavailable(duna(DPC));
        assertEquals(1, management.pauseCount);

        handler2 = activate(as2);
        assertEquals("AS2 reaches the DPC, the user must be resumed", 1, management.resumeCount);
        assertSame(as2, routes.getAsForRoute(DPC, -1, -1, 0));
    }

    /** An AS the SG reports active, but with no active ASP of ours, cannot carry the DPC: it must not hold off PAUSE. */
    @Test
    public void asWithoutActiveAspDoesNotHoldOffThePause() throws Exception {
        routes.addRoute(DPC, -1, -1, "AS_1");
        routes.addRoute(DPC, -1, -1, "AS_2");
        handler1 = activate(as1);
        upAsp(as2);
        as2.getPeerFSM().signal(TransitionState.AS_STATE_CHANGE_ACTIVE);   // NTFY only, no ASP Active of ours
        management.reset();

        as1.getPeerFSM().signal(TransitionState.AS_STATE_CHANGE_PENDING);
        new AsStatePenTimeout(as1, as1.getPeerFSM()).onEvent(null);

        assertEquals(1, management.pauseCount);
    }

    /** RFC 4666 4.5.3: the periodic audit asks the AS that sent DUNA about the DPCs it still marks, until DAVA. */
    @Test
    public void auditSendsDaudForDpcsStillMarkedUnavailable() throws Exception {
        routes.addRoute(1002, -1, -1, "AS_1");
        routes.addRoute(1003, -1, -1, "AS_1");
        routes.addRoute(1003, -1, -1, "AS_2");
        activateAll();
        handler1.handleDestinationUnavailable(duna(1002));
        handler1.handleDestinationUnavailable(duna(1003));
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(1002, 1003)), auditedPcs());

        handler1.handleDestinationAvailable(dava(1002));
        assertEquals(java.util.Collections.singleton(1003), auditedPcs());

        handler1.handleDestinationAvailable(dava(1003));
        assertEquals(java.util.Collections.emptySet(), auditedPcs());
    }

    private java.util.Set<Integer> auditedPcs() {
        sent.clear();
        routes.auditUnavailableDestinations();
        java.util.Set<Integer> pcs = new java.util.HashSet<>();
        for (org.mobicents.protocols.ss7.m3ua.message.M3UAMessage m : sent) {
            org.mobicents.protocols.ss7.m3ua.message.ssnm.DestinationStateAudit daud =
                    (org.mobicents.protocols.ss7.m3ua.message.ssnm.DestinationStateAudit) m;
            assertEquals(as1.getRoutingContext(), daud.getRoutingContexts());
            for (int pc : daud.getAffectedPointCodes().getPointCodes()) {
                pcs.add(pc);
            }
        }
        assertTrue(sent.size() <= 1);  // one DAUD per AS
        return pcs;
    }

    private AsImpl newAs(String name) {
        AsImpl as = new AsImpl(name, null, null, 1, Functionality.AS, ExchangeType.DE, null, null);
        as.setM3UAManagement(management);
        management.appServers.add(as);
        return as;
    }

    private SignalingNetworkManagementHandler activate(AsImpl as) throws Exception {
        AspImpl asp = upAsp(as);
        asp.getLocalFSM().signal(TransitionState.ASP_ACTIVE_SENT);
        asp.getLocalFSM().signal(TransitionState.ASP_ACTIVE_ACK);
        as.getPeerFSM().signal(TransitionState.AS_STATE_CHANGE_ACTIVE);
        return new SignalingNetworkManagementHandler(asp.getAspFactory());
    }

    private AspImpl upAsp(AsImpl as) throws Exception {
        AspFactoryImpl factory = new AspFactoryImpl() {
            @Override
            protected void write(org.mobicents.protocols.ss7.m3ua.message.M3UAMessage message) {
                sent.add(message);
            }
        };
        factory.setFunctionality(Functionality.AS);
        factory.setExchangeType(ExchangeType.DE);
        factory.setM3UAManagement(management);
        AspImpl asp = factory.createAsp();
        asp.setAs(as);
        as.appServerProcs.add(asp);
        asp.getLocalFSM().signal(TransitionState.COMM_UP);
        return asp;
    }

    private DestinationUnavailableImpl duna(int pc) {
        return duna(pc, (short) 0);
    }

    private DestinationUnavailableImpl duna(int pc, short mask) {
        DestinationUnavailableImpl duna = new DestinationUnavailableImpl();
        duna.setAffectedPointCodes(management.parameterFactory.createAffectedPointCode(new int[]{pc}, new short[]{mask}));
        return duna;
    }

    private DestinationAvailableImpl dava(int pc) {
        DestinationAvailableImpl dava = new DestinationAvailableImpl();
        dava.setAffectedPointCodes(management.parameterFactory.createAffectedPointCode(new int[]{pc}, new short[]{0}));
        return dava;
    }

    private static class RecordingManagement extends M3UAManagementImpl {
        private volatile int pauseCount;
        private volatile int resumeCount;
        private Mtp3PausePrimitive lastPause;
        private final java.util.List<Integer> pausedDpcs = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final java.util.List<Integer> statusDpcs = new java.util.concurrent.CopyOnWriteArrayList<>();
        /** PAUSE/RESUME in the order they reach the user's queue. */
        private final java.util.List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        private volatile Runnable beforePause = () -> { };

        RecordingManagement() {
            super("per-as-duna-test");
        }

        void reset() {
            pauseCount = 0;
            resumeCount = 0;
            pausedDpcs.clear();
            events.clear();
        }

        RouteRow routeRow(int dpc) throws Exception {
            java.lang.reflect.Field f = M3UARouteManagement.class.getDeclaredField("routeTable");
            f.setAccessible(true);
            return ((java.util.Map<Integer, RouteRow>) f.get(getRouteManagement())).get(dpc);
        }

        @Override
        public void sendPauseMessageToLocalUser(Mtp3PausePrimitive message) {
            beforePause.run();
            events.add("PAUSE");
            pauseCount++;
            lastPause = message;
            pausedDpcs.add(message.getAffectedDpc());
        }

        @Override
        public void sendStatusMessageToLocalUser(org.mobicents.protocols.ss7.mtp.Mtp3StatusPrimitive message) {
            statusDpcs.add(message.getAffectedDpc());
        }

        @Override
        public void sendResumeMessageToLocalUser(Mtp3ResumePrimitive message) {
            events.add("RESUME");
            resumeCount++;
        }
    }
}
