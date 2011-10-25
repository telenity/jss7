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

package org.mobicents.protocols.ss7.map.service.sms;

import org.apache.log4j.Logger;
import org.mobicents.protocols.asn.Tag;
import org.mobicents.protocols.ss7.map.MAPDialogImpl;
import org.mobicents.protocols.ss7.map.MAPProviderImpl;
import org.mobicents.protocols.ss7.map.MAPServiceBaseImpl;
import org.mobicents.protocols.ss7.map.api.MAPApplicationContext;
import org.mobicents.protocols.ss7.map.api.MAPApplicationContextName;
import org.mobicents.protocols.ss7.map.api.MAPApplicationContextVersion;
import org.mobicents.protocols.ss7.map.api.MAPDialog;
import org.mobicents.protocols.ss7.map.api.MAPException;
import org.mobicents.protocols.ss7.map.api.MAPOperationCode;
import org.mobicents.protocols.ss7.map.api.MAPParsingComponentException;
import org.mobicents.protocols.ss7.map.api.MAPParsingComponentExceptionReason;
import org.mobicents.protocols.ss7.map.api.MAPServiceListener;
import org.mobicents.protocols.ss7.map.api.dialog.ServingCheckData;
import org.mobicents.protocols.ss7.map.api.dialog.ServingCheckResult;
import org.mobicents.protocols.ss7.map.api.primitives.AddressString;
import org.mobicents.protocols.ss7.map.api.service.sms.MAPDialogSms;
import org.mobicents.protocols.ss7.map.api.service.sms.MAPServiceSms;
import org.mobicents.protocols.ss7.map.api.service.sms.MAPServiceSmsListener;
import org.mobicents.protocols.ss7.map.dialog.ServingCheckDataImpl;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog;
import org.mobicents.protocols.ss7.tcap.asn.comp.ComponentType;
import org.mobicents.protocols.ss7.tcap.asn.comp.Invoke;
import org.mobicents.protocols.ss7.tcap.asn.comp.OperationCode;
import org.mobicents.protocols.ss7.tcap.asn.comp.Parameter;
import org.mobicents.protocols.ss7.tcap.asn.ApplicationContextName;
import org.mobicents.protocols.ss7.tcap.asn.TcapFactory;
import org.mobicents.protocols.asn.AsnInputStream;

/**
 * 
 * @author sergey vetyutnev
 * 
 */
public class MAPServiceSmsImpl extends MAPServiceBaseImpl implements MAPServiceSms {

	protected Logger loger = Logger.getLogger(MAPServiceSmsImpl.class);

	public MAPServiceSmsImpl(MAPProviderImpl mapProviderImpl) {
		super(mapProviderImpl);
	}

	
	/*
	 * Creating a new outgoing MAP SMS dialog and adding it to the
	 * MAPProvider.dialog collection
	 * 
	 */
	@Override
	public MAPDialogSms createNewDialog(MAPApplicationContext appCntx, SccpAddress origAddress, AddressString origReference, SccpAddress destAddress,
			AddressString destReference) throws MAPException {

		// We cannot create a dialog if the service is not activated
		if (!this.isActivated())
			throw new MAPException(
					"Cannot create MAPDialogSms because MAPServiceSms is not activated");

		Dialog tcapDialog = this.createNewTCAPDialog(origAddress, destAddress);
		MAPDialogSmsImpl dialog = new MAPDialogSmsImpl(appCntx, tcapDialog, this.mapProviderImpl,
				this, origReference, destReference);

		this.putMAPDialogIntoCollection(dialog);

		return dialog;
	}

	@Override
	protected MAPDialogImpl createNewDialogIncoming(MAPApplicationContext appCntx, Dialog tcapDialog) {
		return new MAPDialogSmsImpl(appCntx, tcapDialog, this.mapProviderImpl, this, null, null);
	}

