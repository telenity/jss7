package org.mobicents.protocols.ss7.tcap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mobicents.protocols.ss7.sccp.SccpProvider;
import org.mobicents.protocols.ss7.sccp.impl.SccpHarness;

/**
 * On stop the TCAP provider leaves SCCP first, so SCCP answers the SSN itself, and only then shuts its executor down:
 * no message is handed to a stack that can no longer run it.
 */
public class TCAPStopOrderTest extends SccpHarness {

    private TCAPStackImpl tcapStack;
    private final List<Boolean> executorShutdownAtDeregister = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        this.sccpStack1Name = "TCAPStopOrderTestSccpStack1";
        this.sccpStack2Name = "TCAPStopOrderTestSccpStack2";
        super.setUp();

        SccpProvider spy = (SccpProvider) Proxy.newProxyInstance(SccpProvider.class.getClassLoader(),
                new Class<?>[] { SccpProvider.class }, (proxy, method, args) -> {
                    if (method.getName().equals("deregisterSccpListener")) {
                        executorShutdownAtDeregister.add(
                                ((TCAPProviderImpl) tcapStack.getProvider())._EXECUTOR.isShutdown());
                    }
                    try {
                        return method.invoke(sccpProvider1, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        tcapStack = new TCAPStackImpl(spy, 8);
        tcapStack.start();
    }

    @After
    public void tearDown() {
        super.tearDown();
    }

    @Test
    public void listenerIsDeregisteredBeforeExecutorShutdown() throws Exception {
        TCAPProviderImpl provider = (TCAPProviderImpl) tcapStack.getProvider();
        tcapStack.stop();

        assertEquals(1, executorShutdownAtDeregister.size());
        assertFalse("executor already shut down when SCCP listener was removed", executorShutdownAtDeregister.get(0));
        assertTrue(provider._EXECUTOR.isShutdown());
    }
}
