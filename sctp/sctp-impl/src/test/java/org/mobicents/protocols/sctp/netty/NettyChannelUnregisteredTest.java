package org.mobicents.protocols.sctp.netty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.PayloadData;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;

/**
 * A channel that is unregistered without channelInactive having fired (it died before becoming active) must still
 * mark its association down; otherwise the association stays "up" and reconnect / the next COMM_UP is refused.
 * A stale channel whose association has already been taken over by a newer channel must leave it alone.
 * The handlers are driven directly, so no kernel SCTP is needed.
 */
public class NettyChannelUnregisteredTest {

    private NettySctpManagementImpl management;
    private final AtomicInteger shutdowns = new AtomicInteger();

    @Before
    public void setUp() throws Exception {
        management = new NettySctpManagementImpl("channel-unregistered-test");
        management.setPersistDir(Files.createTempDirectory("channel-unregistered-test").toString());
        management.start();
        management.removeAllResourses();
    }

    @After
    public void tearDown() throws Exception {
        management.removeAllResourses();
        management.stop();
    }

    private volatile CountDownLatch shutdownEntered;
    private volatile CountDownLatch shutdownRelease;

    private NettyAssociationImpl up(NettyAssociationImpl association) {
        association.setAssociationListener(new CountingListener());
        association.markAssociationUp(1, 1);
        assertTrue(association.isUp());
        return association;
    }

    private static void unregisterWithoutInactive(NettySctpChannelInboundHandlerAdapter handler) throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(handler);
        ChannelHandlerContext ctx = channel.pipeline().context(handler);
        handler.channelUnregistered(ctx);
    }

    @Test
    public void clientHandlerMarksAssociationDown() throws Exception {
        NettyAssociationImpl association = up((NettyAssociationImpl) management.addAssociation("127.0.0.1", 12345,
                "127.0.0.1", 12346, "client-assoc", IpChannelType.SCTP, null));
        NettySctpClientHandler handler = new NettySctpClientHandler(association);
        association.setChannelHandler(handler);

        unregisterWithoutInactive(handler);

        assertFalse(association.isUp());
        assertEquals(1, shutdowns.get());
    }

    @Test
    public void staleClientHandlerLeavesNewerChannelAlone() throws Exception {
        NettyAssociationImpl association = up((NettyAssociationImpl) management.addAssociation("127.0.0.1", 12345,
                "127.0.0.1", 12346, "client-assoc", IpChannelType.SCTP, null));
        NettySctpClientHandler current = new NettySctpClientHandler(association);
        association.setChannelHandler(current);

        unregisterWithoutInactive(new NettySctpClientHandler(association));

        assertTrue(association.isUp());
        assertEquals(0, shutdowns.get());
        // the newer handler still owns it: its own close takes the association down
        association.channelClosed(current);
        assertFalse(association.isUp());
    }

    @Test
    public void serverHandlerMarksAssociationDown() throws Exception {
        NettyServerImpl server = (NettyServerImpl) management.addServer("srv", "127.0.0.1", 12347);
        NettyAssociationImpl association = up((NettyAssociationImpl) management.addServerAssociation("127.0.0.1", 12348,
                "srv", "server-assoc"));
        NettySctpServerHandler handler = new NettySctpServerHandler(server, management);
        handler.association = association;
        association.setChannelHandler(handler);

        unregisterWithoutInactive(handler);

        assertFalse(association.isUp());
        assertEquals(1, shutdowns.get());
    }

    @Test
    public void staleServerHandlerLeavesNewerChannelAlone() throws Exception {
        NettyServerImpl server = (NettyServerImpl) management.addServer("srv", "127.0.0.1", 12347);
        NettyAssociationImpl association = up((NettyAssociationImpl) management.addServerAssociation("127.0.0.1", 12348,
                "srv", "server-assoc"));
        // the peer reconnected: the new channel's handler owns the association before the old one unregisters
        NettySctpServerHandler stale = new NettySctpServerHandler(server, management);
        stale.association = association;
        NettySctpServerHandler current = new NettySctpServerHandler(server, management);
        current.association = association;
        association.setChannelHandler(current);

        unregisterWithoutInactive(stale);

        assertTrue(association.isUp());
        assertEquals(0, shutdowns.get());
        // the newer handler still owns it: its own close takes the association down
        association.channelClosed(current);
        assertFalse(association.isUp());
    }

    /**
     * The old channel's ownership check, detach and down transition must be one step: a new channel trying to take over
     * meanwhile waits until the teardown is finished, so it cannot be marked up and then knocked down by the old one.
     */
    @Test
    public void newChannelCannotTakeOverDuringTeardown() throws Exception {
        NettyServerImpl server = (NettyServerImpl) management.addServer("srv", "127.0.0.1", 12347);
        NettyAssociationImpl association = up((NettyAssociationImpl) management.addServerAssociation("127.0.0.1", 12348,
                "srv", "server-assoc"));
        NettySctpServerHandler old = new NettySctpServerHandler(server, management);
        old.association = association;
        association.setChannelHandler(old);
        NettySctpServerHandler current = new NettySctpServerHandler(server, management);
        current.association = association;

        shutdownEntered = new CountDownLatch(1);
        shutdownRelease = new CountDownLatch(1);
        Thread teardown = new Thread(() -> {
            try {
                unregisterWithoutInactive(old);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        teardown.start();
        assertTrue(shutdownEntered.await(5, TimeUnit.SECONDS)); // old channel is mid-teardown

        CountDownLatch tookOver = new CountDownLatch(1);
        Thread takeover = new Thread(() -> {
            association.setChannelHandler(current);
            association.markAssociationUp(1, 1);
            tookOver.countDown();
        });
        takeover.start();
        assertFalse("new channel took over while the old one was still tearing down",
                tookOver.await(300, TimeUnit.MILLISECONDS));

        shutdownRelease.countDown();
        teardown.join(5000);
        assertTrue(tookOver.await(5, TimeUnit.SECONDS));
        takeover.join(5000);

        assertTrue(association.isUp());
        assertEquals(1, shutdowns.get());
    }

    private class CountingListener implements AssociationListener {
        public void onCommunicationUp(Association association, int maxInboundStreams, int maxOutboundStreams) {
        }

        public void onCommunicationShutdown(Association association) {
            shutdowns.incrementAndGet();
            if (shutdownEntered != null) {
                shutdownEntered.countDown();
                try {
                    shutdownRelease.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        public void onCommunicationLost(Association association) {
        }

        public void onCommunicationRestart(Association association) {
        }

        public void onPayload(Association association, PayloadData payloadData) {
        }

        public void inValidStreamId(PayloadData payloadData) {
        }
    }
}
