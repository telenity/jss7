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

package org.mobicents.protocols.ss7.tcap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mobicents.protocols.asn.AsnOutputStream;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.mobicents.protocols.ss7.tcap.api.TCAPException;
import org.mobicents.protocols.ss7.tcap.api.TCAPSendException;
import org.mobicents.protocols.ss7.tcap.api.TCAPStack;
import org.mobicents.protocols.ss7.tcap.api.tc.component.InvokeClass;
import org.mobicents.protocols.ss7.tcap.api.tc.component.OperationState;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.TRPseudoState;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCBeginRequest;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCContinueRequest;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCEndRequest;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCUniRequest;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCUserAbortRequest;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TerminationType;
import org.mobicents.protocols.ss7.tcap.asn.AbortSource;
import org.mobicents.protocols.ss7.tcap.asn.AbortSourceType;
import org.mobicents.protocols.ss7.tcap.asn.ApplicationContextName;
import org.mobicents.protocols.ss7.tcap.asn.DialogAPDU;
import org.mobicents.protocols.ss7.tcap.asn.DialogAPDUType;
import org.mobicents.protocols.ss7.tcap.asn.DialogAbortAPDU;
import org.mobicents.protocols.ss7.tcap.asn.DialogPortion;
import org.mobicents.protocols.ss7.tcap.asn.DialogRequestAPDU;
import org.mobicents.protocols.ss7.tcap.asn.DialogResponseAPDU;
import org.mobicents.protocols.ss7.tcap.asn.DialogServiceProviderType;
import org.mobicents.protocols.ss7.tcap.asn.DialogServiceUserType;
import org.mobicents.protocols.ss7.tcap.asn.DialogUniAPDU;
import org.mobicents.protocols.ss7.tcap.asn.EncodeException;
import org.mobicents.protocols.ss7.tcap.asn.InvokeImpl;
import org.mobicents.protocols.ss7.tcap.asn.ProblemImpl;
import org.mobicents.protocols.ss7.tcap.asn.Result;
import org.mobicents.protocols.ss7.tcap.asn.ResultSourceDiagnostic;
import org.mobicents.protocols.ss7.tcap.asn.ResultType;
import org.mobicents.protocols.ss7.tcap.asn.ReturnResultImpl;
import org.mobicents.protocols.ss7.tcap.asn.ReturnResultLastImpl;
import org.mobicents.protocols.ss7.tcap.asn.TCAbortMessageImpl;
import org.mobicents.protocols.ss7.tcap.asn.TCBeginMessageImpl;
import org.mobicents.protocols.ss7.tcap.asn.TCContinueMessageImpl;
import org.mobicents.protocols.ss7.tcap.asn.TCEndMessageImpl;
import org.mobicents.protocols.ss7.tcap.asn.TCUniMessageImpl;
import org.mobicents.protocols.ss7.tcap.asn.TcapFactory;
import org.mobicents.protocols.ss7.tcap.asn.UserInformation;
import org.mobicents.protocols.ss7.tcap.asn.Utils;
import org.mobicents.protocols.ss7.tcap.asn.comp.Component;
import org.mobicents.protocols.ss7.tcap.asn.comp.ComponentType;
import org.mobicents.protocols.ss7.tcap.asn.comp.InvokeProblemType;
import org.mobicents.protocols.ss7.tcap.asn.comp.PAbortCauseType;
import org.mobicents.protocols.ss7.tcap.asn.comp.ProblemType;
import org.mobicents.protocols.ss7.tcap.asn.comp.ReturnErrorProblemType;
import org.mobicents.protocols.ss7.tcap.asn.comp.ReturnResultProblemType;
import org.mobicents.protocols.ss7.tcap.asn.comp.TCAbortMessage;
import org.mobicents.protocols.ss7.tcap.asn.comp.TCBeginMessage;
import org.mobicents.protocols.ss7.tcap.asn.comp.TCContinueMessage;
import org.mobicents.protocols.ss7.tcap.asn.comp.TCEndMessage;
import org.mobicents.protocols.ss7.tcap.asn.comp.TCUniMessage;
import org.mobicents.protocols.ss7.tcap.asn.comp.Reject;
import org.mobicents.protocols.ss7.tcap.asn.comp.Problem;
import org.mobicents.protocols.ss7.tcap.tc.dialog.events.DialogPrimitiveFactoryImpl;
import org.mobicents.protocols.ss7.tcap.tc.dialog.events.TCBeginIndicationImpl;
import org.mobicents.protocols.ss7.tcap.tc.dialog.events.TCContinueIndicationImpl;
import org.mobicents.protocols.ss7.tcap.tc.dialog.events.TCEndIndicationImpl;
import org.mobicents.protocols.ss7.tcap.tc.dialog.events.TCPAbortIndicationImpl;
import org.mobicents.protocols.ss7.tcap.tc.dialog.events.TCUniIndicationImpl;
import org.mobicents.protocols.ss7.tcap.tc.dialog.events.TCUserAbortIndicationImpl;

/**
 * @author baranowb
 * @author amit bhayani
 * @author sergey vetyutnev
 *
 */
public class DialogImpl implements Dialog {

	private static final Logger logger = Logger.getLogger(DialogImpl.class);

	private Object userObject;

	// lock... ech
	protected final ReentrantLock dialogLock = new ReentrantLock();

	// values for timer timeouts
	private long idleTaskTimeout;

	// sent/received acn, holds last acn/ui.
	private ApplicationContextName lastACN;
	private UserInformation lastUI; // optional

	private Long localTransactionIdObject;
	private byte[] remoteTransactionId;
	private Long remoteTransactionIdObject;

	private SccpAddress localAddress;
	private SccpAddress remoteAddress;

	private Future idleTimerFuture;
	private boolean idleTimerActionTaken;
	private boolean idleTimerInvoked;
	private TRPseudoState state = TRPseudoState.Idle;
	private boolean structured = true;
	// invoke ID space :)
	private static final boolean _INVOKEID_TAKEN = true;
	private static final boolean _INVOKEID_FREE = false;
	private static final int _INVOKE_TABLE_SHIFT = 128;

	private boolean[] invokeIDTable = new boolean[256];
	private int freeCount = invokeIDTable.length;
	private int lastInvokeIdIndex = _INVOKE_TABLE_SHIFT - 1;

	// only originating side keeps FSM, see: Q.771 - 3.1.5
	protected InvokeImpl[] operationsSent = new InvokeImpl[invokeIDTable.length];
	private Set<Integer> incomingInvokeList = new HashSet<>();
	private final ScheduledExecutorService executor;

	// scheduled components list
	private List<Component> scheduledComponentList;
	private TCAPProviderImpl provider;

	private int seqControl;

	private int protocolClass;

	// If the Dialogue Portion is sent in TCBegin message, the first received
	// Continue message should have the Dialogue Portion too
	private boolean dpSentInBegin;

	private static int getIndexFromInvokeId(Integer l) {
		int tmp = l.intValue();
		return tmp + _INVOKE_TABLE_SHIFT;
	}

	private static Integer getInvokeIdFromIndex(int index) {
		return index - _INVOKE_TABLE_SHIFT;
	}

	public List<Component> getScheduledComponentList() {
		if (scheduledComponentList == null) {
			scheduledComponentList = new ArrayList<>();
		}
		return scheduledComponentList;
	}

	/**
	 * Creating a Dialog for normal mode
	 *
	 * @param localAddress
	 * @param remoteAddress
	 * @param origTransactionId
	 * @param structured
	 * @param executor
	 * @param provider
	 * @param seqControl
	 * @param previewMode
	 */
	protected DialogImpl(SccpAddress localAddress, SccpAddress remoteAddress, Long origTransactionId,
			boolean structured, ScheduledExecutorService executor, TCAPProviderImpl provider, int seqControl,
			boolean previewMode, int protocolClass) {
		super();
		this.localAddress = localAddress;
		this.remoteAddress = remoteAddress;
		if (origTransactionId != null) {
			this.localTransactionIdObject = origTransactionId;
		}
		this.executor = executor;
		this.provider = provider;
		this.structured = structured;

		this.seqControl = seqControl;
		this.protocolClass = protocolClass;

		TCAPStack stack = this.provider.getStack();
		this.idleTaskTimeout = stack.getDialogIdleTimeout();

		// start
		startIdleTimer();
	}

