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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.sctp.SctpChannel;
import io.netty.channel.sctp.SctpChannelOption;
import io.netty.channel.sctp.SctpMessage;
import io.netty.channel.sctp.nio.NioSctpChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javolution.xml.XMLFormat;
import javolution.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.AssociationType;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.ManagementEventListener;
import org.mobicents.protocols.api.PayloadData;

/**
 * @author <a href="mailto:amit.bhayani@telestax.com">Amit Bhayani</a>
 *
 */
public class NettyAssociationImpl implements Association {

    protected static final Logger logger = Logger.getLogger(NettyAssociationImpl.class.getName());

    private static final String NAME = "name";
    private static final String SERVER_NAME = "serverName";
    private static final String HOST_ADDRESS = "hostAddress";
    private static final String HOST_PORT = "hostPort";

    private static final String PEER_ADDRESS = "peerAddress";
    private static final String SECONDARY_PEER_ADDRESS = "secondaryPeerAddress";
    private static final String PEER_PORT = "peerPort";

    private static final String ASSOCIATION_TYPE = "assoctype";
    private static final String IPCHANNEL_TYPE = "ipChannelType";
    private static final String EXTRA_HOST_ADDRESS = "extraHostAddress";
    private static final String EXTRA_HOST_ADDRESS_SIZE = "extraHostAddresseSize";

    private String hostAddress;
    protected String secondaryHostAddress;
    private int hostPort;
    private String peerAddress;
    protected String secondaryPeerAddress;
    private int peerPort;
    private String serverName;
    private String name;
    private IpChannelType ipChannelType;
    private String[] extraHostAddresses;
    private NettyServerImpl server; // this is filled only for anonymous Associations

    private AssociationType type;

    private AssociationListener associationListener = null;

    private NettySctpManagementImpl management;

    private final AtomicBoolean connecting = new AtomicBoolean(false);
    // Is the Association been started by management?
    private final AtomicBoolean started = new AtomicBoolean(false);
    // Is the Association up (connection is established)
    protected final AtomicBoolean up = new AtomicBoolean(false);

    private NettySctpChannelInboundHandlerAdapter channelHandler;

    /**
     * If association can't start it tries to send INIT to the secondary peer address.
     * It alternates between the two peer addresses until the connection is established.
     */
    protected SocketAddress primaryPeerSocketAddress;
    protected SocketAddress secondaryPeerSocketAddress;

    /**
     * This is used only for SCTP This is the socket address. If the Association has multihome support and if peer address
     * changes, this variable is set to new value so new messages are now sent to changed peer address
     */
    protected volatile SocketAddress peerSocketAddress = null;

    protected volatile String initPrimaryHostAddress = null;
    protected volatile String initSecondaryHostAddress = null;

    protected final AtomicInteger reconnectCount = new AtomicInteger(0);
    protected static final int RECONNECT_COUNT_MAX = 2;

    public NettyAssociationImpl() {
    }

    /**
     * Creating a CLIENT Association
     *
     * @param hostAddress
     * @param hostPort
     * @param peerAddress
     * @param peerPort
     * @param assocName
     * @param ipChannelType
     * @param extraHostAddresses
     * @throws IOException
     */
    public NettyAssociationImpl(String hostAddress, int hostPort, String peerAddress, int peerPort, String assocName,
            IpChannelType ipChannelType, String[] extraHostAddresses, String secondaryPeerAddress) throws IOException {
        this();
        this.hostAddress = hostAddress;
        this.hostPort = hostPort;
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
        this.name = assocName;
        this.ipChannelType = ipChannelType;
        this.extraHostAddresses = extraHostAddresses;
        this.secondaryPeerAddress = secondaryPeerAddress;

        initDerivedFields();

        this.type = AssociationType.CLIENT;
    }

    /**
     * Creating a SERVER Association
     *
     * @param peerAddress
     * @param peerPort
     * @param serverName
     * @param assocName
     * @param ipChannelType
     */
    public NettyAssociationImpl(String peerAddress, int peerPort, String serverName, String assocName,
            IpChannelType ipChannelType) {
        this();
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
        this.serverName = serverName;
        this.name = assocName;
        this.ipChannelType = ipChannelType;

        this.initPrimaryHostAddress = hostAddress;
        if (extraHostAddresses != null && extraHostAddresses.length >= 1) {
            this.secondaryHostAddress = extraHostAddresses[0];
            this.initSecondaryHostAddress = secondaryHostAddress;
        } else {
            this.secondaryHostAddress = null;
        }

        this.type = AssociationType.SERVER;

    }

