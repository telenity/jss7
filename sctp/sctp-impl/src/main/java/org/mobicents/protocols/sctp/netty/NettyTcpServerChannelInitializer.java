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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

/**
 * @author <a href="mailto:amit.bhayani@telestax.com">Amit Bhayani</a>
 * @author Sergey Vetyutnev
 */
public class NettyTcpServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final NettyServerImpl nettyServerImpl;
    private final NettySctpManagementImpl sctpManagementImpl;

    protected NettyTcpServerChannelInitializer(NettyServerImpl nettyServerImpl, NettySctpManagementImpl sctpManagementImpl) {
        this.nettyServerImpl = nettyServerImpl;
        this.sctpManagementImpl = sctpManagementImpl;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast(new NettySctpServerHandler(this.nettyServerImpl, this.sctpManagementImpl));
    }

}
