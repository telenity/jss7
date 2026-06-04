/*
 * Concurrency regression tests for M3UA FSM (C1/C2).
 */
package org.mobicents.protocols.ss7.m3ua.impl.fsm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.protocols.ss7.m3ua.impl.scheduler.M3UAScheduler;

/**
 * Stresses {@link FSM} under concurrent {@link FSM#signal(String)} and scheduler {@link FSM#tick(long)}.
 */
public class FSMConcurrencyTest {

    private static final Set<String> VALID_STATES = new HashSet<String>(Arrays.asList("STATE_A", "STATE_B"));

    private M3UAScheduler m3uaScheduler;
    private ExecutorService schedulerThread;
    private ExecutorService workers;

    @Before
    public void setUp() {
        m3uaScheduler = new M3UAScheduler();
        schedulerThread = Executors.newSingleThreadExecutor();
        schedulerThread.execute(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    m3uaScheduler.run();
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
        workers = Executors.newFixedThreadPool(4);
    }

    @After
    public void tearDown() throws InterruptedException {
        if (schedulerThread != null) {
            schedulerThread.shutdownNow();
            schedulerThread.awaitTermination(2, TimeUnit.SECONDS);
        }
        if (workers != null) {
            workers.shutdownNow();
            workers.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static FSM createToggleFsm() throws Exception {
        FSM fsm = new FSM("concurrency-test");
        fsm.createState("STATE_A");
        fsm.createState("STATE_B");
        fsm.setStart("STATE_A");
        fsm.setEnd("STATE_B");
        fsm.createTransition("toB", "STATE_A", "STATE_B");
        fsm.createTransition("toA", "STATE_B", "STATE_A");
        return fsm;
    }

    @Test
    public void testConcurrentSignalAndSchedulerTick() throws Exception {
        final FSM fsm = createToggleFsm();
        m3uaScheduler.execute(fsm);

        final int iterationsPerThread = 400;
        final AtomicBoolean failed = new AtomicBoolean(false);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(4);

        for (int t = 0; t < 4; t++) {
            workers.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        for (int i = 0; i < iterationsPerThread; i++) {
                            String current = fsm.getState().getName();
                            try {
                                if ("STATE_A".equals(current)) {
                                    fsm.signal("toB");
                                } else {
                                    fsm.signal("toA");
                                }
                            } catch (UnknownTransitionException e) {
                                // Another thread may have changed state between getState() and signal().
                            }
                        }
                    } catch (Exception e) {
                        failed.set(true);
                    } finally {
                        done.countDown();
                    }
                }
            });
        }

        start.countDown();
        assertTrue("Workers did not finish in time", done.await(30, TimeUnit.SECONDS));

        assertFalse("Unexpected exception during concurrent signaling", failed.get());
        assertNotNull(fsm.getState());
        assertTrue("FSM in unexpected state after stress: " + fsm.getState().getName(),
                VALID_STATES.contains(fsm.getState().getName()));
    }

    @Test
    public void testConcurrentAttributeAccess() throws Exception {
        final FSM fsm = createToggleFsm();
        m3uaScheduler.execute(fsm);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(4);

        for (int t = 0; t < 4; t++) {
            final int threadId = t;
            workers.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        for (int i = 0; i < 500; i++) {
                            fsm.setAttribute("key-" + threadId, Integer.valueOf(i));
                            Object v = fsm.getAttribute("key-" + threadId);
                            if (v != null && !(v instanceof Integer)) {
                                throw new IllegalStateException("unexpected attribute type");
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }
            });
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
    }
}
