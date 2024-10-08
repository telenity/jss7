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

package org.mobicents.protocols.ss7.sccp.impl.message;

import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mobicents.protocols.ss7.sccp.impl.SccpStackImpl;
import org.mobicents.protocols.ss7.sccp.impl.parameter.ProtocolClassImpl;
import org.mobicents.protocols.ss7.sccp.message.MessageFactory;
import org.mobicents.protocols.ss7.sccp.message.SccpDataMessage;
import org.mobicents.protocols.ss7.sccp.message.SccpMessage;
import org.mobicents.protocols.ss7.sccp.message.SccpNoticeMessage;
import org.mobicents.protocols.ss7.sccp.parameter.HopCounter;
import org.mobicents.protocols.ss7.sccp.parameter.Importance;
import org.mobicents.protocols.ss7.sccp.parameter.ReturnCause;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;

/**
 * 
 * @author kulikov
 * @author sergey vetyutnev
 * 
 */
public class MessageFactoryImpl implements MessageFactory {
	private static final Logger logger = Logger.getLogger(MessageFactoryImpl.class);

	private transient SccpStackImpl sccpStackImpl;

	public MessageFactoryImpl(SccpStackImpl sccpStackImpl) {
		this.sccpStackImpl = sccpStackImpl;
	}

	public SccpDataMessage createDataMessageClass0(SccpAddress calledParty, SccpAddress callingParty, byte[] data, int localSsn, boolean returnMessageOnError,
			HopCounter hopCounter, Importance importance) {
		return new SccpDataMessageImpl(this.sccpStackImpl.getMaxDataMessage(), new ProtocolClassImpl(0, returnMessageOnError), sccpStackImpl.newSls(), localSsn, calledParty,
				callingParty, data, hopCounter, importance);
	}

	public SccpDataMessage createDataMessageClass1(SccpAddress calledParty, SccpAddress callingParty, byte[] data, int sls, int localSsn, boolean returnMessageOnError,
			HopCounter hopCounter, Importance importance) {
		return new SccpDataMessageImpl(this.sccpStackImpl.getMaxDataMessage(), new ProtocolClassImpl(1, returnMessageOnError), sls, localSsn, calledParty, callingParty, data, hopCounter,
				importance);
	}

	public SccpNoticeMessage createNoticeMessage(int origMsgType, ReturnCause returnCause, SccpAddress calledParty, SccpAddress callingParty, byte[] data,
			HopCounter hopCounter, Importance importance) {
		int type = SccpMessage.MESSAGE_TYPE_UNDEFINED;
		switch (origMsgType) {
		case SccpMessage.MESSAGE_TYPE_UDT:
			type = SccpMessage.MESSAGE_TYPE_UDTS;
			break;
		case SccpMessage.MESSAGE_TYPE_XUDT:
			type = SccpMessage.MESSAGE_TYPE_XUDTS;
			break;
		case SccpMessage.MESSAGE_TYPE_LUDT:
			type = SccpMessage.MESSAGE_TYPE_LUDTS;
			break;
		}
	
		return new SccpNoticeMessageImpl(this.sccpStackImpl.getMaxDataMessage(), type, returnCause, calledParty, callingParty, data, hopCounter, importance);
	}
	
	public SccpMessageImpl createMessage(int type, int opc, int dpc, int sls, InputStream in) throws IOException {
		SccpMessageImpl msg = null;
		switch (type) {
		case SccpMessage.MESSAGE_TYPE_UDT:
		case SccpMessage.MESSAGE_TYPE_XUDT:
		case SccpMessage.MESSAGE_TYPE_LUDT:
			msg = new SccpDataMessageImpl(this.sccpStackImpl.getMaxDataMessage(), type, opc, dpc, sls);
			break;

		case SccpMessage.MESSAGE_TYPE_UDTS:
		case SccpMessage.MESSAGE_TYPE_XUDTS:
		case SccpMessage.MESSAGE_TYPE_LUDTS:
			msg = new SccpNoticeMessageImpl(this.sccpStackImpl.getMaxDataMessage(), type, opc, dpc, sls);
			break;
		}

		if (msg != null) {
			msg.decode(in);
		} else if (logger.isEnabledFor(Level.WARN)) {
			logger.warn("No message implementation for MT: " + type);
		}
		return msg;
	}
}

