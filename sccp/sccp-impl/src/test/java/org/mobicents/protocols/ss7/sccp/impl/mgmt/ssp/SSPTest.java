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

package org.mobicents.protocols.ss7.sccp.impl.mgmt.ssp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.sccp.impl.RemoteSubSystemImpl;
import org.mobicents.protocols.ss7.sccp.impl.SccpHarness;
import org.mobicents.protocols.ss7.sccp.impl.SccpStackImplProxy;
import org.mobicents.protocols.ss7.sccp.impl.User;
import org.mobicents.protocols.ss7.sccp.impl.mgmt.Mtp3PrimitiveMessage;
import org.mobicents.protocols.ss7.sccp.impl.mgmt.Mtp3PrimitiveMessageType;
import org.mobicents.protocols.ss7.sccp.impl.mgmt.SccpMgmtMessage;
import org.mobicents.protocols.ss7.sccp.impl.mgmt.SccpMgmtMessageType;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.junit.AfterClass;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;

/**
 * Test condition when SSN is not available in one stack aka prohibited
 *
 * @author amit bhayani
 * @author baranowb
 * @author sergey vetyutnev
 */
public class SSPTest extends SccpHarness {

	private SccpAddress a1, a2;

	public SSPTest() {
	}

	@Before
	public void setUpClass() throws Exception {
		this.sccpStack1Name = "SSPTestSccpStack1";
		this.sccpStack2Name = "SSPTestSccpStack2";
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

	@Test
	public void testDummy() throws Exception {
		int i = 1;
		assertTrue(i==1);
	}



	/**
	 * Test of configure method, of class SccpStackImpl.
	 */
	@Test
	public void testRemoteRoutingBasedOnSsn() throws Exception {

		a1 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack1PC(), null, 8);
		a2 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack2PC(), null, 8);

		User u1 = new User(sccpStack1.getSccpProvider(), a1, a2, getSSN());
		User u2 = new User(sccpStack2.getSccpProvider(), a2, a1, getSSN());

		sccpStack1.setSstTimerDuration_Min(5000);
		sccpStack1.setSstTimerDuration_IncreaseFactor(1);

		u1.register();
		//u2.register();
		//this will cause: u1 stack will receive SSP, u2 stack will get SST and message.

		u1.send();
		u2.send();

		Thread.sleep(100);

		assertTrue("U1 did not receiv message, it should!", u1.getMessages().size() == 1);
		assertTrue("Inproper message not received!", u1.check());
		assertTrue("U2 Received message, it should not!", u2.getMessages().size() == 0);

		//now lets check functional.mgmt part

		SccpStackImplProxy stack = (SccpStackImplProxy) sccpStack1;

		assertTrue("U1 received Mtp3 Primitve, it should not!", stack.getManagementProxy().getMtp3Messages().size() == 0);
		assertTrue("U1 did not receive Management message, it should !", stack.getManagementProxy().getMgmtMessages().size() == 1);
		SccpMgmtMessage rmsg1_ssp = stack.getManagementProxy().getMgmtMessages().get(0);
		SccpMgmtMessage emsg1_ssp = new SccpMgmtMessage(0, SccpMgmtMessageType.SSP.getType(), getSSN(), 2, 0);
		assertEquals("Failed to match management message in U1", emsg1_ssp, rmsg1_ssp);

		//check if there is no SST
		stack = (SccpStackImplProxy) sccpStack2;

		assertTrue("U2 received Mtp3 Primitve, it should not!", stack.getManagementProxy().getMtp3Messages().size() == 0);
		assertTrue("U2 did not receive Management message, it should !", stack.getManagementProxy().getMgmtMessages().size() == 0);

		Thread.sleep(6000);

		assertTrue("U2 received Mtp3 Primitve, it should not!", stack.getManagementProxy().getMtp3Messages().size() == 0);
		assertTrue("U2 did not receive Management message, it should !", stack.getManagementProxy().getMgmtMessages().size() == 1);
		SccpMgmtMessage rmsg2_sst = stack.getManagementProxy().getMgmtMessages().get(0);
		SccpMgmtMessage emsg2_sst = new SccpMgmtMessage(0, SccpMgmtMessageType.SST.getType(), getSSN(), 2, 0);
		assertEquals("Failed to match management message in U2", emsg2_sst, rmsg2_sst);

