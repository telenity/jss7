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

package org.mobicents.protocols.ss7.m3ua.impl;

import org.junit.Before;
import org.junit.Test;
import org.mobicents.protocols.ss7.m3ua.As;
import org.mobicents.protocols.ss7.m3ua.ExchangeType;
import org.mobicents.protocols.ss7.m3ua.Functionality;
import org.mobicents.protocols.ss7.m3ua.RouteKey;
import org.mobicents.protocols.ss7.m3ua.impl.message.ssnm.DestinationAvailableImpl;
import org.mobicents.protocols.ss7.mtp.Mtp3ResumePrimitive;

import static org.junit.Assert.*;

public class SignalingNetworkManagementHandlerTest {
    private static final int DPC = 123;
    private final RecordingManagement management = new RecordingManagement();
    private AsImpl as;
    private SignalingNetworkManagementHandler handler;
    private DestinationAvailableImpl dava;

    @Before
    public void setUp() throws Exception {
        as = new AsImpl("as", null, null, 1, Functionality.AS, ExchangeType.DE, null, null);
        as.setM3UAManagement(management);
        management.getRouteManagement().route.put(new RouteKey(DPC, -1, -1), new As[]{as, null});
        AspFactoryImpl factory = new AspFactoryImpl();
        factory.setFunctionality(Functionality.AS);
        factory.setExchangeType(ExchangeType.DE);
        factory.setM3UAManagement(management);
        AspImpl asp = factory.createAsp();
        asp.setAs(as);
        asp.getLocalFSM().signal(TransitionState.COMM_UP);
        asp.getLocalFSM().signal(TransitionState.ASP_ACTIVE_SENT);
        asp.getLocalFSM().signal(TransitionState.ASP_ACTIVE_ACK);
        assertEquals(AspState.ACTIVE.toString(), asp.getLocalFSM().getState().getName());
        as.getPeerFSM().signal(TransitionState.AS_STATE_CHANGE_ACTIVE);

        handler = new SignalingNetworkManagementHandler(factory);
        dava = new DestinationAvailableImpl();
        dava.setAffectedPointCodes(management.parameterFactory.createAffectedPointCode(new int[]{DPC}, new short[]{0}));
    }

    @Test
    public void davaDoesNotResumeWhileRoutingAsIsPending() throws Exception {
        as.getPeerFSM().signal(TransitionState.AS_STATE_CHANGE_PENDING);
        assertFalse(as.isUp());
        assertNull(management.getRouteManagement().getAsForRoute(DPC, -1, -1, 0));

        handler.handleDestinationAvailable(dava);

        assertEquals(0, management.resumeCount);
    }

    @Test
    public void davaUsesRoutingFsmWhenSharedStateSaysActive() throws Exception {
        as.getPeerFSM().signal(TransitionState.AS_STATE_CHANGE_PENDING);
        // In double exchange the other AS FSM can overwrite this shared state.
        as.state = AsState.ACTIVE;
        assertTrue(as.isUp());
        assertNull(management.getRouteManagement().getAsForRoute(DPC, -1, -1, 0));

        handler.handleDestinationAvailable(dava);

        assertEquals(0, management.resumeCount);
    }

    @Test
    public void davaResumesAnActiveAuthorizedRoute() {
        assertSame(as, management.getRouteManagement().getAsForRoute(DPC, -1, -1, 0));

        handler.handleDestinationAvailable(dava);

        assertEquals(1, management.resumeCount);
        assertEquals(DPC, management.lastResume.getAffectedDpc());
    }

    @Test
    public void davaDoesNotResumeAnUnauthorizedDestination() {
        dava.setAffectedPointCodes(management.parameterFactory.createAffectedPointCode(new int[]{DPC + 1}, new short[]{0}));

        handler.handleDestinationAvailable(dava);

        assertEquals(0, management.resumeCount);
    }

    private static class RecordingManagement extends M3UAManagementImpl {
        private int resumeCount;
        private Mtp3ResumePrimitive lastResume;

        RecordingManagement() {
            super("ssnm-test");
        }

        @Override
        public void sendResumeMessageToLocalUser(Mtp3ResumePrimitive message) {
            resumeCount++;
            lastResume = message;
        }
    }
}
