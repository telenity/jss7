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

package org.mobicents.protocols.ss7.sccp.impl.router;

import org.junit.Test;
import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.sccp.LoadSharingAlgorithm;
import org.mobicents.protocols.ss7.sccp.OriginationType;
import org.mobicents.protocols.ss7.sccp.RuleType;
import org.mobicents.protocols.ss7.sccp.parameter.GlobalTitle;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Compatibility and regression cover for the matchPattern cursor fix in
 * RuleImpl: confirm the patterns that already worked still match exactly as
 * before.
 *
 * @author alperc
 */
public class RoutingChangeCompatibilityTest {

    // ------------------------------------------------------------------
    // RuleImpl.matchPattern
    // ------------------------------------------------------------------

    /**
     * Patterns without a separator were always evaluated with i == j, so these
     * must behave exactly as they did before the fix.
     */
    @Test
    public void singleSectionPatternsAreUnchanged() throws Exception {
        assertMatches("*", "123456", true);
        assertMatches("123456", "123456", true);
        assertMatches("123456", "123457", false);
        assertMatches("1234??", "123456", true);
        assertMatches("1234??", "12345", false);
        assertMatches("123*", "123456", true);
        assertMatches("123456", "12345", false);
        assertMatches("12345", "123456", false);
    }

    /**
     * Multi-section patterns that do not exhaust the digits early were also
     * unaffected by the cursor bug; they must be unchanged too.
     */
    @Test
    public void multiSectionPatternsStillMatchAsBefore() throws Exception {
        // 80(2) + ??(2) + 0(1) + ???(3) + 9(1) = 9 digits
        assertMatches("80/??/0/???/9", "801203459", true);
        assertMatches("80/??/0/???/9", "8012034569", false);
        assertMatches("800/?????/9", "800123459", true);
        assertMatches("9/?/9/*", "919777", true);
        assertMatches("1/2/3", "123", true);
        assertMatches("1/2/3", "124", false);
        assertMatches("12/34", "1234", true);
        assertMatches("12/34", "1235", false);
    }

    /**
     * The defect: with a trailing "/*" the exhausted-digits branch indexed the
     * pattern with the digit cursor, so after any separator it read the wrong
     * character and the rule failed to match.
     */
    @Test
    public void trailingWildcardMatchesWhenDigitsEndAtSectionBoundary() throws Exception {
        assertMatches("12/45/*", "1245", true);
        assertMatches("123/456/*", "123456", true);
        assertMatches("1/2/*", "12", true);
        // 80(2) + ??(2) + 0(1) = 5 digits, then /* covers the rest
        assertMatches("80/??/0/*", "80120", true);
        // the single-section form of the same case, which always worked
        assertMatches("1245/*", "1245", true);
    }

    /**
     * A pattern ending in a bare separator must not read past the array. The old
     * unguarded pattern[j + 1] access threw ArrayIndexOutOfBoundsException here;
     * with no wildcard after the separator the correct answer is simply no match.
     */
    @Test
    public void patternEndingInSeparatorDoesNotOverrun() throws Exception {
        assertMatches("12/", "12", false);
        assertMatches("12/45/", "1245", false);
        assertMatches("1/", "1", false);
    }

    /** A trailing "/*" must still not match digits that are genuinely different. */
    @Test
    public void trailingWildcardStillRejectsNonMatchingPrefix() throws Exception {
        assertMatches("12/45/*", "1246", false);
        assertMatches("12/45/*", "99", false);
        assertMatches("123/456/*", "123999", false);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void assertMatches(String pattern, String digits, boolean expected) throws Exception {
        RuleImpl rule = rule(pattern);
        SccpAddress address = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, 0,
                GlobalTitle.getInstance(1, digits), 0);
        boolean actual = rule.matches(address, false);
        if (expected) {
            assertTrue("pattern '" + pattern + "' should match digits '" + digits + "'", actual);
        } else {
            assertFalse("pattern '" + pattern + "' should not match digits '" + digits + "'", actual);
        }
    }

    private RuleImpl rule(String pattern) throws Exception {
        SccpAddress address = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, 0,
                GlobalTitle.getInstance(1, pattern), 0);
        StringBuilder mask = new StringBuilder();
        int sections = pattern.split("/", -1).length;
        for (int i = 0; i < sections; i++) {
            if (i > 0) {
                mask.append('/');
            }
            mask.append('K');
        }
        return new RuleImpl(RuleType.Solitary, LoadSharingAlgorithm.Undefined, OriginationType.ALL, address,
                mask.toString());
    }
}
