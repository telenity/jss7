/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2012, Telestax Inc and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.protocols.ss7.tcap;

import static org.junit.Assert.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.sccp.RemoteSccpStatus;
import org.mobicents.protocols.ss7.sccp.SignallingPointStatus;
import org.mobicents.protocols.ss7.sccp.SccpListener;
import org.mobicents.protocols.ss7.sccp.impl.SccpHarness;
import org.mobicents.protocols.ss7.sccp.message.SccpDataMessage;
import org.mobicents.protocols.ss7.sccp.message.SccpNoticeMessage;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.mobicents.protocols.ss7.tcap.api.TCAPSendException;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.TRPseudoState;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCBeginIndication;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TerminationType;

/**
 * Transaction forward ({@link Dialog#forward}) checked on the SCCP level. A (stack1 SSN 8) sends a TC-BEGIN to B
 * (stack2 SSN 8), B forwards it to C (stack1 SSN 9, a TCAP stack) or to a raw SCCP listener (stack1 SSN 10).
 */
public class TCAPForwardTest extends SccpHarness {

    private static final long WAIT_TIME = 300;

    // TC-BEGIN, OTID 0000002A, one Invoke (invokeId 1, opcode 45), no dialogue portion
    private static final byte[] RAW_BEGIN = new byte[] { 0x62, 0x10, 0x48, 0x04, 0x00, 0x00, 0x00, 0x2A, 0x6C, 0x08,
            (byte) 0xA1, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x2D };

    private SccpAddress peer1Address;
    private SccpAddress peer2Address;
    private SccpAddress peer3Address;
    private SccpAddress rawAddress;
    private TCAPStackImpl tcapStack1;
    private TCAPStackImpl tcapStack2;
    private TCAPStackImpl tcapStack3;
    private Client a;
    private Server b;
    private Server c;
    private RawListener raw;