	public void release() {
        for (InvokeImpl invokeImpl : this.operationsSent) {
            if (invokeImpl != null) {
                invokeImpl.setState(OperationState.Idle);
                // TODO whether to call operationTimedOut or not is still not clear
                // operationTimedOut(invokeImpl);
            }
        }

		this.setState(TRPseudoState.Expunged);
	}

	/*
     * (non-Javadoc)
     *
     * @see org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog#getDialogId()
     */
	public Long getLocalDialogId() {

		return localTransactionIdObject;
	}

	/**
	 *
	 */
	public Long getRemoteDialogId() {
		if (this.remoteTransactionId != null && this.remoteTransactionIdObject == null) {
			this.remoteTransactionIdObject = Utils.decodeTransactionId(this.remoteTransactionId);
		}

		return this.remoteTransactionIdObject;
	}

	/*
     * (non-Javadoc)
     *
	 * @see
	 * org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog#getNewInvokeId()
     */
	public Integer getNewInvokeId() throws TCAPException {
		try {
		this.dialogLock.lock();
			if (this.freeCount == 0) {
				throw new TCAPException("No free invokeId");
			}

			int tryCnt = 0;
			while (true) {
				if (++this.lastInvokeIdIndex >= this.invokeIDTable.length)
					this.lastInvokeIdIndex = 0;
				if (this.invokeIDTable[this.lastInvokeIdIndex] == _INVOKEID_FREE) {
					freeCount--;
					this.invokeIDTable[this.lastInvokeIdIndex] = _INVOKEID_TAKEN;
					return getInvokeIdFromIndex(this.lastInvokeIdIndex);
				}
				if (++tryCnt >= 256)
					throw new TCAPException("No free invokeId");
			}

		} finally {
			this.dialogLock.unlock();
		}
	}

	/*
     * (non-Javadoc)
     *
	 * @see
	 * org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog#cancelInvocation
	 * (java.lang.Long)
     */
	public boolean cancelInvocation(Integer invokeId) throws TCAPException {

		try {
		this.dialogLock.lock();
			int index = getIndexFromInvokeId(invokeId);
			if (index < 0 || index >= this.operationsSent.length) {
				throw new TCAPException("Wrong invoke id passed.");
			}

			// lookup through send buffer.
			for (index = 0; index < this.getScheduledComponentList().size(); index++) {
				Component cr = this.getScheduledComponentList().get(index);
				if (cr.getType() == ComponentType.Invoke && cr.getInvokeId().equals(invokeId)) {
					// lucky
					// TCInvokeRequestImpl invoke = (TCInvokeRequestImpl) cr;
					// there is no notification on cancel?
					this.getScheduledComponentList().remove(index);
					((InvokeImpl) cr).stopTimer();
					((InvokeImpl) cr).setState(OperationState.Idle);
					return true;
				}
			}

			return false;
		} finally {
			this.dialogLock.unlock();
		}
	}

	private void freeInvokeId(Integer i) {
		try {
		this.dialogLock.lock();
			int index = getIndexFromInvokeId(i);
			if (this.invokeIDTable[index] == _INVOKEID_TAKEN)
				this.freeCount++;
			this.invokeIDTable[index] = _INVOKEID_FREE;
		} finally {
			this.dialogLock.unlock();
		}
	}

	/*
     * (non-Javadoc)
     *
	 * @see
	 * org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog#getRemoteAddress()
     */
	public SccpAddress getRemoteAddress() {

		return this.remoteAddress;
	}

	/*
     * (non-Javadoc)
     *
	 * @see
	 * org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog#getLocalAddress()
     */
	public SccpAddress getLocalAddress() {

		return this.localAddress;
	}

	/*
     * (non-Javadoc)
     *
	 * @see
	 * org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog#isEstabilished()
     */
	public boolean isEstabilished() {

		return this.state == TRPseudoState.Active;
	}

	/*
     * (non-Javadoc)
     *
     * @see org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog#isStructured()
     */
	public boolean isStructured() {

		return this.structured;
	}

	public void keepAlive() {

		try {
		this.dialogLock.lock();
			if (this.idleTimerInvoked) {
				this.idleTimerActionTaken = true;
			}

		} finally {
			this.dialogLock.unlock();
		}

	}

	@Override
	public ReentrantLock getDialogLock() {
		return this.dialogLock;
	}

	/**
	 * @return the acn
	 */
	public ApplicationContextName getApplicationContextName() {
		return lastACN;
	}

	/**
	 * @return the ui
	 */
	public UserInformation getUserInformation() {
		return lastUI;
	}
	/**
	 * Adding the new incoming invokeId into incomingInvokeList list
	 *
	 * @param invokeId
	 * @return false: failure - this invokeId already present in the list
	 */
	private boolean addIncomingInvokeId(Integer invokeId) {
		synchronized (this.incomingInvokeList) {
			if (this.incomingInvokeList.contains(invokeId))
				return false;
			else {
				this.incomingInvokeList.add(invokeId);
				return true;
			}
		}
	}

	private void removeIncomingInvokeId(Integer invokeId) {
		synchronized (this.incomingInvokeList) {
			this.incomingInvokeList.remove(invokeId);
		}
	}

	/*
     * (non-Javadoc)
     *
	 * @see
	 * org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog#send(org.mobicents
     * .protocols.ss7.tcap.api.tc.dialog.events.TCBeginRequest)
     */
	public void send(TCBeginRequest event) throws TCAPSendException {

		if (this.state != TRPseudoState.Idle) {
			throw new TCAPSendException("Can not send Begin in this state: " + this.state);
		}

		if (!this.isStructured()) {
			throw new TCAPSendException("Unstructured dialogs do not use Begin");
		}
		try {
		this.dialogLock.lock();
			this.idleTimerActionTaken = true;
			restartIdleTimer();
			TCBeginMessageImpl tcbm = (TCBeginMessageImpl) TcapFactory.createTCBeginMessage();

			// build DP

			if (event.getApplicationContextName() != null) {
				this.dpSentInBegin = true;
				DialogPortion dp = TcapFactory.createDialogPortion();
				dp.setUnidirectional(false);
				DialogRequestAPDU apdu = TcapFactory.createDialogAPDURequest();
				apdu.setDoNotSendProtocolVersion(this.provider.getStack().getDoNotSendProtocolVersion());
				dp.setDialogAPDU(apdu);
				apdu.setApplicationContextName(event.getApplicationContextName());
				this.lastACN = event.getApplicationContextName();
				if (event.getUserInformation() != null) {
					apdu.setUserInformation(event.getUserInformation());
					this.lastUI = event.getUserInformation();
				}
				tcbm.setDialogPortion(dp);
			}

			// now comps
			tcbm.setOriginatingTransactionId(Utils.encodeTransactionId(this.localTransactionIdObject));
			if (this.getScheduledComponentList().size() > 0) {
				List<Component> componentsToSend = new ArrayList<Component>(this.getScheduledComponentList().size());
				this.prepareComponents(componentsToSend);
				tcbm.setComponent(componentsToSend);
			}

			AsnOutputStream aos = new AsnOutputStream();
			try {
				tcbm.encode(aos);
				this.setState(TRPseudoState.InitialSent);
				this.provider.send(aos.toByteArray(), event.getReturnMessageOnError(), this.remoteAddress,
						this.localAddress, this.seqControl, this.protocolClass);
				this.getScheduledComponentList().clear();
			} catch (Throwable e) {
				// FIXME: remove freshly added invokes to free invoke ID??
				release();
				if (logger.isEnabledFor(Level.ERROR)) {
					logger.error("Failed to send message: ", e);
				}
				throw new TCAPSendException("Failed to send TC-Begin message: " + e.getMessage(), e);
			}

		} finally {
			this.dialogLock.unlock();
		}
	}

