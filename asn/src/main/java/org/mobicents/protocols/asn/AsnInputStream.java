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

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * @author amit bhayani
 * @author baranowb
 * @author sergey vetyutnev
 */
public class AsnInputStream extends InputStream {

    private static final int DATA_BUCKET_SIZE = 1024;

    private byte[] buffer;
    private int start;
    private int length;
    private int pos;
    private int tagClass;
    private int pCBit;
    private int tag;

    public AsnInputStream(byte[] buf) {
        buffer = buf;
        length = buf.length;
    }

    public AsnInputStream(byte[] buf, int tagClass, boolean isPrimitive, int tag) {
        buffer = buf;
        length = buf.length;
        this.tagClass = tagClass;
        pCBit = isPrimitive ? 0 : 1;
        this.tag = tag;
    }

    /**
     * Creates a sub-stream view over [start, start+length) in parent buffer
     */
    protected AsnInputStream(AsnInputStream buf, int start, int length) throws IOException {
        buffer = buf.buffer;
        this.start = buf.start + start;
        this.length = length;
        if (start < 0 || start > buf.length || this.start < 0 || this.start > buffer.length || length < 0
                || this.start + length > buffer.length)
            throw new IOException("Bad start or length values when creating AsnInputStream");
        tagClass = buf.tagClass;
        pCBit = buf.pCBit;
        tag = buf.tag;
    }

    @Deprecated
    public AsnInputStream(InputStream in) {
        try {
            int av = in.available();
            byte[] buf = new byte[av];
            in.read(buf);
            buffer = buf;
            length = buf.length;
        } catch (IOException e) {
            e.printStackTrace();
            buffer = new byte[0];
        }
    }

    /**
     * Current read position
     */
    public int position() {
        return pos;
    }

    /**
     * Set read position; throws IOException if out of bounds
     */
    public void position(int newPosition) throws IOException {
        if (newPosition < 0 || newPosition > length)
            throw new IOException("Bad newPosition value when setting the new position in the AsnInputStream");
        pos = newPosition;
    }

    /**
     * Bytes remaining in the stream
     */
    @Override
    public int available() {
        return length - pos;
    }

    /**
     * Advance current position by byteCount bytes
     */
    public void advance(int byteCount) throws IOException {
        position(pos + byteCount);
    }

    @Override
    public long skip(long n) throws IOException {
        if (n < 0)
            n = 0;
        int newPosition = pos + (int) n;
        if (newPosition < 0 || newPosition > length)
            newPosition = length;
        long skipCnt = newPosition - pos;
        pos = newPosition;
        return skipCnt;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    /**
     * Read a single byte
     */
    @Override
    public int read() throws IOException {
        if (pos >= length)
            throw new EOFException("AsnInputStream has reached the end");
        return buffer[start + pos++];
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len > b.length)
            len = b.length;
        int cnt = available();
        if (cnt > len)
            cnt = len;
        if (b == null || off < 0 || len < 0 || off + len > b.length)
            throw new EOFException("Target byte array is null or bad off or len values");
        System.arraycopy(buffer, start + pos, b, off, cnt);
        pos += cnt;
        return cnt;
    }

    @Override
    public int read(byte[] b) throws IOException {
        if (b == null)
            throw new EOFException("Target byte array is null");
        return read(b, 0, b.length);
    }

    /**
     * Reads the tag field. Tag class and primitive/constructed mark can be
     * retrieved via getTagClass() and isTagPrimitive().
     * For multi-byte tags (tag >= 31), first octet has bits 5..1 set to 1,
     * subsequent octets use bit 7 as "more" flag and bits 6..0 as tag data.
     */
    public int readTag() throws IOException {
        byte b = (byte) read();
        tagClass = (b & Tag.CLASS_MASK) >> 6;
        pCBit = (b & Tag.PC_MASK) >> 5;
        tag = b & Tag.TAG_MASK;
        if (tag == Tag.TAG_MASK) {
            byte temp;
            tag = 0;
            do {
                temp = (byte) read();
                tag = (tag << 7) | (0x7F & temp);
            } while (0 != (0x80 & temp));
        }
        return tag;
    }

    public int getTagClass() {
        return tagClass;
    }

    public int getTag() {
        return tag;
    }

    public boolean isTagPrimitive() {
        return pCBit == Tag.PC_PRIMITIVITE;
    }