	@Override
	public void addMAPServiceListener(MAPServiceSmsListener mapServiceListener) {
		super.addMAPServiceListener(mapServiceListener);
	}

	@Override
	public void removeMAPServiceListener(MAPServiceSmsListener mapServiceListener) {
		super.removeMAPServiceListener(mapServiceListener);
	}

	
	@Override
	public ServingCheckData isServingService(MAPApplicationContext dialogApplicationContext) {
		MAPApplicationContextName ctx = dialogApplicationContext.getApplicationContextName();
		int vers = dialogApplicationContext.getApplicationContextVersion().getVersion();
		
		switch (ctx) {
		case shortMsgAlertContext:
			if (vers >= 1 && vers <= 2) {
				return new ServingCheckDataImpl(ServingCheckResult.AC_Serving);
			} else if (vers > 2) {
				long[] altOid = dialogApplicationContext.getOID();
				altOid[7] = 2;
				ApplicationContextName alt = TcapFactory.createApplicationContextName(altOid);
				return new ServingCheckDataImpl(ServingCheckResult.AC_VersionIncorrect, alt);
			} else {
				return new ServingCheckDataImpl(ServingCheckResult.AC_VersionIncorrect);
			}

		case shortMsgMORelayContext:
		case shortMsgGatewayContext:
			if (vers >= 1 && vers <= 3) {
				return new ServingCheckDataImpl(ServingCheckResult.AC_Serving);
			} else if (vers > 3) {
				long[] altOid = dialogApplicationContext.getOID();
				altOid[7] = 2;
				ApplicationContextName alt = TcapFactory.createApplicationContextName(altOid);
				return new ServingCheckDataImpl(ServingCheckResult.AC_VersionIncorrect, alt);
			} else {
				return new ServingCheckDataImpl(ServingCheckResult.AC_VersionIncorrect);
			}

		case shortMsgMTRelayContext:
			if (vers >= 2 && vers <= 3) {
				return new ServingCheckDataImpl(ServingCheckResult.AC_Serving);
			} else if (vers > 3) {
				long[] altOid = dialogApplicationContext.getOID();
				altOid[7] = 2;
				ApplicationContextName alt = TcapFactory.createApplicationContextName(altOid);
				return new ServingCheckDataImpl(ServingCheckResult.AC_VersionIncorrect, alt);
			} else {
				return new ServingCheckDataImpl(ServingCheckResult.AC_VersionIncorrect);
			}
		}
		
		return new ServingCheckDataImpl(ServingCheckResult.AC_NotServing);
	}

	@Override
	public MAPApplicationContext getMAPv1ApplicationContext(int operationCode, Invoke invoke) {
		
		switch (operationCode) {
		case MAPOperationCode.mo_forwardSM:
			return MAPApplicationContext.getInstance(MAPApplicationContextName.shortMsgMORelayContext, MAPApplicationContextVersion.version1);
		case MAPOperationCode.alertServiceCentreWithoutResult:
			return MAPApplicationContext.getInstance(MAPApplicationContextName.shortMsgAlertContext, MAPApplicationContextVersion.version1);
		case MAPOperationCode.sendRoutingInfoForSM:
			return MAPApplicationContext.getInstance(MAPApplicationContextName.shortMsgGatewayContext, MAPApplicationContextVersion.version1);
		case MAPOperationCode.reportSM_DeliveryStatus:
			return MAPApplicationContext.getInstance(MAPApplicationContextName.shortMsgGatewayContext, MAPApplicationContextVersion.version1);
		}
		
		return null;
	}
	
