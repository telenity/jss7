/*
 * TeleStax, Open Source Cloud Communications  Copyright 2012.
 * and individual contributors
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
package org.mobicents.protocols.ss7.m3ua.impl.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class M3UASchedulerTest {

    @Test
    public void testExecuteNull() {
        M3UAScheduler scheduler = new M3UAScheduler();
        scheduler.execute(null);
        scheduler.run();
    }

    @Test
    public void testSingleTaskRuns() {
        M3UAScheduler scheduler = new M3UAScheduler();
        final AtomicBoolean ran = new AtomicBoolean(false);
        M3UATask task = new M3UATask() {
            @Override
            public void tick(long now) {
                ran.set(true);
            }
        };
        scheduler.execute(task);
        scheduler.run();
        assertTrue("Task should have been run", ran.get());
    }

    @Test
    public void testCancelledTaskBeforeRun() {
        M3UAScheduler scheduler = new M3UAScheduler();
        final AtomicBoolean ran = new AtomicBoolean(false);
        M3UATask task = new M3UATask() {
            @Override
            public void tick(long now) {
                ran.set(true);
            }
        };
        task.cancel();
        scheduler.execute(task);
        scheduler.run();
        assertFalse("Cancelled task should not run", ran.get());
    }

    @Test
    public void testCancelledTaskAfterRunDoesNotReschedule() {
        M3UAScheduler scheduler = new M3UAScheduler();
        final AtomicInteger runCount = new AtomicInteger(0);
        M3UATask task = new M3UATask() {
            @Override
            public void tick(long now) {
                runCount.incrementAndGet();
                cancel();
            }
        };
        scheduler.execute(task);
        scheduler.run();
        assertEquals(1, runCount.get());
        scheduler.run();
        assertEquals("Should not run again after cancel", 1, runCount.get());
    }

    @Test
    public void testTaskMovedToNextPool() {
        M3UAScheduler scheduler = new M3UAScheduler();
        final AtomicInteger runCount = new AtomicInteger(0);
        M3UATask task = new M3UATask() {
            @Override
            public void tick(long now) {
                runCount.incrementAndGet();
            }
        };
        scheduler.execute(task);
        scheduler.run();
        assertEquals(1, runCount.get());
        scheduler.run();
        assertEquals(2, runCount.get());
    }

    @Test
    public void testMultipleTasks() {
        M3UAScheduler scheduler = new M3UAScheduler();
        final AtomicInteger count = new AtomicInteger(0);
        for (int i = 0; i < 5; i++) {
            scheduler.execute(new M3UATask() {
                @Override
                public void tick(long now) {
                    count.incrementAndGet();
                }
            });
        }
        scheduler.run();
        assertEquals("All 5 tasks should run once", 5, count.get());
        scheduler.run();
        assertEquals("All 5 tasks should run twice", 10, count.get());
    }

    @Test
    public void testMixedCancelledAndActive() {
        M3UAScheduler scheduler = new M3UAScheduler();
        final AtomicInteger activeCount = new AtomicInteger(0);

        M3UATask cancelled = new M3UATask() {
            @Override
            public void tick(long now) {
                activeCount.incrementAndGet();
            }
        };
        cancelled.cancel();
        scheduler.execute(cancelled);

        M3UATask active = new M3UATask() {
            @Override
            public void tick(long now) {
                activeCount.incrementAndGet();
            }
        };
        scheduler.execute(active);

        scheduler.run();
        assertEquals("Only active task should run", 1, activeCount.get());
    }

    @Test
    public void testDoubleBufferingSwitchesPools() {
        M3UAScheduler scheduler = new M3UAScheduler();
        final AtomicInteger count = new AtomicInteger(0);
        M3UATask task = new M3UATask() {
            @Override
            public void tick(long now) {
                count.incrementAndGet();
            }
        };
        scheduler.execute(task);
        for (int i = 0; i < 10; i++) {
            scheduler.run();
        }
        assertEquals("Task should run on every tick", 10, count.get());
    }
}