    /**
     * Reads the length field. Returns Tag.Indefinite_Length for indefinite
     * form (0x80). Short form if bit 8 = 0; long form if bit 8 = 1 with
     * remaining 7 bits encoding the count of subsequent length octets.
     */
    public int readLength() throws IOException {
        int length;
        byte b = (byte) read();
        if ((b & 0x80) == 0)
            return b;
        b = (byte) (b & 0x7F);
        if (b == 0)
            return Tag.Indefinite_Length;
        length = 0;
        byte temp;
        for (int i = 0; i < b; i++) {
            temp = (byte) read();
            length = (length << 8) | (0x00FF & temp);
        }
        return length;
    }

    /**
     * Read sequence after tag: length + content, returns sub-stream
     */
    public AsnInputStream readSequenceStream() throws AsnException, IOException {
        return readSequenceStreamData(readLength());
    }

    /**
     * Read sequence after tag: length + content, returns raw bytes
     */
    public byte[] readSequence() throws AsnException, IOException {
        return readSequenceData(readLength());
    }

    /**
     * After tag and length are read, returns sub-stream for the content
     */
    public AsnInputStream readSequenceStreamData(int length) throws AsnException, IOException {
        if (length == Tag.Indefinite_Length)
            return readSequenceIndefinite();
        int startPos = pos;
        advance(length);
        return new AsnInputStream(this, startPos, length);
    }

    /**
     * After tag and length are read, returns content as byte[]
     */
    public byte[] readSequenceData(int length) throws AsnException, IOException {
        AsnInputStream ais = readSequenceStreamData(length);
        byte[] res = new byte[ais.length];
        System.arraycopy(ais.buffer, ais.start + ais.pos, res, 0, ais.length);
        return res;
    }

    /**
     * Skip indefinite-length content and return sub-stream (excluding EOC)
     */
    public AsnInputStream readSequenceIndefinite() throws AsnException, IOException {
        int startPos = pos;
        advanceIndefiniteLength();
        return new AsnInputStream(this, startPos, pos - startPos - 2);
    }

    /**
     * Read indefinite-length content as bytes (excluding the 0x0000 EOC)
     */
    public byte[] readIndefinite() throws AsnException, IOException {
        int startPos = pos;
        advanceIndefiniteLength();
        byte[] res = new byte[pos - startPos - 2];
        System.arraycopy(buffer, start + startPos, res, 0, pos - startPos - 2);
        return res;
    }

    /**
     * Skip all TLV entries until EOC (0x0000)
     */
    private void advanceIndefiniteLength() throws AsnException, IOException {
        while (available() > 0) {
            int tag = readTag();
            if (tag == 0 && tagClass == 0) {
                if (read() == 0)
                    return;
                throw new AsnException("End-of-contents tag must have the zero length");
            }
            int length = readLength();
            if (length == Tag.Indefinite_Length)
                advanceIndefiniteLength();
            else
                advance(length);
        }
    }

    /**
     * Skip length + content of the current element
     */
    public void advanceElement() throws IOException, AsnException {
        advanceElementData(readLength());
    }

    /**
     * Skip content of the current element (length already read)
     */
    public void advanceElementData(int length) throws IOException, AsnException {
        if (length == Tag.Indefinite_Length)
            advanceIndefiniteLength();
        else
            advance(length);
    }

    public boolean readBoolean() throws AsnException, IOException {
        return readBooleanData(readLength());
    }

    public boolean readBooleanData(int length) throws AsnException, IOException {
        if (pCBit != 0 || length != 1)
            throw new AsnException("Failed when parsing the Boolean field: this field must be primitive and the length must be equal 1");
        return read() != 0;
    }

    public long readInteger() throws AsnException, IOException {
        return readIntegerData(readLength());
    }

    public long readIntegerData(int length) throws AsnException, IOException {
        long value;
        byte temp;
        if (pCBit != 0 || length == 0 || length == Tag.Indefinite_Length)
            throw new AsnException("Failed when parsing the Integer field: this field must be primitive and have the length more than zero");
        temp = (byte) read();
        value = temp;
        for (int i = 0; i < length - 1; i++) {
            temp = (byte) read();
            value = (value << 8) | (0x00FF & temp);
        }
        return value;
    }

    public double readReal() throws AsnException, IOException {
        return readRealData(readLength());
    }

