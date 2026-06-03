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

import io.netty.channel.ChannelHandlerContext;

import java.net.InetSocketAddress;
import java.util.Set;

import io.netty.channel.sctp.SctpChannel;
import org.apache.log4j.Logger;
import org.mobicents.protocols.api.IpChannelType;

/**
 * Handler implementation for the SCTP echo client. It initiates the ping-pong traffic between the echo client and server by
 * sending the first message to the server.
 * 
 * @author <a href="mailto:amit.bhayani@telestax.com">Amit Bhayani</a>
 * 
 */
public class NettySctpClientHandler extends NettySctpChannelInboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(NettySctpClientHandler.class);

    /**
     * Creates a client-side handler.
     */
    public NettySctpClientHandler(NettyAssociationImpl nettyAssociationImpl) {
        this.association = nettyAssociationImpl;
    }

    @Override
    public void channelUnregistered(final ChannelHandlerContext ctx) throws Exception {
        logger.warn(String.format("ChannelUnregistered event: association=%s", association));
        this.association.setChannelHandler(null);

        this.association.scheduleConnect();
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("channelRegistered event: association=%s", this.association));
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("channelActive event: association=%s", this.association));
        }

        this.ctx = ctx;
        this.channel = ctx.channel();
        this.association.setChannelHandler(this);

        String host = null;
        int port = 0;
        InetSocketAddress sockAdd = ((InetSocketAddress) channel.remoteAddress());
        if (sockAdd != null) {
            host = sockAdd.getAddress().getHostAddress();
            port = sockAdd.getPort();
        }

        // detect and save secondary peer address for SCTP associations
        if (this.association.getIpChannelType() == IpChannelType.SCTP &&
                this.association.getSecondaryPeerAddress() == null) {
            SctpChannel sctpChannel = (SctpChannel) ctx.channel();
            Set<InetSocketAddress> inetSocketAddresses = sctpChannel.allRemoteAddresses();
            for (InetSocketAddress inetSocketAddress : inetSocketAddresses) {
                if (!inetSocketAddress.equals(channel.remoteAddress())) {
                    this.association.setSecondaryPeerAddress(inetSocketAddress.getAddress().getHostAddress());
                    break;
                }
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info(String.format("Association=%s connected to host=%s port=%d", association.getName(), host, port));
        }

        if (association.getIpChannelType() == IpChannelType.TCP) {
            this.association.markAssociationUp(1, 1);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        logger.error("Exception Caught for Association: " + this.association.getName(), cause);
        ctx.close();
    }

}
