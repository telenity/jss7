/*
 * TeleStax, Open Source Cloud Communications  Copyright 2012. 
 * and individual contributors
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
package org.mobicents.protocols.ss7.m3ua.impl;

import org.apache.log4j.Logger;
import org.mobicents.protocols.ss7.m3ua.Functionality;
import org.mobicents.protocols.ss7.m3ua.impl.fsm.FSM;
import org.mobicents.protocols.ss7.m3ua.message.ssnm.*;
import org.mobicents.protocols.ss7.m3ua.parameter.CongestedIndication;
import org.mobicents.protocols.ss7.m3ua.parameter.ErrorCode;
import org.mobicents.protocols.ss7.m3ua.parameter.RoutingContext;
import org.mobicents.protocols.ss7.m3ua.parameter.UserCause;
import org.mobicents.protocols.ss7.mtp.Mtp3PausePrimitive;
import org.mobicents.protocols.ss7.mtp.Mtp3ResumePrimitive;
import org.mobicents.protocols.ss7.mtp.Mtp3StatusCause;
import org.mobicents.protocols.ss7.mtp.Mtp3StatusPrimitive;

/**
 * @author amit bhayani
 */
public class SignalingNetworkManagementHandler extends MessageHandler {

    private static final Logger logger = Logger.getLogger(SignalingNetworkManagementHandler.class);

    private static final boolean skipSourceCheck = Boolean.parseBoolean(
            System.getProperty("ss7.m3ua.ssnm.skipSourceCheck", "false")
    );

    public SignalingNetworkManagementHandler(AspFactoryImpl aspFactoryImpl) {
        super(aspFactoryImpl);
    }

    private boolean isSourceAuthorizedAndActiveForApc(int apc, AspImpl asp, String type) {
        if (skipSourceCheck) {
            logger.info(String.format("[%s] Source check skipped for APC=%d from ASP=%s (forced by system property)",
                    type, apc, asp.getName()));
            return true;
        }
        AsImpl sourceAs = (AsImpl) asp.getAs();
        // @NOTE: getAsForRoute returns the *first* ACTIVE AS for this APC
        AsImpl expectedAs = aspFactoryImpl.getM3UAManagement().getRouteManagement().getAsForRoute(apc, -1, -1, -1);
        if (expectedAs == null || !expectedAs.getName().equalsIgnoreCase(sourceAs.getName())) {
            logger.warn(String.format("[%s] Ignored for APC=%d from AS=%s; expected=%s", type, apc, sourceAs.getName(),
                    expectedAs != null ? expectedAs.getName() : "null"));
            return false;
        }
        return true;
    }

    private boolean isSourceAuthorizedForApc(int apc, AspImpl asp, String type) {
        if (skipSourceCheck) {
            logger.info(String.format("[%s] Source check skipped for APC=%d from ASP=%s (forced by system property)",
                    type, apc, asp.getName()));
            return true;
        }
        AsImpl sourceAs = (AsImpl) asp.getAs();
        boolean rc = aspFactoryImpl.getM3UAManagement().getRouteManagement().isAsStillUsedForDpc(apc, sourceAs);
        if (!rc) {
            logger.warn(String.format("[%s] Ignored for APC=%d from AS=%s", type, apc, sourceAs.getName()));
            return false;
        }
        return true;
    }

    private void handleAffectedPointCodes(AspImpl aspImpl, int[] pcs, String type, Object msg) {
        for (int i = 0; i < pcs.length; i++) {
            int pc = pcs[i];
            AsImpl asImpl = (AsImpl) aspImpl.getAs();
            if ("DAVA".equals(type)) {
                if (isSourceAuthorizedAndActiveForApc(pc, aspImpl, type)) {
                    asImpl.getM3UAManagement().sendResumeMessageToLocalUser(new Mtp3ResumePrimitive(pc));
                }
            }
            if (isSourceAuthorizedForApc(pc, aspImpl, type)) {
                if ("DUNA".equals(type)) {
                    asImpl.getM3UAManagement().sendPauseMessageToLocalUser(new Mtp3PausePrimitive(pc));
                } else if ("SCON".equals(type)) {
                    int cong = 0;
                    if (msg instanceof SignallingCongestion) {
                        CongestedIndication ci = ((SignallingCongestion) msg).getCongestedIndication();
                        if (ci != null && ci.getCongestionLevel() != null) {
                            cong = ci.getCongestionLevel().getLevel();
                        }
                    }
                    Mtp3StatusPrimitive mtpStatus = new Mtp3StatusPrimitive(pc, Mtp3StatusCause.SignallingNetworkCongested, cong);
                    asImpl.getM3UAManagement().sendStatusMessageToLocalUser(mtpStatus);
                }
            }
            if ("DUPU".equals(type)) {
                int cause = 0;
                if (msg instanceof DestinationUPUnavailable) {
                    UserCause uc = ((DestinationUPUnavailable) msg).getUserCause();
                    if (uc != null) cause = uc.getCause();
                }
                Mtp3StatusPrimitive mtpStatus = new Mtp3StatusPrimitive(pc, Mtp3StatusCause.getMtp3StatusCause(cause), 0);
                asImpl.getM3UAManagement().sendStatusMessageToLocalUser(mtpStatus);
            }
        }
    }

