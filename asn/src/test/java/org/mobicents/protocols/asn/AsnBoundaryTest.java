package org.mobicents.protocols.asn;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class AsnBoundaryTest {

	@Test
	public void encodesLengthBoundaries() throws Exception {
		AsnOutputStream output = new AsnOutputStream();

		output.writeLength(0);
		output.writeLength(127);
		output.writeLength(128);
		output.writeLength(255);
		output.writeLength(256);
		output.writeLength(65535);
		output.writeLength(65536);
		output.writeLength(Tag.Indefinite_Length);

		assertArrayEquals(new byte[] { 0x00, 0x7f, (byte) 0x81, (byte) 0x80, (byte) 0x81, (byte) 0xff,
				(byte) 0x82, 0x01, 0x00, (byte) 0x82, (byte) 0xff, (byte) 0xff, (byte) 0x83, 0x01, 0x00, 0x00,
				(byte) 0x80 }, output.toByteArray());
	}

	@Test
	public void encodesKnownLengthOctetStringDirectly() throws Exception {
		byte[] value = new byte[128];
		for (int i = 0; i < value.length; i++) {
			value[i] = (byte) i;
		}

		AsnOutputStream output = new AsnOutputStream();
		output.writeOctetString(Tag.CLASS_APPLICATION, 3, value);

		byte[] encoded = output.toByteArray();
		assertEquals((byte) 0x43, encoded[0]);
		assertEquals((byte) 0x81, encoded[1]);
		assertEquals((byte) 0x80, encoded[2]);
		assertEquals(131, encoded.length);
		for (int i = 0; i < value.length; i++) {
			assertEquals(value[i], encoded[i + 3]);
		}
	}

	@Test
	public void decodesLengthBoundaries() throws Exception {
		AsnInputStream input = new AsnInputStream(new byte[] { 0x00, 0x7f, (byte) 0x81, (byte) 0x80, (byte) 0x81,
				(byte) 0xff, (byte) 0x82, 0x01, 0x00, (byte) 0x82, (byte) 0xff, (byte) 0xff, (byte) 0x83, 0x01,
				0x00, 0x00, (byte) 0x80 });

		assertEquals(0, input.readLength());
		assertEquals(127, input.readLength());
		assertEquals(128, input.readLength());
		assertEquals(255, input.readLength());
		assertEquals(256, input.readLength());
		assertEquals(65535, input.readLength());
		assertEquals(65536, input.readLength());
		assertEquals(Tag.Indefinite_Length, input.readLength());
		assertEquals(0, input.available());
	}

	@Test
	public void createsZeroCopySequenceStreamView() throws Exception {
		byte[] encoded = new byte[] { Tag.SEQUENCE, 0x03, 0x01, 0x01, (byte) 0xff };
		AsnInputStream input = new AsnInputStream(encoded);

		assertEquals(Tag.SEQUENCE, input.readTag());
		AsnInputStream sequence = input.readSequenceStream();

		encoded[4] = 0x00;
		assertEquals(Tag.BOOLEAN, sequence.readTag());
		assertEquals(false, sequence.readBoolean());
		assertEquals(0, input.available());
	}

	@Test
	public void skipsNestedIndefiniteLengthContent() throws Exception {
		byte[] encoded = new byte[] { Tag.SEQUENCE, (byte) 0x80, Tag.SEQUENCE, (byte) 0x80, Tag.STRING_OCTET, 0x03,
				0x01, 0x02, 0x03, 0x00, 0x00, 0x00, 0x00 };
		AsnInputStream input = new AsnInputStream(encoded);

		assertEquals(Tag.SEQUENCE, input.readTag());
		input.advanceElement();
		assertEquals(0, input.available());
	}

	@Test
	@SuppressWarnings("deprecation")
	public void readsLargePrimitiveOctetStringIntoOutputStream() throws Exception {
		byte[] expected = new byte[1500];
		for (int i = 0; i < expected.length; i++) {
			expected[i] = (byte) i;
		}

		AsnOutputStream output = new AsnOutputStream();
		output.writeOctetString(expected);

		AsnInputStream input = new AsnInputStream(output.toByteArray());
		assertEquals(Tag.STRING_OCTET, input.readTag());
		ByteArrayOutputStream decoded = new ByteArrayOutputStream(expected.length);
		input.readOctetString(decoded);

		assertArrayEquals(expected, decoded.toByteArray());
		assertEquals(0, input.available());
	}

	@Test(expected = AsnException.class)
	public void primitiveStringFailsAtEndOfStream() throws Exception {
		AsnInputStream input = new AsnInputStream(new byte[] { Tag.STRING_IA5, 0x03, 'o', 'k' });
		assertEquals(Tag.STRING_IA5, input.readTag());
		input.readIA5String();
	}

	@Test(expected = EOFException.class)
	public void integerFailsAtEndOfStream() throws Exception {
		AsnInputStream input = new AsnInputStream(new byte[] { Tag.INTEGER, 0x02, 0x01 });
		assertEquals(Tag.INTEGER, input.readTag());
		input.readInteger();
	}

	@Test(expected = EOFException.class)
	public void readByteFailsAtEndOfStream() throws Exception {
		new AsnInputStream(new byte[0]).read();
	}

	@Test(expected = EOFException.class)
	public void highTagFailsAtEndOfStream() throws Exception {
		new AsnInputStream(new byte[] { 0x1f }).readTag();
	}

	@Test(expected = EOFException.class)
	public void longLengthFailsAtEndOfStream() throws Exception {
		new AsnInputStream(new byte[] { (byte) 0x82, 0x01 }).readLength();
	}

	@Test(expected = IOException.class)
	public void rejectsInvalidPosition() throws Exception {
		new AsnInputStream(new byte[1]).position(2);
	}

	@Test(expected = AsnException.class)
	public void primitiveBooleanWithConstructedTag() throws Exception {
		// constructed bit set: tag=0x21 (UNIVERSAL, constructed, BOOLEAN)
		AsnInputStream input = new AsnInputStream(new byte[] { 0x21, 0x01, 0x00 });
		input.readTag();
		input.readBoolean();
	}

	@Test(expected = AsnException.class)
	public void primitiveNullWithWrongLength() throws Exception {
		// correct tag + length 1 instead of 0
		AsnInputStream input = new AsnInputStream(new byte[] { Tag.NULL, 0x01, 0x00 });
		input.readTag();
		input.readNull();
	}

	@Test(expected = AsnException.class)
	public void primitiveIntegerWithZeroLength() throws Exception {
		AsnInputStream input = new AsnInputStream(new byte[] { Tag.INTEGER, 0x00 });
		input.readTag();
		input.readInteger();
	}

	@Test(expected = AsnException.class)
	public void primitiveOctetStringWithIndefiniteLength() throws Exception {
		// primitive octet-string tag + indefinite length
		AsnInputStream input = new AsnInputStream(new byte[] { Tag.STRING_OCTET, (byte) 0x80, 0x00, 0x00 });
		input.readTag();
		input.readOctetString();
	}

	@Test(expected = AsnException.class)
	public void negativeTagThrowsAsnException() throws Exception {
		AsnOutputStream output = new AsnOutputStream();
		output.writeTag(Tag.CLASS_UNIVERSAL, true, -1);
	}

	@Test(expected = AsnException.class)
	public void realUnknownInfoBits() throws Exception {
		// infoBits=0x40: bit6=1, bit7=0 → not base10 (00) nor binary (bit7=1)
		AsnInputStream input = new AsnInputStream(new byte[] { Tag.REAL, 0x02, 0x40, 0x00 });
		input.readTag();
		input.readReal();
	}

	@Test
	public void writeIntegerDataReturnValue() throws Exception {
		AsnOutputStream os = new AsnOutputStream();
		assertEquals(1, os.writeIntegerData(0));
		os.reset();
		assertEquals(1, os.writeIntegerData(127));
		os.reset();
		assertEquals(1, os.writeIntegerData(-128));
		os.reset();
		assertEquals(2, os.writeIntegerData(128));
		os.reset();
		assertEquals(2, os.writeIntegerData(-32768));
		os.reset();
		assertEquals(8, os.writeIntegerData(Long.MAX_VALUE));
		os.reset();
		assertEquals(8, os.writeIntegerData(Long.MIN_VALUE));
	}

	@Test
	public void writeBooleanDataReturnValue() throws Exception {
		assertEquals(1, new AsnOutputStream().writeBooleanData(true));
	}

	@Test
	public void writeNullDataReturnValue() throws Exception {
		assertEquals(0, new AsnOutputStream().writeNullData());
	}

	@Test
	public void positionTracking() throws Exception {
		AsnInputStream input = new AsnInputStream(new byte[] { 0x01, 0x02, 0x03, 0x04 });
		assertEquals(0, input.position());
		input.read();
		assertEquals(1, input.position());
		input.read();
		assertEquals(2, input.position());
		input.position(0);
		assertEquals(0, input.position());
	}
}
