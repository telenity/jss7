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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.util.concurrent.DefaultThreadFactory;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mobicents.protocols.asn.AsnInputStream;
import org.mobicents.protocols.asn.AsnOutputStream;
import org.mobicents.protocols.asn.Tag;
import org.mobicents.protocols.ss7.sccp.RemoteSccpStatus;
import org.mobicents.protocols.ss7.sccp.SccpListener;
import org.mobicents.protocols.ss7.sccp.SccpProvider;
import org.mobicents.protocols.ss7.sccp.SignallingPointStatus;
import org.mobicents.protocols.ss7.sccp.message.MessageFactory;
import org.mobicents.protocols.ss7.sccp.message.SccpDataMessage;
import org.mobicents.protocols.ss7.sccp.message.SccpNoticeMessage;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.mobicents.protocols.ss7.tcap.api.ComponentPrimitiveFactory;
import org.mobicents.protocols.ss7.tcap.api.DialogPrimitiveFactory;
import org.mobicents.protocols.ss7.tcap.api.TCAPException;
import org.mobicents.protocols.ss7.tcap.api.TCAPProvider;
import org.mobicents.protocols.ss7.tcap.api.TCListener;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.TRPseudoState;
import org.mobicents.protocols.ss7.tcap.asn.ApplicationContextName;
import org.mobicents.protocols.ss7.tcap.asn.DialogPortion;
import org.mobicents.protocols.ss7.tcap.asn.DialogRequestAPDUImpl;
import org.mobicents.protocols.ss7.tcap.asn.DialogResponseAPDU;
import org.mobicents.protocols.ss7.tcap.asn.DialogServiceProviderType;
import org.mobicents.protocols.ss7.tcap.asn.InvokeImpl;
import org.mobicents.protocols.ss7.tcap.asn.ParseException;
import org.mobicents.protocols.ss7.tcap.asn.Result;
import org.mobicents.protocols.ss7.tcap.asn.ResultSourceDiagnostic;
import org.mobicents.protocols.ss7.tcap.asn.ResultType;
import org.mobicents.protocols.ss7.tcap.asn.TCAbortMessageImpl;
import org.mobicents.protocols.ss7.tcap.asn.TCNoticeIndicationImpl;
import org.mobicents.protocols.ss7.tcap.asn.TCUnidentifiedMessage;
import org.mobicents.protocols.ss7.tcap.asn.TcapFactory;
import org.mobicents.protocols.ss7.tcap.asn.comp.PAbortCauseType;
import org.mobicents.protocols.ss7.tcap.asn.comp.TCAbortMessage;
import org.mobicents.protocols.ss7.tcap.asn.comp.TCBeginMessage;
import org.mobicents.protocols.ss7.tcap.asn.comp.TCContinueMessage;
import org.mobicents.protocols.ss7.tcap.asn.comp.TCEndMessage;
import org.mobicents.protocols.ss7.tcap.asn.comp.TCUniMessage;
import org.mobicents.protocols.ss7.tcap.tc.component.ComponentPrimitiveFactoryImpl;
import org.mobicents.protocols.ss7.tcap.tc.dialog.events.DialogPrimitiveFactoryImpl;
import org.mobicents.protocols.ss7.tcap.tc.dialog.events.TCBeginIndicationImpl;
import org.mobicents.protocols.ss7.tcap.tc.dialog.events.TCContinueIndicationImpl;
import org.mobicents.protocols.ss7.tcap.tc.dialog.events.TCEndIndicationImpl;
import org.mobicents.protocols.ss7.tcap.tc.dialog.events.TCPAbortIndicationImpl;
import org.mobicents.protocols.ss7.tcap.tc.dialog.events.TCUniIndicationImpl;
import org.mobicents.protocols.ss7.tcap.tc.dialog.events.TCUserAbortIndicationImpl;
import org.mobicents.protocols.ss7.tcap.asn.Utils;

/**
 * @author amit bhayani
 * @author baranowb
 * @author sergey vetyutnev
 *
 */
public class TCAPProviderImpl implements TCAPProvider, SccpListener {

	private static final Logger logger = Logger.getLogger(TCAPProviderImpl.class);

