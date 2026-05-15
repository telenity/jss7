package org.mobicents.protocols.ss7.tcap.asn;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.mobicents.protocols.asn.AsnInputStream;
import org.mobicents.protocols.asn.AsnOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

public class UtilsTest {

    private static final Random RND = new Random(0xCAFEBABE);

    // --------------------------------------------------------
    // encodeTransactionId
    // --------------------------------------------------------

    @Test
    public void testEncodeTransactionId_VeryBasix() {
        long tx = 0x78ee0308;

        byte[] data = Utils.encodeTransactionId(tx);

        Assert.assertEquals(4, data.length);
        Assert.assertEquals((byte) 0x78, data[0]);
        Assert.assertEquals((byte) 0xee, data[1]);
        Assert.assertEquals((byte) 0x03, data[2]);
        Assert.assertEquals((byte) 0x08, data[3]);
    }

    @Test
    public void testEncodeTransactionId_Basic() {
        long tx = 0x11223344L;

        byte[] data = Utils.encodeTransactionId(tx);

        Assert.assertEquals(4, data.length);
        Assert.assertEquals((byte) 0x11, data[0]);
        Assert.assertEquals((byte) 0x22, data[1]);
        Assert.assertEquals((byte) 0x33, data[2]);
        Assert.assertEquals((byte) 0x44, data[3]);
    }

    @Test
    public void testEncodeTransactionId_Boundaries() {
        Assert.assertEquals(
                new byte[]{0,0,0,0},
                Utils.encodeTransactionId(0)
        );

        Assert.assertEquals(
                new byte[]{(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF},
                Utils.encodeTransactionId(0xFFFFFFFFL)
        );
    }

    // --------------------------------------------------------
    // decodeTransactionId
    // --------------------------------------------------------

    @Test
    public void testDecodeTransactionId_Basic() {
        byte[] data = new byte[] {
                (byte)0x11,
                (byte)0x22,
                (byte)0x33,
                (byte)0x44
        };

        long val = Utils.decodeTransactionId(data);

        Assert.assertEquals(0x11223344L, val);
    }

    @Test
    public void testDecodeTransactionId_ShortArray() {
        byte[] data = new byte[] {
                (byte)0xAA,
                (byte)0xBB
        };

        long val = Utils.decodeTransactionId(data);

        Assert.assertEquals(0xAABBL, val);
    }

    // --------------------------------------------------------
    // Encode -> Decode Round Trip
    // --------------------------------------------------------

    @Test
    public void testEncodeDecode_RoundTrip_Random() {
        for (int i = 0; i < 10000; i++) {
            long tx = RND.nextInt() & 0xFFFFFFFFL;

            byte[] enc = Utils.encodeTransactionId(tx);
            long dec = Utils.decodeTransactionId(enc);

            Assert.assertEquals(tx, dec);
        }
    }

    // --------------------------------------------------------
    // Edge: Leading zero handling
    // --------------------------------------------------------

    @Test
    public void testDecodeTransactionId_LeadingZeros() {

        byte[] data = new byte[] {
                0x00,
                0x00,
                0x12,
                0x34
        };

        long val = Utils.decodeTransactionId(data);

        Assert.assertEquals(0x1234L, val);
    }

    // --------------------------------------------------------
    // Edge: All 0xFF bytes (unsigned interpretation)
    // --------------------------------------------------------

    @Test
    public void testDecodeTransactionId_AllFF() {

        byte[] data = new byte[] {
                (byte)0xFF,
                (byte)0xFF,
                (byte)0xFF,
                (byte)0xFF
        };

        long val = Utils.decodeTransactionId(data);

        Assert.assertEquals(0xFFFFFFFFL, val);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testDecodeTransactionId_OverlongArray() {
        Utils.decodeTransactionId(new byte[] {
                0x01, 0x02, 0x03, 0x04, 0x05,
                0x06, 0x07, 0x08, 0x09
        });
    }
}