	@Override
	public void processComponent(ComponentType compType, OperationCode oc, Parameter parameter, MAPDialog mapDialog, Long invokeId, Long linkedId)
			throws MAPParsingComponentException {
		
		MAPDialogSmsImpl mapDialogSmsImpl = (MAPDialogSmsImpl) mapDialog;

		Long ocValue = oc.getLocalOperationCode();
		if (ocValue == null)
			new MAPParsingComponentException("", MAPParsingComponentExceptionReason.UnrecognizedOperation);
		MAPApplicationContextName acn = mapDialog.getApplicationContext().getApplicationContextName();
		int vers = mapDialog.getApplicationContext().getApplicationContextVersion().getVersion();
		int ocValueInt = (int) (long)ocValue;

		switch (ocValueInt) {
		case MAPOperationCode.mo_forwardSM:
			if (acn == MAPApplicationContextName.shortMsgMORelayContext || acn == MAPApplicationContextName.shortMsgMTRelayContext && vers == 2) {
				if (vers >= 3) {
					if (compType == ComponentType.Invoke)
						this.moForwardShortMessageRequest(parameter, mapDialogSmsImpl, invokeId);
					else
						this.moForwardShortMessageResponse(parameter, mapDialogSmsImpl, invokeId);
				} else {
					if (compType == ComponentType.Invoke)
						this.forwardShortMessageRequest(parameter, mapDialogSmsImpl, invokeId);
					else
						this.forwardShortMessageResponse(parameter, mapDialogSmsImpl, invokeId);
				}
			}
			break;

		case MAPOperationCode.mt_forwardSM:
			if (acn == MAPApplicationContextName.shortMsgMTRelayContext && vers >= 3) {
				if (compType == ComponentType.Invoke)
					this.mtForwardShortMessageRequest(parameter, mapDialogSmsImpl, invokeId);
				else
					this.mtForwardShortMessageResponse(parameter, mapDialogSmsImpl, invokeId);
			}
			break;

		case MAPOperationCode.sendRoutingInfoForSM:
			if (acn == MAPApplicationContextName.shortMsgGatewayContext ) {
				if (compType == ComponentType.Invoke)
					this.sendRoutingInfoForSMRequest(parameter, mapDialogSmsImpl, invokeId);
				else
					this.sendRoutingInfoForSMResponse(parameter, mapDialogSmsImpl, invokeId);
			}
			break;

		case MAPOperationCode.reportSM_DeliveryStatus:
			if (acn == MAPApplicationContextName.shortMsgGatewayContext) {
				if (compType == ComponentType.Invoke)
					this.reportSMDeliveryStatusRequest(parameter, mapDialogSmsImpl, invokeId);
				else
					this.reportSMDeliveryStatusResponse(parameter, mapDialogSmsImpl, invokeId);
			}
			break;

		case MAPOperationCode.informServiceCentre:
			if (acn == MAPApplicationContextName.shortMsgGatewayContext && vers >= 2) {
				if (compType == ComponentType.Invoke)
					this.informServiceCentreRequest(parameter, mapDialogSmsImpl, invokeId);
			}
			break;

		case MAPOperationCode.alertServiceCentre:
			if (acn == MAPApplicationContextName.shortMsgAlertContext && vers >= 2) {
				if (compType == ComponentType.Invoke)
					this.alertServiceCentreRequest(parameter, mapDialogSmsImpl, invokeId);
				else
					this.alertServiceCentreResponse(parameter, mapDialogSmsImpl, invokeId);
			}
			break;

		case MAPOperationCode.alertServiceCentreWithoutResult:
			if (acn == MAPApplicationContextName.shortMsgAlertContext && vers == 1) {
				if (compType == ComponentType.Invoke)
					this.alertServiceCentreRequest(parameter, mapDialogSmsImpl, invokeId);
			}
			break;
			
		default:
			new MAPParsingComponentException("", MAPParsingComponentExceptionReason.UnrecognizedOperation);
		}
	}