    public double readRealData(int length) throws AsnException, IOException {
        if (pCBit != 0 || length == Tag.Indefinite_Length)
            throw new AsnException("Failed when parsing the Real field: this field must be primitive");
        if (length == 0)
            return 0.0;
        if (length == 1) {
            int b = read() & 0xFF;
            if (b == 0x40)
                return Double.POSITIVE_INFINITY;
            if (b == 0x41)
                return Double.NEGATIVE_INFINITY;
            throw new AsnException("Failed when parsing the Real field: Real length indicates positive/negative infinity, but value is wrong: " + Integer.toBinaryString(b));
        }
        int infoBits = read();
        length--;
        if ((infoBits & 0xC0) == 0) {
            // FIXME: add check on boundary of simple length
            // ISO 6093 NR1/NR2/NR3 format — ASCII character string
            String nrRep = new String(buffer, start + pos, length, StandardCharsets.US_ASCII);
            return Double.parseDouble(nrRep);
        } else if ((infoBits & 0x80) == 0x80) {
            // Binary encoding: sign + base(2|8|16) + scale + exponent + mantissa
            int tmp;
            int signBit = (infoBits & BERStatics.REAL_BB_SIGN_MASK) << 1;
            long e = 0;
            int s = (infoBits & BERStatics.REAL_BB_SCALE_MASK) >> 2;
            tmp = infoBits & BERStatics.REAL_BB_EE_MASK;
            if (tmp == 0x0) {
                e = read() & 0xFF;
                length--;
            } else if (tmp == 0x01) {
                e = (read() & 0xFF) << 8;
                length--;
                e |= read() & 0xFF;
                length--;
                if (e > 0x7FF)
                    throw new AsnException("Exponent part has too many bits set, allowed are 11, present: " + Long.toBinaryString(e));
                e &= 0x7FF;
            } else {
                throw new AsnException("Exponent part has too many bits set, allowed are 11, but stream indicates 3 or more octets");
            }
            if (length > 7)
                throw new AsnException("Length exceeds JAVA double mantissa size");
            long n = 0;
            while (length > 0) {
                --length;
                long readV = (((long) read() << 32) >>> 32) & 0xFF;
                readV = readV << (length * 8);
                n |= readV;
            }
            if ((n & 0x0FFFFFFF) > 4503599627370495L)
                throw new AsnException("Overflow on mantissa");
            int shift = (int) Math.pow(2, s) - 1;
            n = n << shift;
            int base = (infoBits & BERStatics.REAL_BB_BASE_MASK) >> 4;
            if (base == 0x01)
                e = e * 3;
            else if (base == 0x10)
                e = e * 4;
            if (e > 0x7FF)
                throw new AsnException("Exponent part has too many bits set, allowed are 11, present: " + Long.toBinaryString(e));
            byte[] doubleRep = new byte[8];
            doubleRep[0] = (byte) signBit;
            doubleRep[0] |= ((e >> 4) & 0xFF);
            doubleRep[1] = (byte) ((e & 0x0F) << 4);
            doubleRep[7] = (byte) n;
            doubleRep[6] = (byte) (n >> 8);
            doubleRep[5] = (byte) (n >> 16);
            doubleRep[4] = (byte) (n >> 24);
            doubleRep[3] = (byte) (n >> 32);
            doubleRep[2] = (byte) (n >> 40);
            doubleRep[1] |= (byte) ((n >> 48) & 0x0F);
            return ByteBuffer.wrap(doubleRep).getDouble();
        } else {
            throw new AsnException("Failed when parsing the Real field: Unknown infoBits: " + infoBits);
        }
    }

    public BitSetStrictLength readBitString() throws AsnException, IOException {
        return readBitStringData(readLength());
    }

    public BitSetStrictLength readBitStringData(int length) throws AsnException, IOException {
        BitSetStrictLength bitSet = new BitSetStrictLength(0);
        bitSet.setStrictLength(_readBitString(bitSet, length, 0));
        return bitSet;
    }

    @Deprecated
    public void readBitString(BitSet bitSet) throws AsnException, IOException {
        readBitStringData(bitSet, readLength());
    }

    @Deprecated
    public void readBitStringData(BitSet bitSet, int length) throws AsnException, IOException {
        _readBitString(bitSet, length, 0);
    }

    @Deprecated
    public void readBitStringData(BitSet bitSet, int length, boolean isTagPrimitive) throws AsnException, IOException {
        pCBit = isTagPrimitive ? 0 : 1;
        _readBitString(bitSet, length, 0);
    }

