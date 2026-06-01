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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import javolution.text.TextBuilder;
import javolution.util.FastList;
import javolution.util.FastMap;
import javolution.xml.XMLObjectReader;
import javolution.xml.XMLObjectWriter;
import javolution.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationType;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.Management;
import org.mobicents.protocols.api.ManagementEventListener;
import org.mobicents.protocols.api.Server;
import org.mobicents.protocols.api.ServerListener;
import org.mobicents.protocols.sctp.AssociationMap;

/**
 * @author <a href="mailto:amit.bhayani@telestax.com">Amit Bhayani</a>
 * 
 */
public class NettySctpManagementImpl implements Management {

    private static final Logger logger = Logger.getLogger(NettySctpManagementImpl.class);

    private static final String SCTP_PERSIST_DIR_KEY = "sctp.persist.dir";
    private static final String USER_DIR_KEY = "user.dir";
    private static final String PERSIST_FILE_NAME = "sctp.xml";

    private static final String SERVERS = "servers";
    private static final String ASSOCIATIONS = "associations";

    private static final String CONNECT_DELAY_PROP = "connectdelay";
    private static final String SINGLE_THREAD_PROP = "singlethread";
    private static final String WORKER_THREADS_PROP = "workerthreads";

    private final TextBuilder persistFile = TextBuilder.newInstance();

    protected static final NettySctpXMLBinding binding = new NettySctpXMLBinding();
    protected static final String TAB_INDENT = "\t";
    private static final String CLASS_ATTRIBUTE = "type";

    private final String name;

    protected String persistDir = null;
    private int connectDelay = 5000;

    private ServerListener serverListener = null;

    private CopyOnWriteArrayList<ManagementEventListener> managementEventListeners = new CopyOnWriteArrayList<>();
    protected FastList<Server> servers = new FastList<>();
    protected AssociationMap<String, Association> associations = new AssociationMap<>();
    private volatile boolean started = false;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ScheduledExecutorService clientExecutor;

    private int bossSize;
    private int workerSize;
    private int clientSize;

    public NettySctpManagementImpl(String name) throws IOException {
        this(name, 1, 0, 1);
    }

    public NettySctpManagementImpl(String name, int bossSize, int workerSize, int clientSize) throws IOException {
        this.name = name;
        binding.setClassAttribute(CLASS_ATTRIBUTE);
        binding.setAlias(NettyServerImpl.class, "server");
        binding.setAlias(NettyAssociationImpl.class, "association");
        binding.setAlias(String.class, "string");
        this.bossSize = bossSize;
        this.workerSize = workerSize;
        this.clientSize = clientSize;
    }

        @Override
    public String getName() {
        return this.name;
    }

        @Override
    public String getPersistDir() {
        return this.persistDir;
    }

        @Override
    public void setPersistDir(String persistDir) {
        this.persistDir = persistDir;
    }

        @Override
    public ServerListener getServerListener() {
        return this.serverListener;
    }

        @Override
    public void setServerListener(ServerListener serverListener) {
        this.serverListener = serverListener;
    }

    protected EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    protected EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    protected ScheduledExecutorService getClientExecutor() {
        return clientExecutor;
    }

        @Override
    public void addManagementEventListener(ManagementEventListener listener) {
        this.managementEventListeners.addIfAbsent(listener);
    }

        @Override
    public void removeManagementEventListener(ManagementEventListener listener) {
        this.managementEventListeners.remove(listener);
    }

        @Override
    public void start() throws Exception {
        if (this.started) {
            logger.warn(String.format("management=%s is already started", this.name));
            return;
        }

        synchronized (this) {
            this.persistFile.clear();

            if (persistDir != null) {
                this.persistFile.append(persistDir).append(File.separator).append(this.name).append("_")
                        .append(PERSIST_FILE_NAME);
            } else {
                persistFile.append(System.getProperty(SCTP_PERSIST_DIR_KEY, System.getProperty(USER_DIR_KEY)))
                        .append(File.separator).append(this.name).append("_").append(PERSIST_FILE_NAME);
            }

            logger.info(String.format("SCTP configuration file path %s", persistFile));

            this.bossGroup = new NioEventLoopGroup(bossSize, new DefaultThreadFactory("Sctp-BossGroup-" + this.name));
            this.workerGroup = new NioEventLoopGroup(workerSize, new DefaultThreadFactory("Sctp-WorkerGroup-" + this.name));
            this.clientExecutor = new ScheduledThreadPoolExecutor(clientSize, new DefaultThreadFactory("Sctp-ClientExecutorGroup-"
                    + this.name));

            try {
                this.load();
            } catch (IOException e) {
                logger.warn(String.format("Failed to load the SCTP configuration file. \n%s", e.getMessage()));
            }

            this.started = true;

            if (logger.isInfoEnabled()) {
                logger.info(String.format("Started SCTP Management=%s", this.name));
            }

            for (ManagementEventListener lstr : managementEventListeners) {
                try {
                    lstr.onServiceStarted();
                } catch (Throwable ee) {
                    logger.error("Exception while invoking onServiceStarted", ee);
                }
            }
        }
    }

