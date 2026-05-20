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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.ServerChannel;
import io.netty.channel.sctp.SctpServerChannel;
import io.netty.channel.sctp.nio.NioSctpServerChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import javolution.util.FastList;
import javolution.xml.XMLFormat;
import javolution.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.Server;

/**
 * @author <a href="mailto:amit.bhayani@telestax.com">Amit Bhayani</a>
 * 
 */
public class NettyServerImpl implements Server {

    private static final Logger logger = Logger.getLogger(NettyServerImpl.class.getName());

    private static final String NAME = "name";
    private static final String HOST_ADDRESS = "hostAddress";
    private static final String HOST_PORT = "hostPort";
    private static final String IPCHANNEL_TYPE = "ipChannelType";

    private static final String ASSOCIATIONS = "associations";
    private static final String EXTRA_HOST_ADDRESS = "extraHostAddress";
    private static final String ACCEPT_ANONYMOUS_CONNECTIONS = "acceptAnonymousConnections";
    private static final String MAX_CONCURRENT_CONNECTIONS_COUNT = "maxConcurrentConnectionsCount";

    private static final String STARTED = "started";

    private static final String EXTRA_HOST_ADDRESS_SIZE = "extraHostAddresseSize";

    private String name;
    private String hostAddress;
    private int hostport;
    private volatile boolean started = false;
    private IpChannelType ipChannelType;
    private boolean acceptAnonymousConnections;
    private int maxConcurrentConnectionsCount;
    private String[] extraHostAddresses;

    private NettySctpManagementImpl management = null;

    protected FastList<String> associations = new FastList<>();
    protected FastList<Association> anonymAssociations = new FastList<>();

    // Netty declarations
    // The channel on which we'll accept connections
    private SctpServerChannel serverChannelSctp;
    private NioServerSocketChannel serverChannelTcp;

    /**
     * 
     */
    public NettyServerImpl() {
    }

    /**
     * @param name
     * @param hostAddress
     * @param hostport
     * @param ipChannelType
     * @param acceptAnonymousConnections
     * @param maxConcurrentConnectionsCount
     * @param extraHostAddresses
     * @throws IOException
     */
    public NettyServerImpl(String name, String hostAddress, int hostport, IpChannelType ipChannelType,
            boolean acceptAnonymousConnections, int maxConcurrentConnectionsCount, String[] extraHostAddresses)
            throws IOException {
        this.name = name;
        this.hostAddress = hostAddress;
        this.hostport = hostport;
        this.ipChannelType = ipChannelType;
        this.acceptAnonymousConnections = acceptAnonymousConnections;
        this.maxConcurrentConnectionsCount = maxConcurrentConnectionsCount;
        this.extraHostAddresses = extraHostAddresses;
    }

        @Override
    public IpChannelType getIpChannelType() {
        return this.ipChannelType;
    }

        @Override
    public boolean isAcceptAnonymousConnections() {
        return acceptAnonymousConnections;
    }

        @Override
    public int getMaxConcurrentConnectionsCount() {
        return this.maxConcurrentConnectionsCount;
    }

        @Override
    public void setMaxConcurrentConnectionsCount(int val) {
        this.maxConcurrentConnectionsCount = val;
    }

        @Override
    public String getName() {
        return this.name;
    }

        @Override
    public String getHostAddress() {
        return this.hostAddress;
    }

        @Override
    public int getHostport() {
        return this.hostport;
    }

        @Override
    public String[] getExtraHostAddresses() {
        return this.extraHostAddresses;
    }

        @Override
    public boolean isStarted() {
        return this.started;
    }

        @Override
    public List<String> getAssociations() {
        return this.associations.unmodifiable();
    }

        @Override
    public List<Association> getAnonymAssociations() {
        return this.anonymAssociations.unmodifiable();
    }

    protected ServerChannel getIpChannel() {
        if (this.ipChannelType == IpChannelType.SCTP)
            return this.serverChannelSctp;
        else
            return this.serverChannelTcp;
    }

    /**
     * @param management the management to set
     */
    protected void setManagement(NettySctpManagementImpl management) {
        this.management = management;
    }

    protected void start() throws Exception {
        this.initSocket();
        this.started = true;

        if (logger.isInfoEnabled()) {
            logger.info(String.format("Started Server=%s", this.name));
        }
    }

    protected void stop() throws Exception {
        FastList<String> tempAssociations = associations;
        for (FastList.Node<String> n = tempAssociations.head(), end = tempAssociations.tail(); (n = n.getNext()) != end;) {
            String assocName = n.getValue();
            Association associationTemp = this.management.getAssociation(assocName);
            if (associationTemp.isStarted()) {
                throw new Exception(String.format("Stop all the associations first. Association=%s is still started",
                        associationTemp.getName()));
            }
        }

        synchronized (this.anonymAssociations) {
            // stopping all anonymous associations
            for (Association ass : this.anonymAssociations) {
                ass.stopAnonymousAssociation();
            }
            this.anonymAssociations.clear();
        }

        this.started = false;

        if (logger.isInfoEnabled()) {
            logger.info(String.format("Stopped Server=%s", this.name));
        }

        // Stop underlying channel and wait till its done
        if (this.getIpChannel() != null) {
            try {
                this.getIpChannel().close().sync();
            } catch (Exception e) {
                logger.warn(String.format("Error while stopping the Server=%s", this.name), e);
            }
        }
    }

