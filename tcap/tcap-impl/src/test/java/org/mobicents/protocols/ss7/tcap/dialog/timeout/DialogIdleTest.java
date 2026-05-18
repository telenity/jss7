/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
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

package org.mobicents.protocols.ss7.tcap.dialog.timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.sccp.impl.SccpHarness;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.mobicents.protocols.ss7.tcap.EventType;
import org.mobicents.protocols.ss7.tcap.TCAPStackImpl;
import org.mobicents.protocols.ss7.tcap.TestEvent;
import org.mobicents.protocols.ss7.tcap.api.TCAPException;
import org.mobicents.protocols.ss7.tcap.api.TCAPSendException;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TerminationType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test for call flow.
 *
 * @author baranowb
 *
 */
public class DialogIdleTest extends SccpHarness {

	private static final int _DIALOG_TIMEOUT = 500;
	private static final int _WAIT = 100;
	private static final int _LATCH_TIMEOUT = 15000;
	private TCAPStackImpl tcapStack1;
	private TCAPStackImpl tcapStack2;
	private SccpAddress peer1Address;
	private SccpAddress peer2Address;
	private Client client;
	private Server server;

	public DialogIdleTest() {

	}

	@Before
	public void setUpClass() throws Exception {
		this.sccpStack1Name = "DialogIdleTestSccpStack1";
		this.sccpStack2Name = "DialogIdleTestSccpStack2";
		System.out.println("setUpClass");
	}

	@After
	public void tearDownClass() throws Exception {
		System.out.println("tearDownClass");
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see junit.framework.TestCase#setUp()
	 */
	@Before
	public void setUp() throws Exception {
		System.out.println("setUp");
		super.setUp();

		peer1Address = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 1, null, 8);
		peer2Address = new SccpAddress(RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN, 2, null, 8);

		this.tcapStack1 = new TCAPStackImpl(this.sccpProvider1, 8);
		this.tcapStack2 = new TCAPStackImpl(this.sccpProvider2, 8);

		this.tcapStack1.start();
		this.tcapStack2.start();

		this.tcapStack1.setInvokeTimeout(0);
		this.tcapStack2.setInvokeTimeout(0);
		this.tcapStack1.setDialogIdleTimeout(_DIALOG_TIMEOUT-100);
		this.tcapStack2.setDialogIdleTimeout(_DIALOG_TIMEOUT);

		this.client = new Client(tcapStack1, peer1Address, peer2Address);
		this.server = new Server(tcapStack2, peer2Address, peer1Address);

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see junit.framework.TestCase#tearDown()
	 */
	@After
	public void tearDown() {
		System.out.println("tearDown");
		this.tcapStack1.stop();
		this.tcapStack2.stop();
		super.tearDown();
	}

	@Test
	public void testCreateOnly() throws Exception {
		long stamp = System.currentTimeMillis();
		List<TestEvent> expectedEvents = new ArrayList<TestEvent>();
		TestEvent te = TestEvent.createReceivedEvent(EventType.DialogTimeout, null, 0, stamp + _DIALOG_TIMEOUT);
		expectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.PAbort, null, 1, stamp + _DIALOG_TIMEOUT);
		expectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.DialogRelease, null, 2, stamp + _DIALOG_TIMEOUT);
		expectedEvents.add(te);