	private transient List<TCListener> tcListeners = new CopyOnWriteArrayList<TCListener>();
	protected transient ScheduledExecutorService _EXECUTOR;
	// boundry for Uni directional dialogs :), tx id is always encoded
	// on 4 octets, so this is its max value
	// private static final long _4_OCTETS_LONG_FILL = 4294967295l;
	private transient ComponentPrimitiveFactory componentPrimitiveFactory;
	private transient DialogPrimitiveFactory dialogPrimitiveFactory;
	private transient SccpProvider sccpProvider;

	private transient MessageFactory messageFactory;

	private transient TCAPStackImpl stack; // originating TX id ~=Dialog, its direct
	// mapping, but not described
	// explicitly...
	private transient Map<Long, DialogImpl> dialogs = new ConcurrentHashMap<Long, DialogImpl>();

	private AtomicInteger seqControl = new AtomicInteger();
	private int ssn;
	private long curDialogId;


	protected TCAPProviderImpl(SccpProvider sccpProvider, TCAPStackImpl stack, int ssn) {
		super();
		this.sccpProvider = sccpProvider;
		this.ssn = ssn;
		messageFactory = sccpProvider.getMessageFactory();
		this.stack = stack;

		this.componentPrimitiveFactory = new ComponentPrimitiveFactoryImpl(this);
		this.dialogPrimitiveFactory = new DialogPrimitiveFactoryImpl(this.componentPrimitiveFactory);
	}

	public boolean getPreviewMode() {
		return false;
	}

    /*
     * (non-Javadoc)
     *
	 * @see
	 * org.mobicents.protocols.ss7.tcap.api.TCAPStack#addTCListener(org.mobicents
	 * .protocols.ss7.tcap.api.TCListener)
     */

	public void addTCListener(TCListener lst) {
		if (!this.tcListeners.contains(lst)) {
			this.tcListeners.add(lst);
		}

	}

	/*
     * (non-Javadoc)
     *
     * @see org.mobicents.protocols.ss7.tcap.api.TCAPStack#removeTCListener(org.mobicents .protocols.ss7.tcap.api.TCListener)
     */
	public void removeTCListener(TCListener lst) {
		this.tcListeners.remove(lst);

	}

	private boolean checkAvailableTxId(Long id) {
		if (!this.dialogs.containsKey(id))
			return true;
		else
			return false;
	}

	// some help methods... crude but will work for first impl.
	private synchronized Long getAvailableTxId() throws TCAPException {
		if (this.dialogs.size() >= this.stack.getMaxDialogs())
			throw new TCAPException("Current dialog count exceeds its maximum value");

		while (true) {
			if (this.curDialogId < this.stack.getDialogIdRangeStart())
				this.curDialogId = this.stack.getDialogIdRangeStart() - 1;
			if (++this.curDialogId > this.stack.getDialogIdRangeEnd())
				this.curDialogId = this.stack.getDialogIdRangeStart();
			Long id = this.curDialogId;
			if (checkAvailableTxId(id))
				return id;
		}
	}

	// get next Seq Control value available
	private int getNextSeqControl() {
		int res = seqControl.getAndIncrement();
		return res & stack.getMaxSeqControl();
	}

	/*
     * (non-Javadoc)
     *
	 * @seeorg.mobicents.protocols.ss7.tcap.api.TCAPProvider#
	 * getComopnentPrimitiveFactory()
     */
	public ComponentPrimitiveFactory getComponentPrimitiveFactory() {

		return this.componentPrimitiveFactory;
	}

	/*
     * (non-Javadoc)
     *
	 * @see
	 * org.mobicents.protocols.ss7.tcap.api.TCAPProvider#getDialogPrimitiveFactory
	 * ()
     */
	public DialogPrimitiveFactory getDialogPrimitiveFactory() {

		return this.dialogPrimitiveFactory;
	}

