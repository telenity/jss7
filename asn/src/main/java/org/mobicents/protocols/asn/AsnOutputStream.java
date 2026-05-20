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

package org.mobicents.protocols.asn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * @author amit bhayani
 * @author baranowb
 * @author sergey vetyutnev
 */
public class AsnOutputStream extends OutputStream {

	private static final byte _BOOLEAN_POSITIVE = (byte) 0xFF;
	private static final byte _BOOLEAN_NEGATIVE = 0x00;

	private byte[] buffer;
	private int pos;
	private int length;

	public AsnOutputStream() {
		length = 256;
		buffer = new byte[length];
	}

	/** Returns the written data as a byte array */
	public byte[] toByteArray() {
		if (pos == length)
			return buffer;
		byte[] res = new byte[pos];
		System.arraycopy(buffer, 0, res, 0, pos);
		return res;
	}

	/** Bytes written so far */
	public int size() {
		return pos;
	}

	/** Clear stream content for reuse */
	public void reset() {
		pos = 0;
	}

	private void checkIncreaseArray(int addCount) {
		if (pos + addCount > length) {
			int newLength = length * 2;
			if (newLength < pos + addCount)
				newLength = pos + addCount + length;
			byte[] newBuf = new byte[newLength];
			System.arraycopy(buffer, 0, newBuf, 0, buffer.length);
			buffer = newBuf;
			length = newLength;
		}
	}

	@Override
	public void write(int b) {
		checkIncreaseArray(1);
		buffer[pos++] = (byte) b;
	}

	@Override
	public void write(byte[] b, int off, int len) {
		checkIncreaseArray(len);
		System.arraycopy(b, off, buffer, pos, len);
		pos += len;
	}

	@Override
	public void write(byte[] b) {
		write(b, 0, b.length);
	}

	/**
	 * Write tag field. For tags <= 30: one octet. For tags >= 31: first octet
	 * with bits 5..1 = 0x1F, then subsequent octets with bit 7 = "more" flag.
	 */
	public void writeTag(int tagClass, boolean primitive, int tag) throws AsnException {
		if (tag < 0)
			throw new AsnException("Tag must not be negative");
		if (tag <= 30) {
			int toEncode = (tagClass & 0x03) << 6;
			toEncode |= (primitive ? 0 : 1) << 5;
			toEncode |= tag & 0x1F;
			write(toEncode);
		} else {
			int toEncode = (tagClass & 0x03) << 6;
			toEncode |= (primitive ? 0 : 1) << 5;
			toEncode |= 0x1F;
			write(toEncode);
			byte[] buf = new byte[8];
			int pos = buf.length;
			while (true) {
				int dd;
				if (tag <= 0x7F) {
					dd = tag;
					if (pos != buf.length)
						dd |= 0x80;
					buf[--pos] = (byte) dd;
					break;
				} else {
					dd = tag & 0x7F;
					tag >>= 7;
					if (pos != buf.length)
						dd |= 0x80;
					buf[--pos] = (byte) dd;
				}
			}
			write(buf, pos, buf.length - pos);
		}
	}

	/**
	 * Write length field. Short form for v <= 127, long form (0x80|N + N
	 * octets) for v > 127. Use Tag.Indefinite_Length to write 0x80.
	 */
	public void writeLength(int v) throws IOException {
		if (v == Tag.Indefinite_Length) {
			write(0x80);
		} else if (v > 0x7F) {
			int count = (Integer.SIZE - Integer.numberOfLeadingZeros(v) + 7) / 8;
			checkIncreaseArray(count + 1);
			buffer[pos] = (byte) (0x80 | count);
			for (int i = count - 1; i >= 0; i--) {
				buffer[pos + 1 + (count - 1 - i)] = (byte) (v >> (8 * i));
			}
			pos += count + 1;
		} else {
			write(v);
		}
	}

	/**
	 * Begin definite-length content. After writing the content, caller must
	 * call FinalizeContent(lenPos) with the returned position.
	 */
	public int StartContentDefiniteLength() {
		int lenPos = pos;
		write(0);
		return lenPos;
	}

	/**
	 * Begin indefinite-length content (writes 0x80). After writing content
	 * including inner EOC, caller must call FinalizeContent(returned value).
	 */
	public int StartContentIndefiniteLength() {
		write(0x80);
		return Tag.Indefinite_Length;
	}

	/**
	 * Finalize content: patches the length field. For indefinite form
	 * (lenPos == Indefinite_Length) writes 0x0000 EOC.
	 * For definite form, backfills the length at lenPos.
	 */
	public void FinalizeContent(int lenPos) {
		if (lenPos == Tag.Indefinite_Length) {
			write(0);
			write(0);
		} else {
			int length = pos - lenPos - 1;
			if (length <= 0x7F) {
				buffer[lenPos] = (byte) length;
			} else {
				int count = (Integer.SIZE - Integer.numberOfLeadingZeros(length) + 7) / 8;
				checkIncreaseArray(count);
				System.arraycopy(buffer, lenPos + 1, buffer, lenPos + 1 + count, length);
				pos += count;
				buffer[lenPos] = (byte) (0x80 | count);
				for (int i = count - 1; i >= 0; i--) {
					buffer[lenPos + 1 + (count - 1 - i)] = (byte) (length >> (8 * i));
				}
			}
		}
	}

	public void writeSequence(byte[] data) throws IOException, AsnException {
		writeSequence(Tag.CLASS_UNIVERSAL, Tag.SEQUENCE, data);
	}

	public void writeSequence(int tagClass, int tag, byte[] data) throws IOException, AsnException {
		writeTag(tagClass, false, tag);
		writeLength(data.length);
		write(data);
	}

	public int writeSequenceData(byte[] data) throws IOException, AsnException {
		write(data);
		return data.length;
	}

	public void writeBoolean(boolean value) throws IOException, AsnException {
		writeBoolean(Tag.CLASS_UNIVERSAL, Tag.BOOLEAN, value);
	}

	public void writeBoolean(int tagClass, int tag, boolean value) throws IOException, AsnException {
		writeTag(tagClass, true, tag);
		writeLength(0x01);
		writeBooleanData(value);
	}

	public int writeBooleanData(boolean value) throws IOException {
		write(value ? _BOOLEAN_POSITIVE : _BOOLEAN_NEGATIVE);
		return 1;
	}

	public void writeInteger(long value) throws IOException, AsnException {
		writeInteger(Tag.CLASS_UNIVERSAL, Tag.INTEGER, value);
	}

	public void writeInteger(int tagClass, int tag, long v) throws IOException, AsnException {
		writeTag(tagClass, true, tag);
		int lenPos = StartContentDefiniteLength();
		writeIntegerData(v);
		FinalizeContent(lenPos);
	}

	public int writeIntegerData(long v) throws IOException {
		boolean wasPositive = v > 0;
		long v1 = v;
		if (!wasPositive)
			v1 = -v;
		int count;
		if ((v1 & 0xFF00000000000000L) != 0)
			count = 8;
		else if ((v1 & 0xFF000000000000L) != 0)
			count = 7;
		else if ((v1 & 0xFF0000000000L) != 0)
			count = 6;
		else if ((v1 & 0xFF00000000L) != 0)
			count = 5;
		else if ((v1 & 0xFF000000L) != 0)
			count = 4;
		else if ((v1 & 0xFF0000L) != 0)
			count = 3;
		else if ((v1 & 0xFF00L) != 0)
			count = 2;
		else
			count = 1;
		byte[] dataToWrite = new byte[]{
			(byte)(v >> 56), (byte)(v >> 48), (byte)(v >> 40), (byte)(v >> 32),
			(byte)(v >> 24), (byte)(v >> 16), (byte)(v >> 8), (byte)v
		};
		int extraCount = 0;
		if (wasPositive && (dataToWrite[8 - count] & 0x80) != 0) {
			write(0);
			extraCount = 1;
		}
		write(dataToWrite, 8 - count, count);
		return count + extraCount;
	}

	public void writeReal(String d, int NR) throws IOException, AsnException {
		writeReal(Tag.CLASS_UNIVERSAL, Tag.REAL, d, NR);
	}

	public void writeReal(double d) throws IOException, AsnException {
		writeReal(Tag.CLASS_UNIVERSAL, Tag.REAL, d);
	}

	public void writeReal(int tagClass, int tag, String d, int NR) throws IOException, AsnException {
		writeTag(tagClass, true, tag);
		int lenPos = pos;
		write(0);
		buffer[lenPos] = (byte) writeRealData(d, NR);
	}

	public void writeReal(int tagClass, int tag, double d) throws IOException, AsnException {
		writeTag(tagClass, true, tag);
		int lenPos = StartContentDefiniteLength();
		writeRealData(d);
		FinalizeContent(lenPos);
	}

	/**
	 * Write REAL in ISO 6093 NR1/NR2/NR3 character form.
	 * Verifies the string is parseable as double before writing.
	 */
	public int writeRealData(String d, int NR) throws AsnException, NumberFormatException, IOException {
		Double.parseDouble(d);
		byte[] encoded = d.getBytes(StandardCharsets.US_ASCII);
		// FIXME: add check on length exceeding simple boundary!
		if (encoded.length + 1 > 127)
			throw new AsnException("Not supported yet, is it even in specs?");
		if (NR > 3 || NR < 1)
			throw new AsnException("NR is out of range: <0,3>");
		write(NR);
		write(encoded);
		return encoded.length + 1;
	}

	/** Write REAL in binary encoding (IEEE 754 double → BER binary real) */
	public int writeRealData(double d) throws AsnException, IOException {
		if (d == 0)
			return 0;
		if (d == Double.POSITIVE_INFINITY) {
			write(0x40);
			return 1;
		}
		if (d == Double.NEGATIVE_INFINITY) {
			write(0x41);
			return 1;
		}
		long bits = Double.doubleToLongBits(d);
		write(new byte[]{
			(byte)(((int)(bits >> 57) & 0x40) | 0x81),
			(byte)((int)(bits >> 60) & 0x07),
			(byte)(bits >> 52),
			(byte)((bits >> 48) & 0x0F),
			(byte)(bits >> 40),
			(byte)(bits >> 32),
			(byte)(bits >> 24),
			(byte)(bits >> 16),
			(byte)(bits >> 8),
			(byte)bits
		});
		return 10;
	}

	public void writeBitString(BitSetStrictLength bitString) throws AsnException, IOException {
		writeBitString(Tag.CLASS_UNIVERSAL, Tag.STRING_BIT, bitString);
	}

	public void writeBitString(int tagClass, int tag, BitSetStrictLength bitString) throws AsnException, IOException {
		writeTag(tagClass, true, tag);
		int lenPos = StartContentDefiniteLength();
		writeBitStringData(bitString);
		FinalizeContent(lenPos);
	}

	public int writeBitStringData(BitSetStrictLength bitString) throws AsnException, IOException {
		int bitNumber = bitString.getStrictLength();
		int octetCount = bitNumber / 8;
		int rest = bitNumber % 8;
		if (rest != 0)
			octetCount++;
		write(rest == 0 ? 0 : 8 - rest);
		for (int i = 0; i < octetCount; i++)
			write(_getByte(i * 8, bitString));
		return octetCount;
	}

	/** Extract up to 8 bits from BitSet starting at startIndex into one byte */
	private static byte _getByte(int startIndex, BitSetStrictLength set) throws AsnException {
		byte data = 0;
		for (int i = 7; i >= 0 && startIndex < set.length(); i--, startIndex++) {
			if (set.get(startIndex))
				data |= (1 << i);
		}
		return data;
	}

	public void writeOctetString(byte[] value) throws IOException, AsnException {
		writeOctetString(Tag.CLASS_UNIVERSAL, Tag.STRING_OCTET, value);
	}

	public void writeOctetString(int tagClass, int tag, byte[] value) throws IOException, AsnException {
		writeTag(tagClass, true, tag);
		int lenPos = StartContentDefiniteLength();
		writeOctetStringData(value);
		FinalizeContent(lenPos);
	}

	public int writeOctetStringData(byte[] value) {
		// TODO: we now implements the only primitive encoding here.
		// This is enough for ss7. For constructed encoding we should add another method
		write(value);
		return value.length;
	}

	@Deprecated
	public void writeStringOctet(int tagClass, int tag, InputStream io) throws AsnException, IOException {
		writeTag(tagClass, true, tag);
		writeLength(io.available());
		byte[] data = new byte[io.available()];
		io.read(data);
		write(data);
	}

	@Deprecated
	public void writeStringOctet(InputStream io) throws AsnException, IOException {
		writeStringOctet(Tag.CLASS_UNIVERSAL, Tag.STRING_OCTET, io);
	}

	@Deprecated
	public void writeStringOctetData(InputStream io) throws AsnException, IOException {
		if (io.available() <= 127) {
			byte[] data = new byte[io.available()];
			io.read(data);
			write(data);
		} else {
			throw new AsnException("writeStringOctetData does not support octet strings more than 126 bytes length");
		}
	}

	public void writeNull() throws IOException, AsnException {
		writeNull(Tag.CLASS_UNIVERSAL, Tag.NULL);
	}

	public void writeNull(int tagClass, int tag) throws IOException, AsnException {
		writeTag(tagClass, true, tag);
		writeLength(0x00);
	}

	@Deprecated
	public void writeNULLData() throws IOException {
	}

	public int writeNullData() {
		return 0;
	}

	public void writeObjectIdentifier(long[] oid) throws IOException, AsnException {
		writeObjectIdentifier(Tag.CLASS_UNIVERSAL, Tag.OBJECT_IDENTIFIER, oid);
	}

