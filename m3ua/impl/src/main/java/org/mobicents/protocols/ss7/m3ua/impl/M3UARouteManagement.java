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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javolution.util.FastList;

import org.apache.log4j.Logger;
import org.mobicents.protocols.ss7.m3ua.As;
import org.mobicents.protocols.ss7.m3ua.Asp;
import org.mobicents.protocols.ss7.m3ua.ExchangeType;
import org.mobicents.protocols.ss7.m3ua.Functionality;
import org.mobicents.protocols.ss7.m3ua.IPSPType;
import org.mobicents.protocols.ss7.m3ua.RouteKey;
import org.mobicents.protocols.ss7.m3ua.impl.fsm.FSM;
import org.mobicents.protocols.ss7.m3ua.impl.oam.M3UAOAMMessages;
import org.mobicents.protocols.ss7.m3ua.message.MessageClass;
import org.mobicents.protocols.ss7.m3ua.message.MessageType;
import org.mobicents.protocols.ss7.m3ua.message.ssnm.DestinationStateAudit;
import org.mobicents.protocols.ss7.m3ua.parameter.TrafficModeType;
import org.mobicents.protocols.ss7.mtp.Mtp3PausePrimitive;
import org.mobicents.protocols.ss7.mtp.Mtp3Primitive;
import org.mobicents.protocols.ss7.mtp.Mtp3ResumePrimitive;

/**
 * <p>
 * Management class to manage the route.
 * </p>
 * <p>
 * The DPC, OPC and SI of Message Signaling unit (MSU) transferred by M3UA-User
 * to M3UA layer for routing is checked against configured key. If found, the
 * corresponding {@link AsImpl} is checked for state and if ACTIVE, message will
 * be delivered via this {@link AsImpl}. If multiple {@link AsImpl} are
 * configured and at-least 2 or more are ACTIVE, then depending on
 * {@link TrafficModeType} configured load-sharing is achieved by using SLS from
 * received MSU.
 * </p>
 * <p>
 * For any given key (combination of DPC, OPC and SI) maximum {@link AsImpl} can
 * be configured which acts as route for these key combination.
 * </p>
 * <p>
 * Same {@link AsImpl} can serve multiple key combinations.
 * </p>
 * <p>
 * MTP3 Primitive RESUME is delivered to M3UA-User when {@link AsImpl} becomes
 * ACTIVE and PAUSE is delivered when {@link AsImpl} becomes INACTIVE
 * </p>
 *
 * @author amit bhayani
 *
 */
public class M3UARouteManagement {

    private static final Logger logger = Logger.getLogger(M3UARouteManagement.class);

    private static final int WILDCARD = -1;

    private M3UAManagementImpl m3uaManagement = null;

    // Also guards maxAsForRoute changes against concurrent route creation.
    final Object routeLock = new Object();


    /**
     * persists key vs corresponding As that servers for this key
     */
    protected volatile RouteMap<RouteKey, As[]> route = new RouteMap<>();

    /**
     * Persists DPC vs As's serving this DPC. Used for notifying M3UA-user of
     * MTP3 primitive PAUSE, RESUME.
     */
    private Map<Integer, RouteRow> routeTable = new ConcurrentHashMap<>();

    // Stores the Set of AS that can route traffic (irrespective of OPC or NI)
    // for given DPC
    protected M3UARouteManagement(M3UAManagementImpl m3uaManagement) {
        this.m3uaManagement = m3uaManagement;
    }

    /**
     * SLS bits pick the first AS slot; derived per call because maxAsForRoute/LSB are set after construction.
     */
    private int selectionStartIndex(int sls, int asCount) {
        int bits = asCount <= 2 ? 1 : 2; // setMaxAsForRoute caps routes at 4 ASs
        return this.m3uaManagement.isUseLsbForLinksetSelection() ? sls & ((1 << bits) - 1) : (sls & 0xFF) >> (8 - bits);
    }