	/*
     * (non-Javadoc)
     *
     * @see org.mobicents.protocols.ss7.tcap.api.TCAPProvider#getNewDialog(org.mobicents
     * .protocols.ss7.sccp.parameter.SccpAddress, org.mobicents.protocols.ss7.sccp.parameter.SccpAddress)
     */
	public Dialog getNewDialog(SccpAddress localAddress, SccpAddress remoteAddress) throws TCAPException {
		return getNewDialog(localAddress, remoteAddress, getNextSeqControl(), null, 1);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.mobicents.protocols.ss7.tcap.api.TCAPProvider#getNewDialog(org.mobicents
	 * .protocols.ss7.sccp.parameter.SccpAddress, org.mobicents.protocols.ss7.sccp.parameter.SccpAddress)
	 */
	public Dialog getNewDialog(SccpAddress localAddress, SccpAddress remoteAddress, int protocolClass) throws TCAPException {
		return getNewDialog(localAddress, remoteAddress, protocolClass == 1 ? getNextSeqControl() : 0, null, protocolClass);
	}

	/*
     * (non-Javadoc)
     *
     * @see org.mobicents.protocols.ss7.tcap.api.TCAPProvider#getNewUnstructuredDialog
     * (org.mobicents.protocols.ss7.sccp.parameter.SccpAddress, org.mobicents.protocols.ss7.sccp.parameter.SccpAddress)
     */
	public Dialog getNewUnstructuredDialog(SccpAddress localAddress, SccpAddress remoteAddress) throws TCAPException {
		return _getDialog(localAddress, remoteAddress, false, getNextSeqControl(), null, 1);
	}

	private Dialog getNewDialog(SccpAddress localAddress, SccpAddress remoteAddress, int seqControl, Long id, int protocolClass)
			throws TCAPException {

		return _getDialog(localAddress, remoteAddress, true, seqControl, id, protocolClass);
	}

	private Dialog _getDialog(SccpAddress localAddress, SccpAddress remoteAddress, boolean structured,
							  int seqControl, Long id, int protocolClass) throws TCAPException {

		if (localAddress == null) {
			throw new NullPointerException("LocalAddress must not be null");
		}

		if (id == null) {
			id = this.getAvailableTxId();
		} else {
			if (!checkAvailableTxId(id)) {
				throw new TCAPException("Suggested local TransactionId is already present in system: " + id);
			}
		}
		if (structured) {
			DialogImpl di = new DialogImpl(localAddress, remoteAddress, id, structured, this._EXECUTOR, this, seqControl,
					false, protocolClass);

			this.dialogs.put(id, di);

			return di;
		} else {
			DialogImpl di = new DialogImpl(localAddress, remoteAddress, id, structured, this._EXECUTOR, this, seqControl,
					false, protocolClass);
			return di;
		}
	}

	public int getCurrentDialogsCount() {
		return this.dialogs.size();
	}

	public void send(byte[] data, boolean returnMessageOnError, SccpAddress destinationAddress, SccpAddress originatingAddress,
					 int seqControl, int protocolClass) throws IOException {

		SccpDataMessage msg;
		if (protocolClass == 1) {
			msg = messageFactory.createDataMessageClass1(destinationAddress, originatingAddress, data, seqControl,
					this.ssn, returnMessageOnError, null, null);
		} else {
			msg = messageFactory.createDataMessageClass0(destinationAddress, originatingAddress, data,
					this.ssn, returnMessageOnError, null, null);
		}
		sccpProvider.send(msg);
	}

	public int getMaxUserDataLength(SccpAddress calledPartyAddress, SccpAddress callingPartyAddress) {
		return this.sccpProvider.getMaxUserDataLength(calledPartyAddress, callingPartyAddress);
	}

	public void deliver(DialogImpl dialogImpl, TCBeginIndicationImpl msg) {

		try {
			for (TCListener lst : this.tcListeners) {
				lst.onTCBegin(msg);
			}
		} catch (Exception e) {
			if (logger.isEnabledFor(Level.ERROR)) {
				logger.error("Received exception while delivering data to transport layer.", e);
			}
		}

	}

	public void deliver(DialogImpl dialogImpl, TCContinueIndicationImpl tcContinueIndication) {
		try {
			for (TCListener lst : this.tcListeners) {
				lst.onTCContinue(tcContinueIndication);
			}
		} catch (Exception e) {
			if (logger.isEnabledFor(Level.ERROR)) {
				logger.error("Received exception while delivering data to transport layer.", e);
			}
		}

	}