    @Before
    public void setUp() throws Exception {
        this.sccpStack1Name = "TCAPForwardTestSccpStack1";
        this.sccpStack2Name = "TCAPForwardTestSccpStack2";
        super.setUp();
        resource2.addRemoteSsn(2, getStack1PC(), 9, 0, false);
        resource2.addRemoteSsn(3, getStack1PC(), 10, 0, false);

        peer1Address = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 1, null, 8);
        peer2Address = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 2, null, 8);
        peer3Address = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 1, null, 9);
        rawAddress = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 1, null, 10);

        tcapStack1 = new TCAPStackImpl(sccpProvider1, 8);
        tcapStack2 = new TCAPStackImpl(sccpProvider2, 8);
        tcapStack3 = new TCAPStackImpl(sccpProvider1, 9);
        for (TCAPStackImpl s : new TCAPStackImpl[] { tcapStack1, tcapStack2, tcapStack3 }) {
            s.start();
            s.setInvokeTimeout(0);
            s.setForwardEnabled(true);
        }
        raw = new RawListener();
        sccpProvider1.registerSccpListener(10, raw);

        a = new Client(tcapStack1, peer1Address, peer2Address);
        b = new Server(tcapStack2, peer2Address, peer1Address);
        c = new Server(tcapStack3, peer3Address, peer1Address);
    }

    @After
    public void tearDown() {
        sccpProvider1.deregisterSccpListener(10);
        tcapStack1.stop();
        tcapStack2.stop();
        tcapStack3.stop();
        super.tearDown();
    }

    /** The forwarded SCCP message carries the received TCAP bytes unchanged, the originator as calling party. */
    @Test
    public void forwardedBytesAreUnchanged() throws Exception {
        sccpProvider1.send(sccpProvider1.getMessageFactory().createDataMessageClass1(peer2Address, peer1Address,
                RAW_BEGIN, 5, 8, false, null, null));
        EventTestHarness.waitFor(WAIT_TIME);
        assertEquals(TRPseudoState.InitialReceived, b.dialog.getState());

        b.dialog.forward(rawAddress, null);

        SccpDataMessage fwd = raw.take();
        assertArrayEquals(RAW_BEGIN, fwd.getData());
        assertEquals(rawAddress, fwd.getCalledPartyAddress());
        assertEquals(peer1Address, fwd.getCallingPartyAddress());
        assertEquals(1, fwd.getProtocolClass().getProtocolClass());
        assertEquals(5, fwd.getSls());
        assertNull(raw.poll());
        assertForwarded(b.dialog);
    }

    @Test
    public void protocolClass0IsKept() throws Exception {
        sccpProvider1.send(sccpProvider1.getMessageFactory().createDataMessageClass0(peer2Address, peer1Address,
                RAW_BEGIN, 8, false, null, null));
        EventTestHarness.waitFor(WAIT_TIME);

        b.dialog.forward(rawAddress, null);

        SccpDataMessage fwd = raw.take();
        assertArrayEquals(RAW_BEGIN, fwd.getData());
        assertEquals(0, fwd.getProtocolClass().getProtocolClass());
    }

    /** Off by default: the received BEGIN is not kept and forward() is refused. */
    @Test
    public void refusedWhenForwardingIsDisabled() throws Exception {
        tcapStack2.setForwardEnabled(false);
        sccpProvider1.send(sccpProvider1.getMessageFactory().createDataMessageClass1(peer2Address, peer1Address,
                RAW_BEGIN, 5, 8, false, null, null));
        EventTestHarness.waitFor(WAIT_TIME);
        assertEquals(TRPseudoState.InitialReceived, b.dialog.getState());

        try {
            b.dialog.forward(rawAddress, null);
            fail("forward must be refused while forwarding is disabled");
        } catch (TCAPSendException e) {
            assertTrue(e.getMessage().contains("setForwardEnabled"));
        }
        assertNull(raw.poll());
        assertFalse(new TCAPStackImpl(sccpProvider1, 11).getForwardEnabled());
    }

    /** forward(destination, null) keeps the calling party of the received BEGIN even after setRemoteAddress(). */
    @Test
    public void receivedCallingPartyKeptAfterSetRemoteAddress() throws Exception {
        sccpProvider1.send(sccpProvider1.getMessageFactory().createDataMessageClass1(peer2Address, peer1Address,
                RAW_BEGIN, 5, 8, false, null, null));
        EventTestHarness.waitFor(WAIT_TIME);
        b.dialog.setRemoteAddress(new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 2, null, 147));

        b.dialog.forward(rawAddress, null);

        assertEquals(peer1Address, raw.take().getCallingPartyAddress());
    }

    @Test
    public void originatingAddressOverride() throws Exception {
        SccpAddress other = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 2, null, 147);
        a.startClientDialog();
        a.sendBegin();
        EventTestHarness.waitFor(WAIT_TIME);

        b.dialog.forward(rawAddress, other);

        SccpDataMessage fwd = raw.take();
        assertEquals(other, fwd.getCallingPartyAddress());
        assertEquals(rawAddress, fwd.getCalledPartyAddress());
    }

    /**
     * A -> B -> C: C answers A directly with the transaction ids of the original BEGIN, B keeps nothing and A never
     * hears from B.
     */
    @Test
    public void dialogueContinuesBetweenOriginatorAndDestination() throws Exception {
        a.startClientDialog();
        Dialog originator = a.dialog;
        a.sendBegin();
        EventTestHarness.waitFor(WAIT_TIME);

        Dialog forwarded = b.dialog;
        assertEquals(originator.getLocalDialogId(), forwarded.getRemoteDialogId());
        forwarded.forward(peer3Address, null);
        EventTestHarness.waitFor(WAIT_TIME);
        assertForwarded(forwarded);

        // C sees the originator's transaction id and address, and the original components
        Dialog dest = c.dialog;
        assertNotNull(dest);
        assertEquals(TRPseudoState.InitialReceived, dest.getState());
        assertEquals(originator.getLocalDialogId(), dest.getRemoteDialogId());
        assertEquals(peer1Address, dest.getRemoteAddress());
        assertEquals(peer3Address, dest.getLocalAddress());
        assertEquals(2, ((TCBeginIndication) c.getObserverdEvents().get(0).getEvent()).getComponents().size());

        c.sendContinue2();
        EventTestHarness.waitFor(WAIT_TIME);

        // A takes C's answer to its own BEGIN: DTID matched, C becomes the peer
        assertEquals(TRPseudoState.Active, originator.getState());
        assertEquals(dest.getLocalDialogId(), originator.getRemoteDialogId());
        assertEquals(peer3Address, originator.getRemoteAddress());
        assertEquals(EventType.Continue, a.getObserverdEvents().get(1).getEventType());
        assertEquals(2, a.getObserverdEvents().size()); // nothing from B

        a.sendEnd(TerminationType.Basic);
        EventTestHarness.waitFor(WAIT_TIME);
        assertEquals(EventType.End, c.getObserverdEvents().get(2).getEventType());
        assertEquals(TRPseudoState.Expunged, dest.getState());
        assertEquals(TRPseudoState.Expunged, originator.getState());
        assertEquals(2, b.getObserverdEvents().size()); // B: its BEGIN and release only
    }

    /** The TCAP user decides inside onTCBegin, as jss7sgw does: the BEGIN is forwarded before the callback returns. */
    @Test
    public void forwardFromOnTCBegin() throws Exception {
        tcapStack2.getProvider().removeTCListener(b);
        Server forwarder = new Server(tcapStack2, peer2Address, peer1Address) {
            @Override
            public void onTCBegin(TCBeginIndication ind) {
                super.onTCBegin(ind);
                try {
                    ind.getDialog().forward(peer3Address, null);
                } catch (TCAPSendException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        a.startClientDialog();
        a.sendBegin();
        EventTestHarness.waitFor(WAIT_TIME);

        assertForwarded(forwarder.dialog);
        assertEquals(EventType.DialogRelease, forwarder.getObserverdEvents().get(1).getEventType());
        assertNotNull(c.dialog);
        assertEquals(a.dialog.getLocalDialogId(), c.dialog.getRemoteDialogId());
        assertEquals(1, a.getObserverdEvents().size()); // only its own BEGIN, no abort from B
    }

    @Test
    public void rejectedOnLocallyCreatedDialog() throws Exception {
        a.startClientDialog();
        assertRejected(a.dialog); // Idle
        a.sendBegin();
        assertRejected(a.dialog); // InitialSent
        assertEquals(TRPseudoState.InitialSent, a.dialog.getState());
    }

    @Test
    public void rejectedAfterContinue() throws Exception {
        receiveBegin();
        b.sendContinue2();
        assertRejected(b.dialog);
        assertEquals(TRPseudoState.Active, b.dialog.getState());
    }

    @Test
    public void rejectedAfterEnd() throws Exception {
        receiveBegin();
        b.sendEnd(TerminationType.Basic);
        assertRejected(b.dialog);
    }

    @Test
    public void rejectedAfterUserAbort() throws Exception {
        receiveBegin();
        b.sendAbort(null, null, null);
        assertRejected(b.dialog);
    }

    @Test
    public void rejectedAfterRelease() throws Exception {
        receiveBegin();
        b.dialog.release();
        assertRejected(b.dialog);
    }

    @Test
    public void forwardedOnlyOnce() throws Exception {
        receiveBegin();
        b.dialog.forward(rawAddress, null);
        assertNotNull(raw.take());
        assertRejected(b.dialog);
    }

    @Test
    public void rejectedWithoutDestination() throws Exception {
        receiveBegin();
        try {
            b.dialog.forward(null, null);
            fail("forward accepted without destination");
        } catch (TCAPSendException expected) {
        }
        assertEquals(TRPseudoState.InitialReceived, b.dialog.getState());
        b.sendContinue2();
        assertEquals(TRPseudoState.Active, b.dialog.getState());
    }

    @Test
    public void rejectedOnUniDialog() throws Exception {
        a.startUniDialog();
        a.sendUni();
        EventTestHarness.waitFor(WAIT_TIME);
        assertRejected(b.dialog);
    }

    private void receiveBegin() throws Exception {
        a.startClientDialog();
        a.sendBegin();
        EventTestHarness.waitFor(WAIT_TIME);
        assertEquals(TRPseudoState.InitialReceived, b.dialog.getState());
    }

    private void assertForwarded(Dialog d) {
        assertEquals(TRPseudoState.Expunged, d.getState());
        assertEquals(0, ((TCAPProviderImpl) tcapStack2.getProvider()).getCurrentDialogsCount());
    }

    /** forward() throws and nothing goes out on SCCP. */
    private void assertRejected(Dialog d) throws Exception {
        try {
            d.forward(rawAddress, null);
            fail("forward accepted in state " + d.getState());
        } catch (TCAPSendException expected) {
        }
        EventTestHarness.waitFor(WAIT_TIME);
        assertNull(raw.poll());
    }

    private static class RawListener implements SccpListener {
        private final BlockingQueue<SccpDataMessage> messages = new LinkedBlockingQueue<>();

        SccpDataMessage take() throws InterruptedException {
            SccpDataMessage m = messages.poll(5, TimeUnit.SECONDS);
            assertNotNull("no SCCP message on the forward destination", m);
            return m;
        }

        SccpDataMessage poll() {
            return messages.poll();
        }

        @Override
        public void onMessage(SccpDataMessage message) {
            messages.add(message);
        }

        @Override
        public void onNotice(SccpNoticeMessage message) {
        }

        @Override
        public void onCoordRequest(int dpc, int ssn, int multiplicityIndicator) {
        }

        @Override
        public void onCoordResponse(int dpc, int ssn, int multiplicityIndicator) {
        }

        @Override
        public void onState(int dpc, int ssn, boolean inService, int multiplicityIndicator) {
        }

        @Override
        public void onPcState(int dpc, SignallingPointStatus status, int restrictedImportanceLevel,
                RemoteSccpStatus remoteSccpStatus) {
        }
    }
}
