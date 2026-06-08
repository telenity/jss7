package org.mobicents.protocols.ss7.m3ua;

import java.util.Map;

public interface M3UACounterProvider {

    /**
     * return a number of data packets transmitted through association.
     */
    Map<String, Counter> getPacketsPerAssTx();

    /**
     * return a number of data packets received through association
     */
    Map<String, Counter> getPacketsPerAssRx();

}