	public void deliver(DialogImpl dialogImpl, TCEndIndicationImpl tcEndIndication) {
		try {
			for (TCListener lst : this.tcListeners) {
				lst.onTCEnd(tcEndIndication);
			}
		} catch (Exception e) {
			if (logger.isEnabledFor(Level.ERROR)) {
				logger.error("Received exception while delivering data to transport layer.", e);
			}
		}
	}

	public void deliver(DialogImpl dialogImpl, TCPAbortIndicationImpl tcAbortIndication) {
		try {
			for (TCListener lst : this.tcListeners) {
				lst.onTCPAbort(tcAbortIndication);
			}
		} catch (Exception e) {
			if (logger.isEnabledFor(Level.ERROR)) {
				logger.error("Received exception while delivering data to transport layer.", e);
			}
		}

	}

	public void deliver(DialogImpl dialogImpl, TCUserAbortIndicationImpl tcAbortIndication) {
		try {
			for (TCListener lst : this.tcListeners) {
				lst.onTCUserAbort(tcAbortIndication);
			}
		} catch (Exception e) {
			if (logger.isEnabledFor(Level.ERROR)) {
				logger.error("Received exception while delivering data to transport layer.", e);
			}
		}

	}

	public void deliver(DialogImpl dialogImpl, TCUniIndicationImpl tcUniIndication) {
		try {
			for (TCListener lst : this.tcListeners) {
				lst.onTCUni(tcUniIndication);
			}
		} catch (Exception e) {
			if (logger.isEnabledFor(Level.ERROR)) {
				logger.error("Received exception while delivering data to transport layer.", e);
			}
		}
	}

	public void deliver(DialogImpl dialogImpl, TCNoticeIndicationImpl tcNoticeIndication) {
		try {
			for (TCListener lst : this.tcListeners) {
				lst.onTCNotice(tcNoticeIndication);
			}
		} catch (Exception e) {
			if (logger.isEnabledFor(Level.ERROR)) {
				logger.error("Received exception while delivering data to transport layer.", e);
			}
		}
	}

	public void release(DialogImpl d) {
		Long did = d.getLocalDialogId();

        this.doRelease(d);

        this.dialogs.remove(did);
	}

	private void doRelease(DialogImpl d) {
		try {
			for (TCListener lst : this.tcListeners) {
				lst.onDialogReleased(d);
			}
		} catch (Exception e) {
			if (logger.isEnabledFor(Level.ERROR)) {
				logger.error("Received exception while delivering dialog release.", e);
			}
		}
	}

	/**
	 * @param d
	 */
	public void timeout(DialogImpl d) {
		try {
			for (TCListener lst : this.tcListeners) {
				lst.onDialogTimeout(d);
			}
		} catch (Exception e) {
			if (logger.isEnabledFor(Level.ERROR)) {
				logger.error("Received exception while delivering dialog release.", e);
			}
		}
	}

	public TCAPStackImpl getStack()
	{
		return this.stack;
	}

	// ///////////////////////////////////////////
	// Some methods invoked by operation FSM //
	// //////////////////////////////////////////
	public Future createOperationTimer(Runnable operationTimerTask, long invokeTimeout) {

		return this._EXECUTOR.schedule(operationTimerTask, invokeTimeout, TimeUnit.MILLISECONDS);
	}

	public void operationTimedOut(InvokeImpl tcInvokeRequestImpl) {
		try {
			for (TCListener lst : this.tcListeners) {
				lst.onInvokeTimeout(tcInvokeRequestImpl);
			}
		} catch (Exception e) {
			if (logger.isEnabledFor(Level.ERROR)) {
				logger.error("Received exception while delivering Begin.", e);
			}
		}
	}

	void start() {
		logger.info("Starting TCAP Provider");

		ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(
				stack.getCorePoolSize(), new DefaultThreadFactory("Tcap-Thread"));
		executor.setRemoveOnCancelPolicy(true);
		this._EXECUTOR = executor;
		this.sccpProvider.registerSccpListener(ssn, this);
		logger.info("Registered SCCP listener with address " + ssn);
	}

	void stop() {
		this._EXECUTOR.shutdown();
		this.sccpProvider.deregisterSccpListener(ssn);

		this.dialogs.clear();
	}

