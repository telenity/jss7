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
                Collections.newSetFromMap(new ConcurrentHashMap<>());
        final AtomicInteger duplicates = new AtomicInteger(0);
        final AtomicInteger exceptions = new AtomicInteger(0);

        ExecutorService exec = Executors.newFixedThreadPool(threads);
        List<Future<Void>> futures = new ArrayList<>();

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


    // freed ids go to the back: an id is reused only after all others, also across the ring's wrap
    @Test
    public void testFreedIdsAreReusedInFifoOrder() throws Exception {
        this.tcapStack1.stop();
        this.tcapStack1.setDialogIdRangeStart(10);
        this.tcapStack1.setDialogIdRangeEnd(20); // 11 ids
        this.tcapStack1.setMaxDialogs(5);
        this.tcapStack1.start();
        TCAPProviderImpl provider = (TCAPProviderImpl) this.tcapStack1.getProvider();

        for (int i = 0; i < 30; i++) {
            Dialog d = provider.getNewDialog(peer1Address, peer2Address);
            assertEquals(10 + i % 11, d.getLocalDialogId().longValue());
            ((DialogImpl) d).setState(TRPseudoState.Expunged);
        }
        // next is 18; release out of order: 20 after the rest, then 18
        Dialog d18 = provider.getNewDialog(peer1Address, peer2Address);
        Dialog d19 = provider.getNewDialog(peer1Address, peer2Address);
        Dialog d20 = provider.getNewDialog(peer1Address, peer2Address);
        ((DialogImpl) d20).setState(TRPseudoState.Expunged);
        ((DialogImpl) d18).setState(TRPseudoState.Expunged);
        for (long id = 10; id <= 17; id++) {
            Dialog d = provider.getNewDialog(peer1Address, peer2Address);
            assertEquals(id, d.getLocalDialogId().longValue());
            ((DialogImpl) d).setState(TRPseudoState.Expunged);
        }
        assertEquals(20L, provider.getNewDialog(peer1Address, peer2Address).getLocalDialogId().longValue());
        assertEquals(18L, provider.getNewDialog(peer1Address, peer2Address).getLocalDialogId().longValue());
        assertEquals(19L, d19.getLocalDialogId().longValue());
    }

    // maxDialogs is normally below the range size; set the field past it to reach the "no free id" path
    @Test
    public void testNoFreeDialogIdWhenRangeExhausted() throws Exception {
        this.tcapStack1.stop();
        this.tcapStack1.setDialogIdRangeStart(10);
        this.tcapStack1.setDialogIdRangeEnd(14); // 5 ids
        this.tcapStack1.setMaxDialogs(2);
        this.tcapStack1.start();
        this.tcapStack1.maxDialogs = 100;
        TCAPProviderImpl provider = (TCAPProviderImpl) this.tcapStack1.getProvider();

        Dialog[] d = new Dialog[5];
        for (int i = 0; i < 5; i++) {
            d[i] = provider.getNewDialog(peer1Address, peer2Address);
            assertEquals(10L + i, d[i].getLocalDialogId().longValue());
        }
        assertNoFreeDialogId(provider);
        assertNoFreeDialogId(provider);
        assertEquals(5, provider.getCurrentDialogsCount());

        ((DialogImpl) d[3]).setState(TRPseudoState.Expunged);
        ((DialogImpl) d[1]).setState(TRPseudoState.Expunged);
        ((DialogImpl) d[1]).setState(TRPseudoState.Expunged); // twice: freed once
        assertEquals(13L, provider.getNewDialog(peer1Address, peer2Address).getLocalDialogId().longValue());
        assertEquals(11L, provider.getNewDialog(peer1Address, peer2Address).getLocalDialogId().longValue());
        assertNoFreeDialogId(provider);
    }

    // maxDialogs is checked before the free ids
    @Test
    public void testMaxDialogsCheckedFirst() throws Exception {
        this.tcapStack1.stop();
        this.tcapStack1.setDialogIdRangeStart(1);
        this.tcapStack1.setDialogIdRangeEnd(3);
        this.tcapStack1.setMaxDialogs(1);
        this.tcapStack1.start();
        TCAPProviderImpl provider = (TCAPProviderImpl) this.tcapStack1.getProvider();

        // smallest range: 3 ids, one dialog at a time, ids go round 1,2,3,1,..
        for (int i = 0; i < 10; i++) {
            Dialog d = provider.getNewDialog(peer1Address, peer2Address);
            assertEquals(1L + i % 3, d.getLocalDialogId().longValue());
            try {
                provider.getNewDialog(peer1Address, peer2Address);
                fail("maxDialogs is 1");
            } catch (TCAPException e) {
                assertEquals("Current dialog count exceeds its maximum value", e.getMessage());
            }
            ((DialogImpl) d).setState(TRPseudoState.Expunged);
        }
    }

    // ids at the top of the allowed range
    @Test
    public void testRangeAtIntegerMax() throws Exception {
        this.tcapStack1.stop();
        this.tcapStack1.setDialogIdRangeStart(Integer.MAX_VALUE - 4L);
        this.tcapStack1.setDialogIdRangeEnd(Integer.MAX_VALUE);
        this.tcapStack1.setMaxDialogs(3);
        this.tcapStack1.start();
        TCAPProviderImpl provider = (TCAPProviderImpl) this.tcapStack1.getProvider();

        for (int i = 0; i < 12; i++) {
            Dialog d = provider.getNewDialog(peer1Address, peer2Address);
            assertEquals(Integer.MAX_VALUE - 4L + i % 5, d.getLocalDialogId().longValue());
            ((DialogImpl) d).setState(TRPseudoState.Expunged);
        }
    }

    // stop drops all ids; start refills the configured range from its start, also with a new range
    @Test
    public void testStopStartResetsIds() throws Exception {
        this.tcapStack1.stop();
        this.tcapStack1.setDialogIdRangeStart(10);
        this.tcapStack1.setDialogIdRangeEnd(20);
        this.tcapStack1.setMaxDialogs(5);
        this.tcapStack1.start();
        TCAPProviderImpl provider = (TCAPProviderImpl) this.tcapStack1.getProvider();

        for (int i = 0; i < 7; i++) {
            Dialog d = provider.getNewDialog(peer1Address, peer2Address);
            ((DialogImpl) d).setState(TRPseudoState.Expunged);
        }
        Dialog live = provider.getNewDialog(peer1Address, peer2Address);
        assertEquals(17L, live.getLocalDialogId().longValue());

        this.tcapStack1.stop();
        assertEquals(0, provider.getCurrentDialogsCount());
        assertNoFreeDialogId(provider); // stopped: no ids at all
        ((DialogImpl) live).setState(TRPseudoState.Expunged); // released after stop: not given back
        assertNoFreeDialogId(provider);

        this.tcapStack1.start();
        for (long id = 10; id <= 20; id++) {
            Dialog d = provider.getNewDialog(peer1Address, peer2Address);
            assertEquals(id, d.getLocalDialogId().longValue());
            ((DialogImpl) d).setState(TRPseudoState.Expunged);
        }
        assertEquals(10L, provider.getNewDialog(peer1Address, peer2Address).getLocalDialogId().longValue());

        this.tcapStack1.stop();
        this.tcapStack1.setDialogIdRangeStart(100);
        this.tcapStack1.setDialogIdRangeEnd(103);
        this.tcapStack1.setMaxDialogs(2);
        this.tcapStack1.start();
        for (int i = 0; i < 9; i++) {
            Dialog d = provider.getNewDialog(peer1Address, peer2Address);
            assertEquals(100L + i % 4, d.getLocalDialogId().longValue());
            ((DialogImpl) d).setState(TRPseudoState.Expunged);
        }

        // a larger range after a smaller one
        this.tcapStack1.stop();
        this.tcapStack1.setDialogIdRangeStart(1);
        this.tcapStack1.setDialogIdRangeEnd(1000);
        this.tcapStack1.setMaxDialogs(10);
        this.tcapStack1.start();
        this.tcapStack1.maxDialogs = 2000;
        for (int i = 0; i < 1000; i++) {
            assertEquals(i + 1L, provider.getNewDialog(peer1Address, peer2Address).getLocalDialogId().longValue());
        }
        assertNoFreeDialogId(provider);
    }

    // a dialog from before a stop/start, released after the restart while its id belongs to a new dialog:
    // the id is not freed again, so it is never handed out twice
    @Test
    public void testDialogFromBeforeRestartReleasedLater() throws Exception {
        this.tcapStack1.stop();
        this.tcapStack1.setDialogIdRangeStart(10);
        this.tcapStack1.setDialogIdRangeEnd(14); // 5 ids
        this.tcapStack1.setMaxDialogs(2);
        this.tcapStack1.start();
        TCAPProviderImpl provider = (TCAPProviderImpl) this.tcapStack1.getProvider();
        Dialog old = provider.getNewDialog(peer1Address, peer2Address);
        assertEquals(10L, old.getLocalDialogId().longValue());

        this.tcapStack1.stop();
        this.tcapStack1.start();
        this.tcapStack1.maxDialogs = 100;
        Dialog current = provider.getNewDialog(peer1Address, peer2Address);
        assertEquals(10L, current.getLocalDialogId().longValue());

        ((DialogImpl) old).setState(TRPseudoState.Expunged);
        for (long id = 11; id <= 14; id++) {
            assertEquals(id, provider.getNewDialog(peer1Address, peer2Address).getLocalDialogId().longValue());
        }
        assertNoFreeDialogId(provider);
        assertEquals(5, provider.getCurrentDialogsCount());
    }

    // threads allocate and release at once: a live id is never given twice, and afterwards
    // the whole range is still there exactly once
    @Test
    public void testConcurrentAllocateReleaseNoDuplicates() throws Exception {
        this.tcapStack1.stop();
        this.tcapStack1.setDialogIdRangeStart(1);
        this.tcapStack1.setDialogIdRangeEnd(40);
        this.tcapStack1.setMaxDialogs(30);
        this.tcapStack1.start();
        final TCAPProviderImpl provider = (TCAPProviderImpl) this.tcapStack1.getProvider();

        final int threads = 8;
        final int cycles = 3000;
        final Set<Long> live = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
        final AtomicInteger duplicates = new AtomicInteger();
        final AtomicInteger outOfRange = new AtomicInteger();
        final java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        List<Future<Void>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(exec.submit(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    go.await();
                    java.util.ArrayDeque<Dialog> held = new java.util.ArrayDeque<>();
                    for (int j = 0; j < cycles; j++) {
                        Dialog d = provider.getNewDialog(peer1Address, peer2Address); // at most 8*3 live < 30
                        long id = d.getLocalDialogId();
                        if (id < 1 || id > 40)
                            outOfRange.incrementAndGet();
                        if (!live.add(id))
                            duplicates.incrementAndGet();
                        held.addLast(d);
                        if (held.size() == 3 || j == cycles - 1) {
                            while (!held.isEmpty()) {
                                Dialog r = held.removeFirst();
                                live.remove(r.getLocalDialogId()); // before release, so the id is not yet reusable
                                ((DialogImpl) r).setState(TRPseudoState.Expunged);
                            }
                        }
                    }
                    return null;
                }
            }));
        }
        go.countDown();
        for (Future<Void> f : futures) {
            f.get(60, java.util.concurrent.TimeUnit.SECONDS);
        }
        exec.shutdownNow();

        assertEquals(0, duplicates.get());
        assertEquals(0, outOfRange.get());
        assertEquals(0, provider.getCurrentDialogsCount());

        this.tcapStack1.maxDialogs = 100;
        Set<Long> ids = new java.util.HashSet<>();
        for (int i = 0; i < 40; i++) {
            assertTrue(ids.add(provider.getNewDialog(peer1Address, peer2Address).getLocalDialogId()));
        }
        assertEquals(40, ids.size());
        assertNoFreeDialogId(provider);
    }

    private void assertNoFreeDialogId(TCAPProviderImpl provider) {
        try {
            provider.getNewDialog(peer1Address, peer2Address);
            fail("no free dialog id");
        } catch (TCAPException e) {
            assertEquals("No free dialog id available", e.getMessage());
        }
    }
}
