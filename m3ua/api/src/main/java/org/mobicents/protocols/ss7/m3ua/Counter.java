package org.mobicents.protocols.ss7.m3ua;

import java.util.concurrent.atomic.AtomicLong;

public class Counter {

    private AtomicLong count = new AtomicLong(0);

    public void add() {
        add(1);
    }

    public void add(long increment) {
        count.addAndGet(increment);
    }

    public long getAndReset() {
        return count.getAndSet(0);
    }

}
