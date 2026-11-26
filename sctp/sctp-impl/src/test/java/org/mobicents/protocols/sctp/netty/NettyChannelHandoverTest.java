package org.mobicents.protocols.sctp.netty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.PayloadData;

import com.sun.nio.sctp.AssociationChangeNotification;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * A peer that reconnects to a server association before its old channel is torn down: the user must end up
 * consistent ("up" with the transport up), whatever the order of the old channel's loss and the new COMM_UP.
 * Only the event order is driven, no kernel SCTP; RealSctpHandoverStressTest runs it on kernel SCTP. The handlers'
 * ctx.close() is held back, as on a real event loop, where channelInactive comes in a later task.
 */
public class NettyChannelHandoverTest {

    private NettySctpManagementImpl management;
    private final AtomicInteger ups = new AtomicInteger();
    private final AtomicInteger losts = new AtomicInteger();
    private final AtomicInteger shutdowns = new AtomicInteger();
    private final List<String> events = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        management = new NettySctpManagementImpl("channel-handover-test");
        management.setPersistDir(Files.createTempDirectory("channel-handover-test").toString());
        management.start();
        management.removeAllResourses();
    }

    @After
    public void tearDown() throws Exception {
        management.removeAllResourses();
        management.stop();
    }

    /** The order seen on kernel SCTP: the old channel's COMM_LOST, then the peer is back on a new channel. */
    @Test
    public void lossThenReconnectBringsUserUpAgain() throws Exception {
        NettyServerImpl server = (NettyServerImpl) management.addServer("srv", "127.0.0.1", 12357);
        NettyAssociationImpl association = (NettyAssociationImpl) management.addServerAssociation("127.0.0.1", 12358,
                "srv", "server-assoc");
        association.setAssociationListener(new Counting());
        NettySctpServerHandler old = handler(server, association);
        ChannelHandlerContext oldCtx = ctx(old);
        association.setChannelHandler(old);
        old.userEventTriggered(oldCtx, notification(AssociationChangeNotification.AssocChangeEvent.COMM_UP, 16, 16));

        old.userEventTriggered(oldCtx, notification(AssociationChangeNotification.AssocChangeEvent.COMM_LOST, 0, 0));
        NettySctpServerHandler current = handler(server, association);   // before the old channel is torn down
        association.setChannelHandler(current);
        current.userEventTriggered(ctx(current), notification(AssociationChangeNotification.AssocChangeEvent.COMM_UP, 4, 2));
        old.channelInactive(oldCtx);

        assertTrue(association.isUp());
        assertEquals(1, losts.get());
        assertEquals(1, shutdowns.get());
        assertEquals("the new channel's COMM_UP reaches the user", 2, ups.get());
        assertEquals("[up 16/16, lost, down, up 4/2]", events.toString());
    }

    /**
     * The rarer order: the new channel takes over before the old one's COMM_LOST. The takeover reports the old one
     * down, so the new COMM_UP reaches the user with its own stream counts; the stale loss is ignored.
     */
    @Test
    public void takeoverBeforeOldLossBringsUserDownThenUp() throws Exception {
        NettyServerImpl server = (NettyServerImpl) management.addServer("srv", "127.0.0.1", 12357);
        NettyAssociationImpl association = (NettyAssociationImpl) management.addServerAssociation("127.0.0.1", 12358,
                "srv", "server-assoc");
        association.setAssociationListener(new Counting());
        NettySctpServerHandler old = handler(server, association);
        ChannelHandlerContext oldCtx = ctx(old);
        association.setChannelHandler(old);
        old.userEventTriggered(oldCtx, notification(AssociationChangeNotification.AssocChangeEvent.COMM_UP, 16, 16));

        NettySctpServerHandler current = handler(server, association);
        association.setChannelHandler(current);
        current.userEventTriggered(ctx(current), notification(AssociationChangeNotification.AssocChangeEvent.COMM_UP, 4, 2));
        old.userEventTriggered(oldCtx, notification(AssociationChangeNotification.AssocChangeEvent.COMM_LOST, 0, 0));
        old.channelInactive(oldCtx);

        assertTrue(association.isUp());
        assertEquals("a stale channel's loss is not reported", 0, losts.get());
        assertEquals(1, shutdowns.get());
        assertEquals(2, ups.get());
        assertEquals("[up 16/16, down, up 4/2]", events.toString());
    }

    /** COMM_LOST on a client association's own channel reaches the user, then the association is down. */
    @Test
    public void clientLossReachesUser() throws Exception {
        NettyAssociationImpl association = (NettyAssociationImpl) management.addAssociation("127.0.0.1", 12359,
                "127.0.0.1", 12360, "client-assoc");
        association.setAssociationListener(new Counting());
        NettySctpClientHandler handler = new NettySctpClientHandler(association);
        ChannelHandlerContext ctx = ctx(handler);
        association.setChannelHandler(handler); // what channelActive does
        handler.userEventTriggered(ctx, notification(AssociationChangeNotification.AssocChangeEvent.COMM_UP, 16, 16));

        handler.userEventTriggered(ctx, notification(AssociationChangeNotification.AssocChangeEvent.COMM_LOST, 0, 0));

        assertFalse(association.isUp());
        assertEquals("[up 16/16, lost, down]", events.toString());
    }

    private static NettySctpServerHandler handler(NettyServerImpl server, NettyAssociationImpl association) {
        NettySctpServerHandler h = new NettySctpServerHandler(server, null);
        h.association = association;
        return h;
    }

    private static ChannelHandlerContext ctx(NettySctpChannelInboundHandlerAdapter handler) {
        EmbeddedChannel channel = new EmbeddedChannel();
        // EmbeddedChannel would run channelInactive inside ctx.close(); the test fires it itself, later
        channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
            @Override
            public void close(ChannelHandlerContext ctx, ChannelPromise promise) {
                promise.setSuccess();
            }
        });
        channel.pipeline().addLast(handler);
        return channel.pipeline().context(handler);
    }

    private static AssociationChangeNotification notification(final AssociationChangeNotification.AssocChangeEvent event,
            final int maxIn, final int maxOut) {
        return new AssociationChangeNotification() {
            @Override
            public com.sun.nio.sctp.Association association() {
                return new com.sun.nio.sctp.Association(1, maxIn, maxOut) {
                };
            }

            @Override
            public AssocChangeEvent event() {
                return event;
            }
        };
    }

    private class Counting implements AssociationListener {
        public void onCommunicationUp(Association association, int maxInboundStreams, int maxOutboundStreams) {
            ups.incrementAndGet();
            events.add("up " + maxInboundStreams + "/" + maxOutboundStreams);
        }

        public void onCommunicationShutdown(Association association) {
            shutdowns.incrementAndGet();
            events.add("down");
        }

        public void onCommunicationLost(Association association) {
            losts.incrementAndGet();
            events.add("lost");
        }

        public void onCommunicationRestart(Association association) {
        }

        public void onPayload(Association association, PayloadData payloadData) {
        }

        public void inValidStreamId(PayloadData payloadData) {
        }
    }
}