    private int _readBitString(BitSet bitSet, int length, int counter) throws AsnException, IOException {
        if (pCBit == 0) {
            // TODO We are assuming that there is always pad, even if it is 00.
            // This may not be true for some Constructed BitString where padding
            // is only applied to last TLV. In which case this algo is incorrect
            int pad = read();
            for (int count = 1; count < (length - 1); count++) {
                byte dataByte = (byte) read();
                for (int bits = 0; bits < 8; bits++) {
                    if (0 != (dataByte & (0x80 >> bits)))
                        bitSet.set(counter);
                    ++counter;
                }
            }
            byte lastByte = (byte) read();
            for (int bits = 0; bits < (8 - pad); bits++) {
                if (0 != (lastByte & (0x80 >> bits)))
                    bitSet.set(counter);
                ++counter;
            }
            return counter;
        } else {
            // Constructed: iterate inner TLVs (must be UNIVERSAL STRING_BIT)
            if (length == Tag.Indefinite_Length) {
                while (true) {
                    int tag = readTag();
                    if (tag == 0) {
                        length = read();
                        if (length == 0)
                            break;
                        throw new AsnException("Error while decoding the bit-string: End-of-contents tag must have the zero length");
                    }
                    if (tag != Tag.STRING_BIT || tagClass != Tag.CLASS_UNIVERSAL)
                        throw new AsnException("Error while decoding the bit-string: subsequent bit string tag must be CLASS_UNIVERSAL - STRING_BIT");
                    counter = _readBitString(bitSet, readLength(), counter);
                }
            } else {
                int startPos = pos;
                while (true) {
                    if (pos > startPos + length)
                        throw new AsnException("Error while decoding the bit-string: constructed bit-string content do not fit its length");
                    if (pos == startPos + length)
                        break;
                    int tag = readTag();
                    if (tag != Tag.STRING_BIT || tagClass != Tag.CLASS_UNIVERSAL)
                        throw new AsnException("Error while decoding the bit-string: subsequent bit string tag must be CLASS_UNIVERSAL - STRING_BIT");
                    int length2 = readLength();
                    if (pos + length2 > startPos + length)
                        throw new AsnException("Error while decoding the bit-string: subsequent bit string is inconsistent");
                    counter = _readBitString(bitSet, length2, counter);
                }
            }
            return counter;
        }
    }

    public byte[] readOctetString() throws AsnException, IOException {
        return readOctetStringData(readLength());
    }

    @Deprecated
    public void readOctetString(OutputStream outputStream) throws AsnException, IOException {
        readOctetStringData(outputStream, readLength());
    }

    public byte[] readOctetStringData(int length) throws AsnException, IOException {
        if (pCBit == 0) {
            if (length == Tag.Indefinite_Length)
                throw new AsnException("Error while decoding the octet-string: primitive with Indefinite_Length");
            byte[] buf = new byte[length];
            int cnt = read(buf);
            if (cnt != length)
                throw new AsnException("Error while decoding the octet-string: not enough data for the octet string");
            return buf;
        } else {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            _readOctetString(outputStream, length);
            return outputStream.toByteArray();
        }
    }

    @Deprecated
    public void readOctetStringData(OutputStream outputStream, int length) throws AsnException, IOException {
        _readOctetString(outputStream, length);
    }

    @Deprecated
    public void readOctetStringData(OutputStream outputStream, int length, boolean isTagPrimitive) throws AsnException, IOException {
        pCBit = isTagPrimitive ? 0 : 1;
        _readOctetString(outputStream, length);
    }

    private void _readOctetString(OutputStream outputStream, int length) throws AsnException, IOException {
        if (pCBit == 0) {
            fillOutputStream(outputStream, length);
        } else {
            if (length == Tag.Indefinite_Length) {
                while (true) {
                    int tag = readTag();
                    if (tag == 0) {
                        length = read();
                        if (length == 0)
                            break;
                        throw new AsnException("Error while decoding the octet-string: End-of-contents tag must have the zero length");
                    }
                    if (tag != Tag.STRING_OCTET || tagClass != Tag.CLASS_UNIVERSAL)
                        throw new AsnException("Error while decoding the octet-string: subsequent octet string tag must be CLASS_UNIVERSAL - STRING_OCTET");
                    _readOctetString(outputStream, readLength());
                }
            } else {
                int startPos = pos;
                while (true) {
                    if (pos == startPos + length)
                        break;
                    int tag = readTag();
                    if (tag != Tag.STRING_OCTET || tagClass != Tag.CLASS_UNIVERSAL)
                        throw new AsnException("Error while decoding the octet-string: subsequent octet string tag must be CLASS_UNIVERSAL - STRING_OCTET");
                    int length2 = readLength();
                    if (pos + length2 > startPos + length)
                        throw new AsnException("Error while decoding the octet-string: subsequent octet string is inconsistent");
                    _readOctetString(outputStream, length2);
                }
            }
        }
    }

