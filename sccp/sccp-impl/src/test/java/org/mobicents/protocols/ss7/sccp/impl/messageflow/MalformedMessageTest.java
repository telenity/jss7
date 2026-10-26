package org.mobicents.protocols.ss7.sccp.impl.messageflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.protocols.ss7.mtp.Mtp3;
import org.mobicents.protocols.ss7.mtp.Mtp3TransferPrimitive;
import org.mobicents.protocols.ss7.sccp.impl.SccpHarness;
import org.mobicents.protocols.ss7.sccp.impl.SccpProviderImpl;
import org.mobicents.protocols.ss7.sccp.impl.SccpRoutingControl;
import org.mobicents.protocols.ss7.sccp.impl.SccpStackImpl;
import org.mobicents.protocols.ss7.sccp.impl.message.SccpAddressedMessageImpl;

/**
 * Errors in the SCCP receive path: malformed input is dropped with a one-line warning, while a local runtime error
 * (here an injected routing failure) is logged as an error with its stack trace and is not blamed on the sender.
 */
public class MalformedMessageTest extends SccpHarness {

    private final List<LoggingEvent> events = new ArrayList<LoggingEvent>();
    private final AppenderSkeleton capture = new AppenderSkeleton() {
        protected void append(LoggingEvent event) {
            if (event.getLoggerName().startsWith("org.mobicents.protocols.ss7.sccp.impl.SccpStackImpl")) {
                events.add(event);
            }
        }

        public void close() {
        }

        public boolean requiresLayout() {
            return false;
        }
    };

    @Before
    public void setUp() throws Exception {
        this.sccpStack1Name = "MalformedMessageTestSccpStack1";
        this.sccpStack2Name = "MalformedMessageTestSccpStack2";
        super.setUp();
        Logger.getRootLogger().addAppender(capture);
    }

    @After
    public void tearDown() {
        Logger.getRootLogger().removeAppender(capture);
        super.tearDown();
    }

    private void receive(byte[] sccpMessage) {
        Mtp3TransferPrimitive msg = this.mtp3UserPart1.getMtp3TransferPrimitiveFactory()
                .createMtp3TransferPrimitive(Mtp3._SI_SERVICE_SCCP, 2, 0, getStack2PC(), getStack1PC(), 0, sccpMessage);
        this.sccpStack1.onMtp3TransferMessage(msg);   // must not throw
    }

    private LoggingEvent onlyEvent(Level level) {
        LoggingEvent found = null;
        for (LoggingEvent e : events) {
            if (e.getLevel() == level) {
                found = e;
            }
        }
        assertNotNull("expected a " + level + " log event", found);
        return found;
    }

    @Test
    public void udtWithUnknownGlobalTitleIndicatorIsDropped() {
        // UDT, class 0 + return on error; CdPA = AI 0x14 (GTI 5, route on GT); CgPA = route on SSN 8; 1 data byte
        receive(new byte[]{0x09, (byte) 0x80, 0x03, 0x04, 0x06, 0x01, 0x14, 0x02, 0x42, 0x08, 0x01, 0x00});

        LoggingEvent e = onlyEvent(Level.WARN);
        assertTrue(e.getRenderedMessage().startsWith("Dropped malformed SCCP message from opc=" + getStack2PC()));
        assertEquals(null, e.getThrowableInformation());
    }

    @Test
    public void localRuntimeErrorIsLoggedWithStackTraceNotAsMalformed() throws Exception {
        // Fault injection: a routing bug inside the receive path (listener errors are already caught by routing).
        java.lang.reflect.Field providerField = SccpStackImpl.class.getDeclaredField("sccpProvider");
        providerField.setAccessible(true);
        java.lang.reflect.Field routingField = SccpStackImpl.class.getDeclaredField("sccpRoutingControl");
        routingField.setAccessible(true);
        routingField.set(sccpStack1, new SccpRoutingControl((SccpProviderImpl) providerField.get(sccpStack1), sccpStack1) {
            @Override
            protected void routeMssgFromMtp(SccpAddressedMessageImpl msg) {
                throw new IllegalStateException("boom");
            }
        });

        // well-formed UDT: CdPA and CgPA route on SSN 8, 1 data byte
        receive(new byte[]{0x09, (byte) 0x80, 0x03, 0x05, 0x07, 0x02, 0x42, 0x08, 0x02, 0x42, 0x08, 0x01, 0x00});

        LoggingEvent e = onlyEvent(Level.ERROR);
        assertEquals("Error while processing SCCP message from opc=" + getStack2PC(), e.getRenderedMessage());
        assertNotNull(e.getThrowableInformation());
        assertEquals("boom", e.getThrowableInformation().getThrowable().getMessage());
        for (LoggingEvent other : events) {
            assertTrue(!other.getRenderedMessage().startsWith("Dropped malformed"));
        }
    }
}
