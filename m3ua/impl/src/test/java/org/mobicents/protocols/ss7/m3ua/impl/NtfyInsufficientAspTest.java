package org.mobicents.protocols.ss7.m3ua.impl;

import org.junit.Test;
import org.mobicents.protocols.ss7.m3ua.ExchangeType;
import org.mobicents.protocols.ss7.m3ua.Functionality;
import org.mobicents.protocols.ss7.m3ua.impl.fsm.FSM;
import org.mobicents.protocols.ss7.m3ua.message.M3UAMessage;
import org.mobicents.protocols.ss7.m3ua.impl.message.mgmt.NotifyImpl;
import org.mobicents.protocols.ss7.m3ua.message.MessageClass;
import org.mobicents.protocols.ss7.m3ua.message.MessageType;
import org.mobicents.protocols.ss7.m3ua.parameter.Status;
import org.mobicents.protocols.ss7.mtp.Mtp3PausePrimitive;
import org.mobicents.protocols.ss7.mtp.Mtp3ResumePrimitive;

import static org.junit.Assert.*;

/**
 * NTFY "Insufficient ASP resources" (RFC 4666 3.8.2) is not an AS state change: the AS goes ACTIVE on our ASP
 * Active Ack, and the NTFY must not fail in any AS state.
 */
public class NtfyInsufficientAspTest {
    private static final int DPC = 1009;

    private RecordingManagement management;
    private AsImpl as;
    private AspImpl asp;

    private void setUp(ExchangeType exchangeType) throws Exception {
        management = new RecordingManagement();
        as = new AsImpl("as1", null, null, 1, Functionality.AS, exchangeType, null, null);
        as.setM3UAManagement(management);
        management.appServers.add(as);
        management.getRouteManagement().addRoute(DPC, -1, -1, "as1");

        AspFactoryImpl factory = new AspFactoryImpl();
        factory.setFunctionality(Functionality.AS);
        factory.setExchangeType(exchangeType);
        factory.setM3UAManagement(management);
        factory.started = true;
        asp = factory.createAsp();
        asp.setAs(as);
        as.appServerProcs.add(asp);
        asp.getLocalFSM().signal(TransitionState.COMM_UP);
        asp.getLocalFSM().signal(TransitionState.ASP_ACTIVE_SENT);
    }

    @Test
    public void activatesTheAsOnTheAckNotOnTheNtfy() throws Exception {
        for (ExchangeType exchangeType : new ExchangeType[]{ExchangeType.SE, ExchangeType.DE}) {
            setUp(exchangeType);

            // capture taken 09-10-2026: ASPUP_ACK, NTFY(Insufficient ASP), ASPAC, ASPAC_ACK, no NTFY(AS-ACTIVE)
            read(ntfy(Status.STATUS_Other, Status.INFO_Insufficient_ASP_Resources_Active));
            assertEquals(exchangeType + ": no AS state change on the NTFY", AsState.DOWN, peerState());
            assertEquals(0, management.resumeCount);
            assertNull("nothing routed before our ASP is active", management.getRouteManagement().getAsForRoute(DPC, -1, -1, 0));

            read(management.messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK));
            assertEquals(exchangeType.toString(), AsState.ACTIVE, peerState());
            assertEquals(1, management.resumeCount);
            assertSame(as, management.getRouteManagement().getAsForRoute(DPC, -1, -1, 0));
        }
    }

    @Test
    public void ackFollowedByNtfyAsActiveResumesOnce() throws Exception {
        setUp(ExchangeType.SE);

        read(ntfy(Status.STATUS_AS_State_Change, Status.INFO_AS_INACTIVE));
        assertEquals(AsState.INACTIVE, peerState());
        read(management.messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK));
        read(ntfy(Status.STATUS_AS_State_Change, Status.INFO_AS_ACTIVE));

        assertEquals(AsState.ACTIVE, peerState());
        assertEquals(1, management.resumeCount);
    }

    @Test
    public void insufficientAspNtfyIsANoOpWhileInactiveOrPending() throws Exception {
        setUp(ExchangeType.SE);
        FSM peer = as.getPeerFSM();

        peer.signal(TransitionState.AS_STATE_CHANGE_INACTIVE);
        peer.signal(TransitionState.OTHER_INSUFFICIENT_ASP);   // threw UnknownTransitionException before
        assertEquals(AsState.INACTIVE, peerState());

        read(management.messageFactory.createMessage(MessageClass.ASP_TRAFFIC_MAINTENANCE, MessageType.ASP_ACTIVE_ACK));
        peer.signal(TransitionState.AS_STATE_CHANGE_PENDING);
        peer.signal(TransitionState.OTHER_INSUFFICIENT_ASP);   // threw UnknownTransitionException before
        assertEquals(AsState.PENDING, peerState());

        // T(r) keeps running across the no-op and still takes the AS out of service
        peer.tick(System.currentTimeMillis() + 3000);
        assertNotEquals(AsState.PENDING, peerState());
        assertEquals(1, management.pauseCount);
    }

    private NotifyImpl ntfy(int type, int info) {
        NotifyImpl ntfy = (NotifyImpl) management.messageFactory.createMessage(MessageClass.MANAGEMENT, MessageType.NOTIFY);
        ntfy.setStatus(management.parameterFactory.createStatus(type, info));
        return ntfy;
    }

    private void read(M3UAMessage message) {
        asp.getAspFactory().read(message);
    }

    private AsState peerState() {
        return AsState.getState(as.getPeerFSM().getState().getName());
    }

    private static class RecordingManagement extends M3UAManagementImpl {
        private int resumeCount;
        private int pauseCount;

        RecordingManagement() {
            super("ntfy-insufficient-asp-test");
        }

        @Override
        public void sendResumeMessageToLocalUser(Mtp3ResumePrimitive message) {
            resumeCount++;
        }

        @Override
        public void sendPauseMessageToLocalUser(Mtp3PausePrimitive message) {
            pauseCount++;
        }
    }
}