        @Override
    public void stop() throws Exception {
        if (!this.started) {
            logger.warn(String.format("management=%s is already stopped", this.name));
            return;
        }

        for (ManagementEventListener lstr : managementEventListeners) {
            try {
                lstr.onServiceStopped();
            } catch (Throwable ee) {
                logger.error("Exception while invoking onServiceStopped", ee);
            }
        }

        // We store the original state first
        this.store();

        // Stop all associations
        FastMap<String, Association> associationsTemp = this.associations;
        for (FastMap.Entry<String, Association> n = associationsTemp.head(), end = associationsTemp.tail(); (n = n.getNext()) != end;) {
            Association associationTemp = n.getValue();
            if (associationTemp.isStarted()) {
                ((NettyAssociationImpl) associationTemp).stop();
            }
        }

        FastList<Server> tempServers = servers;
        for (FastList.Node<Server> n = tempServers.head(), end = tempServers.tail(); (n = n.getNext()) != end;) {
            Server serverTemp = n.getValue();
            if (serverTemp.isStarted()) {
                try {
                    ((NettyServerImpl) serverTemp).stop();
                } catch (Exception e) {
                    logger.error(String.format("Exception while stopping the Server=%s", serverTemp.getName()), e);
                }
            }
        }

        // waiting till stopping associations
        for (int i1 = 0; i1 < 20; i1++) {
            boolean assConnected = false;
            for (FastMap.Entry<String, Association> n = this.associations.head(), end = this.associations.tail(); (n = n
                    .getNext()) != end;) {
                Association associationTemp = n.getValue();
                if (associationTemp.isConnected()) {
                    assConnected = true;
                    break;
                }
            }
            if (!assConnected)
                break;
            Thread.sleep(100);
        }

        // TODO - make a general shutdown and waiting for it instead of "waiting till stopping associations" 
        this.bossGroup.shutdownGracefully();
        this.workerGroup.shutdownGracefully();
        this.clientExecutor.shutdown();

        // TODO Should servers be also checked for shutdown?

        this.started = false;

    }

        @Override
    public boolean isStarted() {
        return this.started;
    }

        @Override
    public void removeAllResourses() throws Exception {
        synchronized (this) {
            if (!this.started) {
                throw new Exception(String.format("Management=%s not started", this.name));
            }

            if (this.associations.size() == 0 && this.servers.size() == 0)
                // no resources allocated - nothing to do
                return;

            if (logger.isInfoEnabled()) {
                logger.info(String.format("Removing allocated resources: Servers=%d, Associations=%d", this.servers.size(),
                        this.associations.size()));
            }

            // Remove all associations
            ArrayList<String> lst = new ArrayList<>();
            for (FastMap.Entry<String, Association> n = this.associations.head(), end = this.associations.tail(); (n = n
                    .getNext()) != end;) {
                lst.add(n.getKey());
            }
            for (String n : lst) {
                this.stopAssociation(n);
                this.removeAssociation(n);
            }

            // Remove all servers
            lst.clear();
            for (FastList.Node<Server> n = this.servers.head(), end = this.servers.tail(); (n = n.getNext()) != end;) {
                lst.add(n.getValue().getName());
            }
            for (String n : lst) {
                this.stopServer(n);
                this.removeServer(n);
            }

            // We store the cleared state
            this.store();

            for (ManagementEventListener lstr : managementEventListeners) {
                try {
                    lstr.onRemoveAllResources();
                } catch (Throwable ee) {
                    logger.error("Exception while invoking onRemoveAllResources", ee);
                }
            }
        }

    }