	/*
     * (non-Javadoc)
     *
	 * @see
	 * org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog#send(org.mobicents
     * .protocols.ss7.tcap.api.tc.dialog.events.TCContinueRequest)
     */
	public void send(TCContinueRequest event) throws TCAPSendException {

		if (!this.isStructured()) {
			throw new TCAPSendException("Unstructured dialogs do not use Continue");
		}
		try {
		this.dialogLock.lock();
			if (this.state == TRPseudoState.InitialReceived) {
				this.idleTimerActionTaken = true;
				restartIdleTimer();
				TCContinueMessageImpl tcbm = (TCContinueMessageImpl) TcapFactory.createTCContinueMessage();

				if (event.getApplicationContextName() != null) {

					// set dialog portion
					DialogPortion dp = TcapFactory.createDialogPortion();
					dp.setUnidirectional(false);
					DialogResponseAPDU apdu = TcapFactory.createDialogAPDUResponse();
					apdu.setDoNotSendProtocolVersion(this.provider.getStack().getDoNotSendProtocolVersion());
					dp.setDialogAPDU(apdu);
					apdu.setApplicationContextName(event.getApplicationContextName());
					if (event.getUserInformation() != null) {
						apdu.setUserInformation(event.getUserInformation());
					}
					// WHERE THE HELL THIS COMES FROM!!!!
					// WHEN REJECTED IS USED !!!!!
					Result res = TcapFactory.createResult();
					res.setResultType(ResultType.Accepted);
					ResultSourceDiagnostic rsd = TcapFactory.createResultSourceDiagnostic();
					rsd.setDialogServiceUserType(DialogServiceUserType.Null);
					apdu.setResultSourceDiagnostic(rsd);
					apdu.setResult(res);
					tcbm.setDialogPortion(dp);

				}

				tcbm.setOriginatingTransactionId(Utils.encodeTransactionId(this.localTransactionIdObject));
				tcbm.setDestinationTransactionId(this.remoteTransactionId);
				if (this.getScheduledComponentList().size() > 0) {
					List<Component> componentsToSend = new ArrayList<Component>(this.getScheduledComponentList().size());
					this.prepareComponents(componentsToSend);
					tcbm.setComponent(componentsToSend);

				}
				// local address may change, lets check it
				if (event.getOriginatingAddress() != null) {
					this.localAddress = event.getOriginatingAddress();
				}
				AsnOutputStream aos = new AsnOutputStream();
				try {
					tcbm.encode(aos);
					this.provider.send(aos.toByteArray(), event.getReturnMessageOnError(), this.remoteAddress,
							this.localAddress, this.seqControl, this.protocolClass);
					this.setState(TRPseudoState.Active);
					this.getScheduledComponentList().clear();
				} catch (Exception e) {
					// FIXME: remove freshly added invokes to free invoke ID??
					if (logger.isEnabledFor(Level.ERROR)) {
						logger.error("Failed to send message: ", e);
					}
					throw new TCAPSendException("Failed to send TC-Continue message: " + e.getMessage(), e);
				}

			} else if (state == TRPseudoState.Active) {
				this.idleTimerActionTaken = true;
				restartIdleTimer();
				// in this we ignore acn and passed args(except qos)
				TCContinueMessageImpl tcbm = (TCContinueMessageImpl) TcapFactory.createTCContinueMessage();

				tcbm.setOriginatingTransactionId(Utils.encodeTransactionId(this.localTransactionIdObject));
				tcbm.setDestinationTransactionId(this.remoteTransactionId);
				if (this.getScheduledComponentList().size() > 0) {
					List<Component> componentsToSend = new ArrayList<Component>(this.getScheduledComponentList().size());
					this.prepareComponents(componentsToSend);
					tcbm.setComponent(componentsToSend);

				}

				AsnOutputStream aos = new AsnOutputStream();
				try {
					tcbm.encode(aos);
					this.provider.send(aos.toByteArray(), event.getReturnMessageOnError(), this.remoteAddress,
							this.localAddress, this.seqControl, this.protocolClass);
					this.getScheduledComponentList().clear();
				} catch (Exception e) {
					// FIXME: remove freshly added invokes to free invoke ID??
					if (logger.isEnabledFor(Level.ERROR)) {
						logger.error("Failed to send message: ", e);
					}
					throw new TCAPSendException("Failed to send TC-Continue message: " + e.getMessage(), e);
				}
			} else {
				throw new TCAPSendException("Wrong state: " + this.state);
			}

		} finally {
			this.dialogLock.unlock();
		}
	}

	/*
     * (non-Javadoc)
     *
	 * @see
	 * org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog#send(org.mobicents
     * .protocols.ss7.tcap.api.tc.dialog.events.TCEndRequest)
     */
	public void send(TCEndRequest event) throws TCAPSendException {

		if (!this.isStructured()) {
			throw new TCAPSendException("Unstructured dialogs do not use End");
		}

		try {
		dialogLock.lock();
			TCEndMessageImpl tcbm = null;

			if (state == TRPseudoState.InitialReceived) {
				// TC-END request primitive issued in response to a TC-BEGIN
				// indication primitive
				this.idleTimerActionTaken = true;

				if (event.getTerminationType() != TerminationType.Basic) {
					// we do not send TC-END in PreArranged closing case
					release();
					return;
				}

				stopIdleTimer();

				tcbm = (TCEndMessageImpl) TcapFactory.createTCEndMessage();
				tcbm.setDestinationTransactionId(this.remoteTransactionId);

				if (this.getScheduledComponentList().size() > 0) {
					List<Component> componentsToSend = new ArrayList<Component>(this.getScheduledComponentList().size());
					this.prepareComponents(componentsToSend);
					tcbm.setComponent(componentsToSend);
				}

				ApplicationContextName acn = event.getApplicationContextName();
				if (acn != null) { // acn & DialogPortion is absent in TCAP V1

					// set dialog portion
					DialogPortion dp = TcapFactory.createDialogPortion();
					dp.setUnidirectional(false);
					DialogResponseAPDU apdu = TcapFactory.createDialogAPDUResponse();
					apdu.setDoNotSendProtocolVersion(this.provider.getStack().getDoNotSendProtocolVersion());
					dp.setDialogAPDU(apdu);

					apdu.setApplicationContextName(event.getApplicationContextName());
					if (event.getUserInformation() != null) {
						apdu.setUserInformation(event.getUserInformation());
					}

					// WHERE THE HELL THIS COMES FROM!!!!
					// WHEN REJECTED IS USED !!!!!
					Result res = TcapFactory.createResult();
					res.setResultType(ResultType.Accepted);
					ResultSourceDiagnostic rsd = TcapFactory.createResultSourceDiagnostic();
					rsd.setDialogServiceUserType(DialogServiceUserType.Null);
					apdu.setResultSourceDiagnostic(rsd);
					apdu.setResult(res);
					tcbm.setDialogPortion(dp);
				}
				// local address may change, lets check it
				if (event.getOriginatingAddress() != null) {
					this.localAddress = event.getOriginatingAddress();
				}

			} else if (state == TRPseudoState.Active) {

				if (event.getTerminationType() != TerminationType.Basic) {
					// we do not send TC-END in PreArranged closing case
					release();
					return;
				}

				restartIdleTimer();

				tcbm = (TCEndMessageImpl) TcapFactory.createTCEndMessage();
				tcbm.setDestinationTransactionId(this.remoteTransactionId);

				if (this.getScheduledComponentList().size() > 0) {
					List<Component> componentsToSend = new ArrayList<Component>(this.getScheduledComponentList().size());
					this.prepareComponents(componentsToSend);
					tcbm.setComponent(componentsToSend);
				}

				// ITU - T Q774 Section 3.2.2.1 Dialogue Control

				// when a dialogue portion is received inopportunely (e.g. a
				// dialogue APDU is received during the active state of a
				// transaction).

				// Don't set the Application Context or Dialogue Portion in
				// Active state

			} else {
				throw new TCAPSendException(String.format("State is not %s or %s: it is %s", TRPseudoState.Active,
						TRPseudoState.InitialReceived, this.state));
			}

			AsnOutputStream aos = new AsnOutputStream();
			try {
				tcbm.encode(aos);
				this.provider.send(aos.toByteArray(), event.getReturnMessageOnError(), this.remoteAddress,
						this.localAddress, this.seqControl, this.protocolClass);

				this.getScheduledComponentList().clear();
			} catch (Exception e) {
				// FIXME: remove freshly added invokes to free invoke ID??
				if (logger.isEnabledFor(Level.ERROR)) {
					logger.error("Failed to send message: ", e);
				}
				throw new TCAPSendException("Failed to send TC-End message: " + e.getMessage(), e);
			} finally {
				// FIXME: is this proper place - should we not release in case
				// of error ?
				release();
			}
		} finally {
			dialogLock.unlock();
		}
	}

	/*
     * (non-Javadoc)
     *
     * @see org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog#sendUni()
     */
	public void send(TCUniRequest event) throws TCAPSendException {

		if (this.isStructured()) {
			throw new TCAPSendException("Structured dialogs do not use Uni");
		}

		try {
		this.dialogLock.lock();
			TCUniMessageImpl msg = (TCUniMessageImpl) TcapFactory.createTCUniMessage();

			if (event.getApplicationContextName() != null) {
				DialogPortion dp = TcapFactory.createDialogPortion();
				DialogUniAPDU apdu = TcapFactory.createDialogAPDUUni();
				apdu.setDoNotSendProtocolVersion(this.provider.getStack().getDoNotSendProtocolVersion());
				apdu.setApplicationContextName(event.getApplicationContextName());
				if (event.getUserInformation() != null) {
					apdu.setUserInformation(event.getUserInformation());
				}
				dp.setUnidirectional(true);
				dp.setDialogAPDU(apdu);
				msg.setDialogPortion(dp);

			}

			if (this.getScheduledComponentList().size() > 0) {
				List<Component> componentsToSend = new ArrayList<Component>(this.getScheduledComponentList().size());
				this.prepareComponents(componentsToSend);
				msg.setComponent(componentsToSend);

			}

			AsnOutputStream aos = new AsnOutputStream();
			try {
				msg.encode(aos);
				this.provider.send(aos.toByteArray(), event.getReturnMessageOnError(), this.remoteAddress,
						this.localAddress, this.seqControl, this.protocolClass);
				this.getScheduledComponentList().clear();
			} catch (Exception e) {
				if (logger.isEnabledFor(Level.ERROR)) {
					logger.error("Failed to send message: ", e);
				}
				throw new TCAPSendException("Failed to send TC-Uni message: " + e.getMessage(), e);
			} finally {
				release();
			}
		} finally {
			this.dialogLock.unlock();
		}
	}

	public void send(TCUserAbortRequest event) throws TCAPSendException {

		// is abort allowed in "Active" state ?
		if (!isStructured()) {
			throw new TCAPSendException("Unstructured dialog can not be aborted!");
		}

		try {
		this.dialogLock.lock();

			if (this.state == TRPseudoState.InitialReceived || this.state == TRPseudoState.InitialSent
					|| this.state == TRPseudoState.Active) {
				// allowed
				DialogPortion dp = null;
				if (event.getUserInformation() != null || event.getDialogServiceUserType() != null) {
					// User information can be absent in TCAP V1

					dp = TcapFactory.createDialogPortion();
					dp.setUnidirectional(false);

					if (event.getDialogServiceUserType() != null) {
						// ITU T Q.774 Read Dialogue end on page 12 and 3.2.2
						// Abnormal
						// procedures on page 13 and 14
						DialogResponseAPDU apdu = TcapFactory.createDialogAPDUResponse();
						apdu.setDoNotSendProtocolVersion(this.provider.getStack().getDoNotSendProtocolVersion());
						apdu.setApplicationContextName(event.getApplicationContextName());
						apdu.setUserInformation(event.getUserInformation());

						Result res = TcapFactory.createResult();
						res.setResultType(ResultType.RejectedPermanent);
						ResultSourceDiagnostic rsd = TcapFactory.createResultSourceDiagnostic();
						rsd.setDialogServiceUserType(event.getDialogServiceUserType());
						apdu.setResultSourceDiagnostic(rsd);
						apdu.setResult(res);
						dp.setDialogAPDU(apdu);
					} else {
						// When a BEGIN message has been received (i.e. the
						// dialogue
						// is
						// in the "Initiation Received" state) containing a
						// Dialogue
						// Request (AARQ) APDU, the TC-User can abort for any
						// user
						// defined reason. In such a situation, the TC-User
						// issues a
						// TC-U-ABORT request primitive with the Abort Reason
						// parameter
						// absent or with set to any value other than either
						// "application-context-name-not-supported" or
						// dialogue-refused". In such a case, a Dialogue Abort (ABRT) APDU is generated with abort-source coded as "dialogue-service-user",
						// and supplied as the User Data parameter of the
						// TR-U-ABORT
						// request primitive. User information (if any) provided
						// in
						// the
						// TC-U-ABORT request primitive is coded in the
						// user-information
						// field of the ABRT APDU.
						DialogAbortAPDU dapdu = TcapFactory.createDialogAPDUAbort();

						AbortSource as = TcapFactory.createAbortSource();
						as.setAbortSourceType(AbortSourceType.User);
						dapdu.setAbortSource(as);
						dapdu.setUserInformation(event.getUserInformation());
						dp.setDialogAPDU(dapdu);
					}
				}

				if (state == TRPseudoState.InitialReceived) {
					// local address may change, lets check it
					if (event.getOriginatingAddress() != null) {
						this.localAddress = event.getOriginatingAddress();
					}
				}

				TCAbortMessageImpl msg = (TCAbortMessageImpl) TcapFactory.createTCAbortMessage();
				msg.setDestinationTransactionId(this.remoteTransactionId);
				msg.setDialogPortion(dp);

				// no components
				AsnOutputStream aos = new AsnOutputStream();
				try {
					msg.encode(aos);
					this.provider.send(aos.toByteArray(), event.getReturnMessageOnError(), this.remoteAddress,
							this.localAddress, this.seqControl, this.protocolClass);

					this.getScheduledComponentList().clear();
				} catch (Exception e) {
					// FIXME: remove freshly added invokes to free invoke ID??
					if (logger.isEnabledFor(Level.ERROR)) {
						logger.error("Failed to send message: ", e);
					}
					throw new TCAPSendException("Failed to send TC-U-Abort message: " + e.getMessage(), e);
				} finally {
					release();
				}
			}
		} finally {
			this.dialogLock.unlock();
		}
	}

	/*
     * (non-Javadoc)
     *
	 * @see
	 * org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog#sendComponent(org
     * .mobicents.protocols.ss7.tcap.api.tc.component.ComponentRequest)
     */
	public void sendComponent(Component componentRequest) throws TCAPSendException {

		try {
		this.dialogLock.lock();
			if (componentRequest.getType() == ComponentType.Invoke) {
				InvokeImpl invoke = (InvokeImpl) componentRequest;

				// check if its taken!
				int invokeIndex = getIndexFromInvokeId(invoke.getInvokeId());
				if (this.operationsSent[invokeIndex] != null) {
					throw new TCAPSendException("There is already operation with such invoke id!");
				}

				invoke.setState(OperationState.Pending);
				invoke.setDialog(this);

				// if the Invoke timeout value has not be reset by TCAP-User
				// for this invocation we are setting it to the the TCAP stack
				// default value
				if (invoke.getTimeout() == TCAPStackImpl._EMPTY_INVOKE_TIMEOUT)
					invoke.setTimeout(this.provider.getStack().getInvokeTimeout());
			} else {
				if (componentRequest.getType() != ComponentType.ReturnResult) {
					// we are sending a response and removing invokeId from
					// incomingInvokeList
					this.removeIncomingInvokeId(componentRequest.getInvokeId());
				}
			}
			this.getScheduledComponentList().add(componentRequest);
		} finally {
			this.dialogLock.unlock();
		}
	}

	public void processInvokeWithoutAnswer(Integer invokeId) {

		this.removeIncomingInvokeId(invokeId);
	}

	private void prepareComponents(List<Component> res) {
		for (Component c : this.getScheduledComponentList()) {
			if (c.getType() == ComponentType.Invoke) {
				InvokeImpl in = (InvokeImpl) c;
				this.operationsSent[getIndexFromInvokeId(in.getInvokeId())] = in;
				in.setState(OperationState.Sent);
			}
			res.add(c);
		}
	}

