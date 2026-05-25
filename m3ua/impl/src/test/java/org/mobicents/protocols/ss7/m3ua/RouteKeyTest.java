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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RouteKeyTest {

    @Test
    public void testEqualsSameValues() {
        RouteKey a = new RouteKey(123, 1, 2);
        RouteKey b = new RouteKey(123, 1, 2);
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
    }

    @Test
    public void testEqualsDifferentDpc() {
        RouteKey a = new RouteKey(123, 1, 2);
        RouteKey b = new RouteKey(456, 1, 2);
        assertFalse(a.equals(b));
    }

    @Test
    public void testEqualsDifferentOpc() {
        RouteKey a = new RouteKey(123, 1, 2);
        RouteKey b = new RouteKey(123, 3, 2);
        assertFalse(a.equals(b));
    }

    @Test
    public void testEqualsDifferentSi() {
        RouteKey a = new RouteKey(123, 1, 2);
        RouteKey b = new RouteKey(123, 1, 3);
        assertFalse(a.equals(b));
    }

    @Test
    public void testEqualsNull() {
        RouteKey a = new RouteKey(123, 1, 2);
        assertFalse(a.equals(null));
    }

    @Test
    public void testEqualsDifferentType() {
        RouteKey a = new RouteKey(123, 1, 2);
        assertFalse(a.equals("string"));
    }

    @Test
    public void testEqualsWildcard() {
        RouteKey specific = new RouteKey(123, 1, 2);
        RouteKey wildcard = new RouteKey(123, -1, -1);
        assertFalse(specific.equals(wildcard));
    }

    @Test
    public void testHashCodeConsistentWithEquals() {
        RouteKey a = new RouteKey(123, 1, 2);
        RouteKey b = new RouteKey(123, 1, 2);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testCompareToEqual() {
        RouteKey a = new RouteKey(123, 1, 2);
        RouteKey b = new RouteKey(123, 1, 2);
        assertEquals(0, a.compareTo(b));
    }

    @Test
    public void testCompareToByDpc() {
        RouteKey low = new RouteKey(100, 1, 2);
        RouteKey high = new RouteKey(200, 1, 2);
        assertTrue(low.compareTo(high) < 0);
        assertTrue(high.compareTo(low) > 0);
    }

    @Test
    public void testCompareToByOpc() {
        RouteKey low = new RouteKey(123, 1, 2);
        RouteKey high = new RouteKey(123, 5, 2);
        assertTrue(low.compareTo(high) < 0);
        assertTrue(high.compareTo(low) > 0);
    }

    @Test
    public void testCompareToBySi() {
        RouteKey low = new RouteKey(123, 1, 2);
        RouteKey high = new RouteKey(123, 1, 8);
        assertTrue(low.compareTo(high) < 0);
        assertTrue(high.compareTo(low) > 0);
    }

    @Test
    public void testParse() {
        RouteKey key = RouteKey.parse("123:1:2");
        assertEquals(123, key.getDpc());
        assertEquals(1, key.getOpc());
        assertEquals(2, key.getSi());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseTooManyParts() {
        RouteKey.parse("123:1:2:extra");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseTooFewParts() {
        RouteKey.parse("123:1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseEmpty() {
        RouteKey.parse("");
    }

    @Test(expected = NumberFormatException.class)
    public void testParseNonNumeric() {
        RouteKey.parse("abc:1:2");
    }

    @Test
    public void testToString() {
        RouteKey key = new RouteKey(123, 1, 2);
        assertEquals("123:1:2", key.toString());
    }

    @Test
    public void testParseRoundtrip() {
        RouteKey original = new RouteKey(456, 2, 3);
        RouteKey parsed = RouteKey.parse(original.toString());
        assertEquals(original, parsed);
        assertEquals(0, original.compareTo(parsed));
    }

    @Test
    public void testGetters() {
        RouteKey key = new RouteKey(111, 222, 333);
        assertEquals(111, key.getDpc());
        assertEquals(222, key.getOpc());
        assertEquals(333, key.getSi());
    }

    @Test
    public void testCompareToNullNotThrows() {
        RouteKey a = new RouteKey(1, 2, 3);
        // compareTo with null should throw NullPointerException per Comparable contract
        boolean threw = false;
        try {
            a.compareTo(null);
        } catch (NullPointerException e) {
            threw = true;
        }
        assertTrue("compareTo(null) should throw NullPointerException", threw);
    }
}
