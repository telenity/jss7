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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.sccp.impl.SccpHarness;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.mobicents.protocols.ss7.tcap.api.TCAPException;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.TRPseudoState;
import org.junit.AfterClass;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author sergey vetyutnev
 */
public class DialogIdRangeTest extends SccpHarness {

    private TCAPStackImpl tcapStack1;
    private SccpAddress peer1Address;
    private SccpAddress peer2Address;

    @Before
    public void setUpClass() throws Exception {
        this.sccpStack1Name = "TCAPFunctionalTestSccpStack1";
        this.sccpStack2Name = "TCAPFunctionalTestSccpStack2";

        peer1Address = new SccpAddress(
                RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 1, null, 8);
        peer2Address = new SccpAddress(
                RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 2, null, 8);
    }

    @After
    public void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        this.tcapStack1 = new TCAPStackImpl(this.sccpProvider1, 8);
        this.tcapStack1.start();
    }

    @After
    public void tearDown() {
        this.tcapStack1.stop();
        super.tearDown();
    }

    /**
     * Original range validation test, slightly cleaned up.
     */
    @Test
    public void uniMsgTest() throws Exception {

        Dialog d;

        this.tcapStack1.stop();

        this.tcapStack1.setDialogIdRangeStart(20);
        this.tcapStack1.setDialogIdRangeEnd(10020);
        this.tcapStack1.start();
        this.tcapStack1.stop();

//        this.tcapStack1.setDialogIdRangeStart(20);
//        this.tcapStack1.setDialogIdRangeEnd(10019);
//        try {
//            this.tcapStack1.start();
//            this.tcapStack1.stop();
//            fail("Must be exception for invalid range (end < start + 1)");
//        } catch (Exception e) {
//            // expected
//        }

        this.tcapStack1.setDialogIdRangeStart(20000);
        this.tcapStack1.setDialogIdRangeEnd(20);
        try {
            this.tcapStack1.start();
            this.tcapStack1.stop();
            fail("Must be exception for invalid range (start > end)");
        } catch (Exception e) {
            // expected
        }

        this.tcapStack1.setDialogIdRangeStart(-1);
        this.tcapStack1.setDialogIdRangeEnd(20000);
        try {
            this.tcapStack1.start();
            this.tcapStack1.stop();
            fail("Must be exception for negative start");
        } catch (Exception e) {
            // expected
        }

        this.tcapStack1.setDialogIdRangeStart(1);
        this.tcapStack1.setDialogIdRangeEnd(20000000000L);
        try {
            this.tcapStack1.start();
            this.tcapStack1.stop();
            fail("Must be exception for too large end");
        } catch (Exception e) {
            // expected
        }

        // Valid range
        this.tcapStack1.setDialogIdRangeStart(20);
        this.tcapStack1.setDialogIdRangeEnd(10020);
        this.tcapStack1.start();

        d = this.tcapStack1.getProvider().getNewDialog(peer1Address, peer2Address);
        assertEquals(20L, (long) d.getLocalDialogId());

        this.tcapStack1.setMaxDialogs(5000);
        try {
            this.tcapStack1.setMaxDialogs(15000);
            fail("Must be exception if maxDialogs exceeds available range");
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testMaxDialogsExhaustion() throws Exception {

        this.tcapStack1.stop();

        // Configure a small, tight range
        this.tcapStack1.setDialogIdRangeStart(100);
        this.tcapStack1.setDialogIdRangeEnd(199); // 100 possible IDs
        this.tcapStack1.setMaxDialogs(10);        // but limit to 10
        this.tcapStack1.start();

        TCAPProviderImpl provider = (TCAPProviderImpl) this.tcapStack1.getProvider();

        // Allocate exactly maxDialogs dialogs
        for (int i = 0; i < 10; i++) {
            Dialog d = provider.getNewDialog(peer1Address, peer2Address);
            assertNotNull("DialogId must not be null", d.getLocalDialogId());
        }

        // Next allocation must fail with TCAPException
        try {
            provider.getNewDialog(peer1Address, peer2Address);
            fail("Must throw TCAPException after reaching maxDialogs");
        } catch (TCAPException e) {
            // expected
        }

        assertEquals("Dialog count must be equal to maxDialogs after exhaustion", 10, provider.getCurrentDialogsCount());
    }

    @Test
    public void testMaxDialogsExhaustionExt() throws Exception {

        this.tcapStack1.stop();

        // Configure a small, tight range
        this.tcapStack1.setDialogIdRangeStart(32787);
        this.tcapStack1.setDialogIdRangeEnd(85550); // 100 possible IDs
        this.tcapStack1.setMaxDialogs((int) (this.tcapStack1.getDialogIdRangeEnd() - this.tcapStack1.getDialogIdRangeStart()) - 1);        // but limit to 10
        this.tcapStack1.start();

        TCAPProviderImpl provider = (TCAPProviderImpl) this.tcapStack1.getProvider();

        // Allocate exactly maxDialogs dialogs
        for (int i = 0; i < tcapStack1.getMaxDialogs(); i++) {
            Dialog d = provider.getNewDialog(peer1Address, peer2Address);
            assertNotNull(d.getLocalDialogId());
        }

        // Next allocation must fail with TCAPException
        try {
            provider.getNewDialog(peer1Address, peer2Address);
            fail("Must throw TCAPException after reaching maxDialogs");
        } catch (TCAPException e) {
            // expected
        }

        assertEquals("Dialog count must be equal to maxDialogs after exhaustion", tcapStack1.getMaxDialogs(), provider.getCurrentDialogsCount());
    }

    /**
     * New test: multiple threads calling getNewDialog concurrently should
     * allocate unique dialog IDs without exceptions (within capacity).
     */
    @Test
    public void testConcurrentDialogAllocation() throws Exception {

        this.tcapStack1.stop();

        // Range and maxDialogs large enough for all allocations
        this.tcapStack1.setDialogIdRangeStart(1);
        this.tcapStack1.setDialogIdRangeEnd(10000);
        final int threads = 10;
        final int perThread = 50; // total 500 dialogs
        this.tcapStack1.setMaxDialogs(threads * perThread);
        this.tcapStack1.start();

        final TCAPProviderImpl provider = (TCAPProviderImpl) this.tcapStack1.getProvider();

        final Set<Long> dialogIds =
                Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
        final AtomicInteger duplicates = new AtomicInteger(0);
        final AtomicInteger exceptions = new AtomicInteger(0);

        ExecutorService exec = Executors.newFixedThreadPool(threads);
        List<Future<Void>> futures = new ArrayList<Future<Void>>();

        for (int i = 0; i < threads; i++) {
            futures.add(exec.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    for (int j = 0; j < perThread; j++) {
                        try {
                            Dialog d = provider.getNewDialog(peer1Address, peer2Address);
                            Long id = d.getLocalDialogId();
                            if (!dialogIds.add(id)) {
                                // duplicate allocation detected
                                duplicates.incrementAndGet();
                            }
                        } catch (Exception e) {
                            // Any TCAPException or other error counts here
                            exceptions.incrementAndGet();
                        }
                    }
                    return null;
                }
            }));
        }

        for (Future<Void> f : futures) {
            f.get();
        }
        exec.shutdownNow();

        assertEquals("No exceptions should be thrown during concurrent allocation within capacity", 0, exceptions.get());
        assertEquals("No duplicate dialog IDs should be allocated concurrently", 0, duplicates.get());
        assertEquals("All allocations must produce unique dialog IDs", threads * perThread, dialogIds.size());
    }

    @Test
    public void testUnstructuredDialogIsNotTracked() throws Exception {
        this.tcapStack1.stop();

        this.tcapStack1.setDialogIdRangeStart(1);
        this.tcapStack1.setDialogIdRangeEnd(100);
        this.tcapStack1.setMaxDialogs(50);
        this.tcapStack1.start();

        TCAPProviderImpl provider = (TCAPProviderImpl) this.tcapStack1.getProvider();

        int before = provider.getCurrentDialogsCount();
        Dialog d = provider.getNewUnstructuredDialog(peer1Address, peer2Address);

        assertEquals("Unstructured dialog must not be added to map", before, provider.getCurrentDialogsCount());
        assertEquals(false, d.isStructured());
        assertEquals(-1L, (long) d.getLocalDialogId());
    }

    @Test
    public void testDialogIdReuseAfterRelease() throws Exception {
        this.tcapStack1.stop();

        this.tcapStack1.setDialogIdRangeStart(10);
        this.tcapStack1.setDialogIdRangeEnd(20);
        this.tcapStack1.setMaxDialogs(5);
        this.tcapStack1.start();

        TCAPProviderImpl provider = (TCAPProviderImpl) this.tcapStack1.getProvider();

        Dialog[] dialogs = new Dialog[5];
        for (int i = 0; i < 5; i++) {
            dialogs[i] = provider.getNewDialog(peer1Address, peer2Address);
        }

        long firstId = dialogs[0].getLocalDialogId();
        // release all
        for (Dialog d : dialogs) {
            ((DialogImpl) d).setState(TRPseudoState.Expunged);
        }

        // after release, map should be empty
        assertEquals(provider.getCurrentDialogsCount(), 0);

        // new dialog should be able to reuse freed IDs
        Dialog d2 = provider.getNewDialog(peer1Address, peer2Address);
        long newId = d2.getLocalDialogId();

        assertTrue(newId >= 10 && newId <= 20);
        assertTrue(d2.isStructured());
    }

}