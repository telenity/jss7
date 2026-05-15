package org.mobicents.protocols.ss7.m3ua.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.mobicents.protocols.ss7.m3ua.Counter;
import org.mobicents.protocols.ss7.m3ua.M3UACounterProvider;

public class M3UACounterProviderImpl implements M3UACounterProvider {

    private M3UAManagementImpl m3uaManagementImpl;

    private Map<String, Counter> packetsPerAssTx;
    private Map<String, Counter> packetsPerAssRx;

    public M3UACounterProviderImpl(M3UAManagementImpl m3uaManagementImpl) {
        this.m3uaManagementImpl = m3uaManagementImpl;

        this.packetsPerAssTx = new ConcurrentHashMap<>();
        this.packetsPerAssRx = new ConcurrentHashMap<>();
    }

    @Override
    public Map<String, Counter> getPacketsPerAssTx() {
        Map<String, Counter> current = packetsPerAssTx;
        packetsPerAssTx = new ConcurrentHashMap<>();
        return current;
    }

    public void updatePacketsPerAssTx(String assocName) {
        packetsPerAssTx.compute(assocName, (key, counter) -> {
            if (counter == null) {
                counter = new Counter();
            }
            counter.add();
            return counter;
        });
    }

    @Override
    public Map<String, Counter> getPacketsPerAssRx() {
        Map<String, Counter> current = packetsPerAssRx;
        packetsPerAssRx = new ConcurrentHashMap<>();
        return current;
    }

    public void updatePacketsPerAssRx(String assocName) {
        packetsPerAssRx.compute(assocName, (key, counter) -> {
            if (counter == null) {
                counter = new Counter();
            }
            counter.add();
            return counter;
        });
    }

}
