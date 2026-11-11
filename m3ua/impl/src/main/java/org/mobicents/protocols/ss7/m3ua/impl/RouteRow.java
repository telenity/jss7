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
package org.mobicents.protocols.ss7.m3ua.impl;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.mobicents.protocols.ss7.m3ua.State;
import org.mobicents.protocols.ss7.mtp.Mtp3PausePrimitive;
import org.mobicents.protocols.ss7.mtp.Mtp3Primitive;
import org.mobicents.protocols.ss7.mtp.Mtp3ResumePrimitive;

/**
 *
 * @author amit bhayani
 *
 */
public class RouteRow implements AsStateListener {
    private static final Logger logger = Logger.getLogger(RouteRow.class);

    // last primitive sent for this DPC; M3UARouteManagement updates it with every PAUSE/RESUME it sends
    volatile int mtp3Status = Mtp3PausePrimitive.PAUSE;
    private Set<AsImpl> servedByAsSet;
    // AS that sent DUNA for this DPC (RFC 4666 3.4.1: unreachable via that SG). In-memory only.
    private final Set<AsImpl> unreachableVia = ConcurrentHashMap.newKeySet();
    private int dpc;
    private final M3UAManagementImpl m3uaManagement;

    RouteRow(int dpc, M3UAManagementImpl m3uaManagement) {
        this.dpc = dpc;
        this.m3uaManagement = m3uaManagement;
        this.servedByAsSet = ConcurrentHashMap.newKeySet();
    }

    public int getDpc() {
        return dpc;
    }

    public void setDpc(int dpc) {
        this.dpc = dpc;
    }

    protected void addServedByAs(AsImpl asImpl) {
        this.servedByAsSet.add(asImpl);
        asImpl.addAsStateListener(this);
    }

    protected int servedByAsSize() {
        return this.servedByAsSet.size();
    }

    protected void removeServedByAs(AsImpl asImpl) {
        boolean flag = this.servedByAsSet.remove(asImpl);
        this.unreachableVia.remove(asImpl);
        asImpl.removeAsStateListener(this);
        if (!flag) {
            logger.error(String.format("Removing route As=%s from DPC=%d failed!", asImpl, dpc));
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Removed route As=%s from DPC=%d successfully!", asImpl, dpc));
            }
        }
    }

    protected boolean isServedBy(AsImpl asImpl) {
        return this.servedByAsSet.contains(asImpl);
    }

    protected Set<AsImpl> getUnreachableVia() {
        return this.unreachableVia;
    }

    /** True if at least one routing-active AS of this DPC has not reported it unavailable. */
    protected boolean isReachableViaActiveAs() {
        M3UARouteManagement routes = this.m3uaManagement.getRouteManagement();
        for (AsImpl asImpl : this.servedByAsSet) {
            if (routes.isAsActive(asImpl) && !this.unreachableVia.contains(asImpl)) {
                return true;
            }
        }
        return false;
    }

    // FSM callbacks take routeLock, like DUNA/DAVA, so PAUSE/RESUME reach the user in the order they were decided
    @Override
    public void onAsActive(AsImpl asImpl) {
        M3UARouteManagement routes = this.m3uaManagement.getRouteManagement();
        synchronized (routes.routeLock) {
            // We only send MTP3 RESUME to MTP3 user if its not already sent for this DPC,
            // not via an AS that already reported DUNA for it (e.g. before its ASP Active Ack),
            // and not before one of our ASPs is active (NTFY AS-ACTIVE ahead of the Ack; the Ack notifies again)
            if (this.mtp3Status != Mtp3Primitive.RESUME && !this.unreachableVia.contains(asImpl)
                    && !routes.lacksActiveAsp(asImpl)) {
                this.mtp3Status = Mtp3Primitive.RESUME;
                Mtp3ResumePrimitive mtp3ResumePrimitive = new Mtp3ResumePrimitive(this.dpc);
                this.m3uaManagement.sendResumeMessageToLocalUser(mtp3ResumePrimitive);
            }
        }
    }

    @Override
    public void onAsInActive(AsImpl asImpl) {
        M3UARouteManagement routes = this.m3uaManagement.getRouteManagement();
        synchronized (routes.routeLock) {
            this.unreachableVia.remove(asImpl);
            // Send MTP3 PAUSE to MTP3 user only if its not already sent for this DPC
            if (this.mtp3Status != Mtp3Primitive.PAUSE) {
                for (AsImpl asImplTmp : this.servedByAsSet) {
                    if (this.unreachableVia.contains(asImplTmp)) {
                        continue; // up, but reported DUNA for this DPC
                    }
                    // ACTIVE counts only with an active ASP of ours, as in route selection
                    if ((asImplTmp.getState().getName().equals(State.STATE_ACTIVE) && !routes.lacksActiveAsp(asImplTmp))
                            || (asImplTmp.getState().getName().equals(State.STATE_PENDING))) {
                        // If there are more AS in ACTIVE || PENDING state, no need
                        // to call PAUSE for this DPC
                        return;
                    }
                }

                this.mtp3Status = Mtp3Primitive.PAUSE;
                Mtp3PausePrimitive mtp3PausePrimitive = new Mtp3PausePrimitive(this.dpc);
                this.m3uaManagement.sendPauseMessageToLocalUser(mtp3PausePrimitive);
            }
        }
    }

    @Override
    public String toString() {
        return "RouteRow [dpc=" + dpc + ", mtp3Status=" + mtp3Status + ", asSet=" + servedByAsSet + ", unreachableVia=" + unreachableVia + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + dpc;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RouteRow other = (RouteRow) obj;
        if (dpc != other.dpc)
            return false;
        return true;
    }

}