    private void fillOutputStream(OutputStream stream, int length) throws AsnException, IOException {
        byte[] dataBucket = new byte[DATA_BUCKET_SIZE];
        while (length != 0) {
            int cnt = Math.min(length, DATA_BUCKET_SIZE);
            int readCount = read(dataBucket, 0, cnt);
            if (readCount < cnt)
                throw new AsnException("input stream has reached the end");
            stream.write(dataBucket, 0, readCount);
            length -= readCount;
        }
    }

    public void readNull() throws AsnException, IOException {
        readNullData(readLength());
    }

    public void readNullData(int length) throws AsnException, IOException {
        if (pCBit != 0 || length != 0)
            throw new AsnException("Failed when parsing the NULL field: this field must be primitive and the length must be equal 0");
    }

    public long[] readObjectIdentifier() throws AsnException, IOException {
        return readObjectIdentifierData(readLength());
    }

    public long[] readObjectIdentifierData(int length) throws AsnException, IOException {
        if (pCBit != 0 || length == Tag.Indefinite_Length)
            throw new AsnException("Failed when parsing the ObjectIdentifier field: this field must be primitive and the length must be defined");
        byte[] data = new byte[length];
        read(data);
        length = 2;
        for (int i = 1; i < data.length; ++i) {
            if (data[i] >= 0)
                ++length;
        }
        long[] oids = new long[length];
        int b = 0x00FF & data[0];
        oids[0] = b / 40;
        if (oids[0] == 0 || oids[0] == 1)
            oids[1] = b % 40;
        else {
            oids[0] = 2;
            oids[1] = b - 80;
        }
        int v = 0;
        length = 2;
        for (int i = 1; i < data.length; ++i) {
            byte b1 = data[i];
            if ((b1 & 0x80) != 0x0) {
                v = (v << 7) | ((b1 & 0x7F));
            } else {
                v = (v << 7) | (b1 & 0x7F);
                oids[length++] = v;
                v = 0;
            }
        }
        return length == oids.length ? oids : java.util.Arrays.copyOf(oids, length);
    }

    public String readIA5String() throws AsnException, IOException {
        return readString(StandardCharsets.US_ASCII, Tag.STRING_IA5, readLength());
    }

    public String readIA5StringData(int length) throws AsnException, IOException {
        return readString(StandardCharsets.US_ASCII, Tag.STRING_IA5, length);
    }

    public String readUTF8String() throws AsnException, IOException {
        return readString(StandardCharsets.UTF_8, Tag.STRING_UTF8, readLength());
    }

    public String readUTF8StringData(int length) throws AsnException, IOException {
        return readString(StandardCharsets.UTF_8, Tag.STRING_UTF8, length);
    }

    public String readGraphicString() throws AsnException, IOException {
        return readString(StandardCharsets.US_ASCII, Tag.STRING_GRAPHIC, readLength());
    }

    public String readGraphicStringData(int length) throws AsnException, IOException {
        return readString(StandardCharsets.US_ASCII, Tag.STRING_GRAPHIC, length);
    }

    private String readString(java.nio.charset.Charset charset, int tagValue, int length) throws IOException, AsnException {
        if (pCBit == 0) {
            byte[] buf = new byte[length];
            int readCnt = read(buf);
            if (readCnt < length)
                throw new AsnException("Error decoding string field: not enough data in the stream");
            return new String(buf, charset);
        } else {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            readConstructedString(bos, tagValue, length);
            return new String(bos.toByteArray(), charset);
        }
    }

    /**
     * Recurse through constructed string inner TLVs
     */
    private void readConstructedString(ByteArrayOutputStream bos, int parentTag, int length) throws AsnException, IOException {
        AsnInputStream ais = readSequenceStreamData(length);
        while (ais.available() > 0) {
            int localTag = ais.readTag();
            if (parentTag != localTag)
                throw new AsnException("Error decoding string field: Parent tag=" + parentTag + ", does not match member tag=" + localTag);
            int localLength = ais.readLength();
            if (ais.pCBit == 0) {
                byte[] buf = new byte[localLength];
                int readCnt = ais.read(buf);
                if (readCnt < localLength)
                    throw new AsnException("Error decoding string field: not enough data in the stream");
                bos.write(buf);
            } else {
                ais.readConstructedString(bos, parentTag, localLength);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Size=").append(length).append(", Pos=").append(pos).append(", Tag=").append(tag)
                .append(", TagClass=").append(tagClass).append(", pCBit=").append(pCBit).append("\n");
        byte[] bf = new byte[length];
        System.arraycopy(buffer, start, bf, 0, length);
        sb.append(AsnOutputStream.arrayToString(bf));
        return sb.toString();
    }
}