		assertTrue("Out of sync messages, SST received before SSP.", rmsg2_sst.getTstamp() >= rmsg1_ssp.getTstamp());

		// register;
		u2.register();
		Thread.sleep(5000);
		stack = (SccpStackImplProxy) sccpStack1;
		// double check first message.
		assertTrue("U1 received Mtp3 Primitve, it should not!", stack.getManagementProxy().getMtp3Messages().size() == 0);
		assertTrue("U1 did not receive Management message, it should !", stack.getManagementProxy().getMgmtMessages().size() == 2);
		rmsg1_ssp = stack.getManagementProxy().getMgmtMessages().get(0);
		emsg1_ssp = new SccpMgmtMessage(0, SccpMgmtMessageType.SSP.getType(), getSSN(), 2, 0);
		assertEquals("Failed to match management message in U1", emsg1_ssp, rmsg1_ssp);

		//now second message MUST be SSA here 
		SccpMgmtMessage rmsg1_ssa = stack.getManagementProxy().getMgmtMessages().get(1);
		SccpMgmtMessage emsg1_ssa = new SccpMgmtMessage(1, SccpMgmtMessageType.SSA.getType(), getSSN(), 2, 0);

		assertEquals("Failed to match management message in U1", emsg1_ssa, rmsg1_ssa);

		//now lets check other one
		//check if there is no SST
		stack = (SccpStackImplProxy) sccpStack2;

		assertTrue("U2 received Mtp3 Primitve, it should not!", stack.getManagementProxy().getMtp3Messages().size() == 0);
		assertTrue("U2 did not receive Management message, it should !", stack.getManagementProxy().getMgmtMessages().size() == 2);
		rmsg2_sst = stack.getManagementProxy().getMgmtMessages().get(0);
		emsg2_sst = new SccpMgmtMessage(0, SccpMgmtMessageType.SST.getType(), getSSN(), 2, 0);
		assertEquals("Failed to match management message in U2", emsg2_sst, rmsg2_sst);

		rmsg2_sst = stack.getManagementProxy().getMgmtMessages().get(1);
		emsg2_sst = new SccpMgmtMessage(1, SccpMgmtMessageType.SST.getType(), getSSN(), 2, 0);
		assertEquals("Failed to match management message in U2", emsg2_sst, rmsg2_sst);
		assertTrue("Out of sync messages, SST received before SSP.", rmsg2_sst.getTstamp() >= rmsg1_ssp.getTstamp());

		//now lets wait and check if there is nothing more
		Thread.sleep(5000);
		stack = (SccpStackImplProxy) sccpStack1;
		//double check first message.
		assertTrue("U1 received Mtp3 Primitve, it should not!", stack.getManagementProxy().getMtp3Messages().size() == 0);
		assertTrue("U1 received more functional.mgmt messages than it should !", stack.getManagementProxy().getMgmtMessages().size() == 2);

		stack = (SccpStackImplProxy) sccpStack2;

		assertTrue("U2 received Mtp3 Primitve, it should not!", stack.getManagementProxy().getMtp3Messages().size() == 0);
		assertTrue("U2 received more functional.mgmt messages than it should!", stack.getManagementProxy().getMgmtMessages().size() == 2);

		//try to send;

		u1.send();

		Thread.sleep(100);

		assertTrue("U1 did not receiv message, it should!", u1.getMessages().size() == 1);
		assertTrue("Inproper message not received!", u1.check());
		assertTrue("U2 did not receiv message, it should!", u2.getMessages().size() == 1);