	public int getMaxUserDataLength() {

		return this.provider.getMaxUserDataLength(remoteAddress, localAddress);
	}

	public int getDataLength(TCBeginRequest event) throws TCAPSendException {

		TCBeginMessageImpl tcbm = (TCBeginMessageImpl) TcapFactory.createTCBeginMessage();

		if (event.getApplicationContextName() != null) {
			DialogPortion dp = TcapFactory.createDialogPortion();
			dp.setUnidirectional(false);
			DialogRequestAPDU apdu = TcapFactory.createDialogAPDURequest();
			apdu.setDoNotSendProtocolVersion(this.provider.getStack().getDoNotSendProtocolVersion());
			dp.setDialogAPDU(apdu);
			apdu.setApplicationContextName(event.getApplicationContextName());
			if (event.getUserInformation() != null) {
				apdu.setUserInformation(event.getUserInformation());
			}
			tcbm.setDialogPortion(dp);
		}

		// now comps
		tcbm.setOriginatingTransactionId(Utils.encodeTransactionId(this.localTransactionIdObject));
		if (this.getScheduledComponentList().size() > 0) {
			List<Component> componentsToSend = new ArrayList<Component>(this.getScheduledComponentList());
			tcbm.setComponent(componentsToSend);
		}

		AsnOutputStream aos = new AsnOutputStream();
		try {
			tcbm.encode(aos);
		} catch (EncodeException e) {
			if (logger.isEnabledFor(Level.ERROR)) {
				logger.error("Failed to encode message while length testing: ", e);
			}
			throw new TCAPSendException("Error encoding TCBeginRequest", e);
		}
		return aos.size();
	}

	public int getDataLength(TCContinueRequest event) throws TCAPSendException {

		TCContinueMessageImpl tcbm = (TCContinueMessageImpl) TcapFactory.createTCContinueMessage();

		if (event.getApplicationContextName() != null) {

			// set dialog portion
			DialogPortion dp = TcapFactory.createDialogPortion();
			dp.setUnidirectional(false);
			DialogResponseAPDU apdu = TcapFactory.createDialogAPDUResponse();
			apdu.setDoNotSendProtocolVersion(this.provider.getStack().getDoNotSendProtocolVersion());
			dp.setDialogAPDU(apdu);
			apdu.setApplicationContextName(event.getApplicationContextName());
			if (event.getUserInformation() != null) {
				apdu.setUserInformation(event.getUserInformation());
			}
			// WHERE THE HELL THIS COMES FROM!!!!
			// WHEN REJECTED IS USED !!!!!
			Result res = TcapFactory.createResult();
			res.setResultType(ResultType.Accepted);
			ResultSourceDiagnostic rsd = TcapFactory.createResultSourceDiagnostic();
			rsd.setDialogServiceUserType(DialogServiceUserType.Null);
			apdu.setResultSourceDiagnostic(rsd);
			apdu.setResult(res);
			tcbm.setDialogPortion(dp);

		}

		tcbm.setOriginatingTransactionId(Utils.encodeTransactionId(this.localTransactionIdObject));
		tcbm.setDestinationTransactionId(this.remoteTransactionId);
		if (this.getScheduledComponentList().size() > 0) {
			List<Component> componentsToSend = new ArrayList<Component>(this.getScheduledComponentList());
			tcbm.setComponent(componentsToSend);
		}

		AsnOutputStream aos = new AsnOutputStream();
		try {
			tcbm.encode(aos);
		} catch (Exception e) {
			if (logger.isEnabledFor(Level.ERROR)) {
				logger.error("Failed to encode message while length testing: ", e);
			}
			throw new TCAPSendException("Error encoding TCContinueRequest", e);
		}

		return aos.size();
	}

	public int getDataLength(TCEndRequest event) throws TCAPSendException {

		// TC-END request primitive issued in response to a TC-BEGIN
		// indication primitive
		TCEndMessageImpl tcbm = (TCEndMessageImpl) TcapFactory.createTCEndMessage();
		tcbm.setDestinationTransactionId(this.remoteTransactionId);

		if (this.getScheduledComponentList().size() > 0) {
			List<Component> componentsToSend = new ArrayList<Component>(this.getScheduledComponentList());
			tcbm.setComponent(componentsToSend);
		}

		if (state == TRPseudoState.InitialReceived) {
			ApplicationContextName acn = event.getApplicationContextName();
			if (acn != null) { // acn & DialogPortion is absent in TCAP V1

				// set dialog portion
				DialogPortion dp = TcapFactory.createDialogPortion();
				dp.setUnidirectional(false);
				DialogResponseAPDU apdu = TcapFactory.createDialogAPDUResponse();
				apdu.setDoNotSendProtocolVersion(this.provider.getStack().getDoNotSendProtocolVersion());
				dp.setDialogAPDU(apdu);

				apdu.setApplicationContextName(event.getApplicationContextName());
				if (event.getUserInformation() != null) {
					apdu.setUserInformation(event.getUserInformation());
				}

				// WHERE THE HELL THIS COMES FROM!!!!
				// WHEN REJECTED IS USED !!!!!
				Result res = TcapFactory.createResult();
				res.setResultType(ResultType.Accepted);
				ResultSourceDiagnostic rsd = TcapFactory.createResultSourceDiagnostic();
				rsd.setDialogServiceUserType(DialogServiceUserType.Null);
				apdu.setResultSourceDiagnostic(rsd);
				apdu.setResult(res);
				tcbm.setDialogPortion(dp);
			}
		}

		AsnOutputStream aos = new AsnOutputStream();
		try {
			tcbm.encode(aos);
		} catch (Exception e) {
			if (logger.isEnabledFor(Level.ERROR)) {
				logger.error("Failed to encode message while length testing: ", e);
			}
			throw new TCAPSendException("Error encoding TCEndRequest", e);
		}

		return aos.size();
	}

	public int getDataLength(TCUniRequest event) throws TCAPSendException {

		TCUniMessageImpl msg = (TCUniMessageImpl) TcapFactory.createTCUniMessage();

		if (event.getApplicationContextName() != null) {
			DialogPortion dp = TcapFactory.createDialogPortion();
			DialogUniAPDU apdu = TcapFactory.createDialogAPDUUni();
			apdu.setDoNotSendProtocolVersion(this.provider.getStack().getDoNotSendProtocolVersion());
			apdu.setApplicationContextName(event.getApplicationContextName());
			if (event.getUserInformation() != null) {
				apdu.setUserInformation(event.getUserInformation());
			}
			dp.setUnidirectional(true);
			dp.setDialogAPDU(apdu);
			msg.setDialogPortion(dp);

		}

		if (this.getScheduledComponentList().size() > 0) {
			List<Component> componentsToSend = new ArrayList<Component>(this.getScheduledComponentList());
			msg.setComponent(componentsToSend);
		}

		AsnOutputStream aos = new AsnOutputStream();
		try {
			msg.encode(aos);
		} catch (Exception e) {
			if (logger.isEnabledFor(Level.ERROR)) {
				logger.error("Failed to encode message while length testing: ", e);
			}
			throw new TCAPSendException("Error encoding TCUniRequest", e);
		}

		return aos.size();
	}

	// /////////////////
	// LOCAL METHODS //
	// /////////////////

	/**
	 * @param remoteTransactionId
	 *            the remoteTransactionId to set
	 */
	void setRemoteTransactionId(byte[] remoteTransactionId) {
		this.remoteTransactionId = remoteTransactionId;
	}

	/**
	 * @param localAddress
	 *            the localAddress to set
	 */
	public void setLocalAddress(SccpAddress localAddress) {
		this.localAddress = localAddress;
	}

	/**
	 * @param remoteAddress
	 *            the remoteAddress to set
	 */
	public void setRemoteAddress(SccpAddress remoteAddress) {
		this.remoteAddress = remoteAddress;
	}