    private void initSocket() throws Exception {
        ServerBootstrap b = new ServerBootstrap();
        b.group(this.management.getBossGroup(), this.management.getWorkerGroup());
        if (this.ipChannelType == IpChannelType.SCTP) {
            b.channel(NioSctpServerChannel.class);
            b.option(ChannelOption.SO_BACKLOG, 100);
            b.childHandler(new NettySctpServerChannelInitializer(this, this.management));
        } else {
            b.channel(NioServerSocketChannel.class);
            b.option(ChannelOption.SO_BACKLOG, 100);
            b.childHandler(new NettyTcpServerChannelInitializer(this, this.management));
        }
        b.handler(new LoggingHandler(LogLevel.INFO));

        InetSocketAddress localAddress = new InetSocketAddress(this.hostAddress, this.hostport);

        // Bind the server to primary address.
        ChannelFuture channelFuture = b.bind(localAddress).sync();

        // Get the underlying sctp channel
        if (this.ipChannelType == IpChannelType.SCTP) {
            this.serverChannelSctp = (SctpServerChannel) channelFuture.channel();

            // Bind the secondary address.
            // Please note that, bindAddress in the client channel should be done before connecting if you have not
            // enable Dynamic Address Configuration. See net.sctp.addip_enable kernel param
            if (this.extraHostAddresses != null) {
                for (String localSecondaryAddress : this.extraHostAddresses) {
                    InetAddress localSecondaryInetAddress = InetAddress.getByName(localSecondaryAddress);

                    channelFuture = this.serverChannelSctp.bindAddress(localSecondaryInetAddress).sync();
                }
            }

            if (logger.isInfoEnabled()) {
                logger.info(String.format("SctpServerChannel bound to=%s ", this.serverChannelSctp.allLocalAddresses()));
            }
        } else {
            this.serverChannelTcp = (NioServerSocketChannel) channelFuture.channel();

            if (logger.isInfoEnabled()) {
                logger.info(String.format("ServerSocketChannel bound to=%s ", this.serverChannelTcp.localAddress()));
            }
        }
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();

        sb.append("Server [name=").append(this.name).append(", started=").append(this.started).append(", hostAddress=").append(this.hostAddress)
                .append(", hostPort=").append(hostport).append(", ipChannelType=").append(ipChannelType).append(", acceptAnonymousConnections=")
                .append(this.acceptAnonymousConnections).append(", maxConcurrentConnectionsCount=").append(this.maxConcurrentConnectionsCount)
                .append(", associations(anonymous does not included)=[");

        for (FastList.Node<String> n = this.associations.head(), end = this.associations.tail(); (n = n.getNext()) != end;) {
            sb.append(n.getValue());
            sb.append(", ");
        }

        sb.append("], extraHostAddress=[");

        if (this.extraHostAddresses != null) {
            for (String extraHostAddress : this.extraHostAddresses) {
                sb.append(extraHostAddress);
                sb.append(", ");
            }
        }

        sb.append("]]");

        return sb.toString();
    }

    /**
     * XML Serialization/Deserialization
     */
    protected static final XMLFormat<NettyServerImpl> SERVER_XML = new XMLFormat<NettyServerImpl>(NettyServerImpl.class) {

        @SuppressWarnings("unchecked")
        @Override
        public void read(InputElement xml, NettyServerImpl server) throws XMLStreamException {
            server.name = xml.getAttribute(NAME, "");
            server.started = xml.getAttribute(STARTED, false);
            server.hostAddress = xml.getAttribute(HOST_ADDRESS, "");
            server.hostport = xml.getAttribute(HOST_PORT, 0);
            server.ipChannelType = IpChannelType.getInstance(xml.getAttribute(IPCHANNEL_TYPE, IpChannelType.SCTP.getCode()));
            if (server.ipChannelType == null)
                throw new XMLStreamException("Bad value for server.ipChannelType");

            server.acceptAnonymousConnections = xml.getAttribute(ACCEPT_ANONYMOUS_CONNECTIONS, false);
            server.maxConcurrentConnectionsCount = xml.getAttribute(MAX_CONCURRENT_CONNECTIONS_COUNT, 0);

            int extraHostAddressesSize = xml.getAttribute(EXTRA_HOST_ADDRESS_SIZE, 0);
            server.extraHostAddresses = new String[extraHostAddressesSize];

            for (int i = 0; i < extraHostAddressesSize; i++) {
                server.extraHostAddresses[i] = xml.get(EXTRA_HOST_ADDRESS, String.class);
            }

            server.associations = xml.get(ASSOCIATIONS, FastList.class);
        }

        @Override
        public void write(NettyServerImpl server, OutputElement xml) throws XMLStreamException {
            xml.setAttribute(NAME, server.name);
            xml.setAttribute(STARTED, server.started);
            xml.setAttribute(HOST_ADDRESS, server.hostAddress);
            xml.setAttribute(HOST_PORT, server.hostport);
            xml.setAttribute(IPCHANNEL_TYPE, server.ipChannelType.getCode());
            xml.setAttribute(ACCEPT_ANONYMOUS_CONNECTIONS, server.acceptAnonymousConnections);
            xml.setAttribute(MAX_CONCURRENT_CONNECTIONS_COUNT, server.maxConcurrentConnectionsCount);

            xml.setAttribute(EXTRA_HOST_ADDRESS_SIZE, server.extraHostAddresses != null ? server.extraHostAddresses.length : 0);
            if (server.extraHostAddresses != null) {
                for (String s : server.extraHostAddresses) {
                    xml.add(s, EXTRA_HOST_ADDRESS, String.class);
                }
            }
            xml.add(server.associations, ASSOCIATIONS, FastList.class);
        }
    };
}
