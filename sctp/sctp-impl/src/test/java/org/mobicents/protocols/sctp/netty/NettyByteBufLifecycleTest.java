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

package org.mobicents.protocols.sctp.netty;

import static org.mobicents.protocols.sctp.netty.SctpTestSupport.await;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.PayloadData;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ResourceLeakDetector;

/**
 * Netty 4 regression coverage: ByteBuf ownership on the wire and management restart lifecycle.
 */
public class NettyByteBufLifecycleTest {

    private static final String SERVER_NAME = "bytebuf-server";
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SERVER_PORT = 2360;

    private static final String SERVER_ASSOCIATION_NAME = "bytebufServerAssoc";
    private static final String CLIENT_ASSOCIATION_NAME = "bytebufClientAssoc";

    private static final String CLIENT_HOST = "127.0.0.1";
    private static final int CLIENT_PORT = 2361;

    private static final byte[] CLIENT_MESSAGE = "Client ByteBuf Hi".getBytes();
    private static final byte[] SERVER_MESSAGE = "Server ByteBuf Hi".getBytes();

    private static final String MANAGEMENT_RESTART_NAME = "ByteBufLifecycleTest";

    private ResourceLeakDetector.Level previousLeakLevel;

    private NettySctpManagementImpl management;
    private NettyAssociationImpl serverAssociation;
    private NettyAssociationImpl clientAssociation;

    private volatile boolean clientAssocUp;
    private volatile boolean serverAssocUp;
    private volatile byte[] clientMessage;
    private volatile byte[] serverMessage;

    @Before
    public void setUpLeakDetector() {
        previousLeakLevel = ResourceLeakDetector.getLevel();
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
    }

    @After
    public void restoreLeakDetector() {
        ResourceLeakDetector.setLevel(previousLeakLevel);
    }

    @Test
    public void testByteBufRoundTripTcp() throws Exception {
        runByteBufRoundTrip(IpChannelType.TCP, "netty-bytebuf-management", SERVER_NAME, SERVER_PORT, CLIENT_PORT,
                SERVER_ASSOCIATION_NAME, CLIENT_ASSOCIATION_NAME);
    }

    @Test
    public void testByteBufRoundTripSctp() throws Exception {
        if (NettySctpTransferTest.checkSctpEnabled()) {
            runByteBufRoundTrip(IpChannelType.SCTP, "netty-bytebuf-sctp-management", "bytebuf-sctp-server", 2370, 2371,
                    "bytebufSctpServerAssoc", "bytebufSctpClientAssoc");
        }
    }

    @Test
    public void testManagementRestartTcp() throws Exception {
        NettySctpManagementImpl mgmt = new NettySctpManagementImpl(MANAGEMENT_RESTART_NAME, 1, 2, 1);
        mgmt.setPersistDir("target");

        try {
            runConnectCycle(mgmt, IpChannelType.TCP, "restart-server", 2362, 2363, "restartServerAssoc",
                    "restartClientAssoc");

            assertTrue(hasThreadNamedPrefix("Sctp-BossGroup-" + MANAGEMENT_RESTART_NAME));

            mgmt.stop();
            await(() -> !mgmt.isStarted(), 5000);

            mgmt.start();
            mgmt.removeAllResourses();

            runConnectCycle(mgmt, IpChannelType.TCP, "restart-server-2", 2364, 2365, "restartServerAssoc2",
                    "restartClientAssoc2");
        } finally {
            if (mgmt.isStarted()) {
                mgmt.removeAllResourses();
                mgmt.stop();
            }
        }
    }