	protected void sendProviderAbort(PAbortCauseType pAbortCause, byte[] remoteTransactionId, SccpAddress remoteAddress, SccpAddress localAddress,
									 int seqControl, int protocolClass) {

		TCAbortMessageImpl msg = (TCAbortMessageImpl) TcapFactory.createTCAbortMessage();
		msg.setDestinationTransactionId(remoteTransactionId);
		msg.setPAbortCause(pAbortCause);

		AsnOutputStream aos = new AsnOutputStream();
		try {
			msg.encode(aos);
			this.send(aos.toByteArray(), false, remoteAddress, localAddress, seqControl, protocolClass);
		} catch (Exception e) {
			if (logger.isEnabledFor(Level.ERROR)) {
				logger.error("Failed to send message: ", e);
			}
		}
	}

	protected void sendProviderAbort(DialogServiceProviderType pt, byte[] remoteTransactionId, SccpAddress remoteAddress, SccpAddress localAddress,
			int seqControl, int protocolClass, ApplicationContextName acn) {

		DialogPortion dp = TcapFactory.createDialogPortion();
		dp.setUnidirectional(false);

		DialogResponseAPDU apdu = TcapFactory.createDialogAPDUResponse();
		apdu.setDoNotSendProtocolVersion(this.getStack().getDoNotSendProtocolVersion());

		Result res = TcapFactory.createResult();
		res.setResultType(ResultType.RejectedPermanent);
		ResultSourceDiagnostic rsd = TcapFactory.createResultSourceDiagnostic();
		rsd.setDialogServiceProviderType(pt);
		apdu.setResultSourceDiagnostic(rsd);
		apdu.setResult(res);
		apdu.setApplicationContextName(acn);
		dp.setDialogAPDU(apdu);

		TCAbortMessageImpl msg = (TCAbortMessageImpl) TcapFactory.createTCAbortMessage();
		msg.setDestinationTransactionId(remoteTransactionId);
		msg.setDialogPortion(dp);

		AsnOutputStream aos = new AsnOutputStream();
		try {
			msg.encode(aos);
			this.send(aos.toByteArray(), false, remoteAddress, localAddress, seqControl, protocolClass);
		} catch (Exception e) {
			if (logger.isEnabledFor(Level.ERROR)) {
				logger.error("Failed to send message: ", e);
			}
		}
	}

	public void onCoordRequest(int arg0, int arg1, int arg2) {
		// TODO Auto-generated method stub

	}

	public void onCoordResponse(int arg0, int arg1, int arg2) {
		// TODO Auto-generated method stub

	}

