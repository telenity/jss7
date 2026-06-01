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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.mobicents.protocols.ss7.m3ua.Functionality;
import org.junit.Test;

/**
 * Tests for {@link MessageHandler#getAspForNullRc()}.
 *
 * <p>The previous implementation called {@code aspList.get(0)} before the
 * size check, which threw {@code IndexOutOfBoundsException} on an empty list.
 * The reordered version must return {@code null} on an empty list.
 */
public class MessageHandlerTest {

    private static final class TestMessageHandler extends MessageHandler {
        TestMessageHandler(AspFactoryImpl aspFactoryImpl) {
            super(aspFactoryImpl);
        }
    }

    @Test
    public void getAspForNullRc_emptyList_returnsNull() {
        AspFactoryImpl factory = new AspFactoryImpl();
        TestMessageHandler handler = new TestMessageHandler(factory);

        assertNull(handler.getAspForNullRc());
    }

    @Test
    public void getAspForNullRc_singleAsp_returnsThatAsp() {
        AspFactoryImpl factory = newFactory();
        TestMessageHandler handler = new TestMessageHandler(factory);
        AspImpl asp = factory.createAsp();

        assertSame(asp, handler.getAspForNullRc());
    }

    @Test
    public void getAspForNullRc_multipleAsps_returnsNull() {
        AspFactoryImpl factory = newFactory();
        TestMessageHandler handler = new TestMessageHandler(factory);
        factory.createAsp();
        factory.createAsp();

        // share-by-ASP is invalid for null RC; the handler must reject and return null.
        // The sendError() call inside will NPE on the missing Association, but the
        // try/catch in write() swallows that and we still observe null.
        assertNull(handler.getAspForNullRc());
    }

    private static AspFactoryImpl newFactory() {
        AspFactoryImpl factory = new AspFactoryImpl();
        // AspImpl.init() switches on this; without it, createAsp() NPEs.
        factory.setFunctionality(Functionality.AS);
        return factory;
    }
}
