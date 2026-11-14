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

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.sccp.RemoteSccpStatus;
import org.mobicents.protocols.ss7.sccp.SccpListener;
import org.mobicents.protocols.ss7.sccp.SignallingPointStatus;
import org.mobicents.protocols.ss7.sccp.impl.SccpHarness;
import org.mobicents.protocols.ss7.sccp.message.SccpDataMessage;
import org.mobicents.protocols.ss7.sccp.message.SccpNoticeMessage;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.mobicents.protocols.ss7.tcap.api.TCListener;
import org.mobicents.protocols.ss7.tcap.api.tc.component.InvokeClass;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCBeginIndication;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCContinueIndication;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCEndIndication;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCNoticeIndication;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCPAbortIndication;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCUniIndication;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCUserAbortIndication;
import org.mobicents.protocols.ss7.tcap.asn.Utils;
import org.mobicents.protocols.ss7.tcap.asn.comp.Component;
import org.mobicents.protocols.ss7.tcap.asn.comp.Invoke;
import org.mobicents.protocols.ss7.tcap.asn.comp.OperationCode;
import org.mobicents.protocols.ss7.tcap.asn.comp.Reject;
import org.mobicents.protocols.ss7.tcap.asn.comp.ReturnResultProblemType;

/**
 * A Reject from the peer for our Invoke ends that invocation, for an invoke problem and for a general problem (ITU-T
 * Q.787 2.1.3.1.1-2): a later Return Result-Last for it is rejected locally (unrecognized Invoke ID). The TCAP stack on
 * stack2 SSN 8 is under test; the peer is a raw SCCP listener on stack1 SSN 8 that sends hand-built TCAP messages.
 */
public class RemoteRejectTest extends SccpHarness {

    private static final byte[] PEER_TID = { 0x00, 0x00, 0x00, 0x33 };

    private SccpAddress localAddress;
    private SccpAddress peerAddress;
    private TCAPStackImpl stack;
    private final BlockingQueue<SccpDataMessage> peer = new LinkedBlockingQueue<>();
    private final List<Component> received = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() throws Exception {
        this.sccpStack1Name = "RemoteRejectTestSccpStack1";
        this.sccpStack2Name = "RemoteRejectTestSccpStack2";
        super.setUp();
        localAddress = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 2, null, 8);
        peerAddress = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 1, null, 8);
        stack = new TCAPStackImpl(sccpProvider2, 8);
        stack.setInvokeTimeout(0);
        stack.start();
        stack.getProvider().addTCListener(new Recorder());
        sccpProvider1.registerSccpListener(8, new PeerListener());
    }

    @After
    public void tearDown() {
        sccpProvider1.deregisterSccpListener(8);
        stack.stop();
        super.tearDown();
    }

    @Test
    public void generalProblemRejectEndsTheInvocation() throws Exception {
        assertRejectEndsTheInvocation(0x80); // general problem: mistyped component
    }

    @Test
    public void invokeProblemRejectEndsTheInvocation() throws Exception {
        assertRejectEndsTheInvocation(0x81); // invoke problem: unrecognized operation
    }

    private void assertRejectEndsTheInvocation(int problemTag) throws Exception {
        Dialog d = stack.getProvider().getNewDialog(localAddress, peerAddress);
        int id = d.getNewInvokeId();
        Invoke inv = stack.getProvider().getComponentPrimitiveFactory().createTCInvokeRequest(InvokeClass.Class1);
        inv.setInvokeId(id);
        OperationCode oc = stack.getProvider().getComponentPrimitiveFactory().createOperationCode();
        oc.setLocalOperationCode(45L);
        inv.setOperationCode(oc);
        inv.setTimeout(30000);
        d.sendComponent(inv);
        d.send(stack.getProvider().getDialogPrimitiveFactory().createBegin(d));
        assertNotNull("no Begin to the peer", peer.poll(5, TimeUnit.SECONDS));

        // Reject (id, problem) in the first Continue
        sendContinue(d, new byte[] { (byte) 0xA4, 0x06, 0x02, 0x01, (byte) id, (byte) problemTag, 0x01, 0x01 });
        Reject remote = (Reject) waitFor(Reject.class);
        assertFalse(remote.isLocalOriginated());

        // Return Result-Last (id): rejected locally if the invocation has ended
        received.clear();
        sendContinue(d, new byte[] { (byte) 0xA2, 0x03, 0x02, 0x01, (byte) id });
        Reject local = (Reject) waitFor(Reject.class);
        assertTrue(local.isLocalOriginated());
        assertEquals(ReturnResultProblemType.UnrecognizedInvokeID, local.getProblem().getReturnResultProblemType());
    }

    /** Continue from the peer: OTID, DTID, component portion with this component. */
    private void sendContinue(Dialog d, byte[] component) throws Exception {
        byte[] dtid = Utils.encodeTransactionId(d.getLocalDialogId());
        int len = 6 + 6 + 2 + component.length;
        byte[] m = new byte[2 + len];
        int i = 0;
        m[i++] = 0x65;
        m[i++] = (byte) len;
        m[i++] = 0x48;
        m[i++] = 0x04;
        System.arraycopy(PEER_TID, 0, m, i, 4);
        i += 4;
        m[i++] = 0x49;
        m[i++] = 0x04;
        System.arraycopy(dtid, 0, m, i, 4);
        i += 4;
        m[i++] = 0x6C;
        m[i++] = (byte) component.length;
        System.arraycopy(component, 0, m, i, component.length);
        sccpProvider1.send(sccpProvider1.getMessageFactory().createDataMessageClass1(localAddress, peerAddress, m, 0, 8,
                false, null, null));
    }

    private Component waitFor(Class<?> type) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end) {
            for (Component c : received) {
                if (type.isInstance(c)) {
                    return c;
                }
            }
            Thread.sleep(10);
        }
        fail("no " + type.getSimpleName() + " in " + received);
        return null;
    }

    private class Recorder implements TCListener {
        @Override
        public void onTCContinue(TCContinueIndication ind) {
            if (ind.getComponents() != null) {
                received.addAll(ind.getComponents());
            }
        }

        @Override
        public void onTCUni(TCUniIndication ind) {
        }

        @Override
        public void onTCBegin(TCBeginIndication ind) {
        }

        @Override
        public void onTCEnd(TCEndIndication ind) {
        }

        @Override
        public void onTCUserAbort(TCUserAbortIndication ind) {
        }

        @Override
        public void onTCPAbort(TCPAbortIndication ind) {
        }

        @Override
        public void onTCNotice(TCNoticeIndication ind) {
        }

        @Override
        public void onDialogReleased(Dialog d) {
        }

        @Override
        public void onInvokeTimeout(Invoke tcInvokeRequest) {
        }

        @Override
        public void onDialogTimeout(Dialog d) {
        }
    }

    private class PeerListener implements SccpListener {
        @Override
        public void onMessage(SccpDataMessage message) {
            peer.add(message);
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