    private void handleCommonMessage(RoutingContext rc, int[] pcs, Object msg, String type) {
        if (aspFactoryImpl.getFunctionality() != Functionality.AS && !("SCON".equals(type))) {
            logger.error(String.format("Rx : %s=%s But AppServer Functionality is not AS.", type, msg));
            return;
        }

        if (rc == null) {
            AspImpl aspImpl = getAspForNullRc();
            if (aspImpl == null) {
                sendError(null, aspFactoryImpl.parameterFactory.createErrorCode(ErrorCode.Invalid_Routing_Context));
                logger.error(String.format("Rx : %s=%s with null RC for AspFactory=%s. No ASP configured.",
                        type, msg, aspFactoryImpl.getName()));
                return;
            }
            FSM fsm = aspImpl.getLocalFSM();
            if (fsm == null) {
                logger.error(String.format("Rx : %s=%s but FSM is null", type, msg));
                return;
            }
            if (AspState.getState(fsm.getState().getName()) == AspState.ACTIVE) {
                handleAffectedPointCodes(aspImpl, pcs, type, msg);
            } else {
                logger.error(String.format("Rx : %s for null RoutingContext. But ASP State not ACTIVE. Message=%s",
                        type, msg));
            }
        } else {
            long[] rcs = rc.getRoutingContexts();
            for (int i = 0; i < rcs.length; i++) {
                AspImpl aspImpl = aspFactoryImpl.getAsp(rcs[i]);
                if (aspImpl == null) {
                    sendError(aspFactoryImpl.parameterFactory.createRoutingContext(new long[]{rcs[i]}),
                            aspFactoryImpl.parameterFactory.createErrorCode(ErrorCode.Invalid_Routing_Context));
                    logger.error(String.format("Rx : %s=%s with RC=%d. No ASP configured.", type, msg, rcs[i]));
                    continue;
                }
                FSM fsm = aspImpl.getLocalFSM();
                if (fsm == null) {
                    logger.error(String.format("Rx : %s=%s for ASP=%s. But FSM is null.",
                            type, msg, aspFactoryImpl.getName()));
                    return;
                }
                if (AspState.getState(fsm.getState().getName()) == AspState.ACTIVE) {
                    handleAffectedPointCodes(aspImpl, pcs, type, msg);
                } else {
                    logger.error(String.format("Rx : %s for RC=%d. But ASP State not ACTIVE. Message=%s",
                            type, rcs[i], msg));
                }
            }
        }
    }

    public void handleDestinationUnavailable(DestinationUnavailable duna) {
        handleCommonMessage(duna.getRoutingContexts(), duna.getAffectedPointCodes().getPointCodes(), duna, "DUNA");
    }

    public void handleDestinationAvailable(DestinationAvailable dava) {
        handleCommonMessage(dava.getRoutingContexts(), dava.getAffectedPointCodes().getPointCodes(), dava, "DAVA");
    }

    public void handleSignallingCongestion(SignallingCongestion scon) {
        handleCommonMessage(scon.getRoutingContexts(), scon.getAffectedPointCodes().getPointCodes(), scon, "SCON");
    }

    public void handleDestinationUPUnavailable(DestinationUPUnavailable dupu) {
        handleCommonMessage(dupu.getRoutingContext(), dupu.getAffectedPointCode().getPointCodes(), dupu, "DUPU");
    }

    public void handleDestinationStateAudit(DestinationStateAudit daud) {
        if (aspFactoryImpl.getFunctionality() == Functionality.SGW) {
            logger.warn(String.format("Received DAUD=%s. Not yet implemented.", daud));
        } else {
            logger.error(String.format("Rx : DAUD=%s But AppServer Functionality is not SGW.", daud));
        }
    }

    public void handleDestinationRestricted(DestinationRestricted drst) {
        if (aspFactoryImpl.getFunctionality() == Functionality.AS) {
            logger.warn(String.format("Received DRST=%s. Not implemented yet", drst));
        } else {
            logger.error(String.format("Rx : DRST=%s But AppServer Functionality is not AS.", drst));
        }
    }
}

