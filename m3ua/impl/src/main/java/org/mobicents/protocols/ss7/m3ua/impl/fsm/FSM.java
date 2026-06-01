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

import org.apache.log4j.Logger;
import org.mobicents.protocols.ss7.m3ua.impl.scheduler.M3UATask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author amit bhayani
 */
public class FSM extends M3UATask {

    protected static final Logger logger = Logger.getLogger(FSM.class);

    public static final String ATTRIBUTE_MESSAGE = "message";

    private String name;

    // first and last states in fsm
    protected FSMState start;
    protected FSMState end;

    // intermediate states
    private Map<String, FSMState> states = new ConcurrentHashMap<>();

    private volatile FSMState currentState;

    private Map<String, Object> attributes = new ConcurrentHashMap<>();

    private FSMState oldState;

    public FSM(String name) {
        this.name = name;
    }

    public FSMState getState() {
        return currentState;
    }

    public void setStart(String name) {
        // the start state already has value which differs from current state?
        if (this.start != null && currentState != null) {
            throw new IllegalStateException("Start state can't be changed now");
        }
        this.start = states.get(name);
        this.currentState = start;
    }

    public void setEnd(String name) {
        this.end = states.get(name);
    }

    public FSMState createState(String name) {
        FSMState s = new FSMState(this, name);
        states.put(name, s);
        return s;
    }

    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    private FSMState requireState(String name) {
        if (!states.containsKey(name)) {
            throw new IllegalStateException("Unknown state: " + name);
        }
        return states.get(name);
    }

    public Transition createTransition(String name, String from, String to) {
        if (name.equals("timeout")) {
            throw new IllegalArgumentException("timeout is illegal name for transition");
        }
        FSMState fromState = requireState(from);
        FSMState toState = requireState(to);
        Transition t = new Transition(name, toState);
        fromState.add(t);
        return t;
    }

    public Transition createTimeoutTransition(String from, String to, long timeout) {
        FSMState fromState = requireState(from);
        FSMState toState = requireState(to);
        Transition t = new Transition("timeout", toState);
        fromState.timeout = timeout;
        fromState.add(t);
        return t;
    }

    /**
     * Processes transition.
     *
     * @param name the name of transition.
     */
    public void signal(String name) throws UnknownTransitionException {

        if (start == null) {
            throw new IllegalStateException("The start state is not defined");
        }
        if (end == null) {
            throw new IllegalStateException("The end state is not defined");
        }

        oldState = currentState;
        // switch to next state
        currentState = currentState.signal(name);
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("%s Transition to=%s", this, name));
        }
    }

    public void tick(long now) {
        if (currentState != null) {
            currentState.tick(now);
        }
    }

    @Override
    public String toString() {
        return String.format("FSM.name=%s old state=%s, current state=%s", this.name, (this.oldState != null) ? this.oldState.getName() : "",
                (this.currentState != null) ? this.currentState.getName() : "");
    }

}
