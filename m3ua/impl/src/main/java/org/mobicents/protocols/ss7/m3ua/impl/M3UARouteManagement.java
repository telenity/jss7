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

import java.util.Arrays;

import javolution.util.FastList;
import javolution.util.FastSet;

import org.apache.log4j.Logger;
import org.mobicents.protocols.ss7.m3ua.As;
import org.mobicents.protocols.ss7.m3ua.ExchangeType;
import org.mobicents.protocols.ss7.m3ua.Functionality;
import org.mobicents.protocols.ss7.m3ua.IPSPType;
import org.mobicents.protocols.ss7.m3ua.impl.fsm.FSM;
import org.mobicents.protocols.ss7.m3ua.impl.oam.M3UAOAMMessages;
import org.mobicents.protocols.ss7.m3ua.parameter.TrafficModeType;
import org.mobicents.protocols.ss7.mtp.RoutingLabelFormat;

/**
 * <p>
 * Management class to manage the route.
 * </p>
 * <p>
 * The DPC, OPC and SI of Message Signaling unit (MSU) transfered by M3UA-User
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

    private static final String KEY_SEPARATOR = ":";
    private static final int WILDCARD = -1;

    private M3UAManagementImpl m3uaManagement = null;

    private final int asSelectionMask;
    private int asSlsShiftPlaces = 0x00;

    /**
     * persists key vs corresponding As that servers for this key
     */
    protected RouteMap<String, As[]> route = new RouteMap<String, As[]>();

    /**
     * Persists DPC vs As's serving this DPC. Used for notifying M3UA-user of
     * MTP3 primitive PAUSE, RESUME.
     */
    private FastSet<RouteRow> routeTable = new FastSet<RouteRow>();

    // Stores the Set of AS that can route traffic (irrespective of OPC or NI)
    // for given DPC
    protected M3UARouteManagement(M3UAManagementImpl m3uaManagement) {
        this.m3uaManagement = m3uaManagement;

        switch (this.m3uaManagement.getMaxAsForRoute()) {
            case 1:
            case 2:
                if (this.m3uaManagement.isUseLsbForLinksetSelection()) {
                    this.asSelectionMask = 0x01;
                    this.asSlsShiftPlaces = 0x00;
                } else {
                    this.asSelectionMask = 0x80;
                    this.asSlsShiftPlaces = 0x07;
                }
                break;
            case 3:
            case 4:
                if (this.m3uaManagement.isUseLsbForLinksetSelection()) {
                    this.asSelectionMask = 0x03;
                    this.asSlsShiftPlaces = 0x00;
                } else {
                    this.asSelectionMask = 0xc0;
                    this.asSlsShiftPlaces = 0x06;
                }
                break;
            case 5:
            case 6:
            case 7:
            case 8:
                if (this.m3uaManagement.isUseLsbForLinksetSelection()) {
                    this.asSelectionMask = 0x07;
                    this.asSlsShiftPlaces = 0x00;
                } else {
                    this.asSelectionMask = 0xe0;
                    this.asSlsShiftPlaces = 0x05;
                }
                break;
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
                if (this.m3uaManagement.isUseLsbForLinksetSelection()) {
                    this.asSelectionMask = 0x0f;
                    this.asSlsShiftPlaces = 0x00;
                } else {
                    this.asSelectionMask = 0xf0;
                    this.asSlsShiftPlaces = 0x04;
                }
                break;
            default:
                if (this.m3uaManagement.isUseLsbForLinksetSelection()) {
                    this.asSelectionMask = 0x01;
                    this.asSlsShiftPlaces = 0x00;
                } else {
                    this.asSelectionMask = 0x80;
                    this.asSlsShiftPlaces = 0x07;
                }
                break;
        }
    }

    /**
     * Reset the routeTable. Called after the persistance state of route is read
     * from xml file.
     */
    protected void reset() {
        for (RouteMap.Entry<String, As[]> e = this.route.head(), end = this.route.tail(); (e = e.getNext()) != end;) {
            String key = e.getKey();
            As[] asList = e.getValue();

            try {
                String[] keys = key.split(KEY_SEPARATOR);
                int dpc = Integer.parseInt(keys[0]);
                for (int i = 0; i < asList.length; i++) {
                    AsImpl asImpl = (AsImpl)asList[i];
                    if (asImpl != null) {
                        this.addAsToDPC(dpc, asImpl);
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
     * @throws Exception
     * If corresponding {@link AsImpl} doesn't exist or
     * {@link AsImpl} already added
     */
    protected void addRoute(int dpc, int opc, int si, String asName) throws Exception {
        AsImpl asImpl = null;
        for (FastList.Node<As> n = this.m3uaManagement.appServers.head(), end = this.m3uaManagement.appServers.tail(); (n = n
                .getNext()) != end;) {
			if (n.getValue().getName().compareTo(asName) == 0) {
                asImpl = (AsImpl) n.getValue();
                break;
            }
        }

        if (asImpl == null) {
            throw new Exception(String.format(M3UAOAMMessages.NO_AS_FOUND, asName));
        }

        // Use simple string concatenation for key
        String key = dpc + KEY_SEPARATOR + opc + KEY_SEPARATOR + si;

        As[] asArray = route.get(key);

        if (asArray != null) {
            // check is this As is already added
            for (int i = 0; i < asArray.length; i++) {
                AsImpl asTemp = (AsImpl)asArray[i];
                if (asTemp != null && asImpl.equals(asTemp)) {
                    throw new Exception(String.format("As=%s already added for dpc=%d opc=%d si=%d", asImpl.getName(),
                            dpc, opc, si));
                }
            }
        } else {
            asArray = new AsImpl[this.m3uaManagement.maxAsForRoute];
            route.put(key, asArray);
        }

        // Add to first empty slot
        for (int i = 0; i < asArray.length; i++) {
            if (asArray[i] == null) {
                asArray[i] = asImpl;
                this.m3uaManagement.store();

                this.addAsToDPC(dpc, asImpl);
                return;
            }
        }

        throw new Exception(String.format("dpc=%d opc=%d si=%d combination already has maximum possible As", dpc, opc, si));
    }

    /**
     * Removes the {@link AsImpl} from key (combination of DPC:OPC:Si)
     *
     * @param dpc
     * @param opc
     * @param si
     * @param asName
     * @throws Exception
     * If no As found, or this As is not serving this key
     *
     */
    protected void removeRoute(int dpc, int opc, int si, String asName) throws Exception {
        AsImpl asImpl = null;
        for (FastList.Node<As> n = this.m3uaManagement.appServers.head(), end = this.m3uaManagement.appServers.tail(); (n = n
                .getNext()) != end;) {
			if (n.getValue().getName().compareTo(asName) == 0) {
                asImpl = (AsImpl) n.getValue();
                break;
            }
        }

        if (asImpl == null) {
            throw new Exception(String.format(M3UAOAMMessages.NO_AS_FOUND, asName));
        }

        // Use simple string concatenation for key
        String key = dpc + KEY_SEPARATOR + opc + KEY_SEPARATOR + si;

        As[] asArray = route.get(key);

        if (asArray == null) {
            throw new Exception(String.format("No AS=%s configured  for dpc=%d opc=%d si=%d", asImpl.getName(), dpc,
                    opc, si));
        }

        for (int i = 0; i < asArray.length; i++) {
            AsImpl asTemp = (AsImpl)asArray[i];
            if (asTemp != null && asImpl.equals(asTemp)) {
                asArray[i] = null;
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

    /**
     * Checks if the given AsImpl is still configured for any route combination
     * that uses the specified DPC.
     *
     * @param dpc The DPC to check against.
     * @param asImpl The AsImpl to look for.
     * @return true if the AsImpl is found in any route for the given DPC, false otherwise.
     */
    protected boolean isAsStillUsedForDpc(int dpc, AsImpl asImpl) {
        String dpcPrefix = dpc + KEY_SEPARATOR; // Prefix to check for routes associated with this DPC
        for (RouteMap.Entry<String, As[]> e = this.route.head(), end = this.route.tail(); (e = e.getNext()) != end;) {
            String key = e.getKey();
            // Check if the route key starts with the DPC prefix
            if (key.startsWith(dpcPrefix)) {
                As[] asList = e.getValue();
                for (int i = 0; i < asList.length; i++) {
                    AsImpl asTemp = (AsImpl) asList[i];
                    if (asTemp != null && asTemp.equals(asImpl)) {
                        return true; // Found the AS in a route for this DPC
                    }
                }
            }
        }
        return false; // AS not found in any route for this DPC
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
        String key = dpc + KEY_SEPARATOR + opc + KEY_SEPARATOR + si;
        asArray = route.get(key);

        if (asArray == null) {
            // Check SI wildcard
            key = dpc + KEY_SEPARATOR + opc + KEY_SEPARATOR + WILDCARD;
            asArray = route.get(key);

            if (asArray == null) {
                // Check OPC and SI wildcard
                key = dpc + KEY_SEPARATOR + WILDCARD + KEY_SEPARATOR + WILDCARD;
                asArray = route.get(key);
            }
        }

        if (asArray == null) {
            return null; // No matching route found
        }

        // Load sharing logic based on SLS
        int startIndex = (sls & this.asSelectionMask) >> this.asSlsShiftPlaces;
        int maxAttempts = this.m3uaManagement.getMaxAsForRoute();

        // Attempt to find an active AS, starting from the calculated index and wrapping around
        for (int i = 0; i < maxAttempts; i++) {
            int currentIndex = (startIndex + i) % maxAttempts;
            AsImpl asImpl = (AsImpl)asArray[currentIndex];
            if (this.isAsActive(asImpl)) {
                return asImpl; // Found an active AS
            }
        }

        return null; // No active AS found for the route
    }

    /**
     * Checks if an AsImpl is in an ACTIVE state based on its FSM state and functionality/exchange type.
     *
     * @param asImpl The AsImpl to check.
     * @return true if the AsImpl is considered active for routing, false otherwise.
     */
    private boolean isAsActive(AsImpl asImpl) {
        if (asImpl == null) {
            return false;
        }

        FSM fsm = null;
        Functionality functionality = asImpl.getFunctionality();
        ExchangeType exchangeType = asImpl.getExchangeType();
        IPSPType ipspType = asImpl.getIpspType();

        // Determine which FSM to check based on functionality and exchange type
        if (functionality == Functionality.AS
                || (functionality == Functionality.SGW && exchangeType == ExchangeType.DE)
                || (functionality == Functionality.IPSP && exchangeType == ExchangeType.DE)
                || (functionality == Functionality.IPSP && exchangeType == ExchangeType.SE && ipspType == IPSPType.CLIENT)) {
            fsm = asImpl.getPeerFSM();
        } else {
            fsm = asImpl.getLocalFSM();
        }

        if (fsm != null) {
            // Get the state name and compare with AsState.ACTIVE
            AsState asState = AsState.getState(fsm.getState().getName());
            return (asState == AsState.ACTIVE);
        }

        return false; // No FSM found, not active
    }

    /**
     * Adds an AsImpl to the RouteRow for the given DPC. Creates a new RouteRow if none exists for the DPC.
     *
     * @param dpc The DPC.
     * @param asImpl The AsImpl to add.
     */
    private void addAsToDPC(int dpc, AsImpl asImpl) {
        RouteRow row = null;
        // Find existing RouteRow for DPC
        for (FastSet.Record r = routeTable.head(), end = routeTable.tail(); (r = r.getNext()) != end;) {
            RouteRow value = routeTable.valueOf(r);
            if (value.getDpc() == dpc) {
                row = value;
                break;
            }
        }

        if (row == null) {
            row = new RouteRow(dpc, this.m3uaManagement);
            this.routeTable.add(row);
        }

        // Add AS to the RouteRow
        row.addServedByAs(asImpl);
    }

    /**
     * Removes an AsImpl from the RouteRow for the given DPC, but only if it's no longer
     * used for any route combination for that DPC. Removes the RouteRow if it becomes empty.
     * This method is called internally after verifying the AS is not used for any other route for this DPC.
     *
     * @param dpc The DPC.
     * @param asImpl The AsImpl to remove.
     */
    private void removeAsFromDPC(int dpc, AsImpl asImpl) {
        // Find the RouteRow for DPC
        RouteRow row = null;
        for (FastSet.Record r = routeTable.head(), end = routeTable.tail(); (r = r.getNext()) != end;) {
            RouteRow value = routeTable.valueOf(r);
            if (value.getDpc() == dpc) {
                row = value;
                break;
            }
        }

        if (row == null) {
            // This should ideally not happen if addAsToDPC is always called before removeRoute
            logger.error(String.format("Removing route As=%s from DPC=%d failed. No RouteRow found!", asImpl.getName(), dpc));
        } else {
            row.removeServedByAs(asImpl);
            if (row.servedByAsSize() == 0) {
                this.routeTable.remove(row);
            }
        }
    }

    public void removeAllResourses() throws Exception {
        this.route.clear();
        this.routeTable.clear();
    }
}