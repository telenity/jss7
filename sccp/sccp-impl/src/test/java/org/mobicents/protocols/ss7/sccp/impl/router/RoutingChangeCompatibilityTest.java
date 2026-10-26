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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Compatibility and regression cover for the two GTT routing corrections:
 * the matchPattern cursor fix in RuleImpl and the missing return in
 * RuleComparator.
 *
 * The point is twofold - confirm the patterns that already worked still match
 * exactly as before, and confirm the comparator now satisfies the contract
 * Arrays.sort requires.
 *
 * @author alperc
 */
public class RoutingChangeCompatibilityTest {

    private final RuleComparator comparator = new RuleComparator();

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
    // RuleComparator
    // ------------------------------------------------------------------

    /**
     * Arrays.sort throws "Comparison method violates its general contract" when
     * a comparator is not antisymmetric. Dropping the recursive result made
     * compare(a,b) and compare(b,a) both return -1 for rules whose wildcards sit
     * at the same positions.
     */
    @Test
    public void comparatorIsAntisymmetric() throws Exception {
        List<RuleImpl> rules = sampleRules();
        for (RuleImpl a : rules) {
            for (RuleImpl b : rules) {
                int ab = comparator.compare(a, b);
                int ba = comparator.compare(b, a);
                if (a == b) {
                    continue;
                }
                assertTrue("compare(" + digits(a) + "," + digits(b) + ")=" + ab
                                + " and compare(" + digits(b) + "," + digits(a) + ")=" + ba
                                + " must have opposite signs",
                        (ab < 0 && ba > 0) || (ab > 0 && ba < 0) || (ab == 0 && ba == 0));
            }
        }
    }

    /** Sorting must not throw, and must be stable across repeated input orders. */
    @Test
    public void sortingIsConsistentRegardlessOfInputOrder() throws Exception {
        List<RuleImpl> rules = sampleRules();

        RuleImpl[] ascending = rules.toArray(new RuleImpl[0]);
        Arrays.sort(ascending, comparator);

        List<RuleImpl> reversedInput = new ArrayList<RuleImpl>(rules);
        java.util.Collections.reverse(reversedInput);
        RuleImpl[] fromReversed = reversedInput.toArray(new RuleImpl[0]);
        Arrays.sort(fromReversed, comparator);

        assertEquals("sort order must not depend on input order",
                digitsOf(ascending), digitsOf(fromReversed));
    }

    /**
     * The documented tie-break case: equal wildcard positions fall through to a
     * length comparison, so the shorter pattern sorts first. Previously both
     * directions returned -1 and the order was arbitrary.
     */
    @Test
    public void equalWildcardPositionsFallBackToLength() throws Exception {
        RuleImpl shorter = rule("80/??/9");
        RuleImpl longer = rule("80/??/999");
        assertTrue("shorter pattern must sort above longer one",
                comparator.compare(shorter, longer) < 0);
        assertTrue(comparator.compare(longer, shorter) > 0);
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

    private List<RuleImpl> sampleRules() throws Exception {
        return Arrays.asList(
                rule("800/????/9"),
                rule("*"),
                rule("9/?/9/*"),
                rule("80/??/0/???/9"),
                rule("800/?????/9"),
                rule("80/??/9"),
                rule("80/??/999"),
                rule("123456"),
                rule("1234??"));
    }

    private static String digits(RuleImpl rule) {
        return rule.getPattern().getGlobalTitle().getDigits();
    }

    private static List<String> digitsOf(RuleImpl[] rules) {
        List<String> out = new ArrayList<String>();
        for (RuleImpl r : rules) {
            out.add(digits(r));
        }
        return out;
    }
}