        @Override
    public Server addServer(String serverName, String hostAddress, int port, IpChannelType ipChannelType,
            boolean acceptAnonymousConnections, int maxConcurrentConnectionsCount, String[] extraHostAddresses)
            throws Exception {
        if (!this.started) {
            throw new Exception(String.format("Management=%s not started", this.name));
        }

        if (serverName == null) {
            throw new Exception("Server name cannot be null");
        }

        if (hostAddress == null) {
            throw new Exception("Server host address cannot be null");
        }

        if (port < 1) {
            throw new Exception("Server host port cannot be less than 1");
        }

        synchronized (this) {
            for (FastList.Node<Server> n = this.servers.head(), end = this.servers.tail(); (n = n.getNext()) != end;) {
                Server serverTemp = n.getValue();
                if (serverName.equals(serverTemp.getName())) {
                    throw new Exception(String.format("Server name=%s already exists", serverName));
                }

                if (hostAddress.equals(serverTemp.getHostAddress()) && port == serverTemp.getHostport()) {
                    throw new Exception(String.format("Server name=%s is already bound to %s:%d", serverTemp.getName(),
                            serverTemp.getHostAddress(), serverTemp.getHostport()));
                }
            }

            NettyServerImpl server = new NettyServerImpl(serverName, hostAddress, port, ipChannelType,
                    acceptAnonymousConnections, maxConcurrentConnectionsCount, extraHostAddresses);
            server.setManagement(this);

            FastList<Server> newServers = new FastList<>();
            newServers.addAll(this.servers);
            newServers.add(server);
            this.servers = newServers;

            this.store();

            for (ManagementEventListener lstr : managementEventListeners) {
                try {
                    lstr.onServerAdded(server);
                } catch (Throwable ee) {
                    logger.error("Exception while invoking onServerAdded", ee);
                }
            }

            if (logger.isInfoEnabled()) {
                logger.info(String.format("Created Server=%s", server.getName()));
            }

            return server;
        }
    }

        @Override
    public Server addServer(String serverName, String hostAddress, int port, IpChannelType ipChannelType,
            String[] extraHostAddresses) throws Exception {
        return addServer(serverName, hostAddress, port, ipChannelType, false, 0, extraHostAddresses);
    }

        @Override
    public Server addServer(String serverName, String hostAddress, int port) throws Exception {
        return addServer(serverName, hostAddress, port, IpChannelType.SCTP, false, 0, null);
    }

        @Override
    public void removeServer(String serverName) throws Exception {
        if (!this.started) {
            throw new Exception(String.format("Management=%s not started", this.name));
        }

        if (serverName == null) {
            throw new Exception("Server name cannot be null");
        }

        synchronized (this) {
            Server removeServer = null;
            for (FastList.Node<Server> n = this.servers.head(), end = this.servers.tail(); (n = n.getNext()) != end;) {
                NettyServerImpl serverTemp = (NettyServerImpl) n.getValue();

                if (serverName.equals(serverTemp.getName())) {
                    if (serverTemp.isStarted()) {
                        throw new Exception(String.format("Server=%s is started. Stop the server before removing", serverName));
                    }

                    if (serverTemp.anonymAssociations.size() != 0 || serverTemp.associations.size() != 0) {
                        throw new Exception(String.format(
                                "Server=%s has Associations. Remove all those Associations before removing Server", serverName));
                    }
                    removeServer = serverTemp;
                    break;
                }
            }

            if (removeServer == null) {
                throw new Exception(String.format("No Server found with name=%s", serverName));
            }

            FastList<Server> newServers = new FastList<>();
            newServers.addAll(this.servers);
            newServers.remove(removeServer);
            this.servers = newServers;

            this.store();

            for (ManagementEventListener lstr : managementEventListeners) {
                try {
                    lstr.onServerRemoved(removeServer);
                } catch (Throwable ee) {
                    logger.error("Exception while invoking onServerRemoved", ee);
                }
            }
        }

    }

        @Override
    public void startServer(String serverName) throws Exception {
        if (!this.started) {
            throw new Exception(String.format("Management=%s not started", this.name));
        }

        if (name == null) {
            throw new Exception("Server name cannot be null");
        }

        FastList<Server> tempServers = servers;
        for (FastList.Node<Server> n = tempServers.head(), end = tempServers.tail(); (n = n.getNext()) != end;) {
            Server serverTemp = n.getValue();

            if (serverName.equals(serverTemp.getName())) {
                if (serverTemp.isStarted()) {
                    throw new Exception(String.format("Server=%s is already started", serverName));
                }
                ((NettyServerImpl) serverTemp).start();
                this.store();
                return;
            }
        }

        throw new Exception(String.format("No Server found with name=%s", serverName));

    }

