package org.mobicents.protocols.asn;

import org.junit.Test;

import static org.junit.Assert.*;

public class AsnCompatibilityTest {

	private static final byte[] EXTERNAL_TCAP_MAP = new byte[] { 0x28, 0x23, 0x06, 0x07, 0x04, 0x00, 0x00, 0x01,
			0x01, 0x01, 0x01, (byte) 0xa0, 0x18, (byte) 0xa0, (byte) 0x80, (byte) 0x80, 0x09, (byte) 0x96, 0x02,
			0x24, (byte) 0x80, 0x03, 0x00, (byte) 0x80, 0x00, (byte) 0xf2, (byte) 0x81, 0x07, (byte) 0x91, 0x13,
			0x26, (byte) 0x98, (byte) 0x86, 0x03, (byte) 0xf0, 0x00, 0x00 };

	private static final byte[] EXTERNAL_ASN_PAYLOAD = new byte[] { (byte) 0xa0, (byte) 0x80, (byte) 0x80, 0x09,
			(byte) 0x96, 0x02, 0x24, (byte) 0x80, 0x03, 0x00, (byte) 0x80, 0x00, (byte) 0xf2, (byte) 0x81, 0x07,
			(byte) 0x91, 0x13, 0x26, (byte) 0x98, (byte) 0x86, 0x03, (byte) 0xf0, 0x00, 0x00 };

	@Test
	public void encodesGoldenPrimitiveValuesByteForByte() throws Exception {
		AsnOutputStream output = new AsnOutputStream();

		output.writeTag(Tag.CLASS_CONTEXT_SPECIFIC, false, 1000);
		assertArrayEquals(new byte[] { (byte) 0xBF, (byte) 0x87, (byte) 0x68 }, output.toByteArray());

		output.reset();
		output.writeBoolean(true);
		assertArrayEquals(new byte[] { 0x01, 0x01, (byte) 0xFF }, output.toByteArray());

		output.reset();
		output.writeInteger(128);
		assertArrayEquals(new byte[] { 0x02, 0x02, 0x00, (byte) 0x80 }, output.toByteArray());

		output.reset();
		output.writeReal(Double.NEGATIVE_INFINITY);
		assertArrayEquals(new byte[] { 0x09, 0x01, 0x41 }, output.toByteArray());
	}

	@Test
	public void decodesGoldenPrimitiveValues() throws Exception {
		AsnInputStream input = new AsnInputStream(new byte[] { (byte) 0xBF, (byte) 0x87, (byte) 0x68 });
		assertEquals(1000, input.readTag());
		assertEquals(Tag.CLASS_CONTEXT_SPECIFIC, input.getTagClass());
		assertFalse(input.isTagPrimitive());

		input = new AsnInputStream(new byte[] { 0x02, 0x02, 0x00, (byte) 0x80 });
		assertEquals(Tag.INTEGER, input.readTag());
		assertEquals(128, input.readInteger());

		input = new AsnInputStream(new byte[] { 0x09, 0x01, 0x41 });
		assertEquals(Tag.REAL, input.readTag());
		assertEquals(Double.NEGATIVE_INFINITY, input.readReal(), 0.0);
	}

	@Test
	public void preservesExternalTcapMapFixture() throws Exception {
		AsnInputStream input = new AsnInputStream(EXTERNAL_TCAP_MAP);
		assertEquals(External._TAG_IMPLICIT_SEQUENCE, input.readTag());

		External external = new External();
		external.decode(input);
		assertTrue(external.isOid());
		assertArrayEquals(new long[] { 0, 4, 0, 0, 1, 1, 1, 1 }, external.getOidValue());
		assertFalse(external.isInteger());
		assertTrue(external.isAsn());
		assertArrayEquals(EXTERNAL_ASN_PAYLOAD, external.getEncodeType());

		AsnOutputStream output = new AsnOutputStream();
		external.encode(output);
		assertArrayEquals(EXTERNAL_TCAP_MAP, output.toByteArray());
	}

	@Test
	public void preservesConstructedOctetStringFixture() throws Exception {
		byte[] encoded = new byte[] { 0x24, 20, 0x04, 0x08, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x04,
				0x08, (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd, (byte) 0xee,
				(byte) 0xff };
		byte[] expected = new byte[] { 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
				(byte) 0xaa, (byte) 0xbb, (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff };

		AsnInputStream input = new AsnInputStream(encoded);
		assertEquals(Tag.STRING_OCTET, input.readTag());
		assertFalse(input.isTagPrimitive());
		assertArrayEquals(expected, input.readOctetString());
		assertEquals(0, input.available());
	}
}