    /**
     * Reset the routeTable. Called after the persistence state of route is read
     * from xml file.
     */
    protected void reset() {
        RouteMap<RouteKey, As[]> route = this.route;
        for (RouteMap.Entry<RouteKey, As[]> e = route.head(), end = route.tail(); (e = e.getNext()) != end; ) {
            RouteKey key = e.getKey();
            As[] asList = e.getValue();

            try {
                for (int i = 0; i < asList.length; i++) {
                    AsImpl asImpl = (AsImpl) asList[i];
                    if (asImpl != null) {
                        this.addAsToDPC(key.getDpc(), asImpl);
                    }
                }
            } catch (Exception ex) {
                logger.error(String.format("Error while adding key=%s to As list=%s", key, Arrays.toString(asList)));
            }
        }
    }

    /**
     * Creates key (combination of dpc:opc:si) and adds instance of
     * {@link AsImpl} represented by asName as route for this key
     *
     * @param dpc
     * @param opc
     * @param si
     * @param asName
     * @throws Exception If corresponding {@link AsImpl} doesn't exist or
     *                   {@link AsImpl} already added
     */
    protected void addRoute(int dpc, int opc, int si, String asName) throws Exception {
        synchronized (this.routeLock) {
            AsImpl asImpl = this.findAsOrThrow(asName);

            RouteKey key = new RouteKey(dpc, opc, si);
            RouteMap<RouteKey, As[]> route = this.route;
            As[] asArray = route.get(key);

            if (asArray == null) {
                As[] newAsArray = new AsImpl[this.m3uaManagement.maxAsForRoute];
                newAsArray[0] = asImpl;
                route.put(key, newAsArray);
                this.m3uaManagement.store();
                this.addAsToDPC(dpc, asImpl);
                return;
            }

            for (int i = 0; i < asArray.length; i++) {
                AsImpl asTemp = (AsImpl) asArray[i];
                if (asTemp != null && asImpl.equals(asTemp)) {
                    throw new Exception(String.format("As=%s already added for dpc=%d opc=%d si=%d", asImpl.getName(),
                            dpc, opc, si));
                }
            }

            for (int i = 0; i < asArray.length; i++) {
                if (asArray[i] == null) {
                    // Publish a new array so readers can safely finish with the previous snapshot.
                    As[] newAsArray = asArray.clone();
                    newAsArray[i] = asImpl;
                    route.put(key, newAsArray);
                    this.m3uaManagement.store();
                    this.addAsToDPC(dpc, asImpl);
                    return;
                }
            }

            throw new Exception(String.format("dpc=%d opc=%d si=%d combination already has maximum possible As", dpc, opc, si));
        }
    }

    /**
     * Removes the {@link AsImpl} from key (combination of DPC:OPC:Si)
     *
     * @param dpc
     * @param opc
     * @param si
     * @param asName
     * @throws Exception If no As found, or this As is not serving this key
     *
     */
    protected void removeRoute(int dpc, int opc, int si, String asName) throws Exception {
        synchronized (this.routeLock) {
            AsImpl asImpl = this.findAsOrThrow(asName);

            RouteKey key = new RouteKey(dpc, opc, si);
            RouteMap<RouteKey, As[]> route = this.route;

            As[] asArray = route.get(key);

            if (asArray == null) {
                throw new Exception(String.format("No AS=%s configured  for dpc=%d opc=%d si=%d", asImpl.getName(), dpc,
                        opc, si));
            }

            for (int i = 0; i < asArray.length; i++) {
                AsImpl asTemp = (AsImpl) asArray[i];
                if (asTemp != null && asImpl.equals(asTemp)) {
                    As[] newAsArray = asArray.clone();
                    newAsArray[i] = null;
                    route.put(key, newAsArray);
                    this.m3uaManagement.store();

                    // Check if this AS is still used for the same DPC in ANY route before removing from DPC mapping
                    if (!isAsStillUsedForDpc(dpc, asImpl)) {
                        this.removeAsFromDPC(dpc, asImpl);
                    }
                    return;
                }
            }

            throw new Exception(String.format("No AS=%s configured  for dpc=%d opc=%d si=%d", asImpl.getName(), dpc, opc,
                    si));
        }
    }