        @Override
    public void stopServer(String serverName) throws Exception {
        if (!this.started) {
            throw new Exception(String.format("Management=%s not started", this.name));
        }

        if (serverName == null) {
            throw new Exception("Server name cannot be null");
        }

        FastList<Server> tempServers = servers;
        for (FastList.Node<Server> n = tempServers.head(), end = tempServers.tail(); (n = n.getNext()) != end;) {
            Server serverTemp = n.getValue();

            if (serverName.equals(serverTemp.getName())) {
                ((NettyServerImpl) serverTemp).stop();
                this.store();
                return;
            }
        }

        throw new Exception(String.format("No Server found with name=%s", serverName));

    }

        @Override
    public List<Server> getServers() {
        return servers.unmodifiable();
    }

        @Override
    public Association addServerAssociation(String peerAddress, int peerPort, String serverName, String assocName)
            throws Exception {
        return addServerAssociation(peerAddress, peerPort, serverName, assocName, IpChannelType.SCTP);
    }

        @Override
    public Association addServerAssociation(String peerAddress, int peerPort, String serverName, String assocName,
            IpChannelType ipChannelType) throws Exception {
        if (!this.started) {
            throw new Exception(String.format("Management=%s not started", this.name));
        }

        if (peerAddress == null) {
            throw new Exception("Peer address cannot be null");
        }

        if (peerPort < 0) {
            throw new Exception("Peer port cannot be less than 0");
        }

        if (serverName == null) {
            throw new Exception("Server name cannot be null");
        }

        if (assocName == null) {
            throw new Exception("Association name cannot be null");
        }

        synchronized (this) {
            if (this.associations.get(assocName) != null) {
                throw new Exception(String.format("Already has association=%s", assocName));
            }

            Server server = null;

            for (FastList.Node<Server> n = this.servers.head(), end = this.servers.tail(); (n = n.getNext()) != end;) {
                Server serverTemp = n.getValue();
                if (serverTemp.getName().equals(serverName)) {
                    server = serverTemp;
                }
            }

            if (server == null) {
                throw new Exception(String.format("No Server found for name=%s", serverName));
            }

            for (FastMap.Entry<String, Association> n = this.associations.head(), end = this.associations.tail(); (n = n
                    .getNext()) != end;) {
                Association associationTemp = n.getValue();

                if (associationTemp.getServerName() != null && associationTemp.getServerName().equals(server.getName())
                        && peerAddress.equals(associationTemp.getPeerAddress()) && associationTemp.getPeerPort() == peerPort) {
                    throw new Exception(String.format("Already has association=%s with same peer address=%s and port=%d",
                            associationTemp.getName(), peerAddress, peerPort));
                }
            }

            if (server.getIpChannelType() != ipChannelType)
                throw new Exception("Server and Association have different IP channel types");

            NettyAssociationImpl association = new NettyAssociationImpl(peerAddress, peerPort, serverName, assocName,
                    ipChannelType);
            association.setManagement(this);

            AssociationMap<String, Association> newAssociations = new AssociationMap<>();
            newAssociations.putAll(this.associations);
            newAssociations.put(assocName, association);
            this.associations = newAssociations;

            FastList<String> newAssociations2 = new FastList<>();
            newAssociations2.addAll(((NettyServerImpl) server).associations);
            newAssociations2.add(assocName);
            ((NettyServerImpl) server).associations = newAssociations2;

            this.store();

            for (ManagementEventListener lstr : managementEventListeners) {
                try {
                    lstr.onAssociationAdded(association);
                } catch (Throwable ee) {
                    logger.error("Exception while invoking onAssociationAdded", ee);
                }
            }

            if (logger.isInfoEnabled()) {
                logger.info(String.format("Added Association=%s of type=%s", association.getName(),
                        association.getAssociationType()));
            }

            return association;
        }
    }

        @Override
    public Association addAssociation(String hostAddress, int hostPort, String peerAddress, int peerPort, String assocName)
            throws Exception {
        return addAssociation(hostAddress, hostPort, peerAddress, peerPort, assocName, IpChannelType.SCTP, null);
    }

    @Override
    public Association addAssociation(String hostAddress, int hostPort, String peerAddress, int peerPort, String assocName,
                                      IpChannelType ipChannelType, String[] extraHostAddresses) throws Exception {
        return addAssociation(hostAddress, hostPort, peerAddress, peerPort, assocName, ipChannelType, extraHostAddresses, null);
    }