	public void onMessage(SccpDataMessage message) {

		try {
			byte[] data = message.getData();
			SccpAddress localAddress = message.getCalledPartyAddress();
			SccpAddress remoteAddress = message.getCallingPartyAddress();

			// FIXME: Qs state that OtxID and DtxID consittute to dialog id.....

			// asnData - it should pass
			AsnInputStream ais = new AsnInputStream(data);

			// this should have TC message tag :)
			int tag = ais.readTag();

			if (ais.getTagClass() != Tag.CLASS_APPLICATION) {
				unrecognizedPackageType(message, localAddress, remoteAddress, ais, tag);
				return;
			}

			switch (tag) {
				// continue first, usually we will get more of those. small perf
				// boost
				case TCContinueMessage._TAG:
					TCContinueMessage tcm = null;
					try {
						tcm = TcapFactory.createTCContinueMessage(ais);
					} catch (ParseException e) {
						logger.error("ParseException when parsing TCContinueMessage: ", e);

						// parsing OriginatingTransactionId
						ais = new AsnInputStream(data);
						tag = ais.readTag();
						TCUnidentifiedMessage tcUnidentified = new TCUnidentifiedMessage();
						tcUnidentified.decode(ais);
						if (tcUnidentified.getOriginatingTransactionId() != null) {
							if (e.getPAbortCauseType() != null) {
								this.sendProviderAbort(e.getPAbortCauseType(), tcUnidentified.getOriginatingTransactionId(), remoteAddress, localAddress,
										message.getSls(), message.getProtocolClass().getProtocolClass());
							} else {
								this.sendProviderAbort(PAbortCauseType.BadlyFormattedTxPortion, tcUnidentified.getOriginatingTransactionId(), remoteAddress,
										localAddress, message.getSls(), message.getProtocolClass().getProtocolClass());
							}
						}
						return;
					}

					long dialogId = Utils.decodeTransactionId(tcm.getDestinationTransactionId());
					DialogImpl di;
					di = this.dialogs.get(dialogId);
					if (di == null) {
						logger.warn("TC-CONTINUE: No dialog/transaction for id: " + dialogId);
						this.sendProviderAbort(PAbortCauseType.UnrecognizedTxID, tcm.getOriginatingTransactionId(), remoteAddress, localAddress,
								message.getSls(), message.getProtocolClass().getProtocolClass());
					} else {
						di.processContinue(tcm, localAddress, remoteAddress);
					}

					break;

				case TCBeginMessage._TAG:
					TCBeginMessage tcb = null;
					try {
						tcb = TcapFactory.createTCBeginMessage(ais);
					} catch (ParseException e) {
						logger.error("ParseException when parsing TCBeginMessage: ", e);

						// parsing OriginatingTransactionId
						ais = new AsnInputStream(data);
						tag = ais.readTag();
						TCUnidentifiedMessage tcUnidentified = new TCUnidentifiedMessage();
						tcUnidentified.decode(ais);
						if (tcUnidentified.getOriginatingTransactionId() != null) {
							if (e.getPAbortCauseType() != null) {
								this.sendProviderAbort(e.getPAbortCauseType(), tcUnidentified.getOriginatingTransactionId(), remoteAddress, localAddress,
										message.getSls(), message.getProtocolClass().getProtocolClass());
							} else {
								this.sendProviderAbort(PAbortCauseType.BadlyFormattedTxPortion, tcUnidentified.getOriginatingTransactionId(), remoteAddress,
										localAddress, message.getProtocolClass().getProtocolClass(), message.getSls());
							}
						}
						return;
					}
					if (tcb.getDialogPortion() != null && tcb.getDialogPortion().getDialogAPDU() != null
							&& tcb.getDialogPortion().getDialogAPDU() instanceof DialogRequestAPDUImpl) {
						DialogRequestAPDUImpl dlg = (DialogRequestAPDUImpl) tcb.getDialogPortion().getDialogAPDU();
						if (dlg.getProtocolVersion() != null && !dlg.getProtocolVersion().isSupportedVersion()) {
							logger.error("Unsupported protocol version of has been received when parsing TCBeginMessage");
							this.sendProviderAbort(DialogServiceProviderType.NoCommonDialogPortion, tcb.getOriginatingTransactionId(), remoteAddress, localAddress,
									message.getSls(), message.getProtocolClass().getProtocolClass(), dlg.getApplicationContextName());
							return;
						}
					}

					di = null;
					try {
						di = (DialogImpl) this.getNewDialog(localAddress, remoteAddress, message.getSls(), null,
								message.getProtocolClass().getProtocolClass());
					} catch (TCAPException e) {
						this.sendProviderAbort(PAbortCauseType.ResourceLimitation, tcb.getOriginatingTransactionId(), remoteAddress, localAddress,
								message.getSls(), message.getProtocolClass().getProtocolClass());
						logger.error("Too many registered current dialogs when receiving TCBeginMessage:" + e.getMessage());
						return;
					}
					di.processBegin(tcb, localAddress, remoteAddress);
					break;

				case TCEndMessage._TAG:
					TCEndMessage teb = null;
					try {
						teb = TcapFactory.createTCEndMessage(ais);
					} catch (ParseException e) {
						logger.error("ParseException when parsing TCEndMessage: ", e);
						return;
					}

					dialogId = Utils.decodeTransactionId(teb.getDestinationTransactionId());
					di = this.dialogs.get(dialogId);
					if (di == null) {
						logger.warn("TC-END: No dialog/transaction for id: " + dialogId);
					} else {
						di.processEnd(teb, localAddress, remoteAddress);
					}
					break;

				case TCAbortMessage._TAG:
					TCAbortMessage tub = null;
					try {
						tub = TcapFactory.createTCAbortMessage(ais);
					} catch (ParseException e) {
						logger.error("ParseException when parsing TCAbortMessage: ", e);
						return;
					}

					dialogId = Utils.decodeTransactionId(tub.getDestinationTransactionId());
					di = this.dialogs.get(dialogId);
					if (di == null) {
						logger.warn("TC-ABORT: No dialog/transaction for id: " + dialogId);
					} else {
						di.processAbort(tub, localAddress, remoteAddress);
					}
					break;

				case TCUniMessage._TAG:
					TCUniMessage tcuni;
					try {
						tcuni = TcapFactory.createTCUniMessage(ais);
					} catch (ParseException e) {
						logger.error("ParseException when parsing TCUniMessage: ", e);
						return;
					}

					DialogImpl uniDialog = (DialogImpl) this.getNewUnstructuredDialog(localAddress, remoteAddress);
					uniDialog.processUni(tcuni, localAddress, remoteAddress);
					break;

				default:
					unrecognizedPackageType(message, localAddress, remoteAddress, ais, tag);
					break;
			}
		} catch (Exception e) {
			logger.error(String.format("Error while decoding Rx SccpMessage=%s", message), e);
		}
	}

