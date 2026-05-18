/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.mobicents.protocols.ss7.sccp.impl.parameter;

import org.junit.*;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;

import javolution.xml.XMLObjectReader;
import javolution.xml.XMLObjectWriter;

import org.mobicents.protocols.ss7.indicator.GlobalTitleIndicator;
import org.mobicents.protocols.ss7.indicator.NatureOfAddress;
import org.mobicents.protocols.ss7.indicator.NumberingPlan;
import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.sccp.parameter.GlobalTitle;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;

/**
 * 
 * @author kulikov
 */
public class SccpAddressTest {

//	private SccpAddressCodec codec = new SccpAddressCodec(false);
	private byte[] data = new byte[] { 0x12, (byte) 0x92, 0x00, 0x11, 0x04, (byte) 0x97, 0x20, (byte) 0x73, 0x00,
			(byte) 0x92, 0x09 };

	public SccpAddressTest() {
	}

	@BeforeClass
	public static void setUpClass() throws Exception {
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	}

	@Before
	public void setUp() {
	}

	@After
	public void tearDown() {
	}

	/**
	 * Test of decode method, of class SccpAddressCodec.
	 */
	@Test
	public void testDecode1() throws Exception {
		SccpAddress address = SccpAddressCodec.decode(data);

		assertEquals(0, address.getSignalingPointCode());
		assertEquals(146, address.getSubsystemNumber());
		assertEquals("79023700299", address.getGlobalTitle().getDigits());
	}

	@Test
	public void testDecode2() throws Exception {
		SccpAddress address = SccpAddressCodec.decode(new byte[] { 0x42, 0x08 });

		assertEquals(0, address.getSignalingPointCode());
		assertEquals(8, address.getSubsystemNumber());
		assertNull(address.getGlobalTitle());
	}

	/**
	 * Test of encode method, of class SccpAddressCodec.
	 */
	@Test
	public void testEncode() throws Exception {
		GlobalTitle gt = GlobalTitle.getInstance(0, NumberingPlan.ISDN_TELEPHONY, NatureOfAddress.INTERNATIONAL,
				"79023700299");
		SccpAddress address = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, 0, gt, 146);
		byte[] bin = SccpAddressCodec.encode(address, false);
		assertTrue("Wrong encoding", Arrays.equals(data, bin));
	}

	@Test
	public void testEncode2() throws Exception {
		SccpAddress address = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 0, null, 8);
		byte[] bin = SccpAddressCodec.encode(address, false);
		assertTrue("Wrong encoding", Arrays.equals(new byte[] { 0x42, 0x08 }, bin));
	}

	/**
	 * Test to see if the DPC is removed from the SCCP Address when instructed
	 * 
	 * @throws Exception
	 */
	@Test
	public void testEncode3() throws Exception {
		byte[] data1 = new byte[] { 0x12, 0x06, 0x00, 0x11, 0x04, 0x39, 0x07, (byte) 0x92, 0x49, 0x00, 0x06 };
//		SccpAddressCodec codec = new SccpAddressCodec(true);

		GlobalTitle gt = GlobalTitle.getInstance(0, NumberingPlan.ISDN_TELEPHONY, NatureOfAddress.INTERNATIONAL,
				"93702994006");
		SccpAddress address = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, 5530, gt, 6);
		byte[] bin = SccpAddressCodec.encode(address, true);
		assertTrue("Wrong encoding", Arrays.equals(data1, bin));
		
		//Now test decode
		
	}
	
	/**
	 * Test of getAddressIndicator method, of class SccpAddress.
	 */
	@Test
	public void testEquals() {
		GlobalTitle gt = GlobalTitle.getInstance(NatureOfAddress.NATIONAL, "123");
		SccpAddress a1 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, 0, gt, 0);
		SccpAddress a2 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, 0, gt, 0);
		assertEquals(a2, a1);
		assertEquals(a2.hashCode(), a1.hashCode());
	}

	@Test
	public void testEquals1() {
		GlobalTitle gt = GlobalTitle.getInstance(0, NumberingPlan.ISDN_TELEPHONY, NatureOfAddress.INTERNATIONAL,
				"79023700271");
		SccpAddress a1 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, 146, gt, 0);

		HashMap<SccpAddress, Integer> map = new HashMap();
		map.put(a1, 1);

		SccpAddress a2 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, 146, gt, 0);
		Integer i = map.get(a2);

		if (i == null) {
			fail("Address did not match");
		}

		assertEquals(i, new Integer(1));
	}

	@Test
	public void testSerialization() throws Exception {
		GlobalTitle gt = GlobalTitle.getInstance(0, NumberingPlan.ISDN_TELEPHONY, NatureOfAddress.INTERNATIONAL,
				"79023700271");
		SccpAddress a1 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, 146, gt, 0);

		// Writes
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		XMLObjectWriter writer = XMLObjectWriter.newInstance(output);
		writer.setIndentation("\t"); // Optional (use tabulation for
		// indentation).
		writer.write(a1, "SccpAddress", SccpAddress.class);
		writer.close();

		System.out.println(output.toString());

		ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
		XMLObjectReader reader = XMLObjectReader.newInstance(input);
		SccpAddress aiOut = reader.read("SccpAddress", SccpAddress.class);

		assertEquals(aiOut.getAddressIndicator().getGlobalTitleIndicator(), GlobalTitleIndicator.GLOBAL_TITLE_INCLUDES_TRANSLATION_TYPE_NUMBERING_PLAN_ENCODING_SCHEME_AND_NATURE_OF_ADDRESS);
		assertEquals(aiOut.getAddressIndicator().getRoutingIndicator(), RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE);
		assertTrue(aiOut.getAddressIndicator().pcPresent());
		assertFalse(aiOut.getAddressIndicator().ssnPresent());

		assertEquals(aiOut.getSignalingPointCode(), 146);
		assertEquals(aiOut.getSubsystemNumber(), 0);

		assertEquals(aiOut.getGlobalTitle().getDigits(), "79023700271");
	}

	@Test
	public void testSerialization1() throws Exception {

		SccpAddress a1 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 146, null, 8);

		// Writes
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		XMLObjectWriter writer = XMLObjectWriter.newInstance(output);
		writer.setIndentation("\t"); // Optional (use tabulation for
		// indentation).
		writer.write(a1, "SccpAddress", SccpAddress.class);
		writer.close();

		System.out.println(output.toString());

		ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
		XMLObjectReader reader = XMLObjectReader.newInstance(input);
		SccpAddress aiOut = reader.read("SccpAddress", SccpAddress.class);

		assertEquals(aiOut.getAddressIndicator()
				.getGlobalTitleIndicator(), GlobalTitleIndicator.NO_GLOBAL_TITLE_INCLUDED);
		assertEquals(aiOut.getAddressIndicator().getRoutingIndicator(), RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN);
		assertTrue(aiOut.getAddressIndicator().pcPresent());
		assertTrue(aiOut.getAddressIndicator().ssnPresent());

		assertEquals(aiOut.getSignalingPointCode(), 146);
		assertEquals(aiOut.getSubsystemNumber(), 8);

		assertNull(aiOut.getGlobalTitle());
	}

}
