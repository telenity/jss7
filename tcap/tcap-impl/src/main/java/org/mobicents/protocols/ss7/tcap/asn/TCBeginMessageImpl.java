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

package org.mobicents.protocols.ss7.tcap.asn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.mobicents.protocols.asn.AsnException;
import org.mobicents.protocols.asn.AsnInputStream;
import org.mobicents.protocols.asn.AsnOutputStream;
import org.mobicents.protocols.asn.Tag;
import org.mobicents.protocols.ss7.tcap.asn.comp.Component;
import org.mobicents.protocols.ss7.tcap.asn.comp.PAbortCauseType;
import org.mobicents.protocols.ss7.tcap.asn.comp.TCBeginMessage;

/**
 * @author baranowb
 * @author sergey vetyutnev
 * 
 */
public class TCBeginMessageImpl implements TCBeginMessage {

	// mandatory
	private byte[] originatingTransactionId;
	// opt
	private DialogPortion dp;
	// opt
	private List<Component> component;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.tcap.asn.comp.TCBeginMessage#getComponent()
	 */
	public List<Component> getComponent() {
		return this.component;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.tcap.asn.comp.TCBeginMessage#getDialogPortion
	 * ()
	 */
	public DialogPortion getDialogPortion() {

		return this.dp;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seeorg.mobicents.protocols.ss7.tcap.asn.comp.TCBeginMessage#
	 * getOriginatingTransactionId()
	 */
	public byte[] getOriginatingTransactionId() {

		return this.originatingTransactionId;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.tcap.asn.comp.TCBeginMessage#setComponent
	 * (org.mobicents.protocols.ss7.tcap.asn.comp.Component[])
	 */
	public void setComponent(List<Component> c) {
		this.component = c;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.tcap.asn.comp.TCBeginMessage#setDialogPortion
	 * (org.mobicents.protocols.ss7.tcap.asn.DialogPortion)
	 */
	public void setDialogPortion(DialogPortion dp) {
		this.dp = dp;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @seeorg.mobicents.protocols.ss7.tcap.asn.comp.TCBeginMessage#
	 * setOriginatingTransactionId(java.lang.String)
	 */
	public void setOriginatingTransactionId(byte[] t) {
		this.originatingTransactionId = t;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.tcap.asn.Encodable#decode(org.mobicents.protocols
	 * .asn.AsnInputStream)
	 */
	public void decode(AsnInputStream ais) throws ParseException {
		try {
			AsnInputStream localAis = ais.readSequenceStream();

			int tag = localAis.readTag();
			if (tag != _TAG_OTX || localAis.getTagClass() != Tag.CLASS_APPLICATION)
				throw new ParseException(PAbortCauseType.IncorrectTxPortion, null,
						"Error decoding TC-Begin: Expected OriginatingTransactionId, found tag: " + tag);
			this.originatingTransactionId = localAis.readOctetString();

			while (true) {
				if (localAis.available() == 0)
					return;
				
				tag = localAis.readTag();
				if (localAis.isTagPrimitive() || localAis.getTagClass() != Tag.CLASS_APPLICATION)
					throw new ParseException(PAbortCauseType.IncorrectTxPortion, null,
							"Error decoding TC-Begin: DialogPortion and Component portion must be constructive and has tag class CLASS_APPLICATION");
				
				switch(tag) {
				case DialogPortion._TAG:
					this.dp = TcapFactory.createDialogPortion(localAis);
					break;
					
				case Component._COMPONENT_TAG:
					AsnInputStream compAis = localAis.readSequenceStream();
					List<Component> cps = new ArrayList<>();
					// its iterator :)
					while (compAis.available() > 0) {
						Component c = TcapFactory.createComponent(compAis);
						if(c == null)
						{
							break;
						}
						cps.add(c);
					}
					setComponent(cps);
					break;
					
				default:
					throw new ParseException(PAbortCauseType.IncorrectTxPortion, null,
							"Error decoding TC-Begin: DialogPortion and Componebt parsing: bad tag - " + tag);
				}
			}

		} catch (IOException e) {
			throw new ParseException(PAbortCauseType.BadlyFormattedTxPortion, null, "IOException while decoding TC-Begin: " + e.getMessage(), e);
		} catch (AsnException e) {
			throw new ParseException(PAbortCauseType.BadlyFormattedTxPortion, null, "AsnException while decoding TC-Begin: " + e.getMessage(), e);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.tcap.asn.Encodable#encode(org.mobicents.protocols
	 * .asn.AsnOutputStream)
	 */
	public void encode(AsnOutputStream aos) throws EncodeException {
		
//		if (this.originatingTransactionId == null)
//			throw new ParseException("Error encoding TC-Begin: originatingTransactionId must not be null");
		
		try {
			aos.writeTag(Tag.CLASS_APPLICATION, false, _TAG);
			int pos = aos.StartContentDefiniteLength();

//			Utils.writeTransactionId(aos, this.originatingTransactionId, Tag.CLASS_APPLICATION, _TAG_OTX);
			aos.writeOctetString(Tag.CLASS_APPLICATION, _TAG_OTX, this.originatingTransactionId);

			if (this.dp != null)
				this.dp.encode(aos);

			if (component != null) {
				aos.writeTag(Tag.CLASS_APPLICATION, false, Component._COMPONENT_TAG);
				int pos2 = aos.StartContentDefiniteLength();
				for (Component c : this.component) {
					c.encode(aos);
				}
				aos.FinalizeContent(pos2);
			}

			aos.FinalizeContent(pos);
			
		} catch (IOException e) {
			throw new EncodeException("IOException while encoding TC-Begin: " + e.getMessage(), e);
		} catch (AsnException e) {
			throw new EncodeException("AsnException while encoding TC-Begin: " + e.getMessage(), e);
		}

	}

}