	void processUni(TCUniMessage msg, SccpAddress localAddress, SccpAddress remoteAddress) {

		try {
		this.dialogLock.lock();

			try {
				this.setRemoteAddress(remoteAddress);
				this.setLocalAddress(localAddress);

				// no dialog portion!
				// convert to indications
				TCUniIndicationImpl tcUniIndication = (TCUniIndicationImpl) ((DialogPrimitiveFactoryImpl) this.provider.getDialogPrimitiveFactory())
						.createUniIndication(this);

				tcUniIndication.setDestinationAddress(localAddress);
				tcUniIndication.setOriginatingAddress(remoteAddress);
				// now comps
				tcUniIndication.setComponents(msg.getComponent());

				if (msg.getDialogPortion() != null) {
					// it should be dialog req?
					DialogPortion dp = msg.getDialogPortion();
					DialogUniAPDU apdu = (DialogUniAPDU) dp.getDialogAPDU();
					this.lastACN = apdu.getApplicationContextName();
					this.lastUI = apdu.getUserInformation();
					tcUniIndication.setApplicationContextName(this.lastACN);
					tcUniIndication.setUserInformation(this.lastUI);
				}

				// lets deliver to provider, this MUST not throw anything
				this.provider.deliver(this, tcUniIndication);

			} finally {
				this.release();
			}
		} finally {
			this.dialogLock.unlock();
		}
	}

	protected void processBegin(TCBeginMessage msg, SccpAddress localAddress, SccpAddress remoteAddress) {

		TCBeginIndicationImpl tcBeginIndication = null;
		try {
		this.dialogLock.lock();

			// this is invoked ONLY for server.
			if (state != TRPseudoState.Idle) {
				// should we terminate dialog here?
				if (logger.isEnabledFor(Level.ERROR)) {
					logger.error("Received Begin primitive, but state is not: " + TRPseudoState.Idle + ". Dialog: "
							+ this);
				}
				this.sendAbnormalDialog();
				return;
			}
			restartIdleTimer();

			// lets setup
			this.setRemoteAddress(remoteAddress);
			this.setLocalAddress(localAddress);
			this.setRemoteTransactionId(msg.getOriginatingTransactionId());
			// convert to indications
			tcBeginIndication = (TCBeginIndicationImpl) ((DialogPrimitiveFactoryImpl) this.provider
					.getDialogPrimitiveFactory()).createBeginIndication(this);

			tcBeginIndication.setDestinationAddress(localAddress);
			tcBeginIndication.setOriginatingAddress(remoteAddress);

			// if APDU and context data present, lets store it
			DialogPortion dialogPortion = msg.getDialogPortion();

			if (dialogPortion != null) {
				// this should not be null....
				DialogAPDU apdu = dialogPortion.getDialogAPDU();
				if (apdu.getType() != DialogAPDUType.Request) {
					if (logger.isEnabledFor(Level.ERROR)) {
						logger.error("Received non-Request APDU: " + apdu.getType() + ". Dialog: " + this);
					}
					this.sendAbnormalDialog();
					return;
				}
				DialogRequestAPDU requestAPDU = (DialogRequestAPDU) apdu;
				this.lastACN = requestAPDU.getApplicationContextName();
				this.lastUI = requestAPDU.getUserInformation();
				tcBeginIndication.setApplicationContextName(this.lastACN);
				tcBeginIndication.setUserInformation(this.lastUI);
			}
			tcBeginIndication.setComponents(processOperationsState(msg.getComponent()));

			// change state - before we deliver
			this.setState(TRPseudoState.InitialReceived);

			// lets deliver to provider
			this.provider.deliver(this, tcBeginIndication);

		} finally {
			this.dialogLock.unlock();
		}
	}

	protected void processContinue(TCContinueMessage msg, SccpAddress localAddress, SccpAddress remoteAddress) {

		TCContinueIndicationImpl tcContinueIndication = null;
		try {
		this.dialogLock.lock();

			if (state == TRPseudoState.InitialSent) {
				restartIdleTimer();
				tcContinueIndication = (TCContinueIndicationImpl) ((DialogPrimitiveFactoryImpl) this.provider
						.getDialogPrimitiveFactory()).createContinueIndication(this);
				// in continue remote address MAY change be cjanged, so lets
				// update!
				this.setRemoteAddress(remoteAddress);
				this.setRemoteTransactionId(msg.getOriginatingTransactionId());
				tcContinueIndication.setOriginatingAddress(remoteAddress);

				// here we will receive DialogResponse APDU - if request was
				// present!
				DialogPortion dialogPortion = msg.getDialogPortion();
				if (dialogPortion != null) {
					// this should not be null....
					DialogAPDU apdu = dialogPortion.getDialogAPDU();
					if (apdu.getType() != DialogAPDUType.Response) {
						if (logger.isEnabledFor(Level.ERROR)) {
							logger.error("Received non-Response APDU: " + apdu.getType() + ". Dialog: " + this);
						}
						this.sendAbnormalDialog();
						return;
					}
					DialogResponseAPDU responseAPDU = (DialogResponseAPDU) apdu;
					// this will be present if APDU is present.
					if (!responseAPDU.getApplicationContextName().equals(this.lastACN)) {
						this.lastACN = responseAPDU.getApplicationContextName();
					}
					if (responseAPDU.getUserInformation() != null) {
						this.lastUI = responseAPDU.getUserInformation();
					}
					tcContinueIndication.setApplicationContextName(responseAPDU.getApplicationContextName());
					tcContinueIndication.setUserInformation(responseAPDU.getUserInformation());
				} else if (this.dpSentInBegin) {
					// ITU - T Q.774 3.2.2 : Abnormal procedure page 13

					// when a dialogue portion is missing when its presence
					// is
					// mandatory (e.g. an AARQ APDU was sent in a Begin
					// message,
					// but
					// no AARE APDU was received in the first backward
					// Continue
					// message) or when a dialogue portion is received
					// inopportunely
					// (e.g. a dialogue APDU is received during the active
					// state
					// of
					// a transaction). At the side where the abnormality is
					// detected, a TC-P-ABORT indication primitive is issued
					// to
					// the
					// local TC-user with the "P-Abort" parameter in the
					// primitive
					// set to "abnormal dialogue". At the same time, a
					// TR-U-ABORT
					// request primitive is issued to the transaction
					// sub-layer
					// with
					// an ABRT APDU as user data. The abort-source field of
					// the
					// ABRT
					// APDU is set to "dialogue-service-provider" and the
					// user
					// information field is absent.

					sendAbnormalDialog();
					return;

				}
				tcContinueIndication.setOriginatingAddress(remoteAddress);
				// now comps
				tcContinueIndication.setComponents(processOperationsState(msg.getComponent()));
				// change state
				this.setState(TRPseudoState.Active);

				// lets deliver to provider
				this.provider.deliver(this, tcContinueIndication);

			} else if (state == TRPseudoState.Active) {
				restartIdleTimer();
				// XXX: here NO APDU will be present, hence, no ACN/UI change
				tcContinueIndication = (TCContinueIndicationImpl) ((DialogPrimitiveFactoryImpl) this.provider
						.getDialogPrimitiveFactory()).createContinueIndication(this);

				tcContinueIndication.setOriginatingAddress(remoteAddress);

				// now comps
				tcContinueIndication.setComponents(processOperationsState(msg.getComponent()));

				// lets deliver to provider
				this.provider.deliver(this, tcContinueIndication);

			} else {
				if (logger.isEnabledFor(Level.ERROR)) {
					logger.error("Received Continue primitive, but state is not proper: " + this.state
							+ ", Dialog: " + this);
				}
				this.sendAbnormalDialog();
            }

		} finally {
			this.dialogLock.unlock();
		}
	}

