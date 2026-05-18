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

package org.mobicents.protocols.ss7.sccp.impl.translation;


import static org.junit.Assert.assertTrue;

import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.sccp.impl.SccpHarness;
import org.mobicents.protocols.ss7.sccp.impl.User;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.junit.AfterClass;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;

/**
 * @author amit bhayani
 * @author kulikov
 * @author baranowb
 */
public class PCSSNSccpStackImplTest extends SccpHarness {

	private SccpAddress a1, a2;

	public PCSSNSccpStackImplTest() {
	}

	@Before
	public void setUpClass() throws Exception {
		this.sccpStack1Name = "PCSSNSccTestSccpStack1";
		this.sccpStack2Name = "PCSSNSccTestSccpStack2";
	}

	@After
	public void tearDownClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
		super.setUp();
	}

	@After
	public void tearDown() {
		super.tearDown();
	}

	/**
	 * Test of configure method, of class SccpStackImpl.
	 */
	@Test
	public void testRemoteRoutingBasedOnSsn() throws Exception {
		a1 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 1, null, 8);
		a2 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 2, null, 8);
		
		User u1 = new User(sccpStack1.getSccpProvider(), a1, a2,getSSN());
		User u2 = new User(sccpStack2.getSccpProvider(), a2, a1,getSSN());

		u1.register();
		u2.register();
		
		u1.send();
		u2.send();

		Thread.currentThread().sleep(3000);

		assertTrue("Message not received",  u1.check());
		assertTrue("Message not received",  u2.check());
	}
	
}