    /**
     * Creating an ANONYMOUS_SERVER Association
     *
     * @param peerAddress
     * @param peerPort
     * @param serverName
     * @param ipChannelType
     */
    protected NettyAssociationImpl(String peerAddress, int peerPort, String serverName, IpChannelType ipChannelType,
            NettyServerImpl server) {
        this();
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
        this.serverName = serverName;
        this.ipChannelType = ipChannelType;
        this.server = server;

        this.type = AssociationType.ANONYMOUS_SERVER;

    }

    protected void initDerivedFields() throws IOException {
        this.initPrimaryHostAddress = hostAddress;
        if (extraHostAddresses != null && extraHostAddresses.length >= 1) {
            this.secondaryHostAddress = extraHostAddresses[0];
            this.initSecondaryHostAddress = secondaryHostAddress;
        } else {
            this.secondaryHostAddress = null;
        }

        this.primaryPeerSocketAddress = new InetSocketAddress(InetAddress.getByName(peerAddress), peerPort);
        this.peerSocketAddress = this.primaryPeerSocketAddress;
        if (secondaryPeerAddress != null && !secondaryPeerAddress.isEmpty()) {
            this.secondaryPeerSocketAddress = new InetSocketAddress(InetAddress.getByName(secondaryPeerAddress), peerPort);
        } else {
            this.secondaryPeerAddress = null;
        }
    }

        @Override
    public IpChannelType getIpChannelType() {
        return this.ipChannelType;
    }

        @Override
    public AssociationType getAssociationType() {
        return this.type;
    }

        @Override
    public String getName() {
        return this.name;
    }

        @Override
    public boolean isStarted() {
        return started.get();
    }

        @Override
    public boolean isConnected() {
        return started.get() && up.get();
    }

        @Override
    public boolean isUp() {
        return up.get();
    }

        @Override
    public AssociationListener getAssociationListener() {
        return this.associationListener;
    }

        @Override
    public void setAssociationListener(AssociationListener associationListener) {
        this.associationListener = associationListener;

    }

        @Override
    public String getHostAddress() {
        return hostAddress;
    }

        @Override
    public int getHostPort() {
        return hostPort;
    }

        @Override
    public String getPeerAddress() {
        return peerAddress;
    }

        @Override
    public int getPeerPort() {
        return peerPort;
    }

        @Override
    public String getServerName() {
        return serverName;
    }

        @Override
    public String[] getExtraHostAddresses() {
        return extraHostAddresses;
    }

