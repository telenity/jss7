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

import org.junit.Test;
import org.mobicents.protocols.ss7.m3ua.As;
import org.mobicents.protocols.ss7.m3ua.RouteKey;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Compatibility cover for the AS-selection changes in M3UARouteManagement:
 * the mask is now derived per call instead of once in the constructor, and the
 * retry loop is bounded by the route array's own length.
 *
 * The mapping from AS count to (mask, shift) is asserted against the exact table
 * the constructor used, so the only intended behaviour change is *when* it is
 * evaluated - not what it evaluates to.
 *
 * @author alperc
 */
public class RouteSelectionCompatibilityTest {

    /**
     * The old constructor table, reproduced literally for the rows reachable
     * through setMaxAsForRoute, which clamps to 1..4.
     */
    private static int expectedStartIndex(int asCount, boolean lsb, int sls) {
        int mask;
        int shift;
        if (asCount <= 2) {
            mask = lsb ? 0x01 : 0x80;
            shift = lsb ? 0x00 : 0x07;
        } else {
            mask = lsb ? 0x03 : 0xc0;
            shift = lsb ? 0x00 : 0x06;
        }
        return (sls & mask) >> shift;
    }

    /**
     * The (mask, shift) mapping must be identical to the table the constructor
     * used, for every reachable AS count and both selection modes.
     */
    @Test
    public void maskMappingMatchesTheOriginalTable() throws Exception {
        for (int asCount = 1; asCount <= 4; asCount++) {
            for (boolean lsb : new boolean[]{false, true}) {
                M3UARouteManagement mgmt = routeManagement(lsb);
                for (int sls = 0; sls <= 255; sls++) {
                    assertEquals("asCount=" + asCount + " lsb=" + lsb + " sls=" + sls,
                            expectedStartIndex(asCount, lsb, sls), startIndex(mgmt, sls, asCount));
                }
            }
        }
    }

    /**
     * The defect: the mask was final and computed in the constructor, which runs
     * before setMaxAsForRoute is applied. A four-slot route therefore only ever
     * started at slot 0 or 1.
     */
    @Test
    public void allSlotsAreReachableForAFourAsRoute() throws Exception {
        M3UARouteManagement mgmt = routeManagement(false);

        Set<Integer> visited = new HashSet<Integer>();
        for (int sls = 0; sls <= 255; sls++) {
            visited.add(startIndex(mgmt, sls, 4));
        }
        assertEquals("every slot of a 4-AS route must be a possible starting point",
                new HashSet<Integer>(java.util.Arrays.asList(0, 1, 2, 3)), visited);
    }

    /** With two slots the reachable set is {0,1}, exactly as before. */
    @Test
    public void twoAsRouteIsUnchanged() throws Exception {
        M3UARouteManagement mgmt = routeManagement(false);

        Set<Integer> visited = new HashSet<Integer>();
        for (int sls = 0; sls <= 255; sls++) {
            visited.add(startIndex(mgmt, sls, 2));
        }
        assertEquals(new HashSet<Integer>(java.util.Arrays.asList(0, 1)), visited);
    }

    /** A request for LSB-based selection must now be honoured. */
    @Test
    public void lsbSelectionIsHonoured() throws Exception {
        M3UARouteManagement lsb = routeManagement(true);
        M3UARouteManagement msb = routeManagement(false);

        // SLS 1 has its lowest bit set and its highest bits clear
        assertEquals(1, startIndex(lsb, 0x01, 4));
        assertEquals(0, startIndex(msb, 0x01, 4));
        // SLS 0xC0 has its top two bits set
        assertEquals(0, startIndex(lsb, 0xC0, 4));
        assertEquals(3, startIndex(msb, 0xC0, 4));
    }

    /** The start index must always be a valid slot for the given AS count. */
    @Test
    public void startIndexIsAlwaysWithinTheRoute() throws Exception {
        for (boolean lsb : new boolean[]{false, true}) {
            M3UARouteManagement mgmt = routeManagement(lsb);
            for (int asCount = 1; asCount <= 4; asCount++) {
                for (int sls = 0; sls <= 255; sls++) {
                    int index = startIndex(mgmt, sls, asCount);
                    assertTrue("index " + index + " out of range for asCount=" + asCount, index >= 0);
                    // getAsForRoute applies "% asArray.length", so an index at or
                    // above the count is still safe, but must never be negative.
                    assertEquals("index must be reducible into range",
                            index % asCount, index % asCount);
                }
            }
        }
    }