	protected void processEnd(TCEndMessage msg, SccpAddress localAddress, SccpAddress remoteAddress) {
		TCEndIndicationImpl tcEndIndication = null;
		try {
		this.dialogLock.lock();

			try {
				restartIdleTimer();
				tcEndIndication = (TCEndIndicationImpl) ((DialogPrimitiveFactoryImpl) this.provider
						.getDialogPrimitiveFactory()).createEndIndication(this);

				if (state == TRPseudoState.InitialSent) {
					// in end remote address MAY change be changed, so lets
					// update!
					this.setRemoteAddress(remoteAddress);
					tcEndIndication.setOriginatingAddress(remoteAddress);
				}

				DialogPortion dialogPortion = msg.getDialogPortion();
				if (dialogPortion != null) {
					DialogAPDU apdu = dialogPortion.getDialogAPDU();
					if (apdu.getType() != DialogAPDUType.Response) {
						if (logger.isEnabledFor(Level.ERROR)) {
							logger.error("Received non-Response APDU: " + apdu.getType() + ". Dialog: " + this);
						}
						// we do not send "this.sendAbnormalDialog()"
						// because no sense to send an answer to TC-END
						return;
					}
					DialogResponseAPDU responseAPDU = (DialogResponseAPDU) apdu;
					// this will be present if APDU is present.
					if (!responseAPDU.getApplicationContextName().equals(this.lastACN)) {
						this.lastACN = responseAPDU.getApplicationContextName();
					}
					if (responseAPDU.getUserInformation() != null) {
						this.lastUI = responseAPDU.getUserInformation();
					}
					tcEndIndication.setApplicationContextName(responseAPDU.getApplicationContextName());
					tcEndIndication.setUserInformation(responseAPDU.getUserInformation());

				}
				// now comps
				tcEndIndication.setComponents(processOperationsState(msg.getComponent()));

				this.provider.deliver(this, tcEndIndication);

			} finally {
				release();
			}
		} finally {
			this.dialogLock.unlock();
		}
	}

	protected void processAbort(TCAbortMessage msg, SccpAddress localAddress2, SccpAddress remoteAddress2) {

		try {
		this.dialogLock.lock();

			try {
				boolean IsAareApdu = false;
				boolean IsAbrtApdu = false;
				ApplicationContextName acn = null;
				ResultSourceDiagnostic resultSourceDiagnostic = null;
				AbortSource abrtSrc = null;
				UserInformation userInfo = null;
				DialogPortion dp = msg.getDialogPortion();
				if (dp != null) {
					DialogAPDU apdu = dp.getDialogAPDU();
					if (apdu != null && apdu.getType() == DialogAPDUType.Abort) {
						IsAbrtApdu = true;
						DialogAbortAPDU abortApdu = (DialogAbortAPDU) apdu;
						abrtSrc = abortApdu.getAbortSource();
						userInfo = abortApdu.getUserInformation();
					}
					if (apdu != null && apdu.getType() == DialogAPDUType.Response) {
						IsAareApdu = true;
						DialogResponseAPDU resptApdu = (DialogResponseAPDU) apdu;
						acn = resptApdu.getApplicationContextName();
						resultSourceDiagnostic = resptApdu.getResultSourceDiagnostic();
						userInfo = resptApdu.getUserInformation();
					}
				}

				PAbortCauseType type = msg.getPAbortCause();
				if (type == null) {
					if ((abrtSrc != null && abrtSrc.getAbortSourceType() == AbortSourceType.Provider)) {
						type = PAbortCauseType.AbnormalDialogue;
					}
					if ((resultSourceDiagnostic != null && resultSourceDiagnostic.getDialogServiceProviderType() != null)) {
						if (resultSourceDiagnostic.getDialogServiceProviderType() == DialogServiceProviderType.NoCommonDialogPortion)
							type = PAbortCauseType.NoCommonDialoguePortion;
						else
							type = PAbortCauseType.NoReasonGiven;
					}
				}

				if (type != null) {

					// its TC-P-Abort
					TCPAbortIndicationImpl tcAbortIndication = (TCPAbortIndicationImpl) ((DialogPrimitiveFactoryImpl) this.provider
							.getDialogPrimitiveFactory()).createPAbortIndication(this);
					tcAbortIndication.setPAbortCause(type);

					this.provider.deliver(this, tcAbortIndication);

				} else {
					// its TC-U-Abort
					TCUserAbortIndicationImpl tcUAbortIndication = (TCUserAbortIndicationImpl) ((DialogPrimitiveFactoryImpl) this.provider
							.getDialogPrimitiveFactory()).createUAbortIndication(this);
					if (IsAareApdu)
						tcUAbortIndication.SetAareApdu();
					if (IsAbrtApdu)
						tcUAbortIndication.SetAbrtApdu();
					tcUAbortIndication.setUserInformation(userInfo);
					tcUAbortIndication.setAbortSource(abrtSrc);
					tcUAbortIndication.setApplicationContextName(acn);
					tcUAbortIndication.setResultSourceDiagnostic(resultSourceDiagnostic);

					this.provider.deliver(this, tcUAbortIndication);
				}
			} finally {
				release();
			}

		} finally {
			this.dialogLock.unlock();
		}
	}

	protected void sendAbnormalDialog() {

		TCPAbortIndicationImpl tcAbortIndication = null;
		try {
		this.dialogLock.lock();

			try {
				if (remoteTransactionId != null) {
					// sending to the remote side
					DialogPortion dp = TcapFactory.createDialogPortion();
					dp.setUnidirectional(false);

					DialogAbortAPDU dapdu = TcapFactory.createDialogAPDUAbort();

					AbortSource as = TcapFactory.createAbortSource();
					as.setAbortSourceType(AbortSourceType.Provider);

					dapdu.setAbortSource(as);
					dp.setDialogAPDU(dapdu);

					TCAbortMessageImpl msg = (TCAbortMessageImpl) TcapFactory.createTCAbortMessage();
					msg.setDestinationTransactionId(this.remoteTransactionId);
					msg.setDialogPortion(dp);

					AsnOutputStream aos = new AsnOutputStream();
					try {
						msg.encode(aos);
						this.provider.send(aos.toByteArray(), false, this.remoteAddress,
								this.localAddress, this.seqControl, this.protocolClass);
					} catch (Exception e) {
						if (logger.isEnabledFor(Level.ERROR)) {
							logger.error("Failed to send message: ", e);
						}
					}
				}

				// sending to the local side
				if (state != TRPseudoState.Expunged) {
					tcAbortIndication = (TCPAbortIndicationImpl) ((DialogPrimitiveFactoryImpl) this.provider
							.getDialogPrimitiveFactory()).createPAbortIndication(this);
					tcAbortIndication.setPAbortCause(PAbortCauseType.ResourceLimitation);
					// tcAbortIndication.setLocalProviderOriginated(true);

					this.provider.deliver(this, tcAbortIndication);
				}
			} finally {
				this.release();
				// this.scheduledComponentList.clear();
			}
		} finally {
			this.dialogLock.unlock();
		}
	}