        @Override
    public void send(PayloadData payloadData) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Tx : Ass=%s %s", this.getName(), payloadData));
        }

        NettySctpChannelInboundHandlerAdapter handler = checkSocketIsOpen();

        final ByteBuf byteBuf = payloadData.getByteBuf();
        if (this.ipChannelType == IpChannelType.SCTP) {
            SctpMessage sctpMessage = new SctpMessage(payloadData.getPayloadProtocolId(), payloadData.getStreamNumber(),
                    payloadData.isUnordered(), byteBuf);
            handler.writeAndFlush(sctpMessage);
        } else {
            handler.writeAndFlush(byteBuf);
        }
    }

    private NettySctpChannelInboundHandlerAdapter checkSocketIsOpen() throws Exception {
        NettySctpChannelInboundHandlerAdapter handler = this.channelHandler;
        if (!started.get() || handler == null)
            throw new Exception(String.format(
                    "Association is not started or underlying sctp/tcp channel is down for Association=%s", this.name));
        return handler;
    }

    @Override
    public ByteBufAllocator getByteBufAllocator() {
        if (this.channelHandler != null)
            return this.channelHandler.channel.alloc();
        else
            return null;
    }

        @Override
    public void acceptAnonymousAssociation(AssociationListener associationListener) throws Exception {
        this.associationListener = associationListener;

        if (this.getAssociationType() != AssociationType.ANONYMOUS_SERVER) {
            throw new UnsupportedOperationException(
                    "Association.acceptAnonymousAssociation() can be applied only for anonymous associations");
        }

        this.start();
    }

        @Override
    public void rejectAnonymousAssociation() {
    }

        @Override
    public void stopAnonymousAssociation() throws Exception {
        if (this.getAssociationType() != AssociationType.ANONYMOUS_SERVER) {
            throw new UnsupportedOperationException(
                    "Association.stopAnonymousAssociation() can be applied only for anonymous associations");
        }

        this.stop();
    }

        @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("NettyAssociationImpl{");
        sb.append("name='").append(name).append('\'');
        sb.append(", ipChannelType=").append(ipChannelType);
        sb.append(", type=").append(type);
        sb.append(", hostAddress='").append(hostAddress).append('\'');
        sb.append(", hostPort=").append(hostPort);
        sb.append(", secondaryHostAddress=").append(secondaryHostAddress);
        sb.append(", peerAddress='").append(peerAddress).append('\'');
        sb.append(", secondaryPeerAddress='").append(secondaryPeerAddress).append('\'');
        sb.append(", peerPort=").append(peerPort);
        sb.append(", serverName='").append(serverName).append('\'');
        sb.append('}');
        return sb.toString();
    }

    /**
     * @param management the management to set
     */
    protected void setManagement(NettySctpManagementImpl management) {
        this.management = management;
    }

    protected void start() throws Exception {
        if (logger.isInfoEnabled()) {
            logger.info(String.format("Management requested to start %s", this));
        }

        if (this.associationListener == null) {
            throw new NullPointerException(String.format("AssociationListener is null for Association=%s", this.name));
        }

        if (started.getAndSet(true)) {
            logger.warn("Association: " + this + " has been already STARTED");
            return;
        }

        if (this.type == AssociationType.CLIENT) {
            this.scheduleConnect();
        }

        if (logger.isInfoEnabled()) {
            if (this.type != AssociationType.ANONYMOUS_SERVER) {
                logger.info(String.format("Started Association=%s", this));
            }
        }

        for (ManagementEventListener lstr : this.management.getManagementEventListeners()) {
            try {
                lstr.onAssociationStarted(this);
            } catch (Throwable ee) {
                logger.error("Exception while invoking onAssociationStarted", ee);
            }
        }
    }

    protected void stop() throws Exception {
        if (logger.isInfoEnabled()) {
            logger.info(String.format("Management requested to stop %s", this));
        }

        if (!started.getAndSet(false)) {
            logger.warn("Association: " + this + " has been already STOPPED");
            return;
        }

        for (ManagementEventListener lstr : this.management.getManagementEventListeners()) {
            try {
                lstr.onAssociationStopped(this);
            } catch (Throwable ee) {
                logger.error("Exception while invoking onAssociationStopped", ee);
            }
        }

        NettySctpChannelInboundHandlerAdapter handler = this.channelHandler;
        if (handler != null) {
            handler.closeChannel();
        }
    }

    protected void read(PayloadData payload) {
        try {
            this.associationListener.onPayload(this, payload);
        } catch (Exception e) {
            logger.error(String.format("Error while calling Listener for Association=%s.Payload=%s", this.name, payload), e);
        }
    }

    protected void markAssociationUp(int maxInboundStreams, int maxOutboundStreams) {
        if (this.server != null) {
            synchronized (this.server.anonymAssociations) {
                this.server.anonymAssociations.add(this);
            }
        }

        if (up.getAndSet(true)) {
            logger.debug("Association: " + this + " has been already marked UP");
            return;
        }

        reconnectCount.set(0);

        this.getAssociationListener().onCommunicationUp(this, maxInboundStreams, maxOutboundStreams);

        for (ManagementEventListener lstr : this.management.getManagementEventListeners()) {
            try {
                lstr.onAssociationUp(this);
            } catch (Throwable ee) {
                logger.error("Exception while invoking onAssociationUp", ee);
            }
        }
    }

    protected void markAssociationDown() {
        if (!up.getAndSet(false)) {
            logger.debug("Association: " + this + " has been already marked DOWN");
            return;
        }

        for (ManagementEventListener lstr : this.management.getManagementEventListeners()) {
            try {
                lstr.onAssociationDown(this);
            } catch (Throwable ee) {
                logger.error("Exception while invoking onAssociationDown", ee);
            }
        }

        this.getAssociationListener().onCommunicationShutdown(this);

        if (this.server != null) {
            synchronized (this.server.anonymAssociations) {
                this.server.anonymAssociations.remove(this);
            }
        }
    }

    protected void scheduleConnect() {
        if (!started.get()) {
            logger.info("Association " + name + " is not started, no need to schedule connect");
            return;
        }

        int connectDelay = this.management.getConnectDelay();
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Scheduling of a channel connection: Association=%s, connectDelay=%d", this,
                    connectDelay));
        }

        if (reconnectCount.getAndIncrement() >= RECONNECT_COUNT_MAX) {
            switchPeerSocketAddress();
        }

        final ScheduledExecutorService loop = this.management.getClientExecutor();
        loop.schedule(() -> connect(), connectDelay, TimeUnit.MILLISECONDS);
    }

    protected void setChannelHandler(NettySctpChannelInboundHandlerAdapter channelHandler) {
        this.channelHandler = channelHandler;
    }

    protected String getSecondaryPeerAddress() {
        return secondaryPeerAddress;
    }

    protected void setSecondaryPeerAddress(String secondaryPeerAddress) {
        if (logger.isInfoEnabled()) {
            logger.debug(String.format("setSecondaryPeerAddress: %s", secondaryPeerAddress));
        }
        this.secondaryPeerAddress = secondaryPeerAddress;
        if (secondaryPeerAddress != null && !secondaryPeerAddress.isEmpty()) {
            try {
                this.secondaryPeerSocketAddress = new InetSocketAddress(InetAddress.getByName(secondaryPeerAddress), peerPort);
            } catch (UnknownHostException e) {
                this.secondaryPeerAddress = null;
            }
        } else {
            this.secondaryPeerAddress = null;
        }
    }

    protected void switchPeerSocketAddress() {
        if (logger.isInfoEnabled()) {
            logger.info("PeerSocketAddress: " + this.peerSocketAddress
                    + ", InitPrimaryHostAddress: " + this.initPrimaryHostAddress
                    + ", InitSecondaryHostAddress: " + this.initSecondaryHostAddress);
        }
        if (this.secondaryPeerSocketAddress != null) {
            this.peerSocketAddress = (this.peerSocketAddress.equals(this.secondaryPeerSocketAddress)
                    ? this.primaryPeerSocketAddress
                    : this.secondaryPeerSocketAddress);

            if (this.secondaryHostAddress != null) {
                this.initPrimaryHostAddress = (this.initPrimaryHostAddress.equals(this.secondaryHostAddress)
                        ? this.hostAddress
                        : this.secondaryHostAddress);
                this.initSecondaryHostAddress = (this.initSecondaryHostAddress.equals(this.secondaryHostAddress)
                        ? this.hostAddress
                        : this.secondaryHostAddress);
            }
        }
        if (logger.isInfoEnabled()) {
            logger.info("PeerSocketAddress: " + this.peerSocketAddress
                    + ", InitPrimaryHostAddress: " + this.initPrimaryHostAddress
                    + ", InitSecondaryHostAddress: " + this.initSecondaryHostAddress);
        }
    }

    protected void connect() {
        if (!started.get() || !connecting.compareAndSet(false, true)) {
            logger.warn("Skipping redundant connect() for association=" + name);
            return;
        }

        if (up.get()) {
            logger.info("Association " + name + " is up, no need to reconnect");
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Initiating connection started: Association=%s", this));
        }

        Bootstrap b;
        InetSocketAddress localAddress;
        try {
            EventLoopGroup group = this.management.getBossGroup();
            b = new Bootstrap();

            b.group(group);
            if (this.ipChannelType == IpChannelType.SCTP) {
                b.channel(NioSctpChannel.class);
                b.option(SctpChannelOption.SCTP_NODELAY, true);
                b.handler(new NettySctpClientChannelInitializer(this));
            } else {
                b.channel(NioSocketChannel.class);
                b.option(ChannelOption.TCP_NODELAY, true);
                b.handler(new NettyTcpClientChannelInitializer(this));
            }

            localAddress = new InetSocketAddress(this.initPrimaryHostAddress, this.hostPort);
        } catch (Exception e) {
            logger.error(String.format("Exception while creating connection for Association=%s", this.getName()), e);
            this.scheduleConnect();
            return;
        }

        // Bind the client channel.
        try {
            ChannelFuture bindFuture = b.bind(localAddress).sync();
            Channel channel = bindFuture.channel();

            if (this.ipChannelType == IpChannelType.SCTP) {
                // Get the underlying sctp channel
                SctpChannel sctpChannel = (SctpChannel) channel;

                // Bind the secondary address.
                // Please note that, bindAddress in the client channel should be done before connecting if you have not
                // enable Dynamic Address Configuration. See net.sctp.addip_enable kernel param
                if (this.secondaryHostAddress != null) {
                    sctpChannel.bindAddress(InetAddress.getByName(initSecondaryHostAddress)).sync();
                }
            }

            // Finish connect
            bindFuture.channel().connect(this.peerSocketAddress);
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Initiating connection scheduled [%d]: Association=%s remoteAddress=%s",
                        reconnectCount.get(), this, peerSocketAddress));
            }
        } catch (Exception e) {
            logger.error(String.format("Exception while finishing connection for Association=%s", this.getName()), e);
        } finally {
            connecting.set(false);
        }
    }

    /**
     * XML Serialization/Deserialization
     */
    protected static final XMLFormat<NettyAssociationImpl> ASSOCIATION_XML = new XMLFormat<NettyAssociationImpl>(
            NettyAssociationImpl.class) {

        @SuppressWarnings("unchecked")
        @Override
        public void read(InputElement xml, NettyAssociationImpl association) throws XMLStreamException {
            association.name = xml.getAttribute(NAME, "");
            association.type = AssociationType.getAssociationType(xml.getAttribute(ASSOCIATION_TYPE, ""));
            association.hostAddress = xml.getAttribute(HOST_ADDRESS, "");
            association.hostPort = xml.getAttribute(HOST_PORT, 0);

            association.peerAddress = xml.getAttribute(PEER_ADDRESS, "");
            association.peerPort = xml.getAttribute(PEER_PORT, 0);
            association.secondaryPeerAddress = xml.getAttribute(SECONDARY_PEER_ADDRESS, null);

            association.serverName = xml.getAttribute(SERVER_NAME, "");
            association.ipChannelType = IpChannelType
                    .getInstance(xml.getAttribute(IPCHANNEL_TYPE, IpChannelType.SCTP.getCode()));
            if (association.ipChannelType == null)
                association.ipChannelType = IpChannelType.SCTP;

            int extraHostAddressesSize = xml.getAttribute(EXTRA_HOST_ADDRESS_SIZE, 0);
            association.extraHostAddresses = new String[extraHostAddressesSize];

            for (int i = 0; i < extraHostAddressesSize; i++) {
                association.extraHostAddresses[i] = xml.get(EXTRA_HOST_ADDRESS, String.class);
            }

            try {
                association.initDerivedFields();
            } catch (IOException e) {
                logger.error("Unable to load association from XML: error while calculating derived fields", e);
            }
        }

        @Override
        public void write(NettyAssociationImpl association, OutputElement xml)
                throws XMLStreamException {
            xml.setAttribute(NAME, association.name);
            xml.setAttribute(ASSOCIATION_TYPE, association.type.getType());
            xml.setAttribute(HOST_ADDRESS, association.hostAddress);
            xml.setAttribute(HOST_PORT, association.hostPort);

            xml.setAttribute(PEER_ADDRESS, association.peerAddress);
            xml.setAttribute(PEER_PORT, association.peerPort);
            if (association.secondaryPeerAddress != null && !association.secondaryPeerAddress.isEmpty()) {
                xml.setAttribute(SECONDARY_PEER_ADDRESS, association.secondaryPeerAddress);
            }

            xml.setAttribute(SERVER_NAME, association.serverName);
            xml.setAttribute(IPCHANNEL_TYPE, association.ipChannelType.getCode());

            xml.setAttribute(EXTRA_HOST_ADDRESS_SIZE,
                    association.extraHostAddresses != null ? association.extraHostAddresses.length : 0);
            if (association.extraHostAddresses != null) {
                for (String s : association.extraHostAddresses) {
                    xml.add(s, EXTRA_HOST_ADDRESS, String.class);
                }
            }
        }
    };
}
