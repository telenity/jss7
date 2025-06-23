package org.mobicents.protocols.ss7.m3ua.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import javolution.util.FastList;
import org.apache.log4j.Logger;
import org.mobicents.protocols.api.Association;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.api.Management;
import org.mobicents.protocols.sctp.netty.NettySctpManagementImpl;
import org.mobicents.protocols.ss7.m3ua.*;
import org.mobicents.protocols.ss7.m3ua.impl.message.MessageFactoryImpl;
import org.mobicents.protocols.ss7.m3ua.impl.message.ssnm.DestinationAvailableImpl;
import org.mobicents.protocols.ss7.m3ua.impl.message.ssnm.DestinationUPUnavailableImpl;
import org.mobicents.protocols.ss7.m3ua.impl.message.ssnm.DestinationUnavailableImpl;
import org.mobicents.protocols.ss7.m3ua.impl.message.ssnm.SignallingCongestionImpl;
import org.mobicents.protocols.ss7.m3ua.impl.parameter.CongestedIndicationImpl;
import org.mobicents.protocols.ss7.m3ua.impl.parameter.ParameterFactoryImpl;
import org.mobicents.protocols.ss7.m3ua.impl.parameter.UserCauseImpl;
import org.mobicents.protocols.ss7.m3ua.message.MessageClass;
import org.mobicents.protocols.ss7.m3ua.message.MessageType;
import org.mobicents.protocols.ss7.m3ua.message.ssnm.SignallingCongestion;
import org.mobicents.protocols.ss7.m3ua.parameter.*;
import org.mobicents.protocols.ss7.mtp.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class MultipleAspTest {
    private static final Logger logger = Logger.getLogger(MultipleAspTest.class);

    /*
     * OPC = 3615, DPC = 4010, 4011, 4033
     */

    private static final int OPC = 3615;
    private static final int DPC1 = 4010;
    private static final int DPC2 = 4011;
    private static final int DPC3 = 4033;

    private static final String SERVER_HOST = "127.0.0.1";

    private static final String SERVER_NAME_STP1 = "stp1";
    private static final int SERVER_PORT_STP1 = 10001;
    private static final String SERVER_ASSOCIATION_NAME_STP1 = "stp1_server_assoc";
    private static final String SERVER_NAME_STP2 = "stp2";
    private static final int SERVER_PORT_STP2 = 10002;
    private static final String SERVER_ASSOCIATION_NAME_STP2 = "stp2_server_assoc";
    private static final String SERVER_NAME_STP3 = "stp3";
    private static final int SERVER_PORT_STP3 = 10003;
    private static final String SERVER_ASSOCIATION_NAME_STP3 = "stp3_server_assoc";

    private static final String CLIENT_HOST = "127.0.0.1";

    private static final int CLIENT_PORT_STP1 = 5001;
    private static final String CLIENT_ASSOCIATION_NAME_STP1 = "stp1_client_assoc";
    private static final int CLIENT_PORT_STP2 = 5002;
    private static final String CLIENT_ASSOCIATION_NAME_STP2 = "stp2_client_assoc";
    private static final int CLIENT_PORT_STP3 = 5003;
    private static final String CLIENT_ASSOCIATION_NAME_STP3 = "stp3_client_assoc";

    private Management sctpManagement = null;
    private M3UAManagementImpl m3uaMgmt = null;
    private ParameterFactoryImpl factory = new ParameterFactoryImpl();

    private Mtp3UserPartListenerImpl mtp3UserPartListener = null;

    private Client client;

    private Server serverStp1;
    private Server serverStp2;
    private Server serverStp3;

    public static String getSystemTempDirectory() {
        String tmpDir = System.getProperty("java.io.tmpdir");
        if (tmpDir != null && !tmpDir.isEmpty()) {
            return tmpDir;
        } else {
            // Fallback (very rare case)
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("mac")) {
                return "/tmp"; // macOS default
            } else if (os.contains("nux")) {
                return "/tmp"; // Linux default
            } else {
                throw new IllegalStateException("Unsupported OS or no tmp directory found.");
            }
        }
    }

    @BeforeMethod
    public void setup() throws Exception {
        System.setProperty("ss7.m3ua.ssnm.skipSourceCheck", "false");

        mtp3UserPartListener = new Mtp3UserPartListenerImpl();

        client = new Client();

        serverStp1 = new Server(SERVER_NAME_STP1, SERVER_PORT_STP1, SERVER_ASSOCIATION_NAME_STP1,
                CLIENT_PORT_STP1, CLIENT_ASSOCIATION_NAME_STP1, DPC1);
        serverStp2 = new Server(SERVER_NAME_STP2, SERVER_PORT_STP2, SERVER_ASSOCIATION_NAME_STP2,
                CLIENT_PORT_STP2, CLIENT_ASSOCIATION_NAME_STP2, DPC2);
        serverStp3 = new Server(SERVER_NAME_STP3, SERVER_PORT_STP3, SERVER_ASSOCIATION_NAME_STP3,
                CLIENT_PORT_STP3, CLIENT_ASSOCIATION_NAME_STP3, DPC3);

        this.sctpManagement = new NettySctpManagementImpl("MultipleAspTest");
        this.sctpManagement.setPersistDir(getSystemTempDirectory());
        this.sctpManagement.setSingleThread(true);
        this.sctpManagement.start();
        this.sctpManagement.removeAllResourses();

        this.m3uaMgmt = new M3UAManagementImpl("MultipleAspTest");
        this.m3uaMgmt.setPersistDir(getSystemTempDirectory());
        this.m3uaMgmt.setTransportManagement(this.sctpManagement);
        this.m3uaMgmt.addMtp3UserPartListener(mtp3UserPartListener);
        this.m3uaMgmt.setMaxAsForRoute(3);
        this.m3uaMgmt.start();
        this.m3uaMgmt.removeAllResourses();
    }

    @AfterMethod
    public void teardown() throws Exception {
        serverStp1.stop();
        serverStp2.stop();
        serverStp3.stop();
        client.stop();
        if (this.m3uaMgmt.isStarted()) {
            this.m3uaMgmt.stop();
        }
        if (this.m3uaMgmt.isStarted()) {
            this.m3uaMgmt.removeAllResourses();
        }
        if (this.sctpManagement.isStarted()) {
            this.sctpManagement.removeAllResourses();
        }
        if (this.sctpManagement.isStarted()) {
            this.sctpManagement.stop();
        }
    }

    @Test
    public void testStp1LinkGoesDown() throws Exception {
        serverStp1.start();
        serverStp2.start();
        serverStp3.start();

        Thread.sleep(1000);

        client.start();

        Thread.sleep(11000);

        // send test messages
        client.sendPayload(DPC1);
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        mtp3UserPartListener.reset();

        // stp1 link goes down
        serverStp1.stop();

        Thread.sleep(1000);

        // send test messages
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        Thread.sleep(1000);

        assertEquals(mtp3UserPartListener.getReceivedData().size(), 5);
    }

    @Test
    public void testDunaFromUnrelatedPc() throws Exception {
        serverStp1.start();
        serverStp2.start();
        serverStp3.start();

        Thread.sleep(1000);

        client.start();

        Thread.sleep(11000);

        // send test messages
        client.sendPayload(DPC1);
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        mtp3UserPartListener.reset();

        Thread.sleep(1000);

        client.sendDunaFromStp1(4060);

        // send test messages
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        Thread.sleep(1000);

        assertEquals(mtp3UserPartListener.getReceivedData().size(), 5);
        assertNull(mtp3UserPartListener.getMtp3PausePrimitive());
    }

    @Test
    public void testStp1SendsDunaItself() throws Exception {
        serverStp1.start();
        serverStp2.start();
        serverStp3.start();

        Thread.sleep(1000);

        client.start();

        Thread.sleep(11000);

        // send test messages
        client.sendPayload(DPC1);
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        mtp3UserPartListener.reset();

        // stp1 sends DUNA itself, link is UP
        client.sendDunaFromStp1(DPC1);

        Thread.sleep(1000);

        // send test messages
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        Thread.sleep(1000);

        assertEquals(mtp3UserPartListener.getReceivedData().size(), 5);
        assertNotNull(mtp3UserPartListener.getMtp3PausePrimitive());
        assertEquals(mtp3UserPartListener.getMtp3PausePrimitive().getAffectedDpc(), DPC1);
    }

    @Test
    public void testStp1SendsSconItself() throws Exception {
        serverStp1.start();
        serverStp2.start();
        serverStp3.start();

        Thread.sleep(1000);

        client.start();

        Thread.sleep(11000);

        // send test messages
        client.sendPayload(DPC1);
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        mtp3UserPartListener.reset();

        // stp1 sends SCON itself, link is UP
        client.sendSconFromStp1(DPC1);

        Thread.sleep(1000);

        // send test messages
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        Thread.sleep(1000);

        assertEquals(mtp3UserPartListener.getReceivedData().size(), 5);
        assertNotNull(mtp3UserPartListener.getMtp3StatusPrimitive());
        assertEquals(mtp3UserPartListener.getMtp3StatusPrimitive().getAffectedDpc(), DPC1);
    }

    @Test
    public void testStp1SendsDupuItself() throws Exception {
        serverStp1.start();
        serverStp2.start();
        serverStp3.start();

        Thread.sleep(1000);

        client.start();

        Thread.sleep(11000);

        // send test messages
        client.sendPayload(DPC1);
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        mtp3UserPartListener.reset();

        // stp1 sends DUPU itself, link is UP
        client.sendDupuFromStp1(DPC1);

        Thread.sleep(1000);

        // send test messages
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        Thread.sleep(1000);

        assertEquals(mtp3UserPartListener.getReceivedData().size(), 5);
        assertNotNull(mtp3UserPartListener.getMtp3StatusPrimitive());
        assertEquals(mtp3UserPartListener.getMtp3StatusPrimitive().getAffectedDpc(), DPC1);
    }

    @Test
    public void testStp1SendsDunaForStp2() throws Exception {
        serverStp1.start();
        serverStp2.start();
        serverStp3.start();

        Thread.sleep(1000);

        client.start();

        Thread.sleep(11000);

        // send test messages
        client.sendPayload(DPC1);
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        mtp3UserPartListener.reset();

        // stp1 sends DUNA for stp2, link is UP
        client.sendDunaFromStp1(DPC2);

        Thread.sleep(1000);

        // send test messages
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        Thread.sleep(1000);

        assertEquals(mtp3UserPartListener.getReceivedData().size(), 5);
        assertNull(mtp3UserPartListener.getMtp3PausePrimitive());
    }

    @Test
    public void testStp1IsDownStp2SendsDavaForStp1() throws Exception {
        serverStp1.start();
        serverStp2.start();
        serverStp3.start();

        Thread.sleep(1000);

        client.start();

        Thread.sleep(11000);

        // send test messages
        client.sendPayload(DPC1);
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        mtp3UserPartListener.reset();

        // stp1 link goes down
        serverStp1.stop();

        Thread.sleep(1000);

        client.sendDavaFromStp2(DPC1);

        // send test messages
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        Thread.sleep(1000);

        assertEquals(mtp3UserPartListener.getReceivedData().size(), 5);
        assertNull(mtp3UserPartListener.getMtp3ResumePrimitive());
    }

    @Test
    public void testStp1IsDownStp2SendsDunaForStp1() throws Exception {
        serverStp1.start();
        serverStp2.start();
        serverStp3.start();

        Thread.sleep(1000);

        client.start();

        Thread.sleep(11000);

        // send test messages
        client.sendPayload(DPC1);
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        mtp3UserPartListener.reset();

        // stp1 link goes down
        serverStp1.stop();

        Thread.sleep(1000);

        client.sendDunaFromStp2(DPC1);

        // send test messages
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        Thread.sleep(1000);

        assertEquals(mtp3UserPartListener.getReceivedData().size(), 5);
        assertNull(mtp3UserPartListener.getMtp3PausePrimitive());
    }

    @Test
    public void testStp1IsDownStp2SendsSconForStp1() throws Exception {
        serverStp1.start();
        serverStp2.start();
        serverStp3.start();

        Thread.sleep(1000);

        client.start();

        Thread.sleep(11000);

        // send test messages
        client.sendPayload(DPC1);
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        mtp3UserPartListener.reset();

        // stp1 link goes down
        serverStp1.stop();

        Thread.sleep(1000);

        client.sendSconFromStp2(DPC1);

        // send test messages
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        Thread.sleep(1000);

        assertEquals(mtp3UserPartListener.getReceivedData().size(), 5);
        assertNull(mtp3UserPartListener.getMtp3StatusPrimitive());
    }

    @Test
    public void testStp1IsDownAndUp() throws Exception {
        serverStp1.start();
        serverStp2.start();
        serverStp3.start();

        Thread.sleep(1000);

        client.start();

        Thread.sleep(11000);

        // send test messages
        client.sendPayload(DPC1);
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        mtp3UserPartListener.reset();

        // stp1 link goes down
        serverStp1.stop();

        Thread.sleep(1000);

        // stp1 link goes up
        serverStp1.start();

        Thread.sleep(10000);

        // send test messages
        client.sendPayload(DPC1);
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        Thread.sleep(1000);

        assertEquals(mtp3UserPartListener.getReceivedData().size(), 6);
        // not null, because internal DUNA was sent
        assertNotNull(mtp3UserPartListener.getMtp3PausePrimitive());
        // not null, because internal DAVA was sent
        assertNotNull(mtp3UserPartListener.getMtp3ResumePrimitive());
    }

    @Test
    public void testMultipleRoutes() throws Exception {
        serverStp1.start();
        serverStp2.start();
        serverStp3.start();

        Thread.sleep(1000);

        client.start();

        Thread.sleep(11000);

        // send test messages
        client.sendPayload(DPC1);
        client.sendPayload(DPC2);
        client.sendPayload(DPC3);

        mtp3UserPartListener.reset();

        // add second routes
        m3uaMgmt.addRoute(DPC1, -1, -1, "localAsStp2");
        m3uaMgmt.addRoute(DPC2, -1, -1, "localAsStp1");
        m3uaMgmt.addRoute(DPC3, -1, -1, "localAsStp2");

        Thread.sleep(1000);

        // stp1 link goes down
        serverStp1.stop();

        Thread.sleep(1000);

        // DPC1 -> STP1 + STP2
        // DPC2 -> STP2 + STP1
        // DPC3 -> STP3 + STP2
        // stp2 sends DUNA for DPC3
        // DPC1 -> STP2
        // DPC2 -> STP2
        // DPC3 -> N/A
        client.sendDunaFromStp2(DPC3);

        Thread.sleep(10000);

        // send test messages
        client.sendPayload(DPC1);
        client.sendPayload(DPC2);

        Thread.sleep(1000);

        assertEquals(mtp3UserPartListener.getReceivedData().size(), 5);
        assertNotNull(mtp3UserPartListener.getMtp3PausePrimitive());
    }

    private class Client {

        private AsImpl localAsStp1;
        private AspImpl localAspStp1;
        private AspFactoryImpl localAspFactoryStp1;

        private AsImpl localAsStp2;
        private AspImpl localAspStp2;
        private AspFactoryImpl localAspFactoryStp2;

        private AsImpl localAsStp3;
        private AspImpl localAspStp3;
        private AspFactoryImpl localAspFactoryStp3;

        public Client() {
        }

        public void start() throws Exception {
            IpChannelType ipChannelType = IpChannelType.TCP;

            sctpManagement.addAssociation(CLIENT_HOST, CLIENT_PORT_STP1, SERVER_HOST, SERVER_PORT_STP1,
                    CLIENT_ASSOCIATION_NAME_STP1, ipChannelType, null);
            sctpManagement.addAssociation(CLIENT_HOST, CLIENT_PORT_STP2, SERVER_HOST, SERVER_PORT_STP2,
                    CLIENT_ASSOCIATION_NAME_STP2, ipChannelType, null);
            sctpManagement.addAssociation(CLIENT_HOST, CLIENT_PORT_STP3, SERVER_HOST, SERVER_PORT_STP3,
                    CLIENT_ASSOCIATION_NAME_STP3, ipChannelType, null);

            TrafficModeType trafficModeType = factory.createTrafficModeType(TrafficModeType.Loadshare);
            localAsStp1 = (AsImpl) m3uaMgmt.createAs("localAsStp1", Functionality.AS, ExchangeType.SE,
                    IPSPType.CLIENT, null, trafficModeType, 1, null);
            localAsStp2 = (AsImpl) m3uaMgmt.createAs("localAsStp2", Functionality.AS, ExchangeType.SE,
                    IPSPType.CLIENT, null, trafficModeType, 1, null);
            localAsStp3 = (AsImpl) m3uaMgmt.createAs("localAsStp3", Functionality.AS, ExchangeType.SE,
                    IPSPType.CLIENT, null, trafficModeType, 1, null);

            localAspFactoryStp1 = (AspFactoryImpl) m3uaMgmt.createAspFactory("localAspStp1",
                    CLIENT_ASSOCIATION_NAME_STP1, false);
            localAspFactoryStp2 = (AspFactoryImpl) m3uaMgmt.createAspFactory("localAspStp2",
                    CLIENT_ASSOCIATION_NAME_STP2, false);
            localAspFactoryStp3 = (AspFactoryImpl) m3uaMgmt.createAspFactory("localAspStp3",
                    CLIENT_ASSOCIATION_NAME_STP3, false);

            localAspStp1 = m3uaMgmt.assignAspToAs("localAsStp1", "localAspStp1");
            localAspStp2 = m3uaMgmt.assignAspToAs("localAsStp2", "localAspStp2");
            localAspStp3 = m3uaMgmt.assignAspToAs("localAsStp3", "localAspStp3");

            m3uaMgmt.addRoute(DPC1, -1, -1, "localAsStp1");
            m3uaMgmt.addRoute(DPC2, -1, -1, "localAsStp2");
            m3uaMgmt.addRoute(DPC3, -1, -1, "localAsStp3");

            m3uaMgmt.startAsp("localAspStp1");
            m3uaMgmt.startAsp("localAspStp2");
            m3uaMgmt.startAsp("localAspStp3");
        }

        public void stop() throws Exception {
            m3uaMgmt.stopAsp("localAspStp1");
            m3uaMgmt.stopAsp("localAspStp2");
            m3uaMgmt.stopAsp("localAspStp3");
        }

        public void stopClient() throws Exception {
            m3uaMgmt.removeRoute(DPC1, -1, -1, "localAspStp1");
            m3uaMgmt.removeRoute(DPC2, -1, -1, "localAspStp2");
            m3uaMgmt.removeRoute(DPC3, -1, -1, "localAspStp3");

            m3uaMgmt.unassignAspFromAs("localAsStp1", "localAspStp1");
            m3uaMgmt.unassignAspFromAs("localAsStp2", "localAspStp2");
            m3uaMgmt.unassignAspFromAs("localAsStp3", "localAspStp3");

            m3uaMgmt.destroyAspFactory("localAspStp1");
            m3uaMgmt.destroyAspFactory("localAspStp2");
            m3uaMgmt.destroyAspFactory("localAspStp3");

            m3uaMgmt.destroyAs("localAsStp1");
            m3uaMgmt.destroyAs("localAsStp2");
            m3uaMgmt.destroyAs("localAsStp3");

            sctpManagement.removeAssociation(CLIENT_ASSOCIATION_NAME_STP1);
            sctpManagement.removeAssociation(CLIENT_ASSOCIATION_NAME_STP2);
            sctpManagement.removeAssociation(CLIENT_ASSOCIATION_NAME_STP3);
        }

        public void sendPayload(int dpc) throws Exception {
            Mtp3TransferPrimitiveFactory factory = m3uaMgmt.getMtp3TransferPrimitiveFactory();
            Mtp3TransferPrimitive mtp3TransferPrimitive = factory.createMtp3TransferPrimitive(3, 1, 0, OPC, dpc, 1, new byte[] { 1, 2, 3, 4 });
            m3uaMgmt.sendMessage(mtp3TransferPrimitive);
        }

        public void sendDunaFromStp1(int dpc) {
            MessageFactoryImpl messageFactory = new MessageFactoryImpl();
            ByteBuf byteBuf = Unpooled.buffer();

            DestinationUnavailableImpl msg = (DestinationUnavailableImpl) messageFactory.createMessage(
                    MessageClass.SIGNALING_NETWORK_MANAGEMENT, MessageType.DESTINATION_UNAVAILABLE);
            AffectedPointCode afpc = factory.createAffectedPointCode(new int[]{dpc}, new short[]{0});
            msg.setAffectedPointCodes(afpc);
            msg.encode(byteBuf);

            localAspFactoryStp1.processPayload(IpChannelType.TCP, byteBuf);
        }

        public void sendDavaFromStp2(int dpc) {
            MessageFactoryImpl messageFactory = new MessageFactoryImpl();
            ByteBuf byteBuf = Unpooled.buffer();

            DestinationAvailableImpl msg = (DestinationAvailableImpl) messageFactory.createMessage(
                    MessageClass.SIGNALING_NETWORK_MANAGEMENT, MessageType.DESTINATION_AVAILABLE);
            AffectedPointCode afpc = factory.createAffectedPointCode(new int[]{dpc}, new short[]{0});
            msg.setAffectedPointCodes(afpc);
            msg.encode(byteBuf);

            localAspFactoryStp2.processPayload(IpChannelType.TCP, byteBuf);
        }

        public void sendDunaFromStp2(int dpc) {
            MessageFactoryImpl messageFactory = new MessageFactoryImpl();
            ByteBuf byteBuf = Unpooled.buffer();

            DestinationUnavailableImpl msg = (DestinationUnavailableImpl) messageFactory.createMessage(
                    MessageClass.SIGNALING_NETWORK_MANAGEMENT, MessageType.DESTINATION_UNAVAILABLE);
            AffectedPointCode afpc = factory.createAffectedPointCode(new int[]{dpc}, new short[]{0});
            msg.setAffectedPointCodes(afpc);
            msg.encode(byteBuf);

            localAspFactoryStp2.processPayload(IpChannelType.TCP, byteBuf);
        }

        public void sendSconFromStp1(int dpc) {
            MessageFactoryImpl messageFactory = new MessageFactoryImpl();
            ByteBuf byteBuf = Unpooled.buffer();

            SignallingCongestionImpl msg = (SignallingCongestionImpl) messageFactory.createMessage(
                    MessageClass.SIGNALING_NETWORK_MANAGEMENT, MessageType.SIGNALING_CONGESTION);
            AffectedPointCode afpc = factory.createAffectedPointCode(new int[]{dpc}, new short[]{0});
            msg.setAffectedPointCodes(afpc);
            msg.encode(byteBuf);

            localAspFactoryStp1.processPayload(IpChannelType.TCP, byteBuf);
        }

        public void sendSconFromStp2(int dpc) {
            MessageFactoryImpl messageFactory = new MessageFactoryImpl();
            ByteBuf byteBuf = Unpooled.buffer();

            SignallingCongestionImpl msg = (SignallingCongestionImpl) messageFactory.createMessage(
                    MessageClass.SIGNALING_NETWORK_MANAGEMENT, MessageType.SIGNALING_CONGESTION);
            AffectedPointCode afpc = factory.createAffectedPointCode(new int[]{dpc}, new short[]{0});
            msg.setAffectedPointCodes(afpc);
            msg.encode(byteBuf);

            localAspFactoryStp2.processPayload(IpChannelType.TCP, byteBuf);
        }

        public void sendDupuFromStp1(int dpc) {
            MessageFactoryImpl messageFactory = new MessageFactoryImpl();
            ByteBuf byteBuf = Unpooled.buffer();

            DestinationUPUnavailableImpl msg = (DestinationUPUnavailableImpl) messageFactory.createMessage(
                    MessageClass.SIGNALING_NETWORK_MANAGEMENT, MessageType.DESTINATION_USER_PART_UNAVAILABLE);
            AffectedPointCode afpc = factory.createAffectedPointCode(new int[]{dpc}, new short[]{0});
            msg.setAffectedPointCode(afpc);
            UserCauseImpl usrCa = (UserCauseImpl) factory.createUserCause(5, 0);
            msg.setUserCause(usrCa);
            msg.encode(byteBuf);

            localAspFactoryStp1.processPayload(IpChannelType.TCP, byteBuf);
        }

        public void sendDupuFromStp2(int dpc) {
            MessageFactoryImpl messageFactory = new MessageFactoryImpl();
            ByteBuf byteBuf = Unpooled.buffer();

            DestinationUPUnavailableImpl msg = (DestinationUPUnavailableImpl) messageFactory.createMessage(
                    MessageClass.SIGNALING_NETWORK_MANAGEMENT, MessageType.DESTINATION_USER_PART_UNAVAILABLE);
            AffectedPointCode afpc = factory.createAffectedPointCode(new int[]{dpc}, new short[]{0});
            msg.setAffectedPointCode(afpc);
            UserCauseImpl usrCa = (UserCauseImpl) factory.createUserCause(5, 0);
            msg.setUserCause(usrCa);
            msg.encode(byteBuf);

            localAspFactoryStp2.processPayload(IpChannelType.TCP, byteBuf);
        }
    }

    private class Server {
        private final String SERVER_NAME;
        private final int SERVER_PORT;
        private final String SERVER_ASSOCIATION_NAME;
        private final int CLIENT_PORT;
        private final String CLIENT_ASSOCIATION_NAME;

        private AsImpl remAs;
        private AspImpl remAsp;
        private AspFactoryImpl remAspFactory;

        private final int pc;

        public Server(String serverName, int serverPort, String serverAssociationName,
                      int clientPort, String clientAssociationName, int pc) {
            this.SERVER_NAME = serverName;
            this.SERVER_PORT = serverPort;
            this.SERVER_ASSOCIATION_NAME = serverAssociationName;
            this.CLIENT_PORT = clientPort;
            this.CLIENT_ASSOCIATION_NAME = clientAssociationName;
            this.pc = pc;
        }

        private void start() throws Exception {
            IpChannelType ipChannelType = IpChannelType.TCP;

            sctpManagement.addServer(SERVER_NAME, SERVER_HOST, SERVER_PORT, ipChannelType, null);
            sctpManagement.addServerAssociation(CLIENT_HOST, CLIENT_PORT, SERVER_NAME, SERVER_ASSOCIATION_NAME,
                    ipChannelType);
            sctpManagement.startServer(SERVER_NAME);

            TrafficModeType trafficModeType = factory.createTrafficModeType(TrafficModeType.Loadshare);
            remAs = (AsImpl) m3uaMgmt.createAs(SERVER_NAME + "_as", Functionality.SGW, ExchangeType.SE,
                    IPSPType.CLIENT, null, trafficModeType, 1, null);
            remAspFactory = (AspFactoryImpl) m3uaMgmt.createAspFactory(SERVER_NAME + "_asp",
                    SERVER_ASSOCIATION_NAME, false);
            remAsp = m3uaMgmt.assignAspToAs(SERVER_NAME + "_as", SERVER_NAME + "_asp");
            m3uaMgmt.addRoute(OPC, -1, -1, SERVER_NAME + "_as");
            m3uaMgmt.startAsp(SERVER_NAME + "_asp");
        }

        public void stop() throws Exception {
            if (m3uaMgmt.getAs(SERVER_NAME + "_as") != null) {
                m3uaMgmt.stopAsp(SERVER_NAME + "_asp");
                m3uaMgmt.removeRoute(OPC, -1, -1, SERVER_NAME + "_as");
                m3uaMgmt.unassignAspFromAs(SERVER_NAME + "_as", SERVER_NAME + "_asp");
                m3uaMgmt.destroyAspFactory(SERVER_NAME + "_asp");
                m3uaMgmt.destroyAs(SERVER_NAME + "_as");
                sctpManagement.removeAssociation(SERVER_ASSOCIATION_NAME);
                sctpManagement.stopServer(SERVER_NAME);
                sctpManagement.removeServer(SERVER_NAME);
            }
        }

        public void sendPayload(int dpc) throws Exception {
            Mtp3TransferPrimitiveFactory factory = m3uaMgmt.getMtp3TransferPrimitiveFactory();
            Mtp3TransferPrimitive mtp3TransferPrimitive = factory.createMtp3TransferPrimitive(3, 1, 0, pc, OPC, 1, new byte[]{1, 2, 3, 4});
            m3uaMgmt.sendMessage(mtp3TransferPrimitive);
        }

        public AsImpl getRemAs() {
            return remAs;
        }

        public AspImpl getRemAsp() {
            return remAsp;
        }

        public AspFactoryImpl getRemAspFactory() {
            return remAspFactory;
        }
    }

    private class Mtp3UserPartListenerImpl implements Mtp3UserPartListener {
        private FastList<Mtp3TransferPrimitive> receivedData = new FastList<Mtp3TransferPrimitive>();
        private Mtp3ResumePrimitive mtp3ResumePrimitive;
        private Mtp3PausePrimitive mtp3PausePrimitive;
        private Mtp3StatusPrimitive mtp3StatusPrimitive;

        public FastList<Mtp3TransferPrimitive> getReceivedData() {
            return receivedData;
        }

        public Mtp3ResumePrimitive getMtp3ResumePrimitive() {
            return mtp3ResumePrimitive;
        }

        public Mtp3PausePrimitive getMtp3PausePrimitive() {
            return mtp3PausePrimitive;
        }

        public Mtp3StatusPrimitive getMtp3StatusPrimitive() {
            return mtp3StatusPrimitive;
        }

        public void reset() {
            mtp3ResumePrimitive = null;
            mtp3PausePrimitive = null;
            mtp3StatusPrimitive = null;
        }

        @Override
        public void onMtp3PauseMessage(Mtp3PausePrimitive pause) {
            logger.info("Received Mtp3PausePrimitive: " + pause);
            mtp3PausePrimitive = pause;
        }

        @Override
        public void onMtp3ResumeMessage(Mtp3ResumePrimitive resume) {
            logger.info("Received Mtp3ResumePrimitive:" + resume);
            mtp3ResumePrimitive = resume;
        }

        @Override
        public void onMtp3StatusMessage(Mtp3StatusPrimitive status) {
            logger.info("Received Mtp3StatusPrimitive:" + status);
            mtp3StatusPrimitive = status;
        }

        @Override
        public void onMtp3TransferMessage(Mtp3TransferPrimitive value) {
            receivedData.add(value);
        }
    }
}
