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

package org.mobicents.protocols.ss7.sccp.impl.message;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
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
public class MessageReassemblyTest extends SccpHarness {

	private SccpAddress a1, a2;

	public MessageReassemblyTest() {
	}

	@Before
	public void setUpClass() throws Exception {
		this.sccpStack1Name = "MessageReassemblyTestSccpStack1";
		this.sccpStack2Name = "MessageReassemblyTestSccpStack2";
	}

	@After
	public void tearDownClass() throws Exception {
	}

	
	protected void createStack1() {
		sccpStack1 = new SccpStackImplProxy("sspTestSccpStack1");
		sccpProvider1 = sccpStack1.getSccpProvider();
	}

	
	protected void createStack2() {
		sccpStack2 = new SccpStackImplProxy("sspTestSccpStack2");
		sccpProvider2= sccpStack2.getSccpProvider();
	}

	@Before
	public void setUp() throws Exception {
		super.setUp();
	}

	@After
	public void tearDown() {
		super.tearDown();
	}

	public byte[] getDataXudt1() {
		return new byte[] { 17, (byte) 129, 15, 4, 6, 10, 15, 2, 66, 8, 4, 67, 1, 0, 6, 5, 11, 12, 13, 14, 15, 16, 4, (byte) 192, 100, 0, 0, 18, 1, 7, 0 };
	}

	@Test
	public void testReassembly() throws Exception {

		a1 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack1PC(), null, 8);
		a2 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack2PC(), null, 8);

		User u1 = new User(sccpStack1.getSccpProvider(), a1, a2, getSSN());
		User u2 = new User(sccpStack2.getSccpProvider(), a2, a1, getSSN());

		u1.register();
		u2.register();

		sccpStack1.setReassemblyTimerDelay(3000);
		Thread.sleep(100);

		// Receiving a chain of 3 XUDT segments -> success
		assertEquals(0, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), MessageSegmentationTest.getDataSegm1());
		Thread.sleep(100);
		assertEquals(1, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), MessageSegmentationTest.getDataSegm2());
		Thread.sleep(100);
		assertEquals(1, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		assertEquals(0, u1.getMessages().size());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), MessageSegmentationTest.getDataSegm3());
		Thread.sleep(100);
		assertEquals(0, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		assertEquals(1, u1.getMessages().size());
		SccpDataMessage dMsg =  (SccpDataMessage)u1.getMessages().get(0);
		assertTrue(Arrays.equals(dMsg.getData(), MessageSegmentationTest.getDataA()));
		assertEquals(0, u2.getMessages().size());

		// Receiving a single XUDT message without segments -> success
		assertEquals(0, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), getDataXudt1());
		Thread.sleep(100);
		assertEquals(0, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		assertEquals(2, u1.getMessages().size());
		dMsg =  (SccpDataMessage)u1.getMessages().get(1);
		assertTrue(Arrays.equals(dMsg.getData(), SccpDataMessageTest.getDataXudt1Src()));
		assertEquals(0, u2.getMessages().size());

		// Receiving a chain of 3 XUDTS segments -> success
		assertEquals(0, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), MessageSegmentationTest.getDataSegm1_S());
		Thread.sleep(100);
		assertEquals(1, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), MessageSegmentationTest.getDataSegm2_S());
		Thread.sleep(100);
		assertEquals(1, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		assertEquals(2, u1.getMessages().size());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), MessageSegmentationTest.getDataSegm3_S());
		Thread.sleep(100);
		assertEquals(0, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		assertEquals(3, u1.getMessages().size());
		SccpNoticeMessage nMsg =  (SccpNoticeMessage)u1.getMessages().get(2);
		assertTrue(Arrays.equals(nMsg.getData(), MessageSegmentationTest.getDataA()));
		assertEquals(0, u2.getMessages().size());

		// Receiving an only the first segment of 3--segmented chain of 3 XUDT segments -> timeout
		assertEquals(0, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), MessageSegmentationTest.getDataSegm1());
		Thread.sleep(100);
		assertEquals(1, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		Thread.sleep(5000);  // waiting for timeout - current timeout is 3 sec
		assertEquals(0, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		assertEquals(1, u2.getMessages().size());
		assertEquals(ReturnCauseValue.CANNOT_REASEMBLE, ((SccpNoticeMessage) u2.getMessages().get(0)).getReturnCause().getValue());

		// Receiving an only the second segment of 3--segmented chain of 3 XUDT segments -> error
		assertEquals(0, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), MessageSegmentationTest.getDataSegm2());
		Thread.sleep(100);
		assertEquals(0, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		assertEquals(3, u1.getMessages().size());
		assertEquals(1, u2.getMessages().size());

		// Receiving only th 1 and 3 message from a chain of 3 XUDTS segments -> error
		assertEquals(0, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), MessageSegmentationTest.getDataSegm1_S());
		Thread.sleep(100);
		assertEquals(1, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), MessageSegmentationTest.getDataSegm3_S());
		Thread.sleep(100);
		assertEquals(0, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		assertEquals(3, u1.getMessages().size());
		assertEquals(1, u2.getMessages().size()); // no error for service messages

		// Receiving two chains of 3 XUDT and XUDTS segments -> success
		assertEquals(3, u1.getMessages().size());
		assertEquals(0, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), MessageSegmentationTest.getDataSegm1());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), MessageSegmentationTest.getDataSegm1_S());
		Thread.sleep(100);
		assertEquals(2, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), MessageSegmentationTest.getDataSegm2());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), MessageSegmentationTest.getDataSegm2_S());
		Thread.sleep(100);
		assertEquals(2, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		assertEquals(3, u1.getMessages().size());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), MessageSegmentationTest.getDataSegm3());
		this.mtp3UserPart1.sendTransferMessageToLocalUser(getStack2PC(), getStack1PC(), MessageSegmentationTest.getDataSegm3_S());
		Thread.sleep(100);
		assertEquals(0, ((SccpStackImplProxy) this.sccpStack1).getReassemplyCacheSize());
		assertEquals(5, u1.getMessages().size());
		dMsg = (SccpDataMessage) u1.getMessages().get(3);
		nMsg = (SccpNoticeMessage) u1.getMessages().get(4);
		assertTrue(Arrays.equals(dMsg.getData(), MessageSegmentationTest.getDataA()));
		assertTrue(Arrays.equals(nMsg.getData(), MessageSegmentationTest.getDataA()));
		assertEquals(1, u2.getMessages().size());
		
		int i = 1;
		i++;
	}
}

