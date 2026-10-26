package org.mobicents.protocols.ss7.sccp.impl.messageflow;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.sccp.LoadSharingAlgorithm;
import org.mobicents.protocols.ss7.sccp.OriginationType;
import org.mobicents.protocols.ss7.sccp.RuleType;
import org.mobicents.protocols.ss7.sccp.impl.Mtp3UserPartImpl;
import org.mobicents.protocols.ss7.sccp.impl.SccpHarness;
import org.mobicents.protocols.ss7.sccp.impl.SccpStackImplProxy;
import org.mobicents.protocols.ss7.sccp.impl.User;
import org.mobicents.protocols.ss7.sccp.message.SccpDataMessage;
import org.mobicents.protocols.ss7.sccp.parameter.GlobalTitle;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;

/**
 * Sgls rule: traffic is spread by SLS over primary, secondary and every address in the load-share table, skipping
 * destinations that are down.
 */
public class SglsRoutingTest extends SccpHarness {

    private static final int PC_SEC = 12;
    private static final int PC_TABLE = 13;

    protected Mtp3UserPartImpl mtp3UserPart11 = new Mtp3UserPartImpl();

    @Before
    public void setUpClass() throws Exception {
        this.sccpStack1Name = "SglsRoutingTestSccpStack1";
        this.sccpStack2Name = "SglsRoutingTestSccpStack2";
    }

    protected void createStack1() {
        sccpStack1 = new SccpStackImplProxy("sglsTestSccpStack1");
        sccpProvider1 = sccpStack1.getSccpProvider();
        sccpStack1.setMtp3UserPart(2, mtp3UserPart11);
    }

    protected void createStack2() {
        sccpStack2 = new SccpStackImplProxy("sglsTestSccpStack2");
        sccpProvider2 = sccpStack2.getSccpProvider();
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        sccpStack1.getRouter().addMtp3ServiceAccessPoint(2, 2, 11, 2);
        sccpStack1.getRouter().addMtp3Destination(2, 1, PC_SEC, PC_TABLE, 0, 255, 255);
        resource1.addRemoteSpc(2, PC_SEC, 0, 0);
        resource1.addRemoteSsn(2, PC_SEC, getSSN(), 0, false);
        resource1.addRemoteSpc(3, PC_TABLE, 0, 0);
        resource1.addRemoteSsn(3, PC_TABLE, getSSN(), 0, false);
    }

    @After
    public void tearDown() {
        super.tearDown();
    }

    private void send(int sls) throws Exception {
        SccpAddress called = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, 0, GlobalTitle.getInstance(1, "111111"), 0);
        SccpAddress calling = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack1PC(), null, getSSN());
        SccpDataMessage message = this.sccpProvider1.getMessageFactory().createDataMessageClass1(called, calling,
                new byte[]{1, 2, 3}, sls, getSSN(), true, null, null);
        sccpProvider1.send(message);
        Thread.sleep(100);
    }

    private int dpcOfLastMtp3Message() {
        return mtp3UserPart11.getMessages().get(mtp3UserPart11.getMessages().size() - 1).getDpc();
    }

    @Test
    public void testSglsSpreadsBySls() throws Exception {
        SccpAddress a1 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack1PC(), null, getSSN());
        SccpAddress a2 = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack2PC(), null, getSSN());
        User u1 = new User(sccpStack1.getSccpProvider(), a1, a2, getSSN());
        User u2 = new User(sccpStack2.getSccpProvider(), a2, a1, getSSN());
        u1.register();
        u2.register();
        Thread.sleep(100);

        GlobalTitle gt = GlobalTitle.getInstance(1, "111111");
        sccpStack1.getRouter().addPrimaryAddress(1, new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack2PC(), gt, getSSN()));
        sccpStack1.getRouter().addBackupAddress(1, new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, PC_SEC, gt, getSSN()));
        sccpStack1.getRouter().addBackupAddress(2, new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, PC_TABLE, gt, getSSN()));
        SccpAddress pattern = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, 0, gt, 0);
        sccpStack1.getRouter().addRule(1, RuleType.Sgls, LoadSharingAlgorithm.Undefined, OriginationType.ALL, pattern, "K", 1, 1, Arrays.asList(2));

        // three destinations up: sls mod 3 selects primary, secondary, table entry
        send(0);
        assertEquals(1, u2.getMessages().size());
        assertEquals(0, mtp3UserPart11.getMessages().size());
        send(1);
        assertEquals(1, mtp3UserPart11.getMessages().size());
        assertEquals(PC_SEC, dpcOfLastMtp3Message());
        send(2);
        assertEquals(2, mtp3UserPart11.getMessages().size());
        assertEquals(PC_TABLE, dpcOfLastMtp3Message());
        send(3);
        assertEquals(2, u2.getMessages().size());
        assertEquals(2, mtp3UserPart11.getMessages().size());

        // table destination down: sls mod 2 over primary and secondary only
        this.mtp3UserPart1.sendPauseMessageToLocalUser(PC_TABLE);
        Thread.sleep(100);
        send(2);
        assertEquals(3, u2.getMessages().size());
        send(1);
        assertEquals(3, mtp3UserPart11.getMessages().size());
        assertEquals(PC_SEC, dpcOfLastMtp3Message());

        // everything down: SCCP_FAILURE returned to the originator
        this.mtp3UserPart1.sendPauseMessageToLocalUser(PC_SEC);
        this.mtp3UserPart1.sendPauseMessageToLocalUser(getStack2PC());
        Thread.sleep(100);
        send(0);
        assertEquals(1, u1.getMessages().size());
        assertEquals(3, u2.getMessages().size());
        assertEquals(3, mtp3UserPart11.getMessages().size());

        this.mtp3UserPart1.sendResumeMessageToLocalUser(PC_TABLE);
        this.mtp3UserPart1.sendResumeMessageToLocalUser(PC_SEC);
        this.mtp3UserPart1.sendResumeMessageToLocalUser(getStack2PC());
        Thread.sleep(100);
    }
}