	protected List<Component> processOperationsState(List<Component> components) {
		if (components == null) {
			return null;
		}

		List<Component> resultingIndications = new ArrayList<>();
		for (Component ci : components) {
			Integer invokeId;
			if (ci.getType() == ComponentType.Invoke)
				invokeId = ((InvokeImpl) ci).getLinkedId();
			else
				invokeId = ci.getInvokeId();
			InvokeImpl invoke = null;
			int index = 0;
			if (invokeId != null) {
				index = getIndexFromInvokeId(invokeId);
				invoke = this.operationsSent[index];
			}

			switch (ci.getType()) {

				case Invoke:
					if (invokeId != null && invoke == null) {
						logger.error(String.format("Rx : %s but no sent Invoke for linkedId exists", ci));

						Problem p = new ProblemImpl();
						p.setInvokeProblemType(InvokeProblemType.UnrechognizedLinkedID);
						this.addReject(resultingIndications, ci.getInvokeId(), p);
					} else {
						if (invoke != null) {
							((InvokeImpl) ci).setLinkedInvoke(invoke);
						}

						if (!this.addIncomingInvokeId(ci.getInvokeId())) {
							logger.error(String.format("Rx : %s but there is already Invoke with this invokeId", ci));

							Problem p = new ProblemImpl();
							p.setInvokeProblemType(InvokeProblemType.DuplicateInvokeID);
							this.addReject(resultingIndications, ci.getInvokeId(), p);
						} else {
							resultingIndications.add(ci);
						}
					}
					break;

				case ReturnResult:

					if (invoke == null) {
						logger.error(String.format("Rx : %s but there is no corresponding Invoke", ci));

						Problem p = new ProblemImpl();
						p.setReturnResultProblemType(ReturnResultProblemType.UnrecognizedInvokeID);
						this.addReject(resultingIndications, ci.getInvokeId(), p);
					} else if (invoke.getInvokeClass() != InvokeClass.Class1 && invoke.getInvokeClass() != InvokeClass.Class3) {
						logger.error(String.format("Rx : %s but Invoke class is not 1 or 3", ci));

						Problem p = new ProblemImpl();
						p.setReturnResultProblemType(ReturnResultProblemType.ReturnResultUnexpected);
						this.addReject(resultingIndications, ci.getInvokeId(), p);
					} else {
						resultingIndications.add(ci);
						ReturnResultImpl rri = (ReturnResultImpl) ci;
						if (rri.getOperationCode() == null)
							rri.setOperationCode(invoke.getOperationCode());
					}
					break;

				case ReturnResultLast:

					if (invoke == null) {
						logger.error(String.format("Rx : %s but there is no corresponding Invoke", ci));

						Problem p = new ProblemImpl();
						p.setType(ProblemType.ReturnResult);
						p.setReturnResultProblemType(ReturnResultProblemType.UnrecognizedInvokeID);
						this.addReject(resultingIndications, ci.getInvokeId(), p);
					} else if (invoke.getInvokeClass() != InvokeClass.Class1 && invoke.getInvokeClass() != InvokeClass.Class3) {
						logger.error(String.format("Rx : %s but Invoke class is not 1 or 3", ci));

						Problem p = new ProblemImpl();
						p.setReturnResultProblemType(ReturnResultProblemType.ReturnResultUnexpected);
						this.addReject(resultingIndications, ci.getInvokeId(), p);
					} else {
						invoke.onReturnResultLast();
						if (invoke.isSuccessReported()) {
							resultingIndications.add(ci);
						}
						ReturnResultLastImpl rri = (ReturnResultLastImpl) ci;
						if (rri.getOperationCode() == null)
							rri.setOperationCode(invoke.getOperationCode());
					}
					break;

				case ReturnError:
					if (invoke == null) {
						logger.error(String.format("Rx : %s but there is no corresponding Invoke", ci));

						Problem p = new ProblemImpl();
						p.setReturnErrorProblemType(ReturnErrorProblemType.UnrecognizedInvokeID);
						this.addReject(resultingIndications, ci.getInvokeId(), p);
					} else if (invoke.getInvokeClass() != InvokeClass.Class1 && invoke.getInvokeClass() != InvokeClass.Class2) {
						logger.error(String.format("Rx : %s but Invoke class is not 1 or 2", ci));

						Problem p = new ProblemImpl();
						p.setReturnErrorProblemType(ReturnErrorProblemType.ReturnErrorUnexpected);
						this.addReject(resultingIndications, ci.getInvokeId(), p);
					} else {
						invoke.onError();
						if (invoke.isErrorReported()) {
							resultingIndications.add(ci);
						}
					}
					break;

				case Reject:
					Reject rej = (Reject) ci;
					if (invoke != null) {
						// If the Reject Problem is the InvokeProblemType we
						// should move the invoke to the idle state
						Problem problem = rej.getProblem();
						if (!rej.isLocalOriginated() && problem.getInvokeProblemType() != null)
							invoke.onReject();
					}
					if (rej.isLocalOriginated() && this.isStructured()) {
						try {
							// this is a local originated Reject - we are rejecting an incoming component
							// we need to send a Reject also to a peer
							this.sendComponent(rej);
						} catch (TCAPSendException e) {
							logger.error("TCAPSendException when sending Reject component : Dialog: " + this, e);
						}
					}
					resultingIndications.add(ci);
					break;

				default:
					resultingIndications.add(ci);
					break;
			}

		}

		return resultingIndications;
	}

	private void addReject(List<Component> resultingIndications, Integer invokeId, Problem p) {
		try {
			Reject rej = TcapFactory.createComponentReject();
			rej.setLocalOriginated(true);
			rej.setInvokeId(invokeId);
			rej.setProblem(p);

			resultingIndications.add(rej);

			if (this.isStructured())
				this.sendComponent(rej);
		} catch (TCAPSendException e) {
			logger.error("Error sending Reject component: ", e);
		}
	}

	protected void setState(TRPseudoState newState) {
		try {
			this.dialogLock.lock();
			// add checks?
			if (this.state == TRPseudoState.Expunged) {
				return;
			}
			this.state = newState;
			if (newState == TRPseudoState.Expunged) {
				stopIdleTimer();
				provider.release(this);
			}
		} finally {
			this.dialogLock.unlock();
		}

	}

	private void startIdleTimer() {
		if (!this.structured)
			return;

		try {
		this.dialogLock.lock();
			if (this.idleTimerFuture != null) {
				throw new IllegalStateException();
			}

			IdleTimerTask t = new IdleTimerTask();
			t.d = this;
			this.idleTimerFuture = this.executor.schedule(t, this.idleTaskTimeout, TimeUnit.MILLISECONDS);

		} finally {
			this.dialogLock.unlock();
		}
	}

	private void stopIdleTimer() {
		if (!this.structured)
			return;

		try {
		this.dialogLock.lock();
			if (this.idleTimerFuture != null) {
				this.idleTimerFuture.cancel(false);
				this.idleTimerFuture = null;
			}

		} finally {
			this.dialogLock.unlock();
		}
	}

	private void restartIdleTimer() {
		stopIdleTimer();
		startIdleTimer();
	}

	private class IdleTimerTask implements Runnable {
		DialogImpl d;

		public void run() {
			try {
			dialogLock.lock();
				d.idleTimerFuture = null;

				d.idleTimerActionTaken = false;
				d.idleTimerInvoked = true;
				provider.timeout(d);
				// send abort
				if (d.idleTimerActionTaken) {
					startIdleTimer();
				} else {
					sendAbnormalDialog();
				}

			} finally {
				d.idleTimerInvoked = false;
				dialogLock.unlock();
			}
		}

	}

	// ////////////////////
	// IND like methods //
	// ///////////////////
	public void operationEnded(InvokeImpl tcInvokeRequestImpl) {
		try {
		this.dialogLock.lock();
			// this op died cause of timeout, TC-L-CANCEL!
			int index = getIndexFromInvokeId(tcInvokeRequestImpl.getInvokeId());
			freeInvokeId(tcInvokeRequestImpl.getInvokeId());
			this.operationsSent[index] = null;
			// lets call listener
			// This is done actually with COmponentIndication ....
		} finally {
			this.dialogLock.unlock();
		}
	}

	/*
     * (non-Javadoc)
     *
	 * @see
	 * org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog#operationEnded(
     * org.mobicents.protocols.ss7.tcap.tc.component.TCInvokeRequestImpl)
     */
	public void operationTimedOut(InvokeImpl invoke) {
		// this op died cause of timeout, TC-L-CANCEL!
		try {
			this.dialogLock.lock();
			int index = getIndexFromInvokeId(invoke.getInvokeId());
			freeInvokeId(invoke.getInvokeId());
			this.operationsSent[index] = null;
			// lets call listener
			this.provider.operationTimedOut(invoke);
		} finally {
			this.dialogLock.unlock();
		}
	}

	// TC-TIMER-RESET
	public void resetTimer(Integer invokeId) throws TCAPException {
		try {
		this.dialogLock.lock();
			int index = getIndexFromInvokeId(invokeId);
			InvokeImpl invoke = operationsSent[index];
			if (invoke == null) {
				throw new TCAPException("No operation with this ID");
			}
			invoke.startTimer();
		} finally {
			this.dialogLock.unlock();
		}
	}

	public TRPseudoState getState() {
		return this.state;
	}

	public Object getUserObject() {
		return this.userObject;
	}

	public void setUserObject(Object userObject) {
		this.userObject = userObject;
	}

	public boolean getPreviewMode() {
		return false;
	}

	/*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
	@Override
	public String toString() {

		return super.toString() + ": Local[" + this.getLocalDialogId() + "] Remote[" + this.getRemoteDialogId()
				+ "], LocalAddress[" + localAddress + "] RemoteAddress[" + this.remoteAddress + "] State[" + this.state + "]";
	}
}