package org.mobicents.protocols.ss7.m3ua.impl;

import javolution.util.FastMap;

/**
 *
 * @param <K>
 * @param <V>
 * @author amit bhayani
 */
public class RouteMap<K, V> extends FastMap<K, V> {
    public RouteMap() {
        this.shared();
    }
}