		//TODO: should we check flags in MgmtProxies.

	}


	/**
	 * At first the SSN is not available and henvce U1 should receive SSP. After that MTP3Pause recevied for peer(u2, pc2). The resume and all should work again
	 */
	@Test
	public void testRemoteRoutingBasedOnSsn1() throws Exception {

		a1 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack1PC(), null, 8);
		a2 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack2PC(), null, 8);

		User u1 = new User(sccpStack1.getSccpProvider(), a1, a2, getSSN());
		User u2 = new User(sccpStack2.getSccpProvider(), a2, a1, getSSN());

		sccpStack1.setSstTimerDuration_Min(5000);
		sccpStack1.setSstTimerDuration_IncreaseFactor(1);

		u1.register();
		//u2.register();
		//this will cause: u1 stack will receive SSP, u2 stack will get SST and message.

		u1.send();
		u2.send();

		Thread.sleep(100);

		assertTrue("U1 did not receiv message, it should!", u1.getMessages().size() == 1);
		assertTrue("Inproper message not received!", u1.check());
		assertTrue("U2 Received message, it should not!", u2.getMessages().size() == 0);

		//now lets check mgmt part

		SccpStackImplProxy stack = (SccpStackImplProxy) sccpStack1;

		assertTrue("U1 received Mtp3 Primitve, it should not!", stack.getManagementProxy().getMtp3Messages().size() == 0);
		assertTrue("U1 did not receive Management message, it should !", stack.getManagementProxy().getMgmtMessages().size() == 1);
		SccpMgmtMessage rmsg1_ssp = stack.getManagementProxy().getMgmtMessages().get(0);
		SccpMgmtMessage emsg1_ssp = new SccpMgmtMessage(0, SccpMgmtMessageType.SSP.getType(), getSSN(), 2, 0);
		assertEquals("Failed to match management message in U1", emsg1_ssp, rmsg1_ssp);

		// check if there is no SST
		stack = (SccpStackImplProxy) sccpStack2;

		assertTrue("U2 received Mtp3 Primitve, it should not!", stack.getManagementProxy().getMtp3Messages().size() == 0);
		assertTrue("U2 did not receive Management message, it should !", stack.getManagementProxy().getMgmtMessages().size() == 0);

		Thread.sleep(6000);

		assertTrue("U2 received Mtp3 Primitve, it should not!", stack.getManagementProxy().getMtp3Messages().size() == 0);
		assertTrue("U2 did not receive Management message, it should !", stack.getManagementProxy().getMgmtMessages().size() == 1);
		SccpMgmtMessage rmsg2_sst = stack.getManagementProxy().getMgmtMessages().get(0);
		SccpMgmtMessage emsg2_sst = new SccpMgmtMessage(0, SccpMgmtMessageType.SST.getType(), getSSN(), 2, 0);
		assertEquals("Failed to match management message in U2", emsg2_sst, rmsg2_sst);

		assertTrue("Out of sync messages, SST received before SSP.", rmsg2_sst.getTstamp() >= rmsg1_ssp.getTstamp());


		//		super.data1.add(createPausePrimitive(getStack2PC()));
		this.mtp3UserPart1.sendPauseMessageToLocalUser(getStack2PC());

		//register;
		u2.register();
		Thread.sleep(5000);
		stack = (SccpStackImplProxy) sccpStack1;
		//double check first message.
		assertTrue(stack.getManagementProxy().getMtp3Messages().size() == 1);
		assertTrue(stack.getManagementProxy().getMgmtMessages().size() == 1);
		rmsg1_ssp = stack.getManagementProxy().getMgmtMessages().get(0);
		emsg1_ssp = new SccpMgmtMessage(0, SccpMgmtMessageType.SSP.getType(), getSSN(), 2, 0);
		assertEquals("Failed to match management message in U1", emsg1_ssp, rmsg1_ssp);

//		//now second message MUST be SSA here 
//		SccpMgmtMessage rmsg1_ssa = stack.getManagementProxy().getMgmtMessages().get(1);
//		SccpMgmtMessage emsg1_ssa = new SccpMgmtMessage(1,SccpMgmtMessageType.SSA.getType(), getSSN(), 2, 0);
//		
//		assertEquals("Failed to match management message in U1", emsg1_ssa, rmsg1_ssa);

