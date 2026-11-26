package org.mobicents.protocols.ss7.tcap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.sccp.impl.SccpHarness;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.mobicents.protocols.ss7.tcap.api.ComponentPrimitiveFactory;
import org.mobicents.protocols.ss7.tcap.api.TCAPException;
import org.mobicents.protocols.ss7.tcap.api.TCAPSendException;
import org.mobicents.protocols.ss7.tcap.api.tc.component.InvokeClass;
import org.mobicents.protocols.ss7.tcap.api.tc.component.OperationState;
import org.mobicents.protocols.ss7.tcap.asn.InvokeImpl;
import org.mobicents.protocols.ss7.tcap.asn.TcapFactory;
import org.mobicents.protocols.ss7.tcap.asn.comp.Component;
import org.mobicents.protocols.ss7.tcap.asn.comp.ComponentType;
import org.mobicents.protocols.ss7.tcap.asn.comp.InvokeProblemType;
import org.mobicents.protocols.ss7.tcap.asn.comp.Invoke;
import org.mobicents.protocols.ss7.tcap.asn.comp.OperationCode;
import org.mobicents.protocols.ss7.tcap.asn.comp.Problem;
import org.mobicents.protocols.ss7.tcap.asn.comp.ProblemType;
import org.mobicents.protocols.ss7.tcap.asn.comp.Reject;
import org.mobicents.protocols.ss7.tcap.asn.comp.ReturnError;
import org.mobicents.protocols.ss7.tcap.asn.comp.ReturnResultLast;
import org.mobicents.protocols.ss7.tcap.asn.comp.ReturnResultProblemType;

/**
 * A dialog keeps its sent invokes in a short array that grows: more than its initial 2 must all stay addressable.
 * Invoke id allocation and the sent invoke table must behave as the old 256 slot tables did.
 */
public class SentInvokeTableTest extends SccpHarness {

    private TCAPStackImpl tcapStack1;
    private TCAPProviderImpl provider;

    @Before
    public void setUp() throws Exception {
        this.sccpStack1Name = "SentInvokeTableTestSccpStack1";
        this.sccpStack2Name = "SentInvokeTableTestSccpStack2";
        super.setUp();
        this.tcapStack1 = new TCAPStackImpl(this.sccpProvider1, 8);
        this.tcapStack1.start();
        this.provider = (TCAPProviderImpl) this.tcapStack1.getProvider();
    }

    @After
    public void tearDown() {
        this.tcapStack1.stop();
        super.tearDown();
    }

    // 16 invokes: past ArrayList's default capacity of 10
    @Test
    public void manySentInvokesStayAddressable() throws Exception {
        DialogImpl dialog = newDialog();
        InvokeImpl[] sent = new InvokeImpl[16];
        for (int i = 0; i < sent.length; i++) {
            sent[i] = invoke(provider, dialog.getNewInvokeId());
            assertEquals(Integer.valueOf(i), sent[i].getInvokeId());
            dialog.sendComponent(sent[i]);
        }
        dialog.send(provider.getDialogPrimitiveFactory().createBegin(dialog));

        for (int i = 0; i < sent.length; i++) {
            dialog.resetTimer(i); // throws if the invoke was not kept
        }
        try {
            dialog.sendComponent(invoke(provider, 2));
            fail("a second invoke with a sent invoke's id must be refused");
        } catch (TCAPSendException expected) {
        }

        dialog.operationEnded(sent[2]);
        try {
            dialog.resetTimer(2);
            fail("an ended invoke must be gone");
        } catch (TCAPException expected) {
        }
        dialog.resetTimer(15);
        dialog.sendComponent(invoke(provider, 2)); // its id is free again
    }

    // ids come out 0..127 then -128..-1, then exhaustion; a freed id is found again after the wrap
    @Test
    public void invokeIdAllocationOrderAndExhaustion() throws Exception {
        DialogImpl dialog = newDialog();
        List<Integer> got = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            got.add(dialog.getNewInvokeId());
        }
        for (int i = 0; i < 128; i++) {
            assertEquals(Integer.valueOf(i), got.get(i));
            assertEquals(Integer.valueOf(-128 + i), got.get(128 + i));
        }
        assertNoFreeInvokeId(dialog);
        assertNoFreeInvokeId(dialog); // still exhausted, nothing leaked

