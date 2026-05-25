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
package org.mobicents.protocols.ss7.m3ua;

public final class RouteKey implements Comparable<RouteKey> {
    private static final String SEPARATOR = ":";

    private final int dpc;
    private final int opc;
    private final int si;

    public RouteKey(int dpc, int opc, int si) {
        this.dpc = dpc;
        this.opc = opc;
        this.si = si;
    }

    public int getDpc() {
        return dpc;
    }

    public int getOpc() {
        return opc;
    }

    public int getSi() {
        return si;
    }

    public static RouteKey parse(String value) {
        String[] parts = value.split(SEPARATOR);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid route key: " + value);
        }

        return new RouteKey(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RouteKey))
            return false;
        RouteKey k = (RouteKey) o;
        return dpc == k.dpc && opc == k.opc && si == k.si;
    }

    @Override
    public int hashCode() {
        return (dpc * 31 + opc) * 31 + si;
    }

    @Override
    public int compareTo(RouteKey k) {
        int cmp = Integer.compare(dpc, k.dpc);
        if (cmp != 0)
            return cmp;
        cmp = Integer.compare(opc, k.opc);
        if (cmp != 0)
            return cmp;
        return Integer.compare(si, k.si);
    }

    @Override
    public String toString() {
        return dpc + SEPARATOR + opc + SEPARATOR + si;
    }
}
