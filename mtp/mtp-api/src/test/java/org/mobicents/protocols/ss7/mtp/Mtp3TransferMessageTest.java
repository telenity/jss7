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

package org.mobicents.protocols.ss7.mtp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

/**
 * @author sergey vetyutnev
 * 
 */
public class Mtp3TransferMessageTest {

	private byte[] getMsg() {
		return new byte[] { (byte) 0x83, (byte) 232, 3, (byte) 244, (byte) 161, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
	}

	private byte[] getData() {
		return new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 };
	}

	// si = 3 (SCCP)
	// ni = 2
	// mp = 0
	// sio = (si + (ni << 6) + (mt << 4))
	// dpc = 1000
	// opc = 2000
	// sls = 10

	@Test
	public void testDecode() throws Exception {
		Mtp3TransferPrimitiveFactory factory = new Mtp3TransferPrimitiveFactory(RoutingLabelFormat.ITU);
		Mtp3TransferPrimitive msg = factory.createMtp3TransferPrimitive(getMsg());

		assertEquals(3, msg.getSi());
		assertEquals(2, msg.getNi());
		assertEquals(0, msg.getMp());
		assertEquals(1000, msg.getDpc());
		assertEquals(2000, msg.getOpc());
		assertEquals(10, msg.getSls());
		assertTrue(Arrays.equals(msg.getData(), this.getData()));

	}

	@Test
	public void testEncode() throws Exception {
		Mtp3TransferPrimitiveFactory factory = new Mtp3TransferPrimitiveFactory(RoutingLabelFormat.ITU);
		Mtp3TransferPrimitive msg = factory.createMtp3TransferPrimitive(3, 2, 0, 2000, 1000, 10, this.getData());

		byte[] res = msg.encodeMtp3();

		assertTrue(Arrays.equals(res, this.getMsg()));

	}
}
