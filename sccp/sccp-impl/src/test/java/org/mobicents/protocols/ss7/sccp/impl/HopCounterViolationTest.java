package org.mobicents.protocols.ss7.sccp.impl;

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.protocols.ss7.indicator.NatureOfAddress;
import org.mobicents.protocols.ss7.indicator.NumberingPlan;
import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.sccp.LoadSharingAlgorithm;
import org.mobicents.protocols.ss7.sccp.LongMessageRuleType;
import org.mobicents.protocols.ss7.sccp.OriginationType;
import org.mobicents.protocols.ss7.sccp.RemoteSccpStatus;
import org.mobicents.protocols.ss7.sccp.RuleType;
import org.mobicents.protocols.ss7.sccp.SccpListener;
import org.mobicents.protocols.ss7.sccp.SignallingPointStatus;
import org.mobicents.protocols.ss7.sccp.message.SccpDataMessage;
import org.mobicents.protocols.ss7.sccp.message.SccpNoticeMessage;
import org.mobicents.protocols.ss7.sccp.parameter.GT0100;
import org.mobicents.protocols.ss7.sccp.parameter.ReturnCauseValue;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;

/**
 * Q.714 2.8.6 / 4.2: a GT loop ends with a hop counter violation, and the XUDTS that reports it must reach the
 * originator. Q.713 3.18 allows only 15 to 1 in its mandatory hop counter.
 */
public class HopCounterViolationTest extends SccpHarness {

    @Before
    public void setUp() throws Exception {
        this.sccpStack1Name = "HopCounterTestSccpStack1";
        this.sccpStack2Name = "HopCounterTestSccpStack2";
        super.setUp();
    }

    @After
    public void tearDown() {
        super.tearDown();
    }

    private static GT0100 gt(String digits) {
        return new GT0100(0, NumberingPlan.ISDN_TELEPHONY, NatureOfAddress.INTERNATIONAL, digits);
    }

    @Test
    public void hopCounterViolationReachesTheOriginator() throws Exception {
        // both stacks route GT 66... to the other one on GT: a loop
        router1.addPrimaryAddress(1, new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, getStack2PC(), gt("-/-"), getSSN()));
        router2.addPrimaryAddress(1, new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, getStack1PC(), gt("-/-"), getSSN()));
        SccpAddress pattern = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, 0, gt("66/*"), 0);
        router1.addRule(1, RuleType.Solitary, LoadSharingAlgorithm.Undefined, OriginationType.ALL, pattern, "K/K", 1, -1);
        router2.addRule(1, RuleType.Solitary, LoadSharingAlgorithm.Undefined, OriginationType.ALL, pattern, "K/K", 1, -1);
        // XUDT carries the hop counter; a short message would go as UDT, which has none
        router1.addLongMessageRule(1, getStack2PC(), getStack2PC(), LongMessageRuleType.XudtEnabled);
        router2.addLongMessageRule(1, getStack1PC(), getStack1PC(), LongMessageRuleType.XudtEnabled);

        final List<SccpNoticeMessage> notices = new CopyOnWriteArrayList<>();
        sccpProvider1.registerSccpListener(getSSN(), new SccpListener() {
            public void onMessage(SccpDataMessage message) {
            }

            public void onNotice(SccpNoticeMessage message) {
                notices.add(message);
            }

            public void onCoordRequest(int dpc, int ssn, int multiplicityIndicator) {
            }

            public void onCoordResponse(int dpc, int ssn, int multiplicityIndicator) {
            }

            public void onState(int dpc, int ssn, boolean inService, int multiplicityIndicator) {
            }

            public void onPcState(int dpc, SignallingPointStatus status, int restrictedImportanceLevel,
                                  RemoteSccpStatus remoteSccpStatus) {
            }
        });

        SccpAddress called = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, 0, gt("660000"), getSSN());
        SccpAddress calling = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, getStack1PC(), null, getSSN());
        sccpProvider1.send(sccpProvider1.getMessageFactory().createDataMessageClass1(called, calling, new byte[300], 0,
                getSSN(), true, null, null));

        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end && notices.stream()
                .noneMatch(n -> n.getReturnCause().getValue() == ReturnCauseValue.HOP_COUNTER_VIOLATION)) {
            Thread.sleep(20);
        }
        assertTrue("originator gets N-NOTICE with hop counter violation", notices.stream()
                .anyMatch(n -> n.getReturnCause().getValue() == ReturnCauseValue.HOP_COUNTER_VIOLATION));
    }
}
