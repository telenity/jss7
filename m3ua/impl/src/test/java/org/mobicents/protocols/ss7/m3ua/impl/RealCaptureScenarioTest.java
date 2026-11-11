package org.mobicents.protocols.ss7.m3ua.impl;

import org.junit.Before;
import org.junit.Test;
import org.mobicents.protocols.ss7.m3ua.ExchangeType;
import org.mobicents.protocols.ss7.m3ua.Functionality;
import org.mobicents.protocols.ss7.m3ua.impl.message.mgmt.NotifyImpl;
import org.mobicents.protocols.ss7.m3ua.impl.message.ssnm.DestinationAvailableImpl;
import org.mobicents.protocols.ss7.m3ua.impl.message.ssnm.DestinationStateAuditImpl;
import org.mobicents.protocols.ss7.m3ua.impl.message.ssnm.DestinationUnavailableImpl;
import org.mobicents.protocols.ss7.m3ua.message.M3UAMessage;
import org.mobicents.protocols.ss7.m3ua.message.MessageClass;
import org.mobicents.protocols.ss7.m3ua.message.MessageType;
import org.mobicents.protocols.ss7.m3ua.parameter.AffectedPointCode;
import org.mobicents.protocols.ss7.m3ua.parameter.ErrorCode;
import org.mobicents.protocols.ss7.m3ua.parameter.Status;
import org.mobicents.protocols.ss7.mtp.Mtp3PausePrimitive;
import org.mobicents.protocols.ss7.mtp.Mtp3ResumePrimitive;
import org.mobicents.protocols.ss7.mtp.Mtp3StatusPrimitive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Field flows from SGW/STP captures (each test gives the capture date and message sequence), replayed against an
 * ASP-side jSS7 (Functionality.AS, SE). SG messages go through the real handlers ({@link AspFactoryImpl#read});
 * jSS7 sends its own ASP Active on ASPUP_ACK; ASP Down is done as {@code AspFactoryImpl.stop()} does. Point codes
 * are the ones in the captures. Tests in the last group are not from a capture; they use the same harness.
 */
public class RealCaptureScenarioTest {

    private Recording mgmt;
    private M3UARouteManagement routes;

    @Before
    public void setUp() {
        mgmt = new Recording();
        routes = mgmt.getRouteManagement();
    }

    /** Capture taken 09-10-2026: ASPUP_ACK + NTFY(Insufficient ASP), then ASPAC_ACK, no AS-ACTIVE NTFY. */
    @Test
    public void activatesOnTheAckNotOnInsufficientAspNtfy() throws Exception {
        AsImpl as = newAs("AS_1", 1001);
        AspImpl asp = connect(as);

        sg(asp, msg(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK));
        sg(asp, ntfy(Status.STATUS_Other, Status.INFO_Insufficient_ASP_Resources_Active));
        assertEquals(AsState.DOWN, peerState(as));
        assertEquals(AspState.ACTIVE_SENT, localState(asp));
        assertTrue(mgmt.resumed.isEmpty());
        assertNull("nothing routed before our ASP is active", routes.getAsForRoute(1001, -1, -1, 0));

        sg(asp, msg(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK));
        assertEquals(AsState.ACTIVE, peerState(as));
        assertEquals(Collections.singletonList(1001), mgmt.resumed);
        assertSame(as, routes.getAsForRoute(1001, -1, -1, 0));
    }

    /** Captures taken 09-10-2026 and 19-12-2024: ASPUP_ACK + NTFY(AS-INACTIVE), ASPAC_ACK + NTFY(AS-ACTIVE) in one packet. */
    @Test
    public void ackAndAsActiveNtfyResumeOnce() throws Exception {
        AsImpl as = newAs("AS_1", 1001);
        AspImpl asp = connect(as);

        sg(asp, msg(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK));
        sg(asp, ntfy(Status.STATUS_AS_State_Change, Status.INFO_AS_INACTIVE));
        assertEquals(AsState.INACTIVE, peerState(as));
        sg(asp, msg(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK));
        sg(asp, ntfy(Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));

        assertEquals(AsState.ACTIVE, peerState(as));
        assertEquals(Collections.singletonList(1001), mgmt.resumed);
    }

    /** Capture taken 09-10-2026, second instance: DUNA(1009) 12 s after the other instance's ASPDN, DAVA(1009) 55 s later. */
    @Test
    public void otherInstanceGetsDunaThenDava() throws Exception {
        AsImpl as = newAs("AS_2", 1009);
        AspImpl asp = activate(as);
        mgmt.clear();

        sg(asp, duna(1009));
        assertEquals(Collections.singletonList(1009), mgmt.paused);
        assertNull(routes.getAsForRoute(1009, -1, -1, 0));

        sg(asp, dava(1009));
        assertEquals(Collections.singletonList(1009), mgmt.resumed);
        assertSame(as, routes.getAsForRoute(1009, -1, -1, 0));
    }

    /**
     * Captures taken 14-11-2025, 13/15-08-2025 and 06-10-2026: no NTFY. The SG audits our own PC (1011) with DAUD
     * between ASPAC and its Ack, then sends DUNA(1011). Two of the 14-11-2025 captures end with our ASP Down.
     */
    @Test
    public void noNtfyAndPeerAuditOfOurOwnPc() throws Exception {
        AsImpl as = newAs("AS_1", 1012);
        AspImpl asp = connect(as);

        sg(asp, msg(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK));
        sg(asp, daud(1011));
        sg(asp, msg(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK));
        sg(asp, duna(1011));

        assertEquals("activated by the Ack, no NTFY needed", AsState.ACTIVE, peerState(as));
        assertEquals(Collections.singletonList(1012), mgmt.resumed);
        assertTrue("DUNA for our own (unrouted) PC is ignored", mgmt.paused.isEmpty());
        assertSame(as, routes.getAsForRoute(1012, -1, -1, 0));

        // our ASP Down, ASPDN_ACK, T(r) expiry
        aspDown(asp);
        asp.getLocalFSM().signal(TransitionState.ASP_DOWN_ACK);   // handleAspDownAck, minus stopping the association
        assertEquals(AspState.DOWN, localState(asp));
        as.getPeerFSM().tick(System.currentTimeMillis() + 3000);
        assertEquals(Collections.singletonList(1012), mgmt.paused);
        assertNull(routes.getAsForRoute(1012, -1, -1, 0));
    }

    /** Capture taken 19-12-2024: DUNA(1010) from both SGs 33 ms apart, DAVA(1010) from both ~30 s later. */
    @Test
    public void dunaFromBothSgsThenDava() throws Exception {
        AsImpl viaSg1 = newAs("AS_SG1", 1010);
        AsImpl viaSg2 = newAs("AS_SG2", 1010);
        AspImpl sg1 = activate(viaSg1);
        AspImpl sg2 = activate(viaSg2);
        mgmt.clear();

        sg(sg1, duna(1010));
        assertTrue("still reachable via SG2", mgmt.paused.isEmpty());
        assertSame(viaSg2, routes.getAsForRoute(1010, -1, -1, 0));
        sg(sg2, duna(1010));
        assertEquals(Collections.singletonList(1010), mgmt.paused);
        assertNull(routes.getAsForRoute(1010, -1, -1, 0));

        sg(sg1, dava(1010));
        assertEquals(Collections.singletonList(1010), mgmt.resumed);
        assertSame(viaSg1, routes.getAsForRoute(1010, -1, -1, 0));
        sg(sg2, dava(1010));
        assertNotNull(routes.getAsForRoute(1010, -1, -1, 1));
    }

    /**
     * Capture taken 19-12-2024: our ASP goes inactive (here via an unsolicited ASPIA_ACK; jSS7 never sends
     * ASPIA), NTFY(AS-PENDING), NTFY(AS-INACTIVE) 2 s later; T(r) takes the AS out; ASPAC_ACK + NTFY(AS-ACTIVE)
     * bring it back.
     */
    @Test
    public void pendingThenInactiveThenReactivation() throws Exception {
        AsImpl as = newAs("AS_1", 1010);
        AspImpl asp = activate(as);
        mgmt.clear();

        sg(asp, msg(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_INACTIVE_ACK));
        assertEquals(AspState.INACTIVE, localState(asp));
        sg(asp, ntfy(Status.STATUS_AS_State_Change, Status.INFO_AS_PENDING));
        assertEquals(AsState.PENDING, peerState(as));
        assertEquals("entering PENDING, jSS7 re-sends ASP Active for its INACTIVE ASP",
                AspState.ACTIVE_SENT, localState(asp));
        sg(asp, ntfy(Status.STATUS_AS_State_Change, Status.INFO_AS_INACTIVE));
        assertEquals("NTFY(AS-INACTIVE) does not cut T(r) short", AsState.PENDING, peerState(as));
        assertTrue(mgmt.paused.isEmpty());

        as.getPeerFSM().tick(System.currentTimeMillis() + 3000);
        assertEquals(Collections.singletonList(1010), mgmt.paused);

        sg(asp, msg(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK));
        sg(asp, ntfy(Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));
        assertEquals(Collections.singletonList(1010), mgmt.resumed);
        assertSame(as, routes.getAsForRoute(1010, -1, -1, 0));
    }

    /**
     * Captures taken 06-05-2025: routes 1001->AS_1, 1002->AS_2, 1003->AS_3; the 1003
     * association never comes up. STPs 1001 and 1002 broadcast DAVA(1005, 1006, 1003, 1004).
     */
    @Test
    public void davaBroadcastForAnotherAsDpcIsIgnored() throws Exception {
        AsImpl as1 = newAs("AS_1", 1001);
        AsImpl as2 = newAs("AS_2", 1002);
        newAs("AS_3", 1003);
        AspImpl stp1 = activate(as1);
        AspImpl stp2 = activate(as2);
        mgmt.clear();

        sg(stp1, dava(1005, 1003));
        sg(stp2, dava(1005, 1006, 1003, 1004));

        assertTrue("1003 is only routed via AS_3, which is down", mgmt.resumed.isEmpty());
        assertNull(routes.getAsForRoute(1003, -1, -1, 0));
        assertSame(as1, routes.getAsForRoute(1001, -1, -1, 0));
        assertSame(as2, routes.getAsForRoute(1002, -1, -1, 0));
    }

    /**
     * Captures taken 07-12-2021 and 26-07-2024: the AS is already active via other ASPs, so the SG reports AS-ACTIVE with the
     * ASPUP_ACK, before our ASP Active (RFC 4666 4.3.4.5). Route selection waits for our ASPAC_ACK.
     */
    @Test
    public void asActiveNtfyBeforeOurAspActive() throws Exception {
        AsImpl as = newAs("AS_SMSC", 1008);
        AspImpl asp = connect(as);

        sg(asp, msg(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK));
        sg(asp, ntfy(Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));
        assertEquals(AsState.ACTIVE, peerState(as));
        assertEquals(AspState.ACTIVE_SENT, localState(asp));
        assertNull("our ASP is not active yet", routes.getAsForRoute(1008, -1, -1, 0));
        assertTrue("RESUME waits for our ASPAC_ACK too", mgmt.resumed.isEmpty());

        sg(asp, msg(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK));
        assertSame(as, routes.getAsForRoute(1008, -1, -1, 0));
        assertEquals(Collections.singletonList(1008), mgmt.resumed);
    }

    /** Capture taken 26-07-2024: ASPUP_ACK + NTFY(AS-PENDING) [+ NTFY(AS-ACTIVE)] before our ASP Active. */
    @Test
    public void asPendingNtfyAfterAspUpIsIgnored() throws Exception {
        AsImpl as = newAs("AS_MSC", 1013);
        AspImpl asp = connect(as);

        sg(asp, msg(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK));
        as.getPeerFSM().signal(TransitionState.AS_STATE_CHANGE_PENDING);   // what the NTFY does; threw before
        assertEquals(AsState.DOWN, peerState(as));
        sg(asp, ntfy(Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));
        sg(asp, msg(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK));

        as.getPeerFSM().tick(System.currentTimeMillis() + 3000);           // no T(r) was started
        assertEquals(AsState.ACTIVE, peerState(as));
        assertSame(as, routes.getAsForRoute(1013, -1, -1, 0));
        assertTrue(mgmt.paused.isEmpty());
    }

    /**
     * Capture taken 07-12-2021: ~4 s after activation every ASP gets DAVA for 3 PCs with no DUNA before. RESUME goes to
     * the user only when the DPC status changes (RFC 4666 1.3.2.5).
     */
    @Test
    public void unsolicitedDavaResumesOnlyOnChange() throws Exception {
        AsImpl viaStp1 = newAs("AS_STP1", 1008);
        AsImpl viaStp2 = newAs("AS_STP2", 1008);
        AspImpl stp1 = activate(viaStp1);
        AspImpl stp2 = activate(viaStp2);
        assertEquals(Collections.singletonList(1008), mgmt.resumed);
        mgmt.clear();

        sg(stp1, dava(1008));
        sg(stp2, dava(1008));
        assertTrue("already resumed at activation", mgmt.resumed.isEmpty());

        sg(stp1, duna(1008));
        sg(stp2, duna(1008));
        assertEquals(Collections.singletonList(1008), mgmt.paused);
        sg(stp1, dava(1008));
        sg(stp2, dava(1008));
        assertEquals("one RESUME for the change", Collections.singletonList(1008), mgmt.resumed);
    }

    /**
     * Captures taken 21-10-2020 and 23-10-2020 (PC 1007): DUNA(1007), DAVA 7 s later; then the
     * operator blocks our ASP at the SG: unsolicited ASPIA_ACK, 0.8 s later NTFY(AS-PENDING) + NTFY(AS-INACTIVE) in
     * one packet, and our re-sent ASP Active draws ERR "Refused - Management Blocking" (RFC 4666 3.8.1).
     */
    @Test
    public void managementBlockingAtTheSg() throws Exception {
        AsImpl as = newAs("AS_SMSC", 1007);
        AspImpl asp = activate(as);
        mgmt.clear();
        sg(asp, duna(1007));
        sg(asp, dava(1007));
        assertEquals(Collections.singletonList(1007), mgmt.paused);
        assertEquals(Collections.singletonList(1007), mgmt.resumed);
        mgmt.clear();

        sg(asp, msg(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_INACTIVE_ACK));
        assertNull("blocked ASP is not selected", routes.getAsForRoute(1007, -1, -1, 0));
        sg(asp, ntfy(Status.STATUS_AS_State_Change, Status.INFO_AS_PENDING));
        sg(asp, ntfy(Status.STATUS_AS_State_Change, Status.INFO_AS_INACTIVE));
        assertEquals("jSS7 re-sends ASP Active", AspState.ACTIVE_SENT, localState(asp));
        sg(asp, err(ErrorCode.Refused_Management_Blocking));
        assertEquals(AspState.ACTIVE_SENT, localState(asp));
        assertNull(routes.getAsForRoute(1007, -1, -1, 0));
        assertTrue(mgmt.paused.isEmpty());

        as.getPeerFSM().tick(System.currentTimeMillis() + 3000);   // T(r)
        assertEquals(Collections.singletonList(1007), mgmt.paused);

        sg(asp, msg(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK));   // block lifted
        assertSame(as, routes.getAsForRoute(1007, -1, -1, 0));
        assertEquals(Collections.singletonList(1007), mgmt.resumed);
    }

    /**
     * Captures taken 02-12-2019: this SG re-reports destination state on
     * activation: ASPAC_ACK + NTFY(AS-ACTIVE) + DUNA for 8 PCs in one packet. Only routed DPCs are affected; our
     * routes there are 1014/1015, 1016 stands in for a routed PC in the list.
     */
    @Test
    public void dunaListWithTheAck() throws Exception {
        AsImpl as = newAs("AS_1", 1014);
        routes.addRoute(1016, -1, -1, "AS_1");
        AspImpl asp = connect(as);

        sg(asp, msg(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK));
        sg(asp, ntfy(Status.STATUS_AS_State_Change, Status.INFO_AS_INACTIVE));
        sg(asp, msg(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK));
        sg(asp, ntfy(Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));
        sg(asp, duna(1016, 1017, 1018, 1019, 1020, 1021, 1022, 1023));

        assertEquals(Collections.singletonList(1016), mgmt.paused);
        assertNull(routes.getAsForRoute(1016, -1, -1, 0));
        assertSame(as, routes.getAsForRoute(1014, -1, -1, 0));
    }

    // ---------------------------------------------------------------- not from a capture
    // Same harness, but the flows below were derived from the RFC or from the code, not seen in a capture.

    /**
     * The SG forces our ASP inactive (unsolicited ASPIA_ACK, RFC 4666 4.3.4.4) while the AS stays active via
     * another node's ASP, so no NTFY follows. Our AS must no longer be selected.
     */
    @Test
    public void sgForcesAspInactiveWhileAsActiveElsewhere() throws Exception {
        AsImpl as = newAs("AS_SHARED", 1001);
        AspImpl asp = activate(as);

        sg(asp, msg(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_INACTIVE_ACK));

        assertEquals(AspState.INACTIVE, localState(asp));
        assertNull(routes.getAsForRoute(1001, -1, -1, 0));

        // our ASP Active again (AspFactoryImpl.sendAspActive), the SG acks it
        asp.getLocalFSM().signal(TransitionState.ASP_ACTIVE_SENT);
        sg(asp, msg(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK));
        assertSame(as, routes.getAsForRoute(1001, -1, -1, 0));
    }

    /**
     * The AS flaps within T(r) (PENDING -> ACTIVE): the SG's destination state did not change, so a DUNA-paused DPC
     * stays paused; a DAVA during the re-activation resumes it when the AS returns.
     */
    @Test
    public void dunaMarkSurvivesAFlapAndDavaResumesOnReturn() throws Exception {
        AsImpl as = newAs("AS_FLAP", 1003);
        AspImpl asp = activate(as);
        sg(asp, duna(1003));
        mgmt.clear();

        aspDown(asp);                                   // AS -> PENDING, T(r) running
        asp.getLocalFSM().signal(TransitionState.ASP_DOWN_ACK);
        asp.getAspFactory().started = true;
        asp.getLocalFSM().signal(TransitionState.COMM_UP);
        sg(asp, msg(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK));   // jSS7 sends ASP Active
        sg(asp, dava(1003));                            // before our Ack: no ACTIVE ASP yet, no RESUME
        assertTrue(mgmt.resumed.isEmpty());
        sg(asp, msg(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK));

        assertEquals(AsState.ACTIVE, peerState(as));
        assertSame(as, routes.getAsForRoute(1003, -1, -1, 0));
        assertEquals(Collections.singletonList(1003), mgmt.resumed);
    }

    /** The AS flaps within T(r) and no DAVA comes: the DUNA-paused DPC stays paused and unrouted. */
    @Test
    public void dunaMarkSurvivesAFlapWithinTr() throws Exception {
        AsImpl as = newAs("AS_FLAP", 1003);
        AspImpl asp = activate(as);
        sg(asp, duna(1003));
        mgmt.clear();

        aspDown(asp);
        asp.getLocalFSM().signal(TransitionState.ASP_DOWN_ACK);
        asp.getAspFactory().started = true;
        asp.getLocalFSM().signal(TransitionState.COMM_UP);
        sg(asp, msg(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK));
        sg(asp, msg(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK));

        assertEquals(AsState.ACTIVE, peerState(as));
        assertNull(routes.getAsForRoute(1003, -1, -1, 0));
        assertTrue(mgmt.resumed.isEmpty());
    }

    /** A route added at runtime (planned UI) to an AS that is already up is resumed. */
    @Test
    public void routeAddedWhileAsActiveIsResumed() throws Exception {
        AsImpl as = newAs("AS_UI", 1001);
        activate(as);
        mgmt.clear();

        routes.addRoute(1002, -1, -1, "AS_UI");

        assertEquals(Collections.singletonList(1002), mgmt.resumed);
        assertSame(as, routes.getAsForRoute(1002, -1, -1, 0));
    }

    /**
     * AS1 goes PENDING -> ACTIVE (NTFY) while AS2 reports DUNA for their shared DPC inside that transition, before AS1
     * is visibly ACTIVE: the DPC must end up resumed, since AS1 reaches it.
     */
    @Test
    public void dunaDuringActivationLeavesNoReachableDpcPaused() throws Exception {
        final AsImpl as1 = newAs("AS_1", 1001);
        AsImpl as2 = newAs("AS_2", 1001);
        activate(as1);
        final AspImpl asp2 = activate(as2);
        final AspImpl asp1 = (AspImpl) as1.appServerProcs.get(0);
        sg(asp1, ntfy(Status.STATUS_AS_State_Change, Status.INFO_AS_PENDING));
        assertEquals(AsState.PENDING, peerState(as1));
        mgmt.clear();
        final boolean[] injected = {false};
        mgmt.addM3UAManagementEventListener((org.mobicents.protocols.ss7.m3ua.M3UAManagementEventListener)
                java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{org.mobicents.protocols.ss7.m3ua.M3UAManagementEventListener.class},
                        (proxy, method, args) -> {
                            // called on entering ACTIVE, before the new state is visible to other threads
                            if (method.getName().equals("onAsActive") && args[0] == as1 && !injected[0]) {
                                injected[0] = true;
                                sg(asp2, duna(1001));
                            }
                            return null;
                        }));

        sg(asp1, ntfy(Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));

        assertTrue(injected[0]);
        assertEquals(AsState.ACTIVE, peerState(as1));
        assertEquals("paused while AS1 was not yet ACTIVE", Collections.singletonList(1001), mgmt.paused);
        assertEquals("resumed once AS1 is ACTIVE", Collections.singletonList(1001), mgmt.resumed);
        assertSame(as1, routes.getAsForRoute(1001, -1, -1, 0));
    }

    // ---------------------------------------------------------------- helpers

    private AsImpl newAs(String name, int dpc) throws Exception {
        AsImpl as = new AsImpl(name, null, null, 1, Functionality.AS, ExchangeType.SE, null, null);
        as.setM3UAManagement(mgmt);
        mgmt.appServers.add(as);
        routes.addRoute(dpc, -1, -1, name);
        return as;
    }

    /** SCTP up: jSS7 sends ASP Up (local FSM DOWN -> UP_SENT). */
    private AspImpl connect(AsImpl as) throws Exception {
        AspFactoryImpl factory = new AspFactoryImpl();
        factory.setFunctionality(Functionality.AS);
        factory.setExchangeType(ExchangeType.SE);
        factory.setM3UAManagement(mgmt);
        factory.started = true;
        AspImpl asp = factory.createAsp();
        asp.setAs(as);
        as.appServerProcs.add(asp);
        asp.getLocalFSM().signal(TransitionState.COMM_UP);
        return asp;
    }

    /** Usual activation: ASPUP_ACK, ASPAC_ACK + NTFY(AS-ACTIVE). */
    private AspImpl activate(AsImpl as) throws Exception {
        AspImpl asp = connect(as);
        sg(asp, msg(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP_ACK));
        sg(asp, msg(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK));
        sg(asp, ntfy(Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));
        assertEquals(AsState.ACTIVE, peerState(as));
        return asp;
    }

    /** What AspFactoryImpl.stop() does on a connected association. */
    private void aspDown(AspImpl asp) throws Exception {
        asp.getAspFactory().started = false;
        asp.getLocalFSM().signal(TransitionState.ASP_DOWN_SENT);
        AsImpl as = (AsImpl) asp.getAs();
        as.getPeerFSM().setAttribute(AsImpl.ATTRIBUTE_ASP, asp);
        as.getPeerFSM().signal(TransitionState.ASP_DOWN);
    }

    private void sg(AspImpl asp, M3UAMessage message) {
        asp.getAspFactory().read(message);
    }

    private M3UAMessage msg(int messageClass, int messageType) {
        return mgmt.messageFactory.createMessage(messageClass, messageType);
    }

    private NotifyImpl ntfy(int type, int info) {
        NotifyImpl ntfy = (NotifyImpl) msg(MessageClass.MANAGEMENT, MessageType.NOTIFY);
        ntfy.setStatus(mgmt.parameterFactory.createStatus(type, info));
        return ntfy;
    }

    private M3UAMessage err(int code) {
        org.mobicents.protocols.ss7.m3ua.message.mgmt.Error e =
                (org.mobicents.protocols.ss7.m3ua.message.mgmt.Error) msg(MessageClass.MANAGEMENT, MessageType.ERROR);
        e.setErrorCode(mgmt.parameterFactory.createErrorCode(code));
        return e;
    }

    private AffectedPointCode apc(int... pcs) {
        return mgmt.parameterFactory.createAffectedPointCode(pcs, new short[pcs.length]);
    }

    private DestinationUnavailableImpl duna(int... pcs) {
        DestinationUnavailableImpl m = new DestinationUnavailableImpl();
        m.setAffectedPointCodes(apc(pcs));
        return m;
    }

    private DestinationAvailableImpl dava(int... pcs) {
        DestinationAvailableImpl m = new DestinationAvailableImpl();
        m.setAffectedPointCodes(apc(pcs));
        return m;
    }

    private DestinationStateAuditImpl daud(int... pcs) {
        DestinationStateAuditImpl m = new DestinationStateAuditImpl();
        m.setAffectedPointCodes(apc(pcs));
        return m;
    }

    private static AsState peerState(AsImpl as) {
        return AsState.getState(as.getPeerFSM().getState().getName());
    }

    private static AspState localState(AspImpl asp) {
        return AspState.getState(asp.getLocalFSM().getState().getName());
    }

    /** Records the MTP primitives jSS7 hands to its user. */
    private static class Recording extends M3UAManagementImpl {
        final List<Integer> paused = new ArrayList<>();
        final List<Integer> resumed = new ArrayList<>();
        final List<Integer> status = new ArrayList<>();

        Recording() {
            super("real-capture-scenario-test");
        }

        void clear() {
            paused.clear();
            resumed.clear();
            status.clear();
        }

        @Override
        public void sendPauseMessageToLocalUser(Mtp3PausePrimitive message) {
            paused.add(message.getAffectedDpc());
        }

        @Override
        public void sendResumeMessageToLocalUser(Mtp3ResumePrimitive message) {
            resumed.add(message.getAffectedDpc());
        }

        @Override
        public void sendStatusMessageToLocalUser(Mtp3StatusPrimitive message) {
            status.add(message.getAffectedDpc());
        }
    }
}