    @Test
    public void testManagementRestartSctp() throws Exception {
        if (NettySctpTransferTest.checkSctpEnabled()) {
            NettySctpManagementImpl mgmt = new NettySctpManagementImpl("ByteBufLifecycleSctpTest", 1, 2, 1);
            mgmt.setPersistDir("target");

            try {
                runConnectCycle(mgmt, IpChannelType.SCTP, "restart-sctp-server", 2372, 2373, "restartSctpServerAssoc",
                        "restartSctpClientAssoc");

                assertTrue(hasThreadNamedPrefix("Sctp-BossGroup-ByteBufLifecycleSctpTest"));

                mgmt.stop();
                await(() -> !mgmt.isStarted(), 5000);

                mgmt.start();
                mgmt.removeAllResourses();

                runConnectCycle(mgmt, IpChannelType.SCTP, "restart-sctp-server-2", 2374, 2375,
                        "restartSctpServerAssoc2", "restartSctpClientAssoc2");
            } finally {
                if (mgmt.isStarted()) {
                    mgmt.removeAllResourses();
                    mgmt.stop();
                }
            }
        }
    }

    @Test
    public void testClientReconnectsAfterServerAssociationRestartTcp() throws Exception {
        runReconnectAfterServerAssociationRestart(IpChannelType.TCP, "netty-reconnect-management", "reconnect-server", 2366,
                2367, "reconnectServerAssoc", "reconnectClientAssoc");
    }

    @Test
    public void testClientReconnectsAfterServerAssociationRestartSctp() throws Exception {
        if (NettySctpTransferTest.checkSctpEnabled()) {
            runReconnectAfterServerAssociationRestart(IpChannelType.SCTP, "netty-reconnect-sctp-management",
                    "reconnect-sctp-server", 2376, 2377, "reconnectSctpServerAssoc", "reconnectSctpClientAssoc");
        }
    }

    private void runByteBufRoundTrip(IpChannelType ipChannelType, String managementName, String serverName, int serverPort,
            int clientPort, String serverAssocName, String clientAssocName) throws Exception {
        initManagement(managementName, 1, 2, 1);

        try {
            management.addServer(serverName, SERVER_HOST, serverPort, ipChannelType, false, 0, null);
            serverAssociation = (NettyAssociationImpl) management.addServerAssociation(CLIENT_HOST, clientPort, serverName,
                    serverAssocName, ipChannelType);
            clientAssociation = (NettyAssociationImpl) management.addAssociation(CLIENT_HOST, clientPort, SERVER_HOST,
                    serverPort, clientAssocName, ipChannelType, null);

            management.startServer(serverName);
            serverAssociation.setAssociationListener(new ByteBufServerListener());
            management.startAssociation(serverAssocName);
            clientAssociation.setAssociationListener(new ByteBufClientListener());
            management.startAssociation(clientAssocName);

            await(() -> clientAssocUp && serverAssocUp && clientMessage != null && serverMessage != null, 5000);

            assertArrayEquals(SERVER_MESSAGE, clientMessage);
            assertArrayEquals(CLIENT_MESSAGE, serverMessage);
        } finally {
            shutdownAssociations(serverName, serverAssocName, clientAssocName);
        }
    }

