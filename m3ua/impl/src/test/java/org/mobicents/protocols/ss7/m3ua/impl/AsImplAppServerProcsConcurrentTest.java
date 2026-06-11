/*
 * Concurrency regression tests for AsImpl appServerProcs.
 */
package org.mobicents.protocols.ss7.m3ua.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.protocols.ss7.m3ua.Asp;

public class AsImplAppServerProcsConcurrentTest {

    private ExecutorService pool;

    @Before
    public void setUp() {
        pool = Executors.newFixedThreadPool(4);
    }

    @After
    public void tearDown() {
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    @Test
    public void testCopyOnWriteAppServerProcsConcurrentIteration() throws Exception {
        final AsImpl as = new AsImpl();
        for (int i = 0; i < 100; i++) {
            as.addAppServerProcess(createAsp("initial-" + i));
        }

        final AtomicInteger exceptionCount = new AtomicInteger(0);
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(3);

        pool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    startGate.await();
                    for (int iter = 0; iter < 500; iter++) {
                        for (Asp asp : as.appServerProcs) {
                            asp.getName();
                        }
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }
        });

        pool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    startGate.await();
                    for (int iter = 0; iter < 500; iter++) {
                        String name = "dynamic-" + iter;
                        as.addAppServerProcess(createAsp(name));
                        as.removeAppServerProcess(name);
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }
        });

        pool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    startGate.await();
                    for (int iter = 0; iter < 500; iter++) {
                        as.getAspList().size();
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }
        });

        startGate.countDown();
        assertTrue("Workers did not finish in time", done.await(30, TimeUnit.SECONDS));

        assertEquals("CopyOnWrite appServerProcs access caused exceptions",
                0, exceptionCount.get());
    }

    @Test
    public void testAppServerProcsReflectsRemove() throws Exception {
        AsImpl as = new AsImpl();
        AspImpl first = createAsp("first");
        AspImpl second = createAsp("second");

        as.addAppServerProcess(first);
        as.addAppServerProcess(second);
        as.removeAppServerProcess(first.getName());

        assertEquals(1, as.getAspList().size());
        assertSame(second, as.getAspList().get(0));
    }

    private AspImpl createAsp(String name) {
        AspImpl asp = new AspImpl();
        asp.name = name;
        return asp;
    }

}
