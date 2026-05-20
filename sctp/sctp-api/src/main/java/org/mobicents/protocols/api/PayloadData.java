/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2012, Telestax Inc and individual contributors
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

package org.mobicents.protocols.api;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;

/**
 * The actual pay load data received or to be sent from/to underlying socket
 * 
 * @author amit bhayani
 * 
 */
public class PayloadData {
	private final int dataLength;
	private final ByteBuf byteBuf;
	private final boolean complete;
	private final boolean unordered;
	private final int payloadProtocolId;
	private final int streamNumber;

    /**
     * @param dataLength
     *            Length of byte[] data
     * @param byteBuf
     *            the payload data
     * @param complete
     *            if this data represents complete protocol data
     * @param unordered
     *            set to true if we don't care for order
     * @param payloadProtocolId
     *            protocol ID of the data carried
     * @param streamNumber
     *            the SCTP stream number
     */
    public PayloadData(int dataLength, ByteBuf byteBuf, boolean complete, boolean unordered, int payloadProtocolId, int streamNumber) {
        this.dataLength = dataLength;
        this.byteBuf = byteBuf;
        this.complete = complete;
        this.unordered = unordered;
        this.payloadProtocolId = payloadProtocolId;
        this.streamNumber = streamNumber;
    }

    /**
     * @param dataLength
     *            Length of byte[] data
     * @param data
     *            the payload data
     * @param complete
     *            if this data represents complete protocol data
     * @param unordered
     *            set to true if we don't care for order
     * @param payloadProtocolId
     *            protocol ID of the data carried
     * @param streamNumber
     *            the SCTP stream number
     */
    public PayloadData(int dataLength, byte[] data, boolean complete, boolean unordered, int payloadProtocolId, int streamNumber) {
        this.dataLength = dataLength;
        this.byteBuf = Unpooled.wrappedBuffer(data);
        this.complete = complete;
        this.unordered = unordered;
        this.payloadProtocolId = payloadProtocolId;
        this.streamNumber = streamNumber;
    }

	/**
	 * @return the dataLength
	 */
	public int getDataLength() {
		return dataLength;
	}

    /**
     * @return the byteBuf
     */
    public ByteBuf getByteBuf() {
        return byteBuf;
    }

    /**
     * @return the data
     */
    public byte[] getData() {
        byte[] array = new byte[byteBuf.readableBytes()];
        byteBuf.getBytes(0, array);
        ReferenceCountUtil.release(byteBuf);
        return array;
    }

	/**
	 * @return the complete
	 */
	public boolean isComplete() {
		return complete;
	}

	/**
	 * @return the unordered
	 */
	public boolean isUnordered() {
		return unordered;
	}

	/**
	 * @return the payloadProtocolId
	 */
	public int getPayloadProtocolId() {
		return payloadProtocolId;
	}

	/**
	 * <p>
	 * This is SCTP Stream sequence identifier.
	 * </p>
	 * <p>
	 * While sending PayloadData to SCTP Association, this value should be set
	 * by SCTP user. If value greater than or equal to maxOutboundStreams or
	 * lesser than 0 is used, packet will be dropped and error message will be
	 * logged
	 * </p>
	 * </p> While PayloadData is received from underlying SCTP socket, this
	 * value indicates stream identifier on which data was received. Its
	 * guaranteed that this value will be greater than 0 and less than
	 * maxInboundStreams
	 * <p>
	 * 
	 * @return the streamNumber
	 */
	public int getStreamNumber() {
		return streamNumber;
	}

	/* Hex chars */
	private static final byte[] HEX_CHAR = new byte[]
			{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

	/*
	 * Helper function that dumps an array of bytes in the hexadecimal format.
	 */
	public static final String dumpBytes(byte[] buffer) {
		if (buffer == null) {
			return "";
		}

		StringBuilder sb = new StringBuilder ();

		for (int i = 0; i < buffer.length; i++) {
			sb.append ("0x").append ((char) (HEX_CHAR[(buffer[i] & 0x00F0) >> 4])).append (
					(char) (HEX_CHAR[buffer[i] & 0x000F])).append (' ');
		}

		return sb.toString ();
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("PayloadData [dataLength=").append(dataLength).append(", complete=").append(complete).append(", unordered=")
				.append(unordered).append(", payloadProtocolId=").append(payloadProtocolId).append(", streamNumber=")
				.append(streamNumber).append("]");
		return sb.toString();
	}

}
