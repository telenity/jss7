package org.mobicents.protocols.sctp.netty;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.function.BooleanSupplier;

public final class SctpTestSupport {

    private static final long POLL_INTERVAL_MS = 50;

    private SctpTestSupport() {
    }

    public static void await(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
    }

    // a fixed client port stays in TIME_WAIT after the client closes, so a rerun within that time cannot bind it
    public static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