	private void forwardShortMessageRequest(Parameter parameter, MAPDialogSmsImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {
		
		if (parameter == null)
			throw new MAPParsingComponentException("Error while decoding forwardShortMessageRequest: Parameter is mandatory but not found",
					MAPParsingComponentExceptionReason.MistypedParameter);

		if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
			throw new MAPParsingComponentException(
					"Error while decoding moForwardShortMessageRequest: Bad tag or tagClass or parameter is primitive, received tag=" + parameter.getTag(),
					MAPParsingComponentExceptionReason.MistypedParameter);

		byte[] buf = parameter.getData();
		AsnInputStream ais = new AsnInputStream(buf);
		ForwardShortMessageRequestIndicationImpl ind = new ForwardShortMessageRequestIndicationImpl();
		ind.decodeData(ais, buf.length);

		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSmsListener) serLis).onForwardShortMessageIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing forwardShortMessageRequest: " + e.getMessage(), e);
			}
		}
	}
	
	private void forwardShortMessageResponse(Parameter parameter, MAPDialogSmsImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {

		ForwardShortMessageResponseIndicationImpl ind = new ForwardShortMessageResponseIndicationImpl();

		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			
			try {
				((MAPServiceSmsListener) serLis).onForwardShortMessageRespIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing forwardShortMessageResponse: " + e.getMessage(), e);
			}			
		}
	}
	
	private void moForwardShortMessageRequest(Parameter parameter, MAPDialogSmsImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {
		
		if (parameter == null)
			throw new MAPParsingComponentException("Error while decoding moForwardShortMessageRequest: Parameter is mandatory but not found",
					MAPParsingComponentExceptionReason.MistypedParameter);

		if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
			throw new MAPParsingComponentException(
					"Error while decoding moForwardShortMessageRequest: Bad tag or tagClass or parameter is primitive, received tag=" + parameter.getTag(),
					MAPParsingComponentExceptionReason.MistypedParameter);

		byte[] buf = parameter.getData();
		AsnInputStream ais = new AsnInputStream(buf);
		MoForwardShortMessageRequestIndicationImpl ind = new MoForwardShortMessageRequestIndicationImpl();
		ind.decodeData(ais, buf.length);

		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSmsListener) serLis).onMoForwardShortMessageIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing onMoForwardShortMessageIndication: " + e.getMessage(), e);
			}
		}
	}
	
	private void moForwardShortMessageResponse(Parameter parameter, MAPDialogSmsImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {
		
		MoForwardShortMessageResponseIndicationImpl ind = new MoForwardShortMessageResponseIndicationImpl();
		
		if (parameter != null) {
			if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
				throw new MAPParsingComponentException(
						"Error while decoding moForwardShortMessageResponse: Bad tag or tagClass or parameter is primitive, received tag=" + parameter.getTag(),
						MAPParsingComponentExceptionReason.MistypedParameter);

			byte[] buf = parameter.getData();
			AsnInputStream ais = new AsnInputStream(buf);
			ind.decodeData(ais, buf.length);
		}

		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSmsListener) serLis).onMoForwardShortMessageRespIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing onMoForwardShortMessageRespIndication: " + e.getMessage(), e);
			}
		}
	}
	
	private void mtForwardShortMessageRequest(Parameter parameter, MAPDialogSmsImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {
		
		if (parameter == null)
			throw new MAPParsingComponentException("Error while decoding mtForwardShortMessageRequest: Parameter is mandatory but not found",
					MAPParsingComponentExceptionReason.MistypedParameter);

		if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
			throw new MAPParsingComponentException(
					"Error while decoding mtForwardShortMessageRequest: Bad tag or tagClass or parameter is primitive, received tag=" + parameter.getTag(),
					MAPParsingComponentExceptionReason.MistypedParameter);

		byte[] buf = parameter.getData();
		AsnInputStream ais = new AsnInputStream(buf);
		MtForwardShortMessageRequestIndicationImpl ind = new MtForwardShortMessageRequestIndicationImpl();
		ind.decodeData(ais, buf.length);
		
		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSmsListener) serLis).onMtForwardShortMessageIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing onMtForwardShortMessageIndication: " + e.getMessage(), e);
			}
		}
	}
	
	private void mtForwardShortMessageResponse(Parameter parameter, MAPDialogSmsImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {
		
		MtForwardShortMessageResponseIndicationImpl ind = new MtForwardShortMessageResponseIndicationImpl();

		if (parameter != null) {
			if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
				throw new MAPParsingComponentException(
						"Error while decoding mtForwardShortMessageResponse: Bad tag or tagClass or parameter is primitive, received tag=" + parameter.getTag(),
						MAPParsingComponentExceptionReason.MistypedParameter);

			byte[] buf = parameter.getData();
			AsnInputStream ais = new AsnInputStream(buf);
			ind.decodeData(ais, buf.length);
		}

		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSmsListener) serLis).onMtForwardShortMessageRespIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing onMtForwardShortMessageRespIndication: " + e.getMessage(), e);
			}
		}
	}
	
	private void sendRoutingInfoForSMRequest(Parameter parameter, MAPDialogSmsImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {
		
		if (parameter == null)
			throw new MAPParsingComponentException("Error while decoding sendRoutingInfoForSMRequest: Parameter is mandatory but not found",
					MAPParsingComponentExceptionReason.MistypedParameter);

		if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
			throw new MAPParsingComponentException(
					"Error while decoding sendRoutingInfoForSMRequest: Bad tag or tagClass or parameter is primitive, received tag=" + parameter.getTag(),
					MAPParsingComponentExceptionReason.MistypedParameter);

		byte[] buf = parameter.getData();
		AsnInputStream ais = new AsnInputStream(buf);
		SendRoutingInfoForSMRequestIndicationImpl ind = new SendRoutingInfoForSMRequestIndicationImpl();
		ind.decodeData(ais, buf.length);

		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSmsListener) serLis).onSendRoutingInfoForSMIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing onSendRoutingInfoForSMIndication: " + e.getMessage(), e);
			}
		}
	}
	
	private void sendRoutingInfoForSMResponse(Parameter parameter, MAPDialogSmsImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {
		
		SendRoutingInfoForSMResponseIndicationImpl ind = new SendRoutingInfoForSMResponseIndicationImpl();
		
		if (parameter == null)
			throw new MAPParsingComponentException("Error while decoding sendRoutingInfoForSMResponse: Parameter is mandatory but not found",
					MAPParsingComponentExceptionReason.MistypedParameter);

		if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
			throw new MAPParsingComponentException(
					"Error while decoding sendRoutingInfoForSMResponse: Bad tag or tagClass or parameter is primitive, received tag=" + parameter.getTag(),
					MAPParsingComponentExceptionReason.MistypedParameter);


		byte[] buf = parameter.getData();
		AsnInputStream ais = new AsnInputStream(buf);
		ind.decodeData(ais, buf.length);

		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSmsListener) serLis).onSendRoutingInfoForSMRespIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing onSendRoutingInfoForSMRespIndication: " + e.getMessage(), e);
			}
		}
	}
	
	private void reportSMDeliveryStatusRequest(Parameter parameter, MAPDialogSmsImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {
		
		if (parameter == null)
			throw new MAPParsingComponentException("Error while decoding sendRoutingInfoForSMRequest: Parameter is mandatory but not found",
					MAPParsingComponentExceptionReason.MistypedParameter);

		if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
			throw new MAPParsingComponentException(
					"Error while decoding sendRoutingInfoForSMRequest: Bad tag or tagClass or parameter is primitive, received tag=" + parameter.getTag(),
					MAPParsingComponentExceptionReason.MistypedParameter);

		byte[] buf = parameter.getData();
		AsnInputStream ais = new AsnInputStream(buf);
		ReportSMDeliveryStatusRequestIndicationImpl ind = new ReportSMDeliveryStatusRequestIndicationImpl(mapDialogImpl.getApplicationContext()
				.getApplicationContextVersion().getVersion());
		ind.decodeData(ais, buf.length);
		
		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSmsListener) serLis).onReportSMDeliveryStatusIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing onReportSMDeliveryStatusIndication: " + e.getMessage(), e);
			}
		}
	}
	
	private void reportSMDeliveryStatusResponse(Parameter parameter, MAPDialogSmsImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {
		
		ReportSMDeliveryStatusResponseIndicationImpl ind = new ReportSMDeliveryStatusResponseIndicationImpl();

		if (parameter != null) {
			if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
				throw new MAPParsingComponentException(
						"Error while decoding reportSMDeliveryStatusResponse: Bad tag or tagClass or parameter is primitive, received tag=" + parameter.getTag(),
						MAPParsingComponentExceptionReason.MistypedParameter);

			byte[] buf = parameter.getData();
			AsnInputStream ais = new AsnInputStream(buf);
			ind.decodeData(ais, buf.length);
		}

		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSmsListener) serLis).onReportSMDeliveryStatusRespIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing onReportSMDeliveryStatusRespIndication: " + e.getMessage(), e);
			}
		}
	}
	
	private void informServiceCentreRequest(Parameter parameter, MAPDialogSmsImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {

		if (parameter == null)
			throw new MAPParsingComponentException("Error while decoding informServiceCentreRequest: Parameter is mandatory but not found",
					MAPParsingComponentExceptionReason.MistypedParameter);

		if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
			throw new MAPParsingComponentException(
					"Error while decoding informServiceCentreRequest: Bad tag or tagClass or parameter is primitive, received tag=" + parameter.getTag(),
					MAPParsingComponentExceptionReason.MistypedParameter);

		InformServiceCentreRequestIndicationImpl ind = new InformServiceCentreRequestIndicationImpl();
		byte[] buf = parameter.getData();
		AsnInputStream ais = new AsnInputStream(buf);
		ind.decodeData(ais, buf.length);

		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);
		
		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSmsListener) serLis).onInformServiceCentreIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing onInformServiceCentreIndication: " + e.getMessage(), e);
			}
		}
	}
	
	private void alertServiceCentreRequest(Parameter parameter, MAPDialogSmsImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {

		if (parameter == null)
			throw new MAPParsingComponentException("Error while decoding alertServiceCentreRequest: Parameter is mandatory but not found",
					MAPParsingComponentExceptionReason.MistypedParameter);

		if (parameter.getTag() != Tag.SEQUENCE || parameter.getTagClass() != Tag.CLASS_UNIVERSAL || parameter.isPrimitive())
			throw new MAPParsingComponentException(
					"Error while decoding alertServiceCentreRequest: Bad tag or tagClass or parameter is primitive, received tag=" + parameter.getTag(),
					MAPParsingComponentExceptionReason.MistypedParameter);

		AlertServiceCentreRequestIndicationImpl ind = new AlertServiceCentreRequestIndicationImpl();
		byte[] buf = parameter.getData();
		AsnInputStream ais = new AsnInputStream(buf);
		ind.decodeData(ais, buf.length);

		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			try {
				((MAPServiceSmsListener) serLis).onAlertServiceCentreIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing onAlertServiceCentreIndication: " + e.getMessage(), e);
			}
		}
	}
	
	private void alertServiceCentreResponse(Parameter parameter, MAPDialogSmsImpl mapDialogImpl, Long invokeId) throws MAPParsingComponentException {
		
		AlertServiceCentreResponseIndicationImpl ind = new AlertServiceCentreResponseIndicationImpl();

		ind.setInvokeId(invokeId);
		ind.setMAPDialog(mapDialogImpl);

		for (MAPServiceListener serLis : this.serviceListeners) {
			
			try {
				((MAPServiceSmsListener) serLis).onAlertServiceCentreRespIndication(ind);
			} catch (Exception e) {
				loger.error("Error processing onAlertServiceCentreRespIndication: " + e.getMessage(), e);
			}			
			
		}
	}

}