        public Association addAssociation(String hostAddress, int hostPort, String peerAddress, int peerPort, String assocName,
            IpChannelType ipChannelType, String[] extraHostAddresses, String secondaryPeerAddress) throws Exception {

        if (!this.started) {
            throw new Exception(String.format("Management=%s not started", this.name));
        }

        if (hostAddress == null) {
            throw new Exception("Host address cannot be null");
        }

        if (hostPort < 0) {
            throw new Exception("Host port cannot be less than 0");
        }

        if (peerAddress == null) {
            throw new Exception("Peer address cannot be null");
        }

        if (peerPort < 1) {
            throw new Exception("Peer port cannot be less than 1");
        }

        if (assocName == null) {
            throw new Exception("Association name cannot be null");
        }

        synchronized (this) {
            for (FastMap.Entry<String, Association> n = this.associations.head(), end = this.associations.tail(); (n = n
                    .getNext()) != end;) {
                Association associationTemp = n.getValue();

                if (assocName.equals(associationTemp.getName())) {
                    throw new Exception(String.format("Already has association=%s", associationTemp.getName()));
                }

                if (peerAddress.equals(associationTemp.getPeerAddress()) && associationTemp.getPeerPort() == peerPort) {
                    throw new Exception(String.format("Already has association=%s with same peer address=%s and port=%d",
                            associationTemp.getName(), peerAddress, peerPort));
                }

                if (hostAddress.equals(associationTemp.getHostAddress()) && associationTemp.getHostPort() == hostPort) {
                    throw new Exception(String.format("Already has association=%s with same host address=%s and port=%d",
                            associationTemp.getName(), hostAddress, hostPort));
                }

            }

            NettyAssociationImpl association = new NettyAssociationImpl(hostAddress, hostPort, peerAddress, peerPort,
                    assocName, ipChannelType, extraHostAddresses, secondaryPeerAddress);
            association.setManagement(this);

            AssociationMap<String, Association> newAssociations = new AssociationMap<>();
            newAssociations.putAll(this.associations);
            newAssociations.put(assocName, association);
            this.associations = newAssociations;

            this.store();

            for (ManagementEventListener lstr : managementEventListeners) {
                try {
                    lstr.onAssociationAdded(association);
                } catch (Throwable ee) {
                    logger.error("Exception while invoking onAssociationAdded", ee);
                }
            }

            if (logger.isInfoEnabled()) {
                logger.info(String.format("Added Association=%s of type=%s", association.getName(),
                        association.getAssociationType()));
            }

            return association;
        }
    }

        @Override
    public void removeAssociation(String assocName) throws Exception {
        if (!this.started) {
            throw new Exception(String.format("Management=%s not started", this.name));
        }

        if (assocName == null) {
            throw new Exception("Association name cannot be null");
        }

        synchronized (this) {
            Association association = this.associations.get(assocName);

            if (association == null) {
                throw new Exception(String.format("No Association found for name=%s", assocName));
            }

            if (association.isStarted()) {
                throw new Exception(String.format("Association name=%s is started. Stop before removing", assocName));
            }

            AssociationMap<String, Association> newAssociations = new AssociationMap<>();
            newAssociations.putAll(this.associations);
            newAssociations.remove(assocName);
            this.associations = newAssociations;

            if (((NettyAssociationImpl) association).getAssociationType() == AssociationType.SERVER) {
                for (FastList.Node<Server> n = this.servers.head(), end = this.servers.tail(); (n = n.getNext()) != end;) {
                    Server serverTemp = n.getValue();
                    if (serverTemp.getName().equals(association.getServerName())) {
                        FastList<String> newAssociations2 = new FastList<>();
                        newAssociations2.addAll(((NettyServerImpl) serverTemp).associations);
                        newAssociations2.remove(assocName);
                        ((NettyServerImpl) serverTemp).associations = newAssociations2;
                        break;
                    }
                }
            }

            this.store();

            for (ManagementEventListener lstr : managementEventListeners) {
                try {
                    lstr.onAssociationRemoved(association);
                } catch (Throwable ee) {
                    logger.error("Exception while invoking onAssociationRemoved", ee);
                }
            }
        }

    }

