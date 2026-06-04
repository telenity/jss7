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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationType;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.Server;

/**
 * Verifies the SCTP management XML persistence round-trip without opening sockets.
 */
public class NettySctpManagementPersistenceTest {

    @Rule
    public TemporaryFolder persistFolder = new TemporaryFolder();

    @Test
    public void testStoreLoadRoundTrip() throws Exception {
        String managementName = "PersistenceTest";

        NettySctpManagementImpl management = newManagement(managementName);
        try {
            management.start();
            management.setConnectDelay(1234);
            management.addServer("server1", "127.0.0.1", 3010, IpChannelType.TCP, true, 3,
                    new String[]{"127.0.0.2", "127.0.0.3"});
            management.addServerAssociation("127.0.0.10", 3020, "server1", "serverAssoc", IpChannelType.TCP);
            management.addAssociation("127.0.0.11", 3030, "127.0.0.12", 3040, "clientAssoc", IpChannelType.TCP,
                    new String[]{"127.0.0.13"}, "127.0.0.14");
            management.stop();
        } finally {
            stopIfStarted(management);
        }

        assertTrue(new File(persistFolder.getRoot(), managementName + "_sctp.xml").isFile());

        NettySctpManagementImpl loadedManagement = newManagement(managementName);
        try {
            loadedManagement.start();

            assertEquals(1234, loadedManagement.getConnectDelay());

            List<Server> servers = loadedManagement.getServers();
            assertEquals(1, servers.size());

            Server server = servers.get(0);
            assertEquals("server1", server.getName());
            assertEquals("127.0.0.1", server.getHostAddress());
            assertEquals(3010, server.getHostport());
            assertEquals(IpChannelType.TCP, server.getIpChannelType());
            assertTrue(server.isAcceptAnonymousConnections());
            assertEquals(3, server.getMaxConcurrentConnectionsCount());
            assertArrayEquals(new String[]{"127.0.0.2", "127.0.0.3"}, server.getExtraHostAddresses());
            assertEquals(1, server.getAssociations().size());
            assertEquals("serverAssoc", server.getAssociations().get(0));

            Map<String, Association> associations = loadedManagement.getAssociations();
            assertEquals(2, associations.size());

            Association serverAssociation = associations.get("serverAssoc");
            assertEquals(AssociationType.SERVER, serverAssociation.getAssociationType());
            assertEquals(IpChannelType.TCP, serverAssociation.getIpChannelType());
            assertEquals("server1", serverAssociation.getServerName());
            assertEquals("127.0.0.10", serverAssociation.getPeerAddress());
            assertEquals(3020, serverAssociation.getPeerPort());

            Association clientAssociation = associations.get("clientAssoc");
            assertEquals(AssociationType.CLIENT, clientAssociation.getAssociationType());
            assertEquals(IpChannelType.TCP, clientAssociation.getIpChannelType());
            assertEquals("127.0.0.11", clientAssociation.getHostAddress());
            assertEquals(3030, clientAssociation.getHostPort());
            assertEquals("127.0.0.12", clientAssociation.getPeerAddress());
            assertEquals(3040, clientAssociation.getPeerPort());
            assertArrayEquals(new String[]{"127.0.0.13"}, clientAssociation.getExtraHostAddresses());
            assertEquals("127.0.0.14", ((NettyAssociationImpl) clientAssociation).secondaryPeerAddress);
        } finally {
            stopIfStarted(loadedManagement);
        }
    }

    private NettySctpManagementImpl newManagement(String managementName) throws Exception {
        NettySctpManagementImpl management = new NettySctpManagementImpl(managementName);
        management.setPersistDir(persistFolder.getRoot().getAbsolutePath());
        return management;
    }

    private void stopIfStarted(NettySctpManagementImpl management) throws Exception {
        if (management != null && management.isStarted()) {
            management.stop();
        }
    }
}