		CountDownLatch latch = new CountDownLatch(1);
		client.setDoneLatch(latch);
		client.startClientDialog();
		latch.await(_LATCH_TIMEOUT, TimeUnit.MILLISECONDS);
		client.compareEvents(expectedEvents);
	}

	@Test
	public void testAfterBeginOnly1() throws Exception {
		//client timeout first
		long stamp = System.currentTimeMillis();
		List<TestEvent> clientExpectedEvents = new ArrayList<TestEvent>();
		TestEvent te = TestEvent.createSentEvent(EventType.Begin, null, 0, stamp + _WAIT);
		clientExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.DialogTimeout, null, 1, stamp + _WAIT + _DIALOG_TIMEOUT);
		clientExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.PAbort, null, 2, stamp + _WAIT + _DIALOG_TIMEOUT);
		clientExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.DialogRelease, null, 3, stamp + _WAIT + _DIALOG_TIMEOUT);
		clientExpectedEvents.add(te);

		List<TestEvent> serverExpectedEvents = new ArrayList<TestEvent>();
		te = TestEvent.createReceivedEvent(EventType.Begin, null, 0, stamp + _WAIT);
		serverExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.DialogTimeout, null, 1, stamp + _WAIT + _DIALOG_TIMEOUT);
		serverExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.PAbort, null, 2, stamp + _WAIT + _DIALOG_TIMEOUT);
		serverExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.DialogRelease, null, 3, stamp + _WAIT + _DIALOG_TIMEOUT);
		serverExpectedEvents.add(te);

		CountDownLatch latch = new CountDownLatch(2);
		client.setDoneLatch(latch);
		server.setDoneLatch(latch);
		client.startClientDialog();
		client.waitFor(_WAIT);
		client.sendBegin();
		latch.await(_LATCH_TIMEOUT, TimeUnit.MILLISECONDS);
		client.compareEvents(clientExpectedEvents);
		server.compareEvents(serverExpectedEvents);
	}

	@Test
	public void testAfterBeginOnly2() throws Exception {
		//server timeout first
		this.tcapStack1.setDialogIdleTimeout(_DIALOG_TIMEOUT);
		this.tcapStack2.setDialogIdleTimeout(_DIALOG_TIMEOUT-100);
		long stamp = System.currentTimeMillis();
		List<TestEvent> clientExpectedEvents = new ArrayList<TestEvent>();
		TestEvent te = TestEvent.createSentEvent(EventType.Begin, null, 0, stamp + _WAIT);
		clientExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.PAbort, null, 1, stamp + _WAIT + _DIALOG_TIMEOUT);
		clientExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.DialogRelease, null, 2, stamp + _WAIT + _DIALOG_TIMEOUT);
		clientExpectedEvents.add(te);

		List<TestEvent> serverExpectedEvents = new ArrayList<TestEvent>();
		te = TestEvent.createReceivedEvent(EventType.Begin, null, 0, stamp + _WAIT);
		serverExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.DialogTimeout, null, 1, stamp + _WAIT + _DIALOG_TIMEOUT);
		serverExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.PAbort, null, 2, stamp + _WAIT + _DIALOG_TIMEOUT);
		serverExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.DialogRelease, null, 3, stamp + _WAIT + _DIALOG_TIMEOUT);
		serverExpectedEvents.add(te);

		CountDownLatch latch = new CountDownLatch(2);
		client.setDoneLatch(latch);
		server.setDoneLatch(latch);
		client.startClientDialog();
		client.waitFor(_WAIT);
		client.sendBegin();
		latch.await(_LATCH_TIMEOUT, TimeUnit.MILLISECONDS);
		client.compareEvents(clientExpectedEvents);
		server.compareEvents(serverExpectedEvents);
	}

	@Test
	public void testAfterContinue() throws Exception {
		long stamp = System.currentTimeMillis();
		List<TestEvent> clientExpectedEvents = new ArrayList<TestEvent>();
		TestEvent te = TestEvent.createSentEvent(EventType.Begin, null, 0, stamp + _WAIT);
		clientExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.Continue, null, 1, stamp + _WAIT * 2);
		clientExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.DialogTimeout, null, 2, stamp + _WAIT * 2 + _DIALOG_TIMEOUT);
		clientExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.PAbort, null, 3, stamp + _WAIT * 2 + _DIALOG_TIMEOUT);
		clientExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.DialogRelease, null, 4, stamp + _WAIT * 2 + _DIALOG_TIMEOUT);
		clientExpectedEvents.add(te);

		List<TestEvent> serverExpectedEvents = new ArrayList<TestEvent>();
		te = TestEvent.createReceivedEvent(EventType.Begin, null, 0, stamp + _WAIT);
		serverExpectedEvents.add(te);
		te = TestEvent.createSentEvent(EventType.Continue, null, 1, stamp + _WAIT * 2);
		serverExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.PAbort, null, 2, stamp + _WAIT * 2 + _DIALOG_TIMEOUT);
		serverExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.DialogRelease, null, 3, stamp + _WAIT * 2 + _DIALOG_TIMEOUT);
		serverExpectedEvents.add(te);

		CountDownLatch latch = new CountDownLatch(2);
		client.setDoneLatch(latch);
		server.setDoneLatch(latch);
		client.startClientDialog(0);
		try {
			client.waitFor(_WAIT);
			client.sendBegin();
			client.waitFor(_WAIT);
			server.sendContinue();
			latch.await(_LATCH_TIMEOUT, TimeUnit.MILLISECONDS);
		} finally {
			client.compareEvents(clientExpectedEvents);
			server.compareEvents(serverExpectedEvents);
		}
	}

	@Test
	public void testAfterContinue2() throws Exception {
		long stamp = System.currentTimeMillis();
		List<TestEvent> clientExpectedEvents = new ArrayList<TestEvent>();
		TestEvent te = TestEvent.createSentEvent(EventType.Begin, null, 0, stamp + _WAIT);
		clientExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.Continue, null, 1, stamp + _WAIT * 2);
		clientExpectedEvents.add(te);
		te = TestEvent.createSentEvent(EventType.Continue, null, 2, stamp + _WAIT * 3);
		clientExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.DialogTimeout, null, 3, stamp + _WAIT * 3 + _DIALOG_TIMEOUT);
		clientExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.PAbort, null, 4, stamp + _WAIT * 3 + _DIALOG_TIMEOUT);
		clientExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.DialogRelease, null, 5, stamp + _WAIT * 3 + _DIALOG_TIMEOUT);
		clientExpectedEvents.add(te);

		List<TestEvent> serverExpectedEvents = new ArrayList<TestEvent>();
		te = TestEvent.createReceivedEvent(EventType.Begin, null, 0, stamp + _WAIT);
		serverExpectedEvents.add(te);
		te = TestEvent.createSentEvent(EventType.Continue, null, 1, stamp + _WAIT * 2);
		serverExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.Continue, null, 2, stamp + _WAIT * 3);
		serverExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.PAbort, null, 3, stamp + _WAIT * 3 + _DIALOG_TIMEOUT);
		serverExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.DialogRelease, null, 4, stamp + _WAIT * 3 + _DIALOG_TIMEOUT);
		serverExpectedEvents.add(te);

		CountDownLatch latch = new CountDownLatch(2);
		client.setDoneLatch(latch);
		server.setDoneLatch(latch);
		client.startClientDialog(0);
		try {
			client.waitFor(_WAIT);
			client.sendBegin();
			client.waitFor(_WAIT);
			server.sendContinue();
			client.waitFor(_WAIT);
			client.sendContinue();
			latch.await(_LATCH_TIMEOUT, TimeUnit.MILLISECONDS);
		} finally {
			client.compareEvents(clientExpectedEvents);
			server.compareEvents(serverExpectedEvents);
		}
	}

	@Test
	public void testAfterEnd() throws Exception {
		long stamp = System.currentTimeMillis();
		List<TestEvent> clientExpectedEvents = new ArrayList<TestEvent>();
		TestEvent te = TestEvent.createSentEvent(EventType.Begin, null, 0, stamp + _WAIT);
		clientExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.Continue, null, 1, stamp + _WAIT * 2);
		clientExpectedEvents.add(te);
		te = TestEvent.createSentEvent(EventType.End, null, 2, stamp + _WAIT * 3);
		clientExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.DialogRelease, null, 3, stamp + _WAIT * 3);
		clientExpectedEvents.add(te);

		List<TestEvent> serverExpectedEvents = new ArrayList<TestEvent>();
		te = TestEvent.createReceivedEvent(EventType.Begin, null, 0, stamp + _WAIT);
		serverExpectedEvents.add(te);
		te = TestEvent.createSentEvent(EventType.Continue, null, 1, stamp + _WAIT * 2);
		serverExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.End, null, 2, stamp + _WAIT * 3);
		serverExpectedEvents.add(te);
		te = TestEvent.createReceivedEvent(EventType.DialogRelease, null, 3, stamp + _WAIT * 3);
		serverExpectedEvents.add(te);

		CountDownLatch latch = new CountDownLatch(2);
		client.setDoneLatch(latch);
		server.setDoneLatch(latch);
		client.startClientDialog(0);
		try {
			client.waitFor(_WAIT);
			client.sendBegin();
			client.waitFor(_WAIT);
			server.sendContinue();
			client.waitFor(_WAIT);
			client.sendEnd(TerminationType.Basic);
			latch.await(_LATCH_TIMEOUT, TimeUnit.MILLISECONDS);
		} finally {
			client.compareEvents(clientExpectedEvents);
			server.compareEvents(serverExpectedEvents);
		}
	}

}

