package org.mobicents.protocols.sctp.netty;

import static org.junit.Assert.assertEquals;
import static org.mobicents.protocols.sctp.netty.SctpTestSupport.freePort;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.Assume;
import org.junit.Test;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.AssociationListener;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.PayloadData;

import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpStandardSocketOptions;

/**
 * On kernel SCTP: a server association must not end up with the transport up and the user without a COMM_UP when
 * the peer's new channel arrives before the old channel's COMM_LOST / close is handled (2.1.0.0: 11-20% of abort
 * cycles; 2.0.1.0: under 1%). The peer
 * (fixed local port, as a provisioned server association requires) drops its association and reconnects at once,
 * many times; after each reconnect the user must end on "up" and a payload sent on the association must reach the
 * new peer channel. Every cycle that does not is reported. Needs kernel
 * SCTP and libsctp (Linux), skipped elsewhere; -Dhandover.cycles sets the number of cycles per test (default 100).
 */
public class RealSctpHandoverStressTest {

    private static final String HOST = "127.0.0.1";
    private static final int CYCLES = Integer.getInteger("handover.cycles", 100);

    private final List<String> events = new CopyOnWriteArrayList<>();
    private int connectRetries;

    @Test
    public void abortAndReconnect() throws Exception {
        run(true);
    }

    @Test
    public void gracefulCloseAndReconnect() throws Exception {
        run(false);
    }

    private void run(boolean abort) throws Exception {
        Assume.assumeTrue("kernel SCTP needed", NettySctpTransferTest.checkSctpEnabled());
        String mode = abort ? "abort" : "graceful";
        NettySctpManagementImpl management = new NettySctpManagementImpl("handover-" + mode, 1, 4, 1);
        management.setPersistDir("target");
        management.start();
        management.removeAllResourses();
        int serverPort = freePort();
        int peerPort = freePort();
        List<String> anomalies = new ArrayList<>();
        try {
            management.addServer("srv", HOST, serverPort, IpChannelType.SCTP, false, 0, null);
            NettyAssociationImpl association = (NettyAssociationImpl) management.addServerAssociation(HOST, peerPort,
                    "srv", "assoc");
            association.setAssociationListener(new Recording());
            management.startServer("srv");
            management.startAssociation("assoc");

            int seen = events.size();
            SctpChannel peer = connect(peerPort, serverPort);
            for (int cycle = 0; cycle <= CYCLES; cycle++) {
                if (!settledUp(association, seen)) {
                    anomalies.add("cycle " + cycle + ": isUp=" + association.isUp() + " events since connect="
                            + since(seen));
                } else if (!delivered(association, peer, (byte) cycle)) {
                    anomalies.add("cycle " + cycle + ": up, but a payload did not reach the new peer channel");
                }
                if (cycle == CYCLES)
                    break;
                seen = events.size();
                if (abort)
                    peer.setOption(SctpStandardSocketOptions.SO_LINGER, 0);
                peer.close();
                peer = connect(peerPort, serverPort); // at once, from the same port
            }
            peer.close();
        } finally {
            management.removeAllResourses();
            management.stop();
        }
        int ups = count("up"), downs = events.size() - ups;
        System.out.println("HANDOVER " + mode + ": cycles=" + CYCLES + " ups=" + ups + " downs=" + downs
                + " restarts=" + count("restart") + " connectRetries=" + connectRetries + " anomalies=" + anomalies.size());
        for (String a : anomalies)
            System.out.println("HANDOVER " + mode + " ANOMALY " + a);
        assertEquals("cycles where the user did not end up 'up': " + anomalies, 0, anomalies.size());
    }

    /**
     * Since this connect the user got a new "up" (or a "restart" of the same association) as its last event and the
     * transport is up, held for 300 ms so a late down of the old channel is seen; within 3 s.
     */
    private boolean settledUp(NettyAssociationImpl association, int from) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (endsUp(association, from)) {
                Thread.sleep(300);
                if (endsUp(association, from))
                    return true;
            }
            Thread.sleep(5);
        }
        return false;
    }

    private boolean endsUp(NettyAssociationImpl association, int from) {
        List<String> since = since(from);
        if (!association.isUp() || since.isEmpty())
            return false;
        String last = since.get(since.size() - 1);
        return last.equals("up") || last.equals("restart");
    }

    // a copy: a callback may append while the events are inspected
    private List<String> since(int from) {
        List<String> all = new ArrayList<>(events);
        return all.subList(from, all.size());
    }

    // isUp() is the association's flag; this proves the association sends on the peer's current channel
    private static boolean delivered(NettyAssociationImpl association, SctpChannel peer, byte mark) throws Exception {
        association.send(new PayloadData(1, new byte[] { mark }, true, false, 0, 0));
        ByteBuffer buf = ByteBuffer.allocate(64);
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            buf.clear();
            MessageInfo info = peer.receive(buf, null, null);
            if (info != null && buf.position() == 1 && buf.get(0) == mark)
                return true;
            Thread.sleep(5);
        }
        return false;
    }

    private SctpChannel connect(int localPort, int serverPort) throws Exception {
        Exception last = null;
        for (int i = 0; i < 200; i++) {
            SctpChannel ch = SctpChannel.open();
            try {
                ch.bind(new InetSocketAddress(HOST, localPort));
                ch.configureBlocking(false);
                ch.connect(new InetSocketAddress(HOST, serverPort));
                long deadline = System.currentTimeMillis() + 3000; // the kernel's own INIT retries take minutes
                while (!ch.finishConnect()) {
                    if (System.currentTimeMillis() > deadline)
                        throw new java.net.ConnectException("no COMM_UP within 3 s");
                    Thread.sleep(5);
                }
                connectRetries += i;
                return ch;
            } catch (Exception e) {
                
                last = e;
                ch.close();
                Thread.sleep(10);
            }
        }
        throw last;
    }

    private int count(String e) {
        int n = 0;
        for (String s : events)
            if (s.equals(e))
                n++;
        return n;
    }

    private class Recording implements AssociationListener {
        public void onCommunicationUp(Association a, int in, int out) {
            events.add("up");
        }

        public void onCommunicationShutdown(Association a) {
            events.add("shutdown");
        }

        public void onCommunicationLost(Association a) {
            events.add("lost");
        }

        public void onCommunicationRestart(Association a) {
            events.add("restart");
        }

        public void onPayload(Association a, PayloadData p) {
        }

        public void inValidStreamId(PayloadData p) {
        }
    }
}
