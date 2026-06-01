package org.mobicents.protocols.ss7.m3ua;

import java.util.concurrent.atomic.LongAdder;

public class Counter {

    private LongAdder count = new LongAdder();

    public void add() {
        add(1);
    }

    public void add(long increment) {
        count.add(increment);
    }

    public long getAndReset() {
        return count.sumThenReset();
    }

}
