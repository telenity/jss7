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

import org.mobicents.protocols.asn.AsnInputStream;
import org.mobicents.protocols.asn.AsnOutputStream;
import org.mobicents.protocols.asn.Tag;
import org.mobicents.protocols.ss7.tcap.asn.comp.Component;
import org.mobicents.protocols.ss7.tcap.asn.comp.ComponentType;
import org.mobicents.protocols.ss7.tcap.asn.comp.ErrorCode;
import org.mobicents.protocols.ss7.tcap.asn.comp.ErrorCodeType;
import org.mobicents.protocols.ss7.tcap.asn.comp.Parameter;
import org.mobicents.protocols.ss7.tcap.asn.comp.ReturnError;

import org.junit.Test; import static org.junit.Assert.*;

/**
 * 
 * @author amit bhayani
 * @author sergey vetyutnev
 *
 */
public class ReturnErrorTest  {
	
	private byte[] getDataWithoutParameter() {
		return new byte[] { 
				//0xA3 - Return ReturnError TAG
				(byte)0xA3,
                //0x06 - Len
				0x06,
                        //0x02 - InvokeID Tag
						0x02,
                        //0x01 - Len
						0x01,
                            //0x05
							0x05,
                        //0x02 - ReturnError Code Tag
						0x02,
                        //0x01 - Len
						0x01,
							//0x0F
							0x0F
		};
	}	
	
	private byte[] getDataWithParameter() {
		return new byte[] { 
				//0xA3 - Return ReturnError TAG
				(byte)0xA3,
                //0x06 - Len
				0x19,
                        //0x02 - InvokeID Tag
						0x02,
                        //0x01 - Len
						0x01,
                            //0x05
							0x05,
                        //0x02 - ReturnError Code Tag
						0x02,
                        //0x01 - Len
						0x01,
							//0x0F
							0x0F,
							//parameter
						(byte) 0xA0,// some tag.1
							17, 
							(byte) 0x80,// some tag.1.1
							2, 
								0x11, 0x11, (byte) 
							0xA1,// some tag.1.2
							04, 
								(byte) 0x82, // some tag.1.3 ?
							2, 0x00, 0x00, (byte) 0x82, 1,// some tag.1.4
							12, (byte) 0x83, // some tag.1.5
							2, 0x33, 0x33, // some trash here
						
		};
	}	

	
	private byte[] getDataLongErrorCode() {
		return new byte[] { -93, 8, 2, 1, -1, 6, 3, 40, 22, 33 };
	}
		
	private byte[] getParameterData() {
		return new byte[] { -128, 2, 17, 17, -95, 4, -126, 2, 0, 0, -126, 1, 12, -125, 2, 51, 51 };
	}
	
	@Test
	public void testDecode() throws IOException, ParseException {
	
		byte[] b = getDataWithoutParameter();
		AsnInputStream asnIs = new AsnInputStream(b);
		Component comp = TcapFactory.createComponent(asnIs);

		assertEquals("Wrong component Type", comp.getType(), ComponentType.ReturnError);
		ReturnError re = (ReturnError) comp;
		assertEquals("Wrong invoke ID", re.getInvokeId(), new Integer(5));
		assertNotNull("No error code.", re.getErrorCode());
		ErrorCode ec = re.getErrorCode();
		assertEquals("Wrong error code type.", ec.getErrorType(), ErrorCodeType.Local);
		long lec = ec.getLocalErrorCode();
		assertEquals("wrong data content.", 15, lec);
		assertNull("No error code.", re.getParameter());		

		
		b = getDataWithParameter();
		asnIs = new AsnInputStream(b);
		comp = TcapFactory.createComponent(asnIs);

		assertEquals("Wrong component Type", comp.getType(), ComponentType.ReturnError);
		re = (ReturnError) comp;
		assertEquals("Wrong invoke ID", re.getInvokeId(), new Integer(5));
		assertNotNull("Parameter should not be null", re.getErrorCode());
		ec = re.getErrorCode();
		assertEquals("Wrong error code type.", ec.getErrorType(), ErrorCodeType.Local);
		lec = ec.getLocalErrorCode();
		assertEquals("wrong data content.", 15, lec);
		
		assertNotNull(re.getParameter());
		Parameter p = re.getParameter();
		assertEquals("Wrong parameter tag.", p.getTag(), 0x00); // 0x00 - since A is for tag class etc.
		assertEquals("Wrong parameter tagClass.", p.getTagClass(), Tag.CLASS_CONTEXT_SPECIFIC);
		assertNotNull("Parameters array is null.", p.getParameters());
		assertEquals("Wrong number of parameters in array.", p.getParameters().length, 4);
		assertTrue("No error code.", Arrays.equals(this.getParameterData(), p.getData()));

		
		b = getDataLongErrorCode();
		asnIs = new AsnInputStream(b);
		comp = TcapFactory.createComponent(asnIs);

		assertEquals("Wrong component Type", comp.getType(), ComponentType.ReturnError);
		re = (ReturnError) comp;
		assertEquals("Wrong invoke ID", re.getInvokeId(), new Integer(-1));
		assertNotNull(re.getErrorCode());
		ec = re.getErrorCode();
		assertEquals("Wrong error code type.", ec.getErrorType(), ErrorCodeType.Global);
		long[] gec = ec.getGlobalErrorCode();
		assertTrue("wrong data content.", Arrays.equals(new long[] { 1, 0, 22, 33 }, gec));
		assertNull(re.getParameter());
	}
	
	
	@Test
	public void testEncode() throws IOException, EncodeException {

		byte[] expected = this.getDataWithoutParameter();
		ReturnError re = TcapFactory.createComponentReturnError();
		re.setInvokeId(5);
		ErrorCode ec = TcapFactory.createErrorCode();
		ec.setLocalErrorCode(15L);
		re.setErrorCode(ec);

		AsnOutputStream asnos = new AsnOutputStream();
		re.encode(asnos);
		byte[] encodedData = asnos.toByteArray();
		assertTrue(Arrays.equals(expected, encodedData));

		
		expected = this.getDataWithParameter();
		re = TcapFactory.createComponentReturnError();
		re.setInvokeId(5);
		ec = TcapFactory.createErrorCode();
		ec.setLocalErrorCode(15L);
		re.setErrorCode(ec);
		Parameter pm = TcapFactory.createParameter();
		pm.setTagClass(Tag.CLASS_CONTEXT_SPECIFIC);
		pm.setTag(0);
		pm.setPrimitive(false);
		pm.setData(getParameterData());
		re.setParameter(pm);

		asnos = new AsnOutputStream();
		re.encode(asnos);
		encodedData = asnos.toByteArray();
		assertTrue(Arrays.equals(expected, encodedData));

		
		expected = this.getDataLongErrorCode();
		re = TcapFactory.createComponentReturnError();
		re.setInvokeId(-1);
		ec = TcapFactory.createErrorCode();
		ec.setGlobalErrorCode(new long[] { 1, 0, 22, 33 });
		re.setErrorCode(ec);

		asnos = new AsnOutputStream();
		re.encode(asnos);
		encodedData = asnos.toByteArray();
		assertTrue(Arrays.equals(expected, encodedData));
	}
}