    private void runReconnectAfterServerAssociationRestart(IpChannelType ipChannelType, String managementName,
            String serverName, int serverPort, int clientPort, String serverAssocName, String clientAssocName)
            throws Exception {
        initManagement(managementName, 1, 2, 1);
        CountingAssociationListener serverListener = new CountingAssociationListener();
        CountingAssociationListener clientListener = new CountingAssociationListener();

        try {
            management.addServer(serverName, SERVER_HOST, serverPort, ipChannelType, false, 0, null);
            serverAssociation = (NettyAssociationImpl) management.addServerAssociation(CLIENT_HOST, clientPort, serverName,
                    serverAssocName, ipChannelType);
            clientAssociation = (NettyAssociationImpl) management.addAssociation(CLIENT_HOST, clientPort, SERVER_HOST,
                    serverPort, clientAssocName, ipChannelType, null);

            management.startServer(serverName);
            serverAssociation.setAssociationListener(serverListener);
            management.startAssociation(serverAssocName);
            clientAssociation.setAssociationListener(clientListener);
            management.startAssociation(clientAssocName);

            await(() -> serverListener.upCount.get() >= 1 && clientListener.upCount.get() >= 1
                    && serverAssociation.isConnected() && clientAssociation.isConnected(), 5000);

            assertEquals(1, serverListener.upCount.get());
            assertEquals(1, clientListener.upCount.get());
            assertTrue(serverAssociation.isConnected());
            assertTrue(clientAssociation.isConnected());

            management.stopAssociation(serverAssocName);

            await(() -> serverListener.downCount.get() >= 1 && clientListener.downCount.get() >= 1
                    && !serverAssociation.isConnected() && !clientAssociation.isConnected(), 5000);

            assertEquals(1, serverListener.downCount.get());
            assertEquals(1, clientListener.downCount.get());
            assertTrue(!serverAssociation.isConnected());
            assertTrue(!clientAssociation.isConnected());

            management.startAssociation(serverAssocName);

            await(() -> serverListener.upCount.get() >= 2 && clientListener.upCount.get() >= 2
                    && serverAssociation.isConnected() && clientAssociation.isConnected(), 5000);

            assertEquals(2, serverListener.upCount.get());
            assertEquals(2, clientListener.upCount.get());
            assertTrue(serverAssociation.isConnected());
            assertTrue(clientAssociation.isConnected());
        } finally {
            shutdownReconnectTest(serverName, serverAssocName, clientAssocName);
        }
    }

    private void runConnectCycle(NettySctpManagementImpl mgmt, IpChannelType ipChannelType, String serverName,
            int serverPort, int clientPort, String serverAssocName, String clientAssocName) throws Exception {
        if (!mgmt.isStarted()) {
            mgmt.start();
            mgmt.setConnectDelay(500);
        }
        mgmt.removeAllResourses();

        mgmt.addServer(serverName, SERVER_HOST, serverPort, ipChannelType, false, 0, null);
        NettyAssociationImpl serverAssoc = (NettyAssociationImpl) mgmt.addServerAssociation(CLIENT_HOST, clientPort,
                serverName, serverAssocName, ipChannelType);
        NettyAssociationImpl clientAssoc = (NettyAssociationImpl) mgmt.addAssociation(CLIENT_HOST, clientPort, SERVER_HOST,
                serverPort, clientAssocName, ipChannelType, null);

        mgmt.startServer(serverName);
        serverAssoc.setAssociationListener(new NoOpAssociationListener());
        mgmt.startAssociation(serverAssocName);
        clientAssoc.setAssociationListener(new NoOpAssociationListener());
        mgmt.startAssociation(clientAssocName);

        await(() -> serverAssoc.isConnected() && clientAssoc.isConnected(), 5000);
        assertTrue(serverAssoc.isConnected());
        assertTrue(clientAssoc.isConnected());

        mgmt.stopAssociation(serverAssocName);
        mgmt.stopAssociation(clientAssocName);
        mgmt.stopServer(serverName);
        mgmt.removeAssociation(clientAssocName);
        mgmt.removeAssociation(serverAssocName);
        mgmt.removeServer(serverName);

        await(() -> !serverAssoc.isConnected() && !clientAssoc.isConnected(), 5000);
    }

    private void initManagement(String name, int bossSize, int workerSize, int clientSize) throws Exception {
        management = new NettySctpManagementImpl(name, bossSize, workerSize, clientSize);
        management.setPersistDir("target");
        management.start();
        management.setConnectDelay(500);
        management.removeAllResourses();
    }

    private void shutdownAssociations(String serverName, String serverAssocName, String clientAssocName) throws Exception {
        if (management == null) {
            return;
        }
        management.stopAssociation(serverAssocName);
        management.stopAssociation(clientAssocName);
        management.stopServer(serverName);
        management.removeAssociation(clientAssocName);
        management.removeAssociation(serverAssocName);
        management.removeServer(serverName);
        management.stop();
        management = null;
    }