	private void unrecognizedPackageType(SccpDataMessage message, SccpAddress localAddress, SccpAddress remoteAddress, AsnInputStream ais, int tag)
			throws ParseException {

		logger.error(String.format("Rx unidentified tag=%s, tagClass=%s. SccpMessage=%s", tag, ais.getTagClass(), message));
		TCUnidentifiedMessage tcUnidentified = new TCUnidentifiedMessage();
		tcUnidentified.decode(ais);

		if (tcUnidentified.getOriginatingTransactionId() != null) {
			byte[] otid = tcUnidentified.getOriginatingTransactionId();

			if (tcUnidentified.getDestinationTransactionId() != null) {
				Long dtid = Utils.decodeTransactionId(tcUnidentified.getDestinationTransactionId());
				this.sendProviderAbort(PAbortCauseType.UnrecognizedMessageType, otid, remoteAddress, localAddress,
						message.getSls(), message.getProtocolClass().getProtocolClass());
			} else {
				this.sendProviderAbort(PAbortCauseType.UnrecognizedMessageType, otid, remoteAddress, localAddress,
						message.getSls(), message.getProtocolClass().getProtocolClass());
			}
		} else {
			this.sendProviderAbort(PAbortCauseType.UnrecognizedMessageType, new byte[0], remoteAddress, localAddress,
					message.getSls(), message.getProtocolClass().getProtocolClass());
		}
	}

	public void onNotice(SccpNoticeMessage msg) {

		DialogImpl dialog = null;

		try {
			byte[] data = msg.getData();
			AsnInputStream ais = new AsnInputStream(data);

			// this should have TC message tag :)
			int tag = ais.readTag();

			TCUnidentifiedMessage tcUnidentified = new TCUnidentifiedMessage();
			tcUnidentified.decode(ais);

			if (tcUnidentified.getOriginatingTransactionId() != null) {
				long otid = Utils.decodeTransactionId(tcUnidentified.getOriginatingTransactionId());
				dialog = this.dialogs.get(otid);
			}
		} catch (Exception e) {
			logger.error(String.format("Error while decoding Rx SccpNoticeMessage=%s", msg), e);
			return;
		}

		TCNoticeIndicationImpl ind = new TCNoticeIndicationImpl();
		ind.setRemoteAddress(msg.getCallingPartyAddress());
		ind.setLocalAddress(msg.getCalledPartyAddress());
		ind.setDialog(dialog);
		ind.setReportCause(msg.getReturnCause().getValue());

		if (dialog != null) {
			try {
			dialog.dialogLock.lock();

				this.deliver(dialog, ind);

				if (dialog.getState() != TRPseudoState.Active) {
					dialog.release();
				}
			} finally {
				dialog.dialogLock.unlock();
			}
		} else {
			this.deliver(dialog, ind);
		}
	}

	public void onPcState(int arg0, SignallingPointStatus arg1, int arg2, RemoteSccpStatus arg3) {
		// TODO Auto-generated method stub

	}

	public void onState(int arg0, int arg1, boolean arg2, int arg3) {
		// TODO Auto-generated method stub

	}

}