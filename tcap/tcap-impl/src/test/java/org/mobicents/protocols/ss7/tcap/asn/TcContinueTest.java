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

package org.mobicents.protocols.ss7.tcap.asn;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Test; import static org.junit.Assert.*;

import org.mobicents.protocols.asn.AsnInputStream;
import org.mobicents.protocols.asn.AsnOutputStream;
import org.mobicents.protocols.ss7.tcap.TCAPTestUtils;
import org.mobicents.protocols.ss7.tcap.asn.comp.Component;
import org.mobicents.protocols.ss7.tcap.asn.comp.ComponentType;
import org.mobicents.protocols.ss7.tcap.asn.comp.Invoke;
import org.mobicents.protocols.ss7.tcap.asn.comp.OperationCode;
import org.mobicents.protocols.ss7.tcap.asn.comp.OperationCodeType;
import org.mobicents.protocols.ss7.tcap.asn.comp.ReturnResultLast;
import org.mobicents.protocols.ss7.tcap.asn.comp.TCContinueMessage;

public class TcContinueTest  {
	
	@Test
	public void testBasicTCContinue() throws IOException, EncodeException, ParseException {

		
		//OrigTran ID (full)............ 145031169
		//DestTran ID (full)............ 144965633
		
		
		//no idea how to check rest...?
		
		//trace
		byte[] b = new byte[]{
		0x65,
		0x16,
		//org txid
		0x48,
		0x04,
		0x08,
		(byte) 0xA5,
		0,
		0x01,
		//dtx
		0x49,
		0x04,
		8,
		(byte) 0xA4,
		0,
		1,
		//comp portion
		0x6C,
		8,
		//invoke
		(byte) 0xA1,
		6,
		//invoke ID
		0x02,
		0x01,
		0x01,
		//op code
		0x02,
		0x01,
		0x37 };
		
		AsnInputStream ais = new AsnInputStream(b);
		int tag = ais.readTag();
		assertEquals("Expected TCInvoke", tag, TCContinueMessage._TAG);
		TCContinueMessage tcm = TcapFactory.createTCContinueMessage(ais);
		
		assertNull("Dialog portion should not be present", tcm.getDialogPortion());
//		assertEquals("Originating transaction id does not match", tcm.getOriginatingTransactionId(), 145031169L);
		assertTrue("Originating transaction id does not match", Arrays.equals(tcm.getOriginatingTransactionId(), new byte[] { 0x08, (byte) 0xA5, 0, 0x01, }));
//		assertEquals("Destination transaction id does not match", tcm.getDestinationTransactionId(), 144965633L);
		assertTrue("Destination transaction id does not match", Arrays.equals(tcm.getDestinationTransactionId(), new byte[] { 8, (byte) 0xA4, 0, 1, }));
		
		assertNotNull("Component portion should be present", tcm.getComponent());
		assertEquals("Component count is wrong", tcm.getComponent().size(), 1);
		Component c = tcm.getComponent().get(0);
		assertEquals("Wrong component type", c.getType(), ComponentType.Invoke);
		Invoke i = (Invoke) c;
		assertEquals("Wrong invoke ID", i.getInvokeId(), new Integer(1));
		assertNull("Linked ID is not null",  i.getLinkedId());
		
		assertNotNull("Operation code is null", i.getOperationCode());
		assertNull("Parameter not null", i.getParameter());
		OperationCode oc = i.getOperationCode();
		assertEquals("Wrong operation type", oc.getOperationType(), OperationCodeType.Local);
		assertEquals("Wrong operation code", oc.getLocalOperationCode(),  new Long(0x37));
		AsnOutputStream aos = new AsnOutputStream();
		tcm.encode(aos);
		byte[] encoded = aos.toByteArray();
		
		TCAPTestUtils.compareArrays(b,encoded);

	}
	
	
	@Test
	public void testBasicTCContinue_Long() throws IOException, EncodeException, ParseException {

		//trace
		byte[] b = new byte[]{
				//TCContinue
				0x65,
				//len
				60,
				//oid
				//OrigTran ID (full)............ 145031169 
				0x48,
				0x04,
				0x08,
				(byte) 0xA5,
				0,
				1,
				//dtx
				//DestTran ID (full)............ 144965633
				0x49,
				4,
				8,
				(byte) 0xA4,
				0,
				1,
				//comp portion
				0x6C,
				46,
				(byte) //invoke
				0xA1,
				44,
				//invokeId
				0x02,
				1,
				0x02,
				//op code
				0x02,
				0x01,
				42,
				//Parameter
				0x24,
				36,
				(byte) //some tag.1
				0xA0,
				17,
				(byte) //some tag.1.1
				0x80,
				2,
				0x11,
				0x11,
				(byte) //some tag.1.2
				0xA1,
				04,
				(byte) //some tag.1.3 ?
				0x82,
				2,
				0x00,
				0x00,
				(byte) //7
				//some tag.1.4
				0x82,
				1,
				12,
				(byte) //some tag.1.5
				0x83,
				2,
				0x33,
				0x33,
				(byte) //some trash here
				//tension indicator 2........ ???
				//use value.................. ???
				//some tag.2
				0xA1,
				3,
				(byte) //some tag.2.1
				0x80,
				1,
				-1,
				(byte) //some tag.3
				0xA2,
				3,
				(byte) //some tag.3.1
				0x80,
				1,
				 -1,
				 (byte) //some tag.4
				0xA3,
				5,
				(byte) //some tag.4.1
				0x82,
				3,
				(byte) // - 85 serviceKey................... 123456 // dont care about this content, lets just make len correct
				0xAB,
				(byte) 0xCD,
				(byte) 0xEF

		};
		
		AsnInputStream ais = new AsnInputStream(b);
		int tag = ais.readTag();
		assertEquals("Expected TCInvoke", tag, TCContinueMessage._TAG);
		TCContinueMessage tcm = TcapFactory.createTCContinueMessage(ais);
		
		assertNull("Dialog portion should not be present", tcm.getDialogPortion());
//		assertEquals("Originating transaction id does not match", tcm.getOriginatingTransactionId(), 145031169L);
//		assertEquals("Desination transaction id does not match", tcm.getDestinationTransactionId(), 144965633L);
		assertTrue("Originating transaction id does not match", Arrays.equals(tcm.getOriginatingTransactionId(), new byte[] { 0x08, (byte) 0xA5, 0, 0x01, }));
		assertTrue("Destination transaction id does not match", Arrays.equals(tcm.getDestinationTransactionId(), new byte[] { 8, (byte) 0xA4, 0, 1, }));
		
		assertNotNull("Component portion should be present", tcm.getComponent());
		assertEquals("Component count is wrong", tcm.getComponent().size(), 1);
		Component c = tcm.getComponent().get(0);
		assertEquals("Wrong component type", c.getType(), ComponentType.Invoke);
		Invoke i = (Invoke) c;
		assertEquals("Wrong invoke ID", i.getInvokeId(), new Integer(2));
		assertNull("Linked ID is not null",  i.getLinkedId());
		
		assertNotNull("Operation code is null", i.getOperationCode());
		assertNotNull("Parameter null", i.getParameter());
		OperationCode oc = i.getOperationCode();
		assertEquals("Wrong operation type", oc.getOperationType(), OperationCodeType.Local);
		assertEquals("Wrong operation code", oc.getLocalOperationCode(), new Long(42));
		
		AsnOutputStream aos = new AsnOutputStream();
		tcm.encode(aos);
		byte[] encoded = aos.toByteArray();
		
		TCAPTestUtils.compareArrays(b,encoded);

	}
	
	
	
	
	@Test
	public void testTCContinueMessage_No_Dialog() throws IOException, EncodeException, ParseException {

		
		
		
		
		//no idea how to check rest...?
		
		//created by hand
		byte[] b = new byte[]{
			//TCContinue
			0x65,
				71,
				//org txid
				//OrigTran ID (full)............ 145031169 
				0x48,
					0x04,
					0x08,
					(byte) 0xA5,
					0,
					0x01,
				//dtx
				//DestTran ID (full)............ 144965633
				0x49,
					4,
					8,
					(byte) 0xA4,
					0,
					1,
				//dialog portion
				//empty
				//comp portion
				0x6C,
				57,
				//invoke
				(byte) 0xA1,
					6,
					//invoke ID
					0x02,
					0x01,
					0x01,
					//op code
					0x02,
					0x01,
					0x37,
				//return result last
				(byte) 0xA2,
					47,
					//inoke id
					0x02,
					0x01,
					0x02,
					//sequence start
					0x30,
					42,
					// 	local operation 
						0x02,
						0x02,
						0x00,
						(byte) 0xFF,
					//	parameter
						0x30,
						36,
						(byte) 0xA0,//some tag.1
						17,
						(byte) 0x80,//some tag.1.1
						2,
						0x11,
						0x11,
						(byte) 0xA1,//some tag.1.2
						04,
						(byte)0x82, //some tag.1.3 ?
						2,
						0x00,
						0x00,
						(byte) 0x82,
						//some tag.1.4
						1,
						12,
						(byte)0x83, //some tag.1.5
						2,
						0x33,
						0x33,
						(byte) 0xA1,//some trash here
						//tension indicator 2........ ???
						//use value.................. ???
						//some tag.2
						3,
						(byte) 0x80,//some tag.2.1
						1,
						-1,
						(byte)0xA2, //some tag.3
						3,
						(byte) 0x80,//some tag.3.1
						1,
						-1,
						(byte) 0xA3,//some tag.4
						5,
						(byte) 0x82,//some tag.4.1
						3,
						(byte) 0xAB,// - 85 serviceKey................... 123456 // dont care about this content, lets just make len correct
						(byte) 0xCD,
						(byte) 0xEF
				};
		
		AsnInputStream ais = new AsnInputStream(b);
		int tag = ais.readTag();
		assertEquals("Expected TCContinue", tag, TCContinueMessage._TAG);
		TCContinueMessage tcm = TcapFactory.createTCContinueMessage(ais);
		
		assertNull("Dialog portion should not be present", tcm.getDialogPortion());
//		assertEquals("Destination transaction id does not match", tcm.getDestinationTransactionId(), 144965633L);
//		assertEquals("Originating transaction id does not match", tcm.getOriginatingTransactionId(), 145031169L);
		assertTrue("Originating transaction id does not match", Arrays.equals(tcm.getOriginatingTransactionId(), new byte[] { 0x08, (byte) 0xA5, 0, 0x01, }));
		assertTrue("Destination transaction id does not match", Arrays.equals(tcm.getDestinationTransactionId(), new byte[] { 8, (byte) 0xA4, 0, 1, }));
		//comp portion
		assertNotNull("Component portion should be present", tcm.getComponent());
		assertEquals("Component count is wrong", tcm.getComponent().size(), 2);
		Component c = tcm.getComponent().get(0);
		assertEquals("Wrong component type", c.getType(), ComponentType.Invoke);
		Invoke i = (Invoke) c;
		assertEquals("Wrong invoke ID", i.getInvokeId(), new Integer(1));
		assertNull("Linked ID is not null",  i.getLinkedId());
		
		c = tcm.getComponent().get(1);
		assertEquals("Wrong component type", c.getType(), ComponentType.ReturnResultLast);
		ReturnResultLast rrl = (ReturnResultLast) c;
		assertEquals("Wrong invoke ID", rrl.getInvokeId(), new Integer(2));
		assertNotNull("Operation code should not be null",  rrl.getOperationCode());
		
		OperationCode ocs = rrl.getOperationCode();
		

		assertEquals("Wrong Operation Code type", ocs.getOperationType(), OperationCodeType.Local);
		assertEquals("Wrong Operation Code", ocs.getLocalOperationCode(), new Long(0x00FF));
		
		assertNotNull("Parameter should not be null", rrl.getParameter());
		
		AsnOutputStream aos = new AsnOutputStream();
		tcm.encode(aos);
		byte[] encoded = aos.toByteArray();
		
		TCAPTestUtils.compareArrays(b,encoded);

	}

	@Test
	public void testTCContinueMessage_No_Component() throws IOException, EncodeException, ParseException {

		
		
		//created by hand
		byte[] b = new byte[]{
				//TCContinue
				0x65,
					56,
					//org txid
					//OrigTran ID (full)............ 145031169 
					0x48,
						0x04,
						0x08,
						(byte) 0xA5,
						0,
						0x01,
					//didTx
					//DstTran ID (full)............ 145031169
					0x49,
					4,
					8,
					(byte) 0xA5,
					0,
					1,
					//dialog portion
					0x6B,
						42,
						//extrnal tag
						0x28,
						40,
						//oid
							0x06,
							7,
							0,
							17,
							(byte) 134,
							5,
							1,
							1,
							1,
							(byte)160, //asn
							
								29,
								0x61,	//dialog response
									27,
									//protocol version
									(byte)0x80, //protocol version
									
										2,
										7,
									(byte) 0x80,
									(byte) 161,//acn 
										9,
										//oid
										6,
										7,
										4,
										0,
										1,
										1,
										1,
										3,
										0,
									//result
								(byte)0xA2,
										0x03,
										0x2, 
											0x1, 
											(byte) 0x0,
									//result source diagnostic
									(byte)0xA3,
										5,
								  (byte)0x0A2, //provider
											3,
											0x02,//int 2
											0x01,
									  (byte)0x2
									//no user info?
		};
		
		AsnInputStream ais = new AsnInputStream(b);
		int tag = ais.readTag();
		assertEquals("Expected TCContinue", tag, TCContinueMessage._TAG);
		TCContinueMessage tcm = TcapFactory.createTCContinueMessage(ais);
		assertNull("Component portion should not be present", tcm.getComponent());
		assertNotNull("Dialog portion should not be null", tcm.getDialogPortion());
//		assertEquals("Destination transaction id does not match", tcm.getDestinationTransactionId(), 145031169L);
//		assertEquals("Originating transaction id does not match", tcm.getOriginatingTransactionId(), 145031169L);
		assertTrue("Originating transaction id does not match", Arrays.equals(tcm.getOriginatingTransactionId(), new byte[] { 0x08, (byte) 0xA5, 0, 0x01, }));
		assertTrue("Destination transaction id does not match", Arrays.equals(tcm.getDestinationTransactionId(), new byte[] { 8, (byte) 0xA5, 0, 1, }));
		
		assertFalse("Dialog should not be Uni",  tcm.getDialogPortion().isUnidirectional());
		DialogAPDU _dapd = tcm.getDialogPortion().getDialogAPDU();
		assertEquals("Wrong dialog APDU type!", _dapd.getType(), DialogAPDUType.Response);
		
		DialogResponseAPDU dapd = (DialogResponseAPDU) _dapd;
		
		//check nulls first
		assertNull("UserInformation should not be present", dapd.getUserInformation());
		
		//not nulls
		assertNotNull("Result should not be null", dapd.getResult());
		Result r = dapd.getResult();
		assertEquals("Wrong result", r.getResultType(), ResultType.Accepted );
		
		
		assertNotNull("Result Source Diagnostic should not be null", dapd.getResultSourceDiagnostic());
		
		ResultSourceDiagnostic rsd = dapd.getResultSourceDiagnostic();
		assertNull("User diagnostic should not be present", rsd.getDialogServiceUserType());
		assertEquals("Wrong provider diagnostic type", rsd.getDialogServiceProviderType(), DialogServiceProviderType.NoCommonDialogPortion);
		
		AsnOutputStream aos = new AsnOutputStream();
		tcm.encode(aos);
		byte[] encoded = aos.toByteArray();
		
		TCAPTestUtils.compareArrays(b,encoded);

	}
	
	@Test
	public void testTCContinueMessage_No_Nothing() throws IOException, EncodeException, ParseException {

		
		
		
		
		//no idea how to check rest...?
		
		//created by hand
		byte[] b = new byte[]{
				//TCContinue
				0x65,
					12,
					//org txid
					//OrigTran ID (full)............ 145031169 
					0x48,
						0x04,
						0x08,
						(byte) 0xA5,
						0,
						0x01,
					//didTx
					//DEstTran ID (full)............ 145031169
					0x49,
					4,
					8,
					(byte) 0xA5,
					0,
					1,
				
		
		};
		
		AsnInputStream ais = new AsnInputStream(b);
		int tag = ais.readTag();
		assertEquals("Expected TCContinue", tag, TCContinueMessage._TAG);
		TCContinueMessage tcm = TcapFactory.createTCContinueMessage(ais);
		
		assertNull("Dialog portion should be null", tcm.getDialogPortion());
		assertNull("Component portion should not be present", tcm.getComponent());
//		assertEquals("Destination transaction id does not match", tcm.getDestinationTransactionId(), 145031169L);
//		assertEquals("Originating transaction id does not match", tcm.getOriginatingTransactionId(), 145031169L);
		assertTrue("Originating transaction id does not match", Arrays.equals(tcm.getOriginatingTransactionId(), new byte[] { 0x08, (byte) 0xA5, 0, 0x01, }));
		assertTrue("Destination transaction id does not match", Arrays.equals(tcm.getDestinationTransactionId(), new byte[] { 8, (byte) 0xA5, 0, 1, }));
	
		AsnOutputStream aos = new AsnOutputStream();
		tcm.encode(aos);
		byte[] encoded = aos.toByteArray();
		
		TCAPTestUtils.compareArrays(b,encoded);

	}
	
