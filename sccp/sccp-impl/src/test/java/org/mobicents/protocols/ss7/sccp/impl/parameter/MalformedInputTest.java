package org.mobicents.protocols.ss7.sccp.impl.parameter;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;

/**
 * Malformed SCCP parameters received from the network must fail as IOException (a normal decode error), never as a
 * runtime exception that escapes the SCCP layer with a full stack trace per packet.
 */
public class MalformedInputTest {

    @Test(expected = IOException.class)
    public void unknownGlobalTitleIndicator() throws Exception {
        SccpAddressCodec.decode(new byte[]{0x14});                    // GTI 5, route on GT
    }

    @Test(expected = IOException.class)
    public void emptyAddress() throws Exception {
        SccpAddressCodec.decode(new byte[0]);
    }

    @Test(expected = IOException.class)
    public void unknownEncodingSchemeGti3() throws Exception {
        SccpAddressCodec.decode(new byte[]{0x0C, 0x00, 0x13});        // GTI 3, tt 0, np 1 / es 3
    }

    @Test(expected = IOException.class)
    public void unknownEncodingSchemeGti4() throws Exception {
        SccpAddressCodec.decode(new byte[]{0x10, 0x00, 0x13, 0x04});  // GTI 4, tt 0, np 1 / es 3, nai 4
    }

    @Test
    public void oddFlagWithoutDigitsDecodesToEmptyDigits() throws Exception {
        SccpAddress address = SccpAddressCodec.decode(new byte[]{0x04, (byte) 0x84});  // GTI 1, odd, nai 4, no digits
        assertEquals("", address.getGlobalTitle().getDigits());
    }

    @Test(expected = IOException.class)
    public void shortSegmentation() throws Exception {
        new SegmentationImpl().decode(new byte[]{(byte) 0x80});
    }

    @Test(expected = IOException.class)
    public void emptyImportance() throws Exception {
        new ImportanceImpl().decode(new byte[0]);
    }
}