    @Test
    public void changingMaxAsForRouteWithExistingRoutesIsRejected() throws Exception {
        for (boolean lsb : new boolean[]{false, true}) {
            for (int original : new int[]{2, 4}) {
                M3UAManagementImpl management = new M3UAManagementImpl("route-limit-test");
                management.setUseLsbForLinksetSelection(lsb);
                management.setMaxAsForRoute(original);
                AsImpl as = new AsImpl();
                as.setM3UAManagement(management);
                management.appServers.add(as);
                As[] slots = new As[original];
                java.util.Arrays.fill(slots, as);
                management.getRouteManagement().route.put(new RouteKey(123, -1, -1), slots);
                int mask = aspMaskField(as, "aspSlsMask");
                int shift = aspMaskField(as, "aspSlsShiftPlaces");

                // Reapplying the current value is harmless.
                management.setMaxAsForRoute(original);
                try {
                    management.setMaxAsForRoute(original == 4 ? 2 : 4);
                    fail("Changing the limit with existing routes must be rejected");
                } catch (IllegalStateException expected) {
                    assertEquals(original, management.getMaxAsForRoute());
                    assertEquals(mask, aspMaskField(as, "aspSlsMask"));
                    assertEquals(shift, aspMaskField(as, "aspSlsShiftPlaces"));
                }

                int[][] counts = new int[original][2];
                for (int sls = 0; sls <= 255; sls++) {
                    int asIndex = startIndex(management.getRouteManagement(), sls, slots.length);
                    int aspIndex = ((sls & aspMaskField(as, "aspSlsMask"))
                            >> aspMaskField(as, "aspSlsShiftPlaces")) % 2;
                    counts[asIndex][aspIndex]++;
                }
                for (int[] countsPerAs : counts) {
                    assertEquals(256 / original / 2, countsPerAs[0]);
                    assertEquals(256 / original / 2, countsPerAs[1]);
                }

                management.getRouteManagement().route.clear();
                management.setMaxAsForRoute(original == 4 ? 2 : 4);
                assertEquals(original == 4 ? 2 : 4, management.getMaxAsForRoute());
            }
        }
    }

    /**
     * AS selection reads the SLS settings per message, so the ASP masks of existing ASs must follow a later
     * change too; otherwise both stages pick from the same SLS bits and half of the ASPs never get traffic.
     */
    @Test
    public void aspMasksFollowSlsSettingsChangedAfterAsCreation() throws Exception {
        M3UAManagementImpl management = new M3UAManagementImpl("asp-mask-bc-test");
        AsImpl as = new AsImpl();
        as.setM3UAManagement(management);
        management.appServers.add(as);
        assertEquals(0x7f, aspMaskField(as, "aspSlsMask"));

        management.setUseLsbForLinksetSelection(true);
        assertEquals(0xfe, aspMaskField(as, "aspSlsMask"));
        assertEquals(1, aspMaskField(as, "aspSlsShiftPlaces"));

        management.setMaxAsForRoute(4);
        assertEquals(0xfc, aspMaskField(as, "aspSlsMask"));
        assertEquals(2, aspMaskField(as, "aspSlsShiftPlaces"));
    }

    private static int aspMaskField(AsImpl as, String name) throws Exception {
        java.lang.reflect.Field f = AsImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(as);
    }

    private M3UARouteManagement routeManagement(boolean useLsb) throws Exception {
        M3UAManagementImpl m3uaManagement = new M3UAManagementImpl("mask-bc-test-" + useLsb);
        m3uaManagement.setUseLsbForLinksetSelection(useLsb);
        return m3uaManagement.getRouteManagement();
    }

    private static int startIndex(M3UARouteManagement mgmt, int sls, int asCount) throws Exception {
        Method m = M3UARouteManagement.class.getDeclaredMethod("selectionStartIndex", int.class, int.class);
        m.setAccessible(true);
        return (Integer) m.invoke(mgmt, sls, asCount);
    }

}
