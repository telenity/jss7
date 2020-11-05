package org.mobicents.protocols.ss7.m3ua.impl;

import org.apache.log4j.Logger;
import org.mobicents.protocols.ss7.m3ua.impl.fsm.FSM;
import org.mobicents.protocols.ss7.m3ua.impl.fsm.FSMState;
import org.mobicents.protocols.ss7.m3ua.impl.fsm.TransitionHandler;

public class THLocalAspInactToAspActSnt implements TransitionHandler {

	private AspImpl aspImpl;
	private FSM fsm;
	private static final Logger logger = Logger.getLogger(THLocalAspInactToAspActSnt.class);

	public THLocalAspInactToAspActSnt(AspImpl aspImpl, FSM fsm) {
		this.aspImpl = aspImpl;
		this.fsm = fsm;
	}

	public boolean process(FSMState state) {
		this.aspImpl.getAspFactory().sendAspActive(aspImpl.getAs());
		return true;
	}

}