    protected RouteKey findRouteKeyForAs(AsImpl asImpl) {
        synchronized (this.routeLock) {
            RouteMap<RouteKey, As[]> route = this.route;
            for (RouteMap.Entry<RouteKey, As[]> e = route.head(), end = route.tail(); (e = e.getNext()) != end; ) {
                As[] asList = e.getValue();
                for (int i = 0; i < asList.length; i++) {
                    AsImpl asTemp = (AsImpl) asList[i];
                    if (asTemp != null && asTemp.equals(asImpl)) {
                        return e.getKey();
                    }
                }
            }
            return null;
        }
    }

    /**
     * Checks if the given AsImpl is still configured for any route combination
     * that uses the specified DPC.
     *
     * @param dpc    The DPC to check against.
     * @param asImpl The AsImpl to look for.
     * @return true if the AsImpl is found in any route for the given DPC, false otherwise.
     */
    protected boolean isAsStillUsedForDpc(int dpc, AsImpl asImpl) {
        RouteMap<RouteKey, As[]> route = this.route;
        for (RouteMap.Entry<RouteKey, As[]> e = route.head(), end = route.tail(); (e = e.getNext()) != end; ) {
            RouteKey key = e.getKey();
            if (key.getDpc() == dpc) {
                As[] asList = e.getValue();
                for (int i = 0; i < asList.length; i++) {
                    AsImpl asTemp = (AsImpl) asList[i];
                    if (asTemp != null && asTemp.equals(asImpl)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Configured route DPCs equal to pc in all but the mask low-order bits. */
    List<Integer> getDpcsMatching(int pc, int mask) {
        return this.routeTable.keySet().stream().filter(dpc -> (dpc >>> mask) == (pc >>> mask))
                .collect(Collectors.toList());
    }

    /** True if any route still binds an AS; removeRoute leaves an emptied key behind as an all-null array. */
    boolean hasBoundAs() {
        for (As[] asArray : this.route.values()) {
            for (As as : asArray) {
                if (as != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private AsImpl findAsOrThrow(String asName) throws Exception {
        for (As as : this.m3uaManagement.appServers) {
            if (as.getName().compareTo(asName) == 0) {
                return (AsImpl) as;
            }
        }

        throw new Exception(String.format(M3UAOAMMessages.NO_AS_FOUND, asName));
    }


    /**
     * <p>
     * Get {@link AsImpl} that is serving key (combination DPC:OPC:SI). It can
     * return null if no key configured or all the {@link AsImpl} are INACTIVE
     * </p>
     * <p>
     * If two or more {@link AsImpl} are active and {@link TrafficModeType}
     * configured is load-shared, load is configured between each {@link AsImpl}
     * depending on SLS
     * </p>
     *
     * @param dpc
     * @param opc
     * @param si
     * @param sls
     * @return The active AsImpl to route the message, or null if no active route found.
     */
    protected AsImpl getAsForRoute(int dpc, int opc, int si, int sls) {
        // TODO : Loadsharing needs to be implemented - The current implementation IS load sharing based on SLS.

        As[] asArray = null;

        // Check specific route first
        RouteMap<RouteKey, As[]> route = this.route;

        asArray = route.get(new RouteKey(dpc, opc, si));

        if (asArray == null) {
            // Check SI wildcard
            asArray = route.get(new RouteKey(dpc, opc, WILDCARD));

            if (asArray == null) {
                // Check OPC and SI wildcard
                asArray = route.get(new RouteKey(dpc, WILDCARD, WILDCARD));
            }
        }

        if (asArray == null) {
            return null; // No matching route found
        }

        RouteRow row = this.routeTable.get(dpc);

        // Use the actual array length, including for routes loaded from persistence.
        int maxAttempts = asArray.length;

        // Load sharing logic based on SLS
        int startIndex = selectionStartIndex(sls, maxAttempts);

        // Attempt to find an active AS, starting from the calculated index and wrapping around
        for (int i = 0; i < maxAttempts; i++) {
            int currentIndex = (startIndex + i) % maxAttempts;
            AsImpl asImpl = (AsImpl) asArray[currentIndex];
            if (this.isAsActive(asImpl) && (row == null || !row.getUnreachableVia().contains(asImpl))) {
                return asImpl; // Found an active AS that has not sent DUNA for this DPC
            }
        }

        return null; // No active AS found for the route
    }

    /**
     * DUNA for dpc from asImpl: only the (dpc, asImpl) pair becomes unreachable. PAUSE/RESUME are queued under
     * routeLock, so the user sees them in the order they were decided. MTP-PAUSE only when no routing-active AS of
     * the DPC can still reach it.
     */
    void onDestinationUnavailable(int dpc, AsImpl asImpl) {
        synchronized (this.routeLock) {
            RouteRow row = this.routeTable.get(dpc);
            if (row == null || !row.isServedBy(asImpl)) {
                return; // route removed after the source check: ignore, as the source check would
            }
            if (!row.getUnreachableVia().add(asImpl)) {
                return; // repeated DUNA (e.g. the answer to a DAUD): nothing changed, RFC 4666 1.3.2.5
            }
            if (row.isReachableViaActiveAs()) {
                logger.info(String.format("[DUNA] APC=%d unreachable via AS=%s only; still reachable via another AS, no PAUSE",
                        dpc, asImpl.getName()));
                return;
            }
            row.mtp3Status = Mtp3Primitive.PAUSE;
            this.m3uaManagement.sendPauseMessageToLocalUser(new Mtp3PausePrimitive(dpc));
        }
    }

    /** DAVA for dpc from asImpl: the (dpc, asImpl) pair is reachable again; RESUME if the DPC status changes. */
    void onDestinationAvailable(int dpc, AsImpl asImpl) {
        synchronized (this.routeLock) {
            RouteRow row = this.routeTable.get(dpc);
            // Inactive AS: RouteRow resumes when it goes active. RFC 4666 1.3.2.5: inform only on change (SGs re-send
            // DAVA unsolicited); no PAUSE counterpart, as the initial PAUSE status is never sent to the user.
            boolean resume = this.isAsActive(asImpl) && (row == null || row.mtp3Status != Mtp3Primitive.RESUME);
            if (row != null) {
                row.getUnreachableVia().remove(asImpl);
                if (resume) {
                    row.mtp3Status = Mtp3Primitive.RESUME;
                }
            }
            if (resume) {
                this.m3uaManagement.sendResumeMessageToLocalUser(new Mtp3ResumePrimitive(dpc));
            }
        }
    }

    /**
     * Checks if an AsImpl is in an ACTIVE state based on its FSM state and functionality/exchange type.
     *
     * @param asImpl The AsImpl to check.
     * @return true if the AsImpl is considered active for routing, false otherwise.
     */
    boolean isAsActive(AsImpl asImpl) {
        if (asImpl == null || this.lacksActiveAsp(asImpl)) {
            return false;
        }

        // Determine which FSM to check based on functionality and exchange type
        FSM fsm = usesPeerFsm(asImpl) ? asImpl.getPeerFSM() : asImpl.getLocalFSM();

        if (fsm != null) {
            // Get the state name and compare with AsState.ACTIVE
            AsState asState = AsState.getState(fsm.getState().getName());
            return (asState == AsState.ACTIVE);
        }

        return false; // No FSM found, not active
    }

    private static boolean usesPeerFsm(AsImpl asImpl) {
        Functionality functionality = asImpl.getFunctionality();
        ExchangeType exchangeType = asImpl.getExchangeType();
        return functionality == Functionality.AS
                || (functionality == Functionality.SGW && exchangeType == ExchangeType.DE)
                || (functionality == Functionality.IPSP && exchangeType == ExchangeType.DE)
                || (functionality == Functionality.IPSP && exchangeType == ExchangeType.SE && asImpl.getIpspType() == IPSPType.CLIENT);
    }

    /**
     * RFC 4666 4.5.3, ETSI TS 102 142 (T10): DAUD for the DPCs still marked unavailable by DUNA, to the AS that
     * reported them, via its active ASP. A lost DAVA otherwise leaves the route paused until the ASP flaps.
     */
    void auditUnavailableDestinations() {
        Map<AsImpl, List<Integer>> dpcsByAs = new HashMap<>();
        for (RouteRow row : this.routeTable.values()) {
            for (AsImpl asImpl : row.getUnreachableVia()) {
                dpcsByAs.computeIfAbsent(asImpl, k -> new ArrayList<>()).add(row.getDpc());
            }
        }
        dpcsByAs.forEach((asImpl, dpcs) -> {
            AspImpl asp = activeAsp(asImpl);
            if (asp != null) {
                AspFactoryImpl factory = asp.getAspFactory();
                int[] pcs = dpcs.stream().mapToInt(Integer::intValue).toArray();
                DestinationStateAudit daud = (DestinationStateAudit) factory.messageFactory.createMessage(
                        MessageClass.SIGNALING_NETWORK_MANAGEMENT, MessageType.DESTINATION_STATE_AUDIT);
                daud.setRoutingContexts(asImpl.getRoutingContext());
                daud.setAffectedPointCodes(factory.parameterFactory.createAffectedPointCode(pcs, new short[pcs.length]));
                factory.write(daud);
            }
        });
    }

    /**
     * The SG may report the AS active with none of our ASPs active (RFC 4666 4.3.4.5, 4.3.4.4), but AsImpl.write
     * needs an ACTIVE ASP.
     */
    boolean lacksActiveAsp(AsImpl asImpl) {
        return usesPeerFsm(asImpl) && activeAsp(asImpl) == null;
    }

    /** One of our ASPs of the AS whose local FSM is ACTIVE, or null. */
    private static AspImpl activeAsp(AsImpl asImpl) {
        for (Asp asp : asImpl.appServerProcs) {
            FSM fsm = ((AspImpl) asp).getLocalFSM();
            if (fsm != null && AspState.getState(fsm.getState().getName()) == AspState.ACTIVE) {
                return (AspImpl) asp;
            }
        }
        return null;
    }

    /**
     * Adds an AsImpl to the RouteRow for the given DPC. Creates a new RouteRow if none exists for the DPC.
     *
     * @param dpc    The DPC.
     * @param asImpl The AsImpl to add.
     */
    private void addAsToDPC(int dpc, AsImpl asImpl) {
        RouteRow row = this.routeTable.get(dpc);
        if (row == null) {
            row = new RouteRow(dpc, this.m3uaManagement);
            RouteRow existing = this.routeTable.putIfAbsent(dpc, row);
            if (existing != null) {
                row = existing;
            }
        }
        row.addServedByAs(asImpl);
        if (this.isAsActive(asImpl)) {
            row.onAsActive(asImpl); // route added at runtime to an AS already up: RESUME, unless already resumed
        }
    }

    /**
     * Removes an AsImpl from the RouteRow for the given DPC, but only if it's no longer
     * used for any route combination for that DPC. Removes the RouteRow if it becomes empty.
     * This method is called internally after verifying the AS is not used for any other route for this DPC.
     *
     * @param dpc    The DPC.
     * @param asImpl The AsImpl to remove.
     */
    private void removeAsFromDPC(int dpc, AsImpl asImpl) {
        RouteRow row = this.routeTable.get(dpc);
        if (row == null) {
            logger.error(String.format("Removing route As=%s from DPC=%d failed. No RouteRow found!", asImpl.getName(), dpc));
        } else {
            row.removeServedByAs(asImpl);
            if (row.servedByAsSize() == 0) {
                this.routeTable.remove(dpc, row);
            } else if (row.mtp3Status == Mtp3Primitive.RESUME && !row.isReachableViaActiveAs()) {
                // the removed AS was the last usable path, e.g. the remaining one has sent DUNA
                row.mtp3Status = Mtp3Primitive.PAUSE;
                this.m3uaManagement.sendPauseMessageToLocalUser(new Mtp3PausePrimitive(dpc));
            }
        }
    }

    public void removeAllResourses() throws Exception {
        synchronized (this.routeLock) {
            this.route.clear();
            this.routeTable.clear();
        }
    }
}
