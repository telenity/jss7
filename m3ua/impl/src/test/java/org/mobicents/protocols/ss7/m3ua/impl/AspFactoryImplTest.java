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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.AssociationType;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.PayloadData;
import org.mobicents.protocols.ss7.m3ua.impl.message.MessageFactoryImpl;
import org.mobicents.protocols.ss7.m3ua.message.M3UAMessage;
import org.mobicents.protocols.ss7.m3ua.message.MessageClass;
import org.mobicents.protocols.ss7.m3ua.message.MessageType;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.util.ReferenceCountUtil;

public class AspFactoryImplTest {

    @Test
    public void write_sendThrows_releasesByteBuf() {
        TestAssociation association = new TestAssociation(true);
        AspFactoryImpl factory = newFactory(association);

        factory.write(newAspUp());

        assertNotNull(association.byteBuf);
        assertEquals(0, association.byteBuf.refCnt());
    }

    @Test
    public void write_sendSucceeds_keepsByteBufOwnedByAssociation() {
        TestAssociation association = new TestAssociation(false);
        AspFactoryImpl factory = newFactory(association);

        factory.write(newAspUp());

        assertNotNull(association.byteBuf);
        assertEquals(1, association.byteBuf.refCnt());
        ReferenceCountUtil.release(association.byteBuf);
    }

    private static AspFactoryImpl newFactory(TestAssociation association) {
        AspFactoryImpl factory = new AspFactoryImpl();
        factory.association = association;
        factory.setM3UAManagement(new M3UAManagementImpl("AspFactoryImplTest"));
        return factory;
    }

    private static M3UAMessage newAspUp() {
        return new MessageFactoryImpl().createMessage(MessageClass.ASP_STATE_MAINTENANCE, MessageType.ASP_UP);
    }

    private static final class TestAssociation implements Association {
        private final boolean failSend;
        private ByteBuf byteBuf;

        private TestAssociation(boolean failSend) {
            this.failSend = failSend;
        }

        @Override
        public void send(PayloadData payloadData) throws Exception {
            this.byteBuf = payloadData.getByteBuf();
            if (failSend) {
                throw new Exception("send failed");
            }
        }

        @Override
        public ByteBufAllocator getByteBufAllocator() throws Exception {
            return UnpooledByteBufAllocator.DEFAULT;
        }

        @Override
        public IpChannelType getIpChannelType() {
            return null;
        }

        @Override
        public AssociationType getAssociationType() {
            return null;
        }

        @Override
        public String getName() {
            return "test-association";
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public boolean isUp() {
            return true;
        }

        @Override
        public AssociationListener getAssociationListener() {
            return null;
        }

        @Override
        public void setAssociationListener(AssociationListener associationListener) {
        }

        @Override
        public String getHostAddress() {
            return null;
        }

        @Override
        public int getHostPort() {
            return 0;
        }

        @Override
        public String getPeerAddress() {
            return null;
        }

        @Override
        public int getPeerPort() {
            return 0;
        }

        @Override
        public String getServerName() {
            return null;
        }

        @Override
        public String[] getExtraHostAddresses() {
            return null;
        }

        @Override
        public void acceptAnonymousAssociation(AssociationListener associationListener) throws Exception {
        }

        @Override
        public void rejectAnonymousAssociation() {
        }

        @Override
        public void stopAnonymousAssociation() throws Exception {
        }
    }
}