    private void shutdownReconnectTest(String serverName, String serverAssocName, String clientAssocName) throws Exception {
        if (management == null) {
            return;
        }
        if (clientAssociation != null && clientAssociation.isStarted()) {
            management.stopAssociation(clientAssocName);
        }
        if (serverAssociation != null && serverAssociation.isStarted()) {
            management.stopAssociation(serverAssocName);
        }
        management.stopServer(serverName);
        management.removeAssociation(clientAssocName);
        management.removeAssociation(serverAssocName);
        management.removeServer(serverName);
        management.stop();
        management = null;
    }

    private static boolean hasThreadNamedPrefix(String prefix) {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void sendByteBufMessage(Association association, byte[] message) throws Exception {
        ByteBuf byteBuf = association.getByteBufAllocator().buffer(message.length);
        byteBuf.writeBytes(message);
        association.send(new PayloadData(message.length, byteBuf, true, false, 3, 1));
    }

    private static byte[] copyReceivedPayload(PayloadData payloadData) {
        ByteBuf byteBuf = payloadData.getByteBuf();
        assertNotNull(byteBuf);
        byte[] copy = new byte[byteBuf.readableBytes()];
        byteBuf.getBytes(byteBuf.readerIndex(), copy);
        ReferenceCountUtil.release(byteBuf);
        return copy;
    }

    private final class ByteBufClientListener implements AssociationListener {

        @Override
        public void onCommunicationUp(Association association, int maxInboundStreams, int maxOutboundStreams) {
            clientAssocUp = true;
            try {
                sendByteBufMessage(association, CLIENT_MESSAGE);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onCommunicationShutdown(Association association) {
        }

        @Override
        public void onCommunicationLost(Association association) {
        }

        @Override
        public void onCommunicationRestart(Association association) {
        }

        @Override
        public void onPayload(Association association, PayloadData payloadData) {
            clientMessage = copyReceivedPayload(payloadData);
        }

        @Override
        public void inValidStreamId(PayloadData payloadData) {
        }
    }

    private final class ByteBufServerListener implements AssociationListener {

        @Override
        public void onCommunicationUp(Association association, int maxInboundStreams, int maxOutboundStreams) {
            serverAssocUp = true;
        }

        @Override
        public void onCommunicationShutdown(Association association) {
        }

        @Override
        public void onCommunicationLost(Association association) {
        }

        @Override
        public void onCommunicationRestart(Association association) {
        }

        @Override
        public void onPayload(Association association, PayloadData payloadData) {
            serverMessage = copyReceivedPayload(payloadData);
            try {
                sendByteBufMessage(association, SERVER_MESSAGE);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void inValidStreamId(PayloadData payloadData) {
        }
    }

    private static final class NoOpAssociationListener implements AssociationListener {

        @Override
        public void onCommunicationUp(Association association, int maxInboundStreams, int maxOutboundStreams) {
        }

        @Override
        public void onCommunicationShutdown(Association association) {
        }

        @Override
        public void onCommunicationLost(Association association) {
        }

        @Override
        public void onCommunicationRestart(Association association) {
        }

        @Override
        public void onPayload(Association association, PayloadData payloadData) {
            ReferenceCountUtil.release(payloadData.getByteBuf());
        }

        @Override
        public void inValidStreamId(PayloadData payloadData) {
        }
    }

    private static final class CountingAssociationListener implements AssociationListener {

        private final AtomicInteger upCount = new AtomicInteger();
        private final AtomicInteger downCount = new AtomicInteger();

        @Override
        public void onCommunicationUp(Association association, int maxInboundStreams, int maxOutboundStreams) {
            upCount.incrementAndGet();
        }

        @Override
        public void onCommunicationShutdown(Association association) {
            downCount.incrementAndGet();
        }

        @Override
        public void onCommunicationLost(Association association) {
        }

        @Override
        public void onCommunicationRestart(Association association) {
        }

        @Override
        public void onPayload(Association association, PayloadData payloadData) {
            ReferenceCountUtil.release(payloadData.getByteBuf());
        }

        @Override
        public void inValidStreamId(PayloadData payloadData) {
        }
    }

}
