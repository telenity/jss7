package org.mobicents.protocols.ss7.m3ua.impl.oam;

import static org.junit.Assert.assertEquals;

import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.sctp.netty.NettySctpManagementImpl;
import org.mobicents.protocols.ss7.m3ua.impl.M3UAManagementImpl;

/**
 * Argument validation of the sctp and m3ua shell commands. Nothing is started, so no connection is opened.
 */
public class ShellExecutorValidationTest {

    private NettySctpManagementImpl sctp;
    private M3UAManagementImpl m3ua;
    private SCTPShellExecutor sctpShell;
    private M3UAShellExecutor m3uaShell;

    @Before
    public void setUp() throws Exception {
        String dir = Files.createTempDirectory("shell-validation").toString();
        sctp = new NettySctpManagementImpl("shell-validation");
        sctp.setPersistDir(dir);
        sctp.start();
        sctp.removeAllResourses();
        m3ua = new M3UAManagementImpl("shell-validation");
        m3ua.setPersistDir(dir);
        m3ua.setTransportManagement(sctp);
        m3ua.start();
        m3ua.removeAllResourses();
        sctpShell = new SCTPShellExecutor();
        sctpShell.setSctpManagement(sctp);
        m3uaShell = new M3UAShellExecutor();
        m3uaShell.setM3uaManagement(m3ua);
    }

    @After
    public void tearDown() throws Exception {
        m3ua.removeAllResourses();
        m3ua.stop();
        sctp.removeAllResourses();
        sctp.stop();
    }

    private String sctp(String cmd) {
        return sctpShell.execute(cmd.split(" "));
    }

    private String m3ua(String cmd) {
        return m3uaShell.execute(cmd.split(" "));
    }

    @Test
    public void associationTypeIsCaseInsensitive() {
        assertEquals("Successfully added client Association=A1",
                sctp("sctp association create A1 client 198.51.100.10 2905 192.0.2.1 2905"));
    }

    @Test
    public void socketTypeDefaultsToSctpOnlyWhenAbsent() throws Exception {
        sctp("sctp association create A1 CLIENT 198.51.100.10 2905 192.0.2.1 2905");
        assertEquals(IpChannelType.SCTP, sctp.getAssociation("A1").getIpChannelType());
        sctp("sctp association create A2 CLIENT 198.51.100.10 2906 192.0.2.1 2906 tcp");
        assertEquals(IpChannelType.TCP, sctp.getAssociation("A2").getIpChannelType());
    }

    @Test
    public void unknownSocketTypeIsRejected() {
        assertEquals("Unknown socket-type 'TCPX'. Expected: SCTP, TCP",
                sctp("sctp server create S1 192.0.2.1 2905 TCPX"));
        assertEquals("Unknown socket-type 'TCPX'. Expected: SCTP, TCP",
                sctp("sctp association create A1 CLIENT 198.51.100.10 2905 192.0.2.1 2905 TCPX"));
        sctp("sctp server create S1 192.0.2.1 2905");
        assertEquals("Unknown socket-type 'TCPX'. Expected: SCTP, TCP",
                sctp("sctp association create A2 SERVER S1 198.51.100.10 2905 TCPX"));
    }

    @Test
    public void asCreateRejectsUnknownValues() {
        assertEquals("Unknown functionality 'IPSX'. Expected: AS, SGW, IPSP", m3ua("m3ua as create AS1 IPSX"));
        assertEquals("Unknown mode 'XE'. Expected: SE, DE", m3ua("m3ua as create AS1 IPSP mode XE"));
        assertEquals("Unknown ipspType 'CLIENTT'. Expected: CLIENT, SERVER", m3ua("m3ua as create AS1 IPSP ipspType CLIENTT"));
        assertEquals("Unknown traffic-mode 'loadshar'. Expected: loadshare, override, broadcast",
                m3ua("m3ua as create AS1 IPSP traffic-mode loadshar"));
        assertEquals("Unknown option 'rcc'. Expected: mode, ipspType, rc, traffic-mode, network-appearance, min-asp",
                m3ua("m3ua as create AS1 IPSP rcc 1"));
        assertEquals("Successfully created AS name=AS1",
                m3ua("m3ua as create AS1 IPSP mode SE ipspType client traffic-mode loadshare"));
    }

    @Test
    public void aspCreateRejectsUnknownOption() {
        sctp("sctp association create A1 CLIENT 198.51.100.10 2905 192.0.2.1 2905");
        assertEquals("Unknown option 'heartbeet'. Expected: aspid, heartbeat", m3ua("m3ua asp create ASP1 A1 heartbeet true"));
    }

    @Test
    public void routeNeedsOpcAndSiTogether() {
        m3ua("m3ua as create AS1 IPSP mode SE ipspType CLIENT");
        assertEquals("Invalid Command", m3ua("m3ua route add AS1 1001 2000"));
        assertEquals("Invalid Command", m3ua("m3ua route remove AS1 1001 2000"));
        assertEquals("AS=AS1 successfully added as route for DPC=1001", m3ua("m3ua route add AS1 1001"));
        assertEquals("AS=AS1 successfully added as route for DPC=1002", m3ua("m3ua route add AS1 1002 2000 3"));
    }
}