	public void writeObjectIdentifier(int tagClass, int tag, long[] oid) throws IOException, AsnException {
		writeTag(tagClass, true, tag);
		int lenPos = StartContentDefiniteLength();
		writeObjectIdentifierData(oid);
		FinalizeContent(lenPos);
	}

	public int writeObjectIdentifierData(long[] oidLeafs) throws IOException {
		if (oidLeafs.length < 2)
			return 0;
		int len = 1;
		int i;
		for (i = 2; i < oidLeafs.length; ++i)
			len += getOIDLeafLength(oidLeafs[i]);
		i = (int) (oidLeafs[0] * 40 + oidLeafs[1]);
		write(0x00FF & i);
		for (i = 2; i < oidLeafs.length; ++i) {
			long v = oidLeafs[i];
			len = getOIDLeafLength(v);
			for (int j = len - 1; j > 0; --j) {
				long m = 0x0080 | (0x007F & (v >> (j * 7)));
				write((int) m);
			}
			write((int) (0x007F & v));
		}
		return len;
	}

	private int getOIDLeafLength(long leaf) {
		if (leaf < 0)
			return 10;
		long l = 1;
		int i;
		for (i = 1; i < 9; ++i) {
			l <<= 7;
			if (leaf < l)
				break;
		}
		return i;
	}

	public void writeStringUTF8(String data) throws AsnException, IOException {
		writeStringUTF8(Tag.CLASS_UNIVERSAL, Tag.STRING_UTF8, data);
	}

	public void writeStringUTF8(int tagClass, int tag, String data) throws IOException, AsnException {
		writeTag(tagClass, true, tag);
		int lenPos = StartContentDefiniteLength();
		writeStringUTF8Data(data);
		FinalizeContent(lenPos);
	}

	public void writeStringUTF8Data(String data) throws IOException, AsnException {
		write(data.getBytes(StandardCharsets.UTF_8));
	}

	public void writeStringIA5(String data) throws AsnException, IOException {
		writeStringIA5(Tag.CLASS_UNIVERSAL, Tag.STRING_IA5, data);
	}

	public void writeStringIA5(int tagClass, int tag, String data) throws IOException, AsnException {
		writeTag(tagClass, true, tag);
		int lenPos = StartContentDefiniteLength();
		writeStringIA5Data(data);
		FinalizeContent(lenPos);
	}

	public void writeStringIA5Data(String data) throws IOException, AsnException {
		write(data.getBytes(StandardCharsets.US_ASCII));
	}

	public void writeStringGraphic(String data) throws AsnException, IOException {
		writeStringIA5(Tag.CLASS_UNIVERSAL, Tag.STRING_GRAPHIC, data);
	}

	public void writeStringGraphic(int tagClass, int tag, String data) throws IOException, AsnException {
		writeTag(tagClass, true, tag);
		int lenPos = StartContentDefiniteLength();
		writeStringIA5Data(data);
		FinalizeContent(lenPos);
	}

	public void writeStringGraphicData(String data) throws IOException, AsnException {
		write(data.getBytes(StandardCharsets.US_ASCII));
	}

	@Deprecated
	public void writeStringBinary(BitSet bitString) throws AsnException, IOException {
		int length = bitString.length();
		BitSetStrictLength bs = new BitSetStrictLength(length);
		for (int i1 = 0; i1 < length; i1++)
			bs.set(i1, bitString.get(i1));
		writeBitString(bs);
	}

	@Deprecated
	public void writeStringBinary(int tagClass, int tag, BitSet bitString) throws AsnException, IOException {
		int length = bitString.length();
		BitSetStrictLength bs = new BitSetStrictLength(length);
		for (int i1 = 0; i1 < length; i1++)
			bs.set(i1, bitString.get(i1));
		writeBitString(tagClass, tag, bs);
	}

	@Deprecated
	public void writeNULL() throws IOException, AsnException {
		writeNull();
	}

	@Deprecated
	public void writeStringBinaryData(BitSet bitString) throws AsnException, IOException {
		int length = bitString.length();
		BitSetStrictLength bs = new BitSetStrictLength(length);
		for (int i1 = 0; i1 < length; i1++)
			bs.set(i1, bitString.get(i1));
		writeBitStringData(bs);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Size=").append(pos).append("\n");
		sb.append(arrayToString(toByteArray()));
		return sb.toString();
	}

	protected static String arrayToString(byte[] bf) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		boolean first = true;
		for (byte b : bf) {
			if (!first)
				sb.append(", ");
			sb.append(b & 0xFF);
			first = false;
		}
		sb.append("]");
		return sb.toString();
	}
}
