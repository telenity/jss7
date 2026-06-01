package org.mobicents.protocols.ss7.m3ua.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.mobicents.protocols.ss7.m3ua.Counter;

public class M3UACounterProviderTest {

    @Test
    public void testSingleThreadIncrementAndSnapshot() {
        M3UACounterProviderImpl provider = new M3UACounterProviderImpl(null);

        provider.updatePacketsPerAssTx("assoc1");
        provider.updatePacketsPerAssTx("assoc1");
        provider.updatePacketsPerAssTx("assoc1");

        Map<String, Counter> snapshot = provider.getPacketsPerAssTx();
        assertEquals(1, snapshot.size());
        assertEquals(3, snapshot.get("assoc1").getAndReset());
        assertEquals(0, snapshot.get("assoc1").getAndReset());
    }

    @Test
    public void testMultipleAssociations() {
        M3UACounterProviderImpl provider = new M3UACounterProviderImpl(null);

        provider.updatePacketsPerAssTx("assoc1");
        provider.updatePacketsPerAssTx("assoc1");
        provider.updatePacketsPerAssRx("assoc1");
        provider.updatePacketsPerAssTx("assoc2");

        Map<String, Counter> txSnapshot = provider.getPacketsPerAssTx();
        assertEquals(2, txSnapshot.size());
        assertEquals(2, txSnapshot.get("assoc1").getAndReset());
        assertEquals(1, txSnapshot.get("assoc2").getAndReset());

        Map<String, Counter> rxSnapshot = provider.getPacketsPerAssRx();
        assertEquals(1, rxSnapshot.size());
        assertEquals(1, rxSnapshot.get("assoc1").getAndReset());
    }

    @Test
    public void testSnapshotResetsProvider() {
        M3UACounterProviderImpl provider = new M3UACounterProviderImpl(null);

        provider.updatePacketsPerAssTx("assoc1");
        Map<String, Counter> snapshot = provider.getPacketsPerAssTx();
        assertEquals(1, snapshot.size());

        Map<String, Counter> emptySnapshot = provider.getPacketsPerAssTx();
        assertEquals(0, emptySnapshot.size());
    }

    @Test
    public void testConcurrentIncrements() throws Exception {
        final M3UACounterProviderImpl provider = new M3UACounterProviderImpl(null);
        final String assocName = "concurrent-assoc";
        final int threadCount = 4;
        final int incrementsPerThread = 100000;
        final CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    provider.updatePacketsPerAssTx(assocName);
                }
                latch.countDown();
            });
            t.start();
        }

        latch.await();

        Map<String, Counter> snapshot = provider.getPacketsPerAssTx();
        assertEquals(1, snapshot.size());
        assertEquals(
            threadCount * incrementsPerThread,
            snapshot.get(assocName).getAndReset());
    }

    @Test
    public void testConcurrentIncrementsMultipleAssociations() throws Exception {
        final M3UACounterProviderImpl provider = new M3UACounterProviderImpl(null);
        final int threadCount = 4;
        final int incrementsPerThread = 50000;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicLong totalExpected = new AtomicLong(0);

        for (int i = 0; i < threadCount; i++) {
            final String assocName = "assoc-" + i;
            totalExpected.addAndGet(incrementsPerThread);
            Thread t = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    provider.updatePacketsPerAssTx(assocName);
                    provider.updatePacketsPerAssRx(assocName);
                }
                latch.countDown();
            });
            t.start();
        }

        latch.await();

        Map<String, Counter> txSnapshot = provider.getPacketsPerAssTx();
        Map<String, Counter> rxSnapshot = provider.getPacketsPerAssRx();
        assertEquals(threadCount, txSnapshot.size());
        assertEquals(threadCount, rxSnapshot.size());
        for (int i = 0; i < threadCount; i++) {
            String assocName = "assoc-" + i;
            assertEquals(incrementsPerThread, txSnapshot.get(assocName).getAndReset());
            assertEquals(incrementsPerThread, rxSnapshot.get(assocName).getAndReset());
        }
    }
}