        @Override
    public Association getAssociation(String assocName) throws Exception {
        if (assocName == null) {
            throw new Exception("Association name cannot be null");
        }
        Association associationTemp = this.associations.get(assocName);

        if (associationTemp == null) {
            throw new Exception(String.format("No Association found for name=%s", assocName));
        }
        return associationTemp;
    }

        @Override
    public Map<String, Association> getAssociations() {
        return new HashMap<>(this.associations);
    }

        @Override
    public void startAssociation(String assocName) throws Exception {
        if (!this.started) {
            throw new Exception(String.format("Management=%s not started", this.name));
        }

        if (assocName == null) {
            throw new Exception("Association name cannot be null");
        }

        Association associationTemp = this.associations.get(assocName);

        if (associationTemp == null) {
            throw new Exception(String.format("No Association found for name=%s", assocName));
        }

        if (associationTemp.isStarted()) {
            throw new Exception(String.format("Association=%s is already started", assocName));
        }

        ((NettyAssociationImpl) associationTemp).start();
        this.store();

    }

        @Override
    public void stopAssociation(String assocName) throws Exception {
        if (!this.started) {
            throw new Exception(String.format("Management=%s not started", this.name));
        }

        if (assocName == null) {
            throw new Exception("Association name cannot be null");
        }

        Association association = this.associations.get(assocName);

        if (association == null) {
            throw new Exception(String.format("No Association found for name=%s", assocName));
        }

        ((NettyAssociationImpl) association).stop();
        this.store();

    }

        @Override
    public int getConnectDelay() {
        return this.connectDelay;
    }

        @Override
    public void setConnectDelay(int connectDelay) throws Exception {
        if (!this.started)
            throw new Exception("ConnectDelay parameter can be updated only when SCTP stack is running");

        this.connectDelay = connectDelay;

        this.store();
    }

        @Override
    public int getWorkerThreads() {
        return this.workerSize;
    }

        @Override
    public void setWorkerThreads(int workerThreads) throws Exception {
        this.workerSize = workerThreads;
    }

        @Override
    public boolean isSingleThread() {
        return workerSize == 1;
    }

        @Override
    public void setSingleThread(boolean singleThread) throws Exception {
        // no op
    }
    
    protected List<ManagementEventListener> getManagementEventListeners() {
        return managementEventListeners;
    }

    @SuppressWarnings("unchecked")
    private void load() throws IOException {
        XMLObjectReader reader = null;
        try {
            reader = XMLObjectReader.newInstance(Files.newInputStream(Paths.get(persistFile.toString())));
            reader.setBinding(binding);

            try {
                this.connectDelay = reader.read(CONNECT_DELAY_PROP, Integer.class);
                reader.read(WORKER_THREADS_PROP, Integer.class);
                reader.read(SINGLE_THREAD_PROP, Boolean.class);
            } catch (NullPointerException npe) {
                // ignore - for backward compatibility when values are not defined
            }

            this.servers = reader.read(SERVERS, FastList.class);

            for (FastList.Node<Server> n = this.servers.head(), end = this.servers.tail(); (n = n.getNext()) != end;) {
                Server serverTemp = n.getValue();
                ((NettyServerImpl) serverTemp).setManagement(this);
                if (serverTemp.isStarted()) {
                    try {
                        ((NettyServerImpl) serverTemp).start();
                    } catch (Exception e) {
                        logger.error(String.format("Error while initiating Server=%s", serverTemp.getName()), e);
                    }
                }
            }

            this.associations = reader.read(ASSOCIATIONS, AssociationMap.class);
            for (FastMap.Entry<String, Association> n = this.associations.head(), end = this.associations.tail(); (n = n
                    .getNext()) != end;) {
                NettyAssociationImpl associationTemp = (NettyAssociationImpl) n.getValue();
                associationTemp.setManagement(this);
            }

        } catch (XMLStreamException ex) {
            // XML persistence load error - ignore
        }
    }

    private void store() {
        try {
            XMLObjectWriter writer = XMLObjectWriter.newInstance(Files.newOutputStream(Paths.get(persistFile.toString())));
            writer.setBinding(binding);
            writer.setIndentation(TAB_INDENT);

            writer.write(this.connectDelay, CONNECT_DELAY_PROP, Integer.class);

            writer.write(this.servers, SERVERS, FastList.class);
            writer.write(this.associations, ASSOCIATIONS, AssociationMap.class);

            writer.close();
        } catch (Exception e) {
            logger.error("Error while persisting the Rule state in file", e);
        }
    }
}