//		//now lets check other one
//		//check if there is no SST
//		 stack = (SccpStackImplProxy) sccpStack2;
//		
//		assertTrue(stack.getManagementProxy().getMtp3Messages().size() == 0, it should not!","U2 received Mtp3 Primitve);
//		assertTrue(stack.getManagementProxy().getMgmtMessages().size() == 2, it should !","U2 did not receive Management message);
//		rmsg2_sst = stack.getManagementProxy().getMgmtMessages().get(0);
//		emsg2_sst = new SccpMgmtMessage(0,SccpMgmtMessageType.SST.getType(), getSSN(), 2, 0);
//		assertEquals("Failed to match management message in U2", emsg2_sst, rmsg2_sst);
//		
//		rmsg2_sst = stack.getManagementProxy().getMgmtMessages().get(1);
//		emsg2_sst = new SccpMgmtMessage(1,SccpMgmtMessageType.SST.getType(), getSSN(), 2, 0);
//		assertEquals("Failed to match management message in U2", emsg2_sst, rmsg2_sst);
//		assertTrue(rmsg2_sst.getTstamp()>=rmsg1_ssp.getTstamp(), SST received before SSP.","Out of sync messages);
//		
//		//now lets wait and check if there is nothing more
//		Thread.currentThread().sleep(12000);
//		stack = (SccpStackImplProxy) sccpStack1;
//		//double check first message.
//		assertTrue(stack.getManagementProxy().getMtp3Messages().size() == 0, it should not!","U1 received Mtp3 Primitve);
//		assertTrue("U1 received more mgmt messages than it should !", stack.getManagementProxy().getMgmtMessages().size() == 2);
//		
//		 stack = (SccpStackImplProxy) sccpStack2;
//			
//		assertTrue(stack.getManagementProxy().getMtp3Messages().size() == 0, it should not!","U2 received Mtp3 Primitve);
//		assertTrue("U2 received more mgmt messages than it should!", stack.getManagementProxy().getMgmtMessages().size() == 2);
//		
//		//try to send;
//		
//		u1.send();
//
//		Thread.currentThread().sleep(1000);
//
//		assertTrue( u1.getMessages().size() == 1, it should!","U1 did not receiv message);
//		assertTrue("Inproper message not received!",  u1.check());
//		assertTrue( u2.getMessages().size() == 1, it should!","U2 did not receiv message);

		//TODO: should we check flags in MgmtProxies.

	}

	/**
	 * Test of configure method, of class SccpStackImpl.
	 */
	@Test
	public void RecdMsgForProhibitedSsnTest() throws Exception {

		a1 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack1PC(), null, 8);
		a2 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack2PC(), null, 8);

		User u1 = new User(sccpStack1.getSccpProvider(), a1, a2, getSSN());
		User u2 = new User(sccpStack2.getSccpProvider(), a2, a1, getSSN());

		sccpStack1.setSstTimerDuration_Min(5000);
		sccpStack1.setSstTimerDuration_IncreaseFactor(1);

		u1.register();
		//u2.register();
		//this will cause: u1 stack will receive SSP, u2 stack will get SST and message.
		Thread.sleep(100);

		RemoteSubSystemImpl rss = (RemoteSubSystemImpl)sccpStack1.getSccpResource().getRemoteSsn(1);
		u1.send();
		Thread.sleep(200);
		assertEquals(1, ((SccpStackImplProxy) sccpStack1).getManagementProxy().getMgmtMessages().size());
		rss.setRemoteSsnProhibited(false);
		u1.send();
		Thread.sleep(200);
		// we do not send SSP during a second after sending
		assertEquals(1, ((SccpStackImplProxy) sccpStack1).getManagementProxy().getMgmtMessages().size());

		Thread.sleep(2000);
		rss.setRemoteSsnProhibited(false);
		u1.send();
		Thread.sleep(100);
		assertEquals(2, ((SccpStackImplProxy) sccpStack1).getManagementProxy().getMgmtMessages().size());
	}

	/**
	 * Test of configure method, of class SccpStackImpl.
	 */
	@Test
	public void ConsernedSpcTest() throws Exception {

		a1 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack1PC(), null, 8);
		a2 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack2PC(), null, 8);

		User u1 = new User(sccpStack1.getSccpProvider(), a1, a2, getSSN());
		User u2 = new User(sccpStack2.getSccpProvider(), a2, a1, getSSN());

		sccpStack1.setSstTimerDuration_Min(5000);
		sccpStack1.setSstTimerDuration_IncreaseFactor(1);

		sccpStack1.getSccpResource().addConcernedSpc(1, getStack2PC());

		Thread.sleep(100);

		assertEquals(0, ((SccpStackImplProxy) sccpStack2).getManagementProxy().getMgmtMessages().size());

		u1.register();
		Thread.sleep(100);

		assertEquals(1, ((SccpStackImplProxy) sccpStack2).getManagementProxy().getMgmtMessages().size());
		assertEquals(SccpMgmtMessageType.SSA, ((SccpStackImplProxy) sccpStack2).getManagementProxy().getMgmtMessages().get(0).getType());

		u1.deregister();
		Thread.sleep(100);

		assertEquals(2, ((SccpStackImplProxy) sccpStack2).getManagementProxy().getMgmtMessages().size());
		assertEquals(SccpMgmtMessageType.SSP, ((SccpStackImplProxy) sccpStack2).getManagementProxy().getMgmtMessages().get(1).getType());

		//Now test when the MTP3Pause's and then Resume's, SSA should be sent

		u1.register();
		Thread.sleep(100);

		assertEquals(3, ((SccpStackImplProxy) sccpStack2).getManagementProxy().getMgmtMessages().size());
		assertEquals(SccpMgmtMessageType.SSA, ((SccpStackImplProxy) sccpStack2).getManagementProxy().getMgmtMessages().get(2).getType());

		//Pause Stack2PC
		this.mtp3UserPart1.sendPauseMessageToLocalUser(getStack2PC());
		Thread.sleep(100);

		assertTrue("U1 did not receive Mtp3 Primitve, it should !", ((SccpStackImplProxy) sccpStack1).getManagementProxy().getMtp3Messages().size() == 1);
		Mtp3PrimitiveMessage rmtpPause = ((SccpStackImplProxy) sccpStack1).getManagementProxy().getMtp3Messages().get(0);
		Mtp3PrimitiveMessage emtpPause = new Mtp3PrimitiveMessage(0, Mtp3PrimitiveMessageType.MTP3_PAUSE, getStack2PC());
		assertEquals("Failed to match management message in U1", emtpPause, rmtpPause);

		//Resume Stack2PC
		this.mtp3UserPart1.sendResumeMessageToLocalUser(getStack2PC());
		Thread.sleep(100);

		assertTrue("U1 did not receive Mtp3 Primitve, it should !", ((SccpStackImplProxy) sccpStack1).getManagementProxy().getMtp3Messages().size() == 2);
		rmtpPause = ((SccpStackImplProxy) sccpStack1).getManagementProxy().getMtp3Messages().get(1);
		emtpPause = new Mtp3PrimitiveMessage(1, Mtp3PrimitiveMessageType.MTP3_RESUME, getStack2PC());
		assertEquals("Failed to match management message in U1", emtpPause, rmtpPause);

		//And stack2 should receive SSA
		assertEquals(4, ((SccpStackImplProxy) sccpStack2).getManagementProxy().getMgmtMessages().size());
		assertEquals(SccpMgmtMessageType.SSA, ((SccpStackImplProxy) sccpStack2).getManagementProxy().getMgmtMessages().get(3).getType());

	}

	protected static byte[] createPausePrimitive(int pc) throws Exception
	{
		byte[] b= new byte[]{
				0,
				(byte)(Mtp3PrimitiveMessageType.MTP3_PAUSE.getType() & 0x00FF),
				(byte)(pc >> 24 & 0xFF),
				(byte)(pc >> 16 & 0xFF),
				(byte)(pc >> 8 & 0xFF),
				(byte)(pc & 0xFF)
		};
		return b;
	}

}