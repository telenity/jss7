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
package org.mobicents.protocols.ss7.m3ua.impl.fsm;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.mobicents.protocols.ss7.m3ua.impl.scheduler.M3UAScheduler;
import org.junit.AfterClass;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author amit bhayani
 *
 */
public class FSMTest {

    private M3UAScheduler m3uaScheduler = new M3UAScheduler();
    private ScheduledExecutorService scheduledExecutorService = null;
    private volatile boolean timedOut = false;
    private volatile boolean stateEntered = false;
    private volatile boolean stateExited = false;
    private volatile boolean transitionHandlerCalled = false;
    private volatile int timeOutCount = 0;

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {

        timedOut = false;
        stateEntered = false;
        stateExited = false;
        transitionHandlerCalled = false;

        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleAtFixedRate(m3uaScheduler, 500, 500, TimeUnit.MILLISECONDS);
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testOnExit() throws Exception {
        FSM fsm = new FSM("test");

        fsm.createState("STATE1").setOnExit(new AsState1Exit(fsm));
        fsm.createState("STATE2");

        fsm.setStart("STATE1");
        fsm.setEnd("STATE2");

        fsm.createTransition("GoToSTATE2", "STATE1", "STATE2");

        m3uaScheduler.execute(fsm);

        fsm.signal("GoToSTATE2");

        assertTrue(stateExited);
        assertEquals("STATE2", fsm.getState().getName());
    }

    @Test
    public void testTransitionHandler() throws Exception {
        FSM fsm = new FSM("test");

        fsm.createState("STATE1");
        fsm.createState("STATE2");

        fsm.setStart("STATE1");
        fsm.setEnd("STATE2");

        fsm.createTransition("GoToSTATE2", "STATE1", "STATE2").setHandler(new State1ToState2Transition());

        m3uaScheduler.execute(fsm);

        fsm.signal("GoToSTATE2");

        assertTrue(transitionHandlerCalled);
        assertEquals("STATE2", fsm.getState().getName());
    }

    /**
     * In this test we set TransitionHandler to cancel the transition and yet
     * original timeout is to be respected
     *
     * @throws Exception
     */
    @Test
    public void testNoTransitionHandler() throws Exception {
        FSM fsm = new FSM("test");

        fsm.createState("STATE1");
        fsm.createState("STATE2");

        fsm.setStart("STATE1");
        fsm.setEnd("STATE2");

        // Transition shouldn't happen
        fsm.createTransition("GoToSTATE2", "STATE1", "STATE2").setHandler(new State1ToState2NoTransition());

        m3uaScheduler.execute(fsm);

        fsm.signal("GoToSTATE2");

        assertTrue(transitionHandlerCalled);
        assertEquals("STATE1", fsm.getState().getName());
    }

    @Test
    public void testOnEnter() throws Exception {
        FSM fsm = new FSM("test");

        fsm.createState("STATE1");
        fsm.createState("STATE2").setOnEnter(new AsState2Enter(fsm));

        fsm.setStart("STATE1");
        fsm.setEnd("STATE2");

        fsm.createTransition("GoToSTATE2", "STATE1", "STATE2");

        m3uaScheduler.execute(fsm);

        fsm.signal("GoToSTATE2");

        assertTrue(stateEntered);
        assertEquals("STATE2", fsm.getState().getName());
    }

    @Test
    public void testTimeout() throws Exception {
        FSM fsm = new FSM("test");

        fsm.createState("STATE1");
        fsm.createState("STATE2").setOnTimeOut(new AsState2Timeout(fsm), 2000);

        fsm.setStart("STATE1");
        fsm.setEnd("STATE2");

        fsm.createTransition("GoToSTATE2", "STATE1", "STATE2");

        m3uaScheduler.execute(fsm);

        fsm.signal("GoToSTATE2");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        assertTrue(timedOut);
        assertEquals("STATE2", fsm.getState().getName());

    }

    @Test
    public void testTimeoutNoTransition() throws Exception {
        FSM fsm = new FSM("test");

        fsm.createState("STATE1");
        fsm.createState("STATE2").setOnTimeOut(new AsState2Timeout(fsm), 2000);
        fsm.createState("STATE3");

        fsm.setStart("STATE1");
        fsm.setEnd("STATE2");

        fsm.createTransition("GoToSTATE2", "STATE1", "STATE2");
        fsm.createTransition("GoToSTATE3", "STATE2", "STATE3").setHandler(new NoTransition());

        m3uaScheduler.execute(fsm);

        fsm.signal("GoToSTATE2");
        assertEquals("STATE2", fsm.getState().getName());
        fsm.signal("GoToSTATE3");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        assertTrue(timedOut);
        assertEquals("STATE2", fsm.getState().getName());

    }

    @Test
    public void testAttributes() throws Exception {
        FSM fsm = new FSM("test");

        fsm.createState("STATE1");
        fsm.createState("STATE2");

        fsm.setStart("STATE1");
        fsm.setEnd("STATE2");

        fsm.setAttribute("key1", "value1");
        fsm.setAttribute("key2", Integer.valueOf(42));

        assertEquals("value1", fsm.getAttribute("key1"));
        assertEquals(Integer.valueOf(42), fsm.getAttribute("key2"));

        fsm.removeAttribute("key1");
        assertNull(fsm.getAttribute("key1"));
        assertEquals(Integer.valueOf(42), fsm.getAttribute("key2"));

        fsm.createTransition("GoToSTATE2", "STATE1", "STATE2");
        m3uaScheduler.execute(fsm);
        fsm.signal("GoToSTATE2");
        assertEquals("STATE2", fsm.getState().getName());
    }

    @Test
    public void testTimeoutTransition() throws Exception {
        FSM fsm = new FSM("test");

        fsm.createState("STATE1");
        fsm.createState("STATE2");
        fsm.createState("STATE3");

        fsm.setStart("STATE1");
        fsm.setEnd("STATE3");

        fsm.createTransition("GoToSTATE2", "STATE1", "STATE2");
        fsm.createTimeoutTransition("STATE2", "STATE2", 1000l).setHandler(new State2TimeoutTransition());

        m3uaScheduler.execute(fsm);

        fsm.signal("GoToSTATE2");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        assertTrue((2 <= timeOutCount) && (timeOutCount <= 3));
        assertEquals("STATE2", fsm.getState().getName());

    }

    @Test(expected = IllegalStateException.class)
    public void testSignalBeforeStart() throws Exception {
        FSM fsm = new FSM("test");
        fsm.createState("STATE1");
        fsm.createState("STATE2");
        fsm.setEnd("STATE2");
        fsm.signal("GoToSTATE2");
    }

    @Test(expected = IllegalStateException.class)
    public void testSignalBeforeEnd() throws Exception {
        FSM fsm = new FSM("test");
        fsm.createState("STATE1");
        fsm.createState("STATE2");
        fsm.setStart("STATE1");
        fsm.signal("GoToSTATE2");
    }

    @Test(expected = IllegalStateException.class)
    public void testCreateTransitionInvalidFrom() {
        FSM fsm = new FSM("test");
        fsm.createState("STATE2");
        fsm.createTransition("t", "UNKNOWN", "STATE2");
    }

    @Test(expected = IllegalStateException.class)
    public void testCreateTransitionInvalidTo() {
        FSM fsm = new FSM("test");
        fsm.createState("STATE1");
        fsm.createTransition("t", "STATE1", "UNKNOWN");
    }

    @Test(expected = IllegalStateException.class)
    public void testCreateTimeoutTransitionInvalidFrom() {
        FSM fsm = new FSM("test");
        fsm.createState("STATE2");
        fsm.createTimeoutTransition("UNKNOWN", "STATE2", 1000);
    }

    @Test(expected = IllegalStateException.class)
    public void testCreateTimeoutTransitionInvalidTo() {
        FSM fsm = new FSM("test");
        fsm.createState("STATE1");
        fsm.createTimeoutTransition("STATE1", "UNKNOWN", 1000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateTransitionWithTimeoutName() {
        FSM fsm = new FSM("test");
        fsm.createState("STATE1");
        fsm.createState("STATE2");
        fsm.createTransition("timeout", "STATE1", "STATE2");
    }

    @Test(expected = IllegalStateException.class)
    public void testSetStartAfterCurrentState() {
        FSM fsm = new FSM("test");
        fsm.createState("STATE1");
        fsm.createState("STATE2");
        fsm.setStart("STATE1");
        fsm.setEnd("STATE2");
        // currentState is now STATE1; changing start should throw
        fsm.setStart("STATE2");
    }

    @Test
    public void testCancelLeavePreservesTimeout() throws Exception {
        FSM fsm = new FSM("test");
        fsm.createState("STATE1");
        fsm.createState("STATE2");
        fsm.createState("STATE3");

        fsm.setStart("STATE1");
        fsm.setEnd("STATE3");

        // Transition from STATE2 to STATE3; handler cancels (returns false)
        fsm.createTransition("to3", "STATE2", "STATE3").setHandler(new TransitionHandler() {
            @Override
            public boolean process(FSMState state) {
                return false; // cancel transition
            }
        });

        // STATE2 has a timeout that transitions to itself (timeout executes)
        fsm.createTimeoutTransition("STATE2", "STATE2", 1000l).setHandler(new TransitionHandler() {
            @Override
            public boolean process(FSMState state) {
                timeOutCount++;
                return true;
            }
        });

        m3uaScheduler.execute(fsm);
        fsm.createTransition("GoToSTATE2", "STATE1", "STATE2");
        fsm.signal("GoToSTATE2");
        assertEquals("STATE2", fsm.getState().getName());

        // Try to transition to STATE3 but get cancelled
        fsm.signal("to3");
        // Should still be in STATE2
        assertEquals("STATE2", fsm.getState().getName());

        // Timeout should still fire (cancelLeave preserves activated time)
        Thread.sleep(2000);
        assertTrue("Timeout should have fired after cancelLeave", timeOutCount >= 1);
    }

    @Test
    public void testRemoveAttribute() throws Exception {
        FSM fsm = new FSM("test");
        fsm.createState("STATE1");
        fsm.createState("STATE2");
        fsm.setStart("STATE1");
        fsm.setEnd("STATE2");
        fsm.createTransition("GoToSTATE2", "STATE1", "STATE2");

        fsm.setAttribute("key", "value");
        assertEquals("value", fsm.getAttribute("key"));
        fsm.removeAttribute("key");
        assertNull(fsm.getAttribute("key"));
        // removing non-existent key should not throw
        fsm.removeAttribute("nonexistent");
    }

    @Test
    public void testUnknownTransition() throws Exception {
        FSM fsm = new FSM("test");
        fsm.createState("STATE1");
        fsm.createState("STATE2");
        fsm.setStart("STATE1");
        fsm.setEnd("STATE2");
        fsm.createTransition("GoToSTATE2", "STATE1", "STATE2");
        m3uaScheduler.execute(fsm);

        boolean threw = false;
        try {
            fsm.signal("UNKNOWN");
        } catch (UnknownTransitionException e) {
            threw = true;
        }
        assertTrue("Should throw UnknownTransitionException for unknown transition", threw);
    }

    class AsState1Exit implements FSMStateEventHandler {

        private FSM fsm;

        public AsState1Exit(FSM fsm) {
            this.fsm = fsm;
        }

        public void onEvent(FSMState state) {
            stateExited = true;
        }
    }

    class AsState2Timeout implements FSMStateEventHandler {

        private FSM fsm;

        public AsState2Timeout(FSM fsm) {
            this.fsm = fsm;
        }

        public void onEvent(FSMState state) {
            timedOut = true;
        }
    }

    class AsState2Enter implements FSMStateEventHandler {

        private FSM fsm;

        public AsState2Enter(FSM fsm) {
            this.fsm = fsm;
        }

        public void onEvent(FSMState state) {
            stateEntered = true;
        }
    }

    class State1ToState2Transition implements TransitionHandler {

        @Override
        public boolean process(FSMState state) {
            transitionHandlerCalled = true;
            return true;
        }

    }

    class State2TimeoutTransition implements TransitionHandler {

        @Override
        public boolean process(FSMState state) {
            timeOutCount++;
            return true;
        }

    }

    class State1ToState2NoTransition implements TransitionHandler {

        @Override
        public boolean process(FSMState state) {
            transitionHandlerCalled = true;
            return false;
        }

    }

    class NoTransition implements TransitionHandler {

        @Override
        public boolean process(FSMState state) {
            return false;
        }

    }
}