	@Test
	public void testTCContinueMessage_All() throws IOException, EncodeException, ParseException {

		
		
		
		
		//no idea how to check rest...?
		
		//created by hand
		byte[] b = new byte[]{
				//TCContinue
				0x65,
					114,
					//org txid
					//OrigTran ID (full)............ 145031169 
					0x48,
						0x04,
						0x08,
						(byte) 0xA5,
						0,
						0x01,
					//dtx
					//DestTran ID (full)............ 144965633
					0x49,
						4,
						8,
						(byte) 0xA4,
						0,
						1,
					//dialog portion
						0x6B,
						42,
						//extrnal tag
						0x28,
						40,
						//oid
							0x06,
							7,
							0,
							17,
							(byte) 134,
							5,
							1,
							1,
							1,
							(byte)160, //asn
							
								29,
								0x61,	//dialog response
									27,
									//protocol version
									(byte)0x80, //protocol version
									
										2,
										7,
									(byte) 0x80,
									(byte) 161,//acn 
										9,
										//oid
										6,
										7,
										4,
										0,
										1,
										1,
										1,
										3,
										0,
									//result
								(byte)0xA2,
										0x03,
										0x2, 
											0x1, 
											(byte) 0x01,
									//result source diagnostic
									(byte)0xA3,
										5,
								  (byte)0x0A2, //provider
											3,
											0x02,//int 2
											0x01,
									  (byte)0x00,
									//no user info?
					//comp portion
					0x6C,
					56,
					//invoke
					(byte) 0xA1,
						6,
						//invoke ID
						0x02,
						0x01,
						0x01,
						//op code
						0x02,
						0x01,
						0x37,
					//return result last
					(byte) 0xA2,
						46,
						//inoke id
						0x02,
						0x01,
						0x02,
						//sequence start
						0x30,
						41,
						//	local operation
							0x02,
							0x01,
							0x01,
						//	parameter
							0x30,
							36,
							(byte) 0xA0,//some tag.1
							17,
							(byte) 0x80,//some tag.1.1
							2,
							0x11,
							0x11,
							(byte) 0xA1,//some tag.1.2
							04,
							(byte)0x82, //some tag.1.3 ?
							2,
							0x00,
							0x00,
							(byte) 0x82,
							//some tag.1.4
							1,
							12,
							(byte)0x83, //some tag.1.5
							2,
							0x33,
							0x33,
							(byte) 0xA1,//some trash here
							//tension indicator 2........ ???
							//use value.................. ???
							//some tag.2
							3,
							(byte) 0x80,//some tag.2.1
							1,
							-1,
							(byte)0xA2, //some tag.3
							3,
							(byte) 0x80,//some tag.3.1
							1,
							-1,
							(byte) 0xA3,//some tag.4
							5,
							(byte) 0x82,//some tag.4.1
							3,
							(byte) 0xAB,// - 85 serviceKey................... 123456 // dont care about this content, lets just make len correct
							(byte) 0xCD,
							(byte) 0xEF
					};
		
		AsnInputStream ais = new AsnInputStream(b);
		int tag = ais.readTag();
		assertEquals("Expected TCContinue", tag, TCContinueMessage._TAG);
		TCContinueMessage tcm = TcapFactory.createTCContinueMessage(ais);
		
		//universal
//		assertEquals("Destination transaction id does not match", tcm.getDestinationTransactionId(), 144965633L);
//		assertEquals("Originating transaction id does not match", tcm.getOriginatingTransactionId(), 145031169L);
		assertTrue("Originating transaction id does not match", Arrays.equals(tcm.getOriginatingTransactionId(), new byte[] { 0x08, (byte) 0xA5, 0, 0x01, }));
		assertTrue("Destination transaction id does not match", Arrays.equals(tcm.getDestinationTransactionId(), new byte[] { 8, (byte) 0xA4, 0, 1, }));
		
		//dialog portion
		assertNotNull("Dialog portion should not be null", tcm.getDialogPortion());
//		assertEquals("Destination transaction id does not match", tcm.getDestinationTransactionId(), 144965633L);
		
		assertFalse("Dialog should not be Uni",  tcm.getDialogPortion().isUnidirectional());
		DialogAPDU _dapd = tcm.getDialogPortion().getDialogAPDU();
		assertEquals("Wrong dialog APDU type!", _dapd.getType(), DialogAPDUType.Response);
		
		DialogResponseAPDU dapd = (DialogResponseAPDU) _dapd;
		
		//check nulls first
		assertNull("UserInformation should not be present", dapd.getUserInformation());
		
		//not nulls
		assertNotNull("Result should not be null", dapd.getResult());
		Result r = dapd.getResult();
		assertEquals("Wrong result", r.getResultType(), ResultType.RejectedPermanent );
		
		
		assertNotNull("Result Source Diagnostic should not be null", dapd.getResultSourceDiagnostic());
		
		ResultSourceDiagnostic rsd = dapd.getResultSourceDiagnostic();
		assertNull("User diagnostic should not be present", rsd.getDialogServiceUserType());
		assertEquals("Wrong provider diagnostic type", rsd.getDialogServiceProviderType(), DialogServiceProviderType.Null);
		
		//comp portion
		assertNotNull("Component portion should be present", tcm.getComponent());
		assertEquals("Component count is wrong", tcm.getComponent().size(), 2);
		Component c = tcm.getComponent().get(0);
		assertEquals("Wrong component type", c.getType(), ComponentType.Invoke);
		Invoke i = (Invoke) c;
		assertEquals("Wrong invoke ID", i.getInvokeId(), new Integer(1));
		assertNull("Linked ID is not null",  i.getLinkedId());
		
		c = tcm.getComponent().get(1);
		assertEquals("Wrong component type", c.getType(), ComponentType.ReturnResultLast);
		ReturnResultLast rrl = (ReturnResultLast) c;
		assertEquals("Wrong invoke ID", rrl.getInvokeId(), new Integer(2));
		assertNotNull("Operation code should not be null",  rrl.getOperationCode());

		
		OperationCode ocs = rrl.getOperationCode();
		
		assertEquals("Wrong Operation Code type", ocs.getOperationType(), OperationCodeType.Local);
		assertEquals("Wrong Operation Code", ocs.getLocalOperationCode(), new Long(1));

		
		assertNotNull("Parameter should not be null", rrl.getParameter());
		
		
		AsnOutputStream aos = new AsnOutputStream();
		tcm.encode(aos);
		byte[] encoded = aos.toByteArray();
		
		TCAPTestUtils.compareArrays(b,encoded);

	}
	
	
}