        // free 127, -128 and 5 (an id is freed when its invoke ends)
        dialog.operationEnded(invoke(provider, -128));
        dialog.operationEnded(invoke(provider, 127));
        dialog.operationEnded(invoke(provider, 5));
        // freeing an already free id must not create an extra free slot
        dialog.operationEnded(invoke(provider, 5));
        // search continues after the last given id (-1, index 127) at index 128: 0..127, then -128..
        assertEquals(Integer.valueOf(5), dialog.getNewInvokeId());
        assertEquals(Integer.valueOf(127), dialog.getNewInvokeId());
        assertEquals(Integer.valueOf(-128), dialog.getNewInvokeId());
        assertNoFreeInvokeId(dialog);
    }

    // allocate/free many times: the id moves round the whole -128..127 space, not stuck at the low ids
    @Test
    public void invokeIdWrapsAround() throws Exception {
        DialogImpl dialog = newDialog();
        for (int i = 0; i < 600; i++) {
            Integer id = dialog.getNewInvokeId();
            int expected = ((i + 128) % 256) - 128;
            assertEquals(Integer.valueOf(expected), id);
            dialog.operationEnded(invoke(provider, id));
        }
    }

    // edge ids -128 and 127 plus many more than the initial table size, ended by each kind of answer
    @Test
    public void sentInvokesEndedByEachAnswerKind() throws Exception {
        DialogImpl dialog = newDialog();
        int[] ids = { -128, 127, 0, -1, 1, 50, -50, 100, -100, 64 };
        InvokeImpl[] sent = new InvokeImpl[ids.length];
        for (int i = 0; i < ids.length; i++) {
            sent[i] = invoke(provider, ids[i]);
            dialog.sendComponent(sent[i]);
        }
        dialog.send(provider.getDialogPrimitiveFactory().createBegin(dialog));
        for (int i = 0; i < ids.length; i++) {
            assertEquals(OperationState.Sent, sent[i].getState());
            dialog.resetTimer(ids[i]);
        }

        List<Component> in = new ArrayList<>();
        in.add(returnResultLast(-128));
        in.add(returnError(127));
        in.add(remoteReject(0, InvokeProblemType.MistypedParameter));
        List<Component> out = dialog.processOperationsState(in);
        assertEquals(3, out.size());
        assertEquals(ComponentType.ReturnResultLast, out.get(0).getType());
        assertEquals(ComponentType.ReturnError, out.get(1).getType());
        assertEquals(ComponentType.Reject, out.get(2).getType());
        for (int i = 0; i < 3; i++) {
            assertEquals(OperationState.Idle, sent[i].getState());
            assertGone(dialog, ids[i]);
        }
        for (int i = 3; i < ids.length; i++) {
            dialog.resetTimer(ids[i]);
        }

        // a second answer for an ended invoke is rejected as unknown
        out = dialog.processOperationsState(Collections.<Component> singletonList(returnResultLast(-128)));
        assertEquals(1, out.size());
        Reject rej = (Reject) out.get(0);
        assertTrue(rej.isLocalOriginated());
        assertEquals(ReturnResultProblemType.UnrecognizedInvokeID, rej.getProblem().getReturnResultProblemType());

        // a received invoke linked to a sent one finds it, an unknown linked id is rejected
        Invoke linked = TcapFactory.createComponentInvoke();
        linked.setInvokeId(1);
        linked.setLinkedId(100);
        out = dialog.processOperationsState(Collections.<Component> singletonList(linked));
        assertEquals(1, out.size());
        assertSame(sent[7], ((InvokeImpl) out.get(0)).getLinkedInvoke());
        Invoke badLinked = TcapFactory.createComponentInvoke();
        badLinked.setInvokeId(2);
        badLinked.setLinkedId(-128);
        out = dialog.processOperationsState(Collections.<Component> singletonList(badLinked));
        assertEquals(InvokeProblemType.UnrechognizedLinkedID, ((Reject) out.get(0)).getProblem().getInvokeProblemType());

        // ended ids can be used again
        dialog.sendComponent(invoke(provider, -128));
        dialog.sendComponent(invoke(provider, 127));
        dialog.sendComponent(invoke(provider, 0));
        try {
            dialog.sendComponent(invoke(provider, 64));
            fail("64 is still a sent invoke");
        } catch (TCAPSendException expected) {
        }
    }

    // a reject that is not about the invoke (a return result problem) does not end it
    @Test
    public void rejectOfReturnResultKeepsInvoke() throws Exception {
        DialogImpl dialog = newDialog();
        InvokeImpl inv = invoke(provider, 7);
        dialog.sendComponent(inv);
        dialog.send(provider.getDialogPrimitiveFactory().createBegin(dialog));

        Reject rej = TcapFactory.createComponentReject();
        rej.setInvokeId(7);
        Problem p = TcapFactory.createProblem(ProblemType.ReturnResult);
        p.setReturnResultProblemType(ReturnResultProblemType.MistypedParameter);
        rej.setProblem(p);
        dialog.processOperationsState(Collections.<Component> singletonList(rej));
        assertEquals(OperationState.Sent, inv.getState());
        dialog.resetTimer(7);
    }

    @Test
    public void invokeTimeoutRemovesSentInvoke() throws Exception {
        DialogImpl dialog = newDialog();
        Integer id = dialog.getNewInvokeId();
        InvokeImpl inv = invoke(provider, id);
        inv.setTimeout(50);
        InvokeImpl other = invoke(provider, dialog.getNewInvokeId());
        InvokeImpl third = invoke(provider, dialog.getNewInvokeId());
        dialog.sendComponent(inv);
        dialog.sendComponent(other);
        dialog.sendComponent(third);
        dialog.send(provider.getDialogPrimitiveFactory().createBegin(dialog));

        long until = System.currentTimeMillis() + 5000;
        while (inv.getState() != OperationState.Idle && System.currentTimeMillis() < until) {
            Thread.sleep(10);
        }
        assertEquals(OperationState.Idle, inv.getState());
        assertGone(dialog, id);
        dialog.resetTimer(other.getInvokeId());
        dialog.resetTimer(third.getInvokeId());
        dialog.sendComponent(invoke(provider, id)); // free again
    }

    // cancel only removes a not yet sent invoke; a sent one stays
    @Test
    public void cancelInvocation() throws Exception {
        DialogImpl dialog = newDialog();
        InvokeImpl sentInv = invoke(provider, -128);
        dialog.sendComponent(sentInv);
        dialog.sendComponent(invoke(provider, 127));
        dialog.sendComponent(invoke(provider, 3));
        assertTrue(dialog.cancelInvocation(127));
        assertFalse(dialog.cancelInvocation(127));
        assertFalse(dialog.cancelInvocation(55));
        dialog.send(provider.getDialogPrimitiveFactory().createBegin(dialog));

        assertFalse(dialog.cancelInvocation(-128)); // sent: not in the send buffer
        dialog.resetTimer(-128);
        dialog.resetTimer(3);
        assertGone(dialog, 127); // cancelled before sending, never sent
        dialog.sendComponent(invoke(provider, 127));
        try {
            dialog.sendComponent(invoke(provider, -128));
            fail("-128 is still a sent invoke");
        } catch (TCAPSendException expected) {
        }
    }

    // two invokes with one id before sending are both accepted: only sent invokes are checked
    @Test
    public void duplicateIdOnlyRefusedOnceSent() throws Exception {
        DialogImpl dialog = newDialog();
        InvokeImpl a = invoke(provider, 9);
        InvokeImpl b = invoke(provider, 9);
        dialog.sendComponent(a);
        dialog.sendComponent(b);
        dialog.send(provider.getDialogPrimitiveFactory().createBegin(dialog));
        try {
            dialog.sendComponent(invoke(provider, 9));
            fail("9 is a sent invoke");
        } catch (TCAPSendException expected) {
        }
        // the table holds the last one sent with that id
        List<Component> out = dialog.processOperationsState(Collections.<Component> singletonList(returnResultLast(9)));
        assertEquals(ComponentType.ReturnResultLast, out.get(0).getType());
        assertEquals(OperationState.Idle, b.getState());
        assertEquals(OperationState.Sent, a.getState());
        assertGone(dialog, 9);
    }

    // release ends every sent invoke and frees exactly their ids
    @Test
    public void releaseClearsEverything() throws Exception {
        DialogImpl dialog = newDialog();
        List<InvokeImpl> sent = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            Integer id = dialog.getNewInvokeId();
            if (i % 30 == 0 || i == 255) { // 0, 30, .., 127, -98, .., -1: 10 invokes sent
                InvokeImpl inv = invoke(provider, id);
                sent.add(inv);
                dialog.sendComponent(inv);
            }
        }
        assertNoFreeInvokeId(dialog);
        dialog.send(provider.getDialogPrimitiveFactory().createBegin(dialog));
        assertEquals(1, provider.getCurrentDialogsCount());

        dialog.release();
        assertEquals(0, provider.getCurrentDialogsCount());
        for (InvokeImpl inv : sent) {
            assertEquals(OperationState.Idle, inv.getState());
            assertGone(dialog, inv.getInvokeId());
        }
        // the search goes on after the last given id (-1) at id 0: freed ids come back in the order first given
        List<Integer> again = new ArrayList<>();
        for (int i = 0; i < sent.size(); i++) {
            again.add(dialog.getNewInvokeId());
        }
        List<Integer> expected = new ArrayList<>();
        for (InvokeImpl inv : sent) {
            expected.add(inv.getInvokeId());
        }
        assertEquals(expected, again);
        assertNoFreeInvokeId(dialog);
    }

    // a dialog that never sent an invoke: lookups and release work on the empty table
    @Test
    public void emptyTable() throws Exception {
        DialogImpl dialog = newDialog();
        assertGone(dialog, 0);
        assertGone(dialog, -128);
        assertGone(dialog, 127);
        List<Component> out = dialog.processOperationsState(Collections.<Component> singletonList(returnError(0)));
        assertEquals(ComponentType.Reject, out.get(0).getType());
        assertNull(dialog.processOperationsState(null));
        dialog.release();
        assertEquals(0, provider.getCurrentDialogsCount());
    }

    // ids outside -128..127 cannot be in the table (components refuse them); resetTimer says "No operation"
    // (master: ArrayIndexOutOfBoundsException for 200, NullPointerException for null)
    @Test
    public void resetTimerWithImpossibleId() throws Exception {
        DialogImpl dialog = newDialog();
        dialog.sendComponent(invoke(provider, 1));
        dialog.send(provider.getDialogPrimitiveFactory().createBegin(dialog));
        assertGone(dialog, 200);
        assertGone(dialog, -129);
        try {
            dialog.resetTimer(null);
            fail();
        } catch (TCAPException expected) {
        }
    }

    // an invoke without an id is refused up front: never queued, so send and release still work
    // (master: NullPointerException from sendComponent; before this check it was queued and release threw)
    @Test
    public void invokeWithoutIdRefused() throws Exception {
        DialogImpl dialog = newDialog();
        InvokeImpl noId = (InvokeImpl) provider.getComponentPrimitiveFactory().createTCInvokeRequest(InvokeClass.Class1);
        try {
            dialog.sendComponent(noId);
            fail("an invoke without an id must be refused");
        } catch (TCAPSendException expected) {
        }
        assertEquals(OperationState.Idle, noId.getState());
        dialog.sendComponent(invoke(provider, 1));
        dialog.send(provider.getDialogPrimitiveFactory().createBegin(dialog));
        dialog.resetTimer(1);
        dialog.release();
        assertEquals(0, provider.getCurrentDialogsCount());
    }

    private DialogImpl newDialog() throws TCAPException {
        return (DialogImpl) provider.getNewDialog(
                new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 1, null, 8),
                new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 2, null, 8));
    }

    private static void assertNoFreeInvokeId(DialogImpl dialog) {
        try {
            dialog.getNewInvokeId();
            fail("all 256 invoke ids are taken");
        } catch (TCAPException expected) {
            assertEquals("No free invokeId", expected.getMessage());
        }
    }

    private static void assertGone(DialogImpl dialog, int invokeId) {
        try {
            dialog.resetTimer(invokeId);
            fail("invoke " + invokeId + " must not be a sent invoke");
        } catch (TCAPException expected) {
            assertEquals("No operation with this ID", expected.getMessage());
        }
    }

    private static ReturnResultLast returnResultLast(int invokeId) {
        ReturnResultLast r = TcapFactory.createComponentReturnResultLast();
        r.setInvokeId(invokeId);
        return r;
    }

    private static ReturnError returnError(int invokeId) {
        ReturnError r = TcapFactory.createComponentReturnError();
        r.setInvokeId(invokeId);
        return r;
    }

    private static Reject remoteReject(int invokeId, InvokeProblemType type) {
        Reject r = TcapFactory.createComponentReject();
        r.setInvokeId(invokeId);
        Problem p = TcapFactory.createProblem(ProblemType.Invoke);
        p.setInvokeProblemType(type);
        r.setProblem(p);
        return r;
    }

    private static InvokeImpl invoke(TCAPProviderImpl provider, int invokeId) {
        ComponentPrimitiveFactory cpFactory = provider.getComponentPrimitiveFactory();
        Invoke invoke = cpFactory.createTCInvokeRequest(InvokeClass.Class1);
        invoke.setInvokeId(invokeId);
        OperationCode oc = cpFactory.createOperationCode();
        oc.setLocalOperationCode(12L);
        invoke.setOperationCode(oc);
        return (InvokeImpl) invoke;
    }
}
