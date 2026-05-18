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

package org.mobicents.protocols.ss7.sccp.impl.messageflow;

import static org.junit.Assert.assertEquals;

import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.sccp.LongMessageRuleType;
import org.mobicents.protocols.ss7.sccp.impl.Mtp3UserPartImpl;
import org.mobicents.protocols.ss7.sccp.impl.SccpHarness;
import org.mobicents.protocols.ss7.sccp.impl.SccpStackImplProxy;
import org.mobicents.protocols.ss7.sccp.impl.User;
import org.mobicents.protocols.ss7.sccp.message.SccpDataMessage;
import org.mobicents.protocols.ss7.sccp.message.SccpNoticeMessage;
import org.mobicents.protocols.ss7.sccp.parameter.ReturnCauseValue;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.junit.AfterClass;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * @author sergey vetyutnev
 * 
 */
public class MessageMultiSapTest extends SccpHarness {

	private SccpAddress a1, a2;
	protected Mtp3UserPartImpl mtp3UserPart11 = new Mtp3UserPartImpl();

	public MessageMultiSapTest() {
	}

	@Before
	public void setUpClass() throws Exception {
		this.sccpStack1Name = "MessageTransferTestSccpStack1";
		this.sccpStack2Name = "MessageTransferTestSccpStack2";
	}

	@After
	public void tearDownClass() throws Exception {
	}

	
	protected void createStack1() {
		sccpStack1 = new SccpStackImplProxy("sspTestSccpStack1");
		sccpProvider1 = sccpStack1.getSccpProvider();

		sccpStack1.setMtp3UserPart(2, mtp3UserPart11);
	}

	
	protected void createStack2() {
		sccpStack2 = new SccpStackImplProxy("sspTestSccpStack2");
		sccpProvider2= sccpStack2.getSccpProvider();
	}

	@Before
	public void setUp() throws Exception {
		super.setUp();
		
		sccpStack1.getRouter().addMtp3ServiceAccessPoint(2, 2, 11, 2);
		sccpStack1.getRouter().addMtp3Destination(2, 1, 12, 12, 0, 255, 255);

		resource1.addRemoteSpc(2, 12, 0, 0);
		resource1.addRemoteSsn(2, 12, getSSN(), 0, false);
	}

	@After
	public void tearDown() {
		super.tearDown();
	}

	public byte[] getDataSrc() {
		return new byte[] { 11, 12, 13, 14, 15 };
	}

	public byte[] getDataUdt1() {
		return new byte[] { 9, 0,     3, 5, 8,    2, 66, 8,    4, 67, 11, 0, 6,     5, 11, 12, 13, 14, 15 };
	}

	public byte[] getDataUdt2() {
		return new byte[] { 9, 0,     3, 7, 10,    4, 67, 2, 0, 8,    4, 67, 11, 0, 6,     5, 11, 12, 13, 14, 15 };
	}

	public byte[] getDataUdt3() {
		return new byte[] { 9, 0,     3, 5, 7,    2, 66, 8,    2, 66, 8,     5, 11, 12, 13, 14, 15 };
	}

	@Test
	public void testTransfer() throws Exception {

		a1 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack1PC(), null, 8);
		a2 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack2PC(), null, 8);
		SccpAddress a3 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 12, null, 8);

		User u1 = new User(sccpStack1.getSccpProvider(), a1, a2, getSSN());
		User u2 = new User(sccpStack2.getSccpProvider(), a2, a1, getSSN());

		u1.register();
		u2.register();

		Thread.sleep(100);

		// send a UDT message to the sap1 (opc=1, dpc=2)
		sccpStack1.getRouter().addLongMessageRule(1, 2, 2, LongMessageRuleType.LongMessagesForbidden);
		sccpStack1.getRouter().addLongMessageRule(2, 12, 12, LongMessageRuleType.LongMessagesForbidden);
		SccpDataMessage message = this.sccpProvider1.getMessageFactory().createDataMessageClass1(a2, a1, getDataSrc(), 0, 8, true, null,
				null);
		sccpProvider1.send(message);
		Thread.sleep(100);
		assertEquals(0, u1.getMessages().size());
		assertEquals(1, u2.getMessages().size());
		assertEquals(0, mtp3UserPart11.getMessages().size());

		// send a UDT message to the sap2 (opc=11, dpc=12)
		message = this.sccpProvider1.getMessageFactory().createDataMessageClass1(a3, a1, getDataSrc(), 0, 8, true, null, null);
		sccpProvider1.send(message);
		Thread.sleep(100);
		assertEquals(0, u1.getMessages().size());
		assertEquals(1, u2.getMessages().size());
		assertEquals(1, mtp3UserPart11.getMessages().size());

		// send a UDT message to the absent sap (remoteSpc and remoteSsn are present and not prohibited)
		resource1.addRemoteSpc(3, 15, 0, 0);
		resource1.addRemoteSsn(3, 15, getSSN(), 0, false);
		SccpAddress a4 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 15, null, 8);
		message = this.sccpProvider1.getMessageFactory().createDataMessageClass1(a4, a1, getDataSrc(), 0, 8, true, null, null);
		sccpProvider1.send(message);
		Thread.sleep(100);
		assertEquals(1, u1.getMessages().size());
		assertEquals(1, u2.getMessages().size());
		assertEquals(1, mtp3UserPart11.getMessages().size());
		SccpNoticeMessage nMsg = (SccpNoticeMessage) u1.getMessages().get(0);
		assertEquals(ReturnCauseValue.SCCP_FAILURE, nMsg.getReturnCause().getValue());

		// receiving a message from the sap2 to the local sccp user -> delivering a message 
		this.mtp3UserPart11.sendTransferMessageToLocalUser(12, 11, getDataUdt1());
		Thread.sleep(500);
		assertEquals(2, u1.getMessages().size());
		assertEquals(1, u2.getMessages().size());
		assertEquals(1, mtp3UserPart11.getMessages().size());

		// receiving a message from the sap2 to the sap2 () -> sccp transit 
		this.mtp3UserPart11.sendTransferMessageToLocalUser(12, 2, getDataUdt2());
		Thread.sleep(100);
		assertEquals(2, u1.getMessages().size());
		assertEquals(2, u2.getMessages().size());
		assertEquals(1, mtp3UserPart11.getMessages().size());

		// receiving a message from mtp3 without dpc in CallingPartyAddress (RouteOnSsn in CallingPartyAddress)
		this.mtp3UserPart11.sendTransferMessageToLocalUser(2, 1, getDataUdt3());
		Thread.sleep(100);
		assertEquals(3, u1.getMessages().size());
		assertEquals(2, u2.getMessages().size());
		assertEquals(1, mtp3UserPart11.getMessages().size());
		SccpDataMessage dMsg = (SccpDataMessage) u1.getMessages().get(2);
		assertEquals(2, dMsg.getCallingPartyAddress().getSignalingPointCode());
	}
}

