/**
 * 
 */
package org.mobicents.protocols.asn;

import java.io.IOException;

/**
 * Represents EXTERNAL type (ITU-T X.690). Subclass to decode the actual data
 * once the encoding type is known. Typical decode pattern:
 *
 * <pre>
 * decode() {
 *   super.decode();
 *   if (super.getType == requiredType)
 *     this.decode();
 *   else
 *     // indicate error
 * }
 * </pre>
 *
 * Encode/decode methods should be extended accordingly.
 *
 * @author baranowb
 * @author amit bhayani
 * @author sergey vetyutnev
 */
public class External {
	// FIXME: makes this proper, it should be kind of universal container...

	protected static final int _TAG_EXTERNAL_CLASS = Tag.CLASS_UNIVERSAL;
	protected static final boolean _TAG_EXTERNAL_PC_PRIMITIVE = false;

	protected static final int _TAG_ASN = 0x00;
	protected static final int _TAG_ASN_CLASS = Tag.CLASS_CONTEXT_SPECIFIC;
	protected static final boolean _TAG_ASN_PC_PRIMITIVE = false;

	protected static final int _TAG_ARBITRARY = 0x02;
	protected static final int _TAG_ARBITRARY_CLASS = Tag.CLASS_CONTEXT_SPECIFIC;

	protected static final int _TAG_OCTET_ALIGNED = 0x01;
	protected static final int _TAG_OCTET_ALIGNED_CLASS = Tag.CLASS_CONTEXT_SPECIFIC;

	protected static final int _TAG_IMPLICIT_SEQUENCE = 0x08;

	protected boolean oid = false;
	protected boolean integer = false;
	protected boolean objDescriptor = false;

	protected long[] oidValue = null;
	protected long indirectReference = 0;
	protected String objDescriptorValue = null;

	private boolean asn = false;
	private boolean octet = false;
	private boolean arbitrary = false;

	//FIXME: ensure structure from file and if it does not allow more than one type of data, enforce that!

	private byte[] data;
	private BitSetStrictLength bitDataString;

	/**
	 * EXTERNAL ::= [UNIVERSAL 8] IMPLICIT SEQUENCE {
	 *   direct-reference    OBJECT IDENTIFIER OPTIONAL,
	 *   indirect-reference  INTEGER OPTIONAL,
	 *   data-value-descriptor ObjectDescriptor OPTIONAL,
	 *   encoding CHOICE {
	 *     single-ASN1-type  [0] ANY,
	 *     octet-aligned     [1] IMPLICIT OCTET STRING,
	 *     arbitrary         [2] IMPLICIT BIT STRING }}
	 */
	public void decode(AsnInputStream ais) throws AsnException {
		oid = false;
		integer = false;
		objDescriptor = false;
		oidValue = null;
		indirectReference = 0;
		objDescriptorValue = null;
		asn = false;
		octet = false;
		arbitrary = false;
		data = null;
		bitDataString = null;
		try {
			AsnInputStream localAsnIS = ais.readSequenceStream();
			while (localAsnIS.available() > 0) {
				int tag = localAsnIS.readTag();
				if (localAsnIS.getTagClass() == Tag.CLASS_UNIVERSAL) {
					switch (tag) {
					case Tag.INTEGER:
						indirectReference = localAsnIS.readInteger();
						setInteger(true);
						break;
					case Tag.OBJECT_IDENTIFIER:
						oidValue = localAsnIS.readObjectIdentifier();
						setOid(true);
						break;
					case Tag.OBJECT_DESCRIPTOR:
						objDescriptorValue = localAsnIS.readGraphicString();
						setObjDescriptor(true);
						break;
					default:
						throw new AsnException("Error while decoding External: Unrecognized tag value=" + tag + ", tagClass=" + localAsnIS.getTagClass());
					}
				} else if (localAsnIS.getTagClass() == Tag.CLASS_CONTEXT_SPECIFIC) {
					switch (tag) {
					case _TAG_ASN:
						data = localAsnIS.readSequence();
						setAsn(true);
						break;
					case _TAG_OCTET_ALIGNED:
						setEncodeType(localAsnIS.readOctetString());
						setOctet(true);
						break;
					case _TAG_ARBITRARY:
						setEncodeBitStringType(localAsnIS.readBitString());
						setArbitrary(true);
						break;
					default:
						throw new AsnException("Error while decoding External: Unrecognized tag value=" + tag + ", tagClass=" + localAsnIS.getTagClass());
					}
					if (localAsnIS.available() != 0)
						throw new AsnException("Error while decoding External: data field must be the last");
				} else {
					throw new AsnException("Error while decoding External: Unrecognized tag value=" + tag + ", tagClass=" + localAsnIS.getTagClass());
				}
			}
		} catch (IOException e) {
			throw new AsnException("IOException while decoding External: " + e.getMessage(), e);
		}
	}

	public void encode(AsnOutputStream aos) throws AsnException {
		encode(aos, Tag.CLASS_UNIVERSAL, Tag.EXTERNAL);
	}

	public void encode(AsnOutputStream aos, int tagClass, int tag) throws AsnException {
		if (!oid && !integer)
			throw new AsnException("Error while encoding External: oid value or integer value must be definite");
		if (!asn && !octet && !arbitrary)
			throw new AsnException("Error while encoding External: asn value, octet value or arbitrary value must be definite");
		try {
			aos.writeTag(tagClass, false, tag);
			int pos1 = aos.StartContentDefiniteLength();
			if (oid)
				aos.writeObjectIdentifier(oidValue);
			if (integer)
				aos.writeInteger(indirectReference);
			if (objDescriptor)
				aos.writeStringGraphic(Tag.CLASS_UNIVERSAL, Tag.OBJECT_DESCRIPTOR, objDescriptorValue);
			if (asn) {
				aos.writeTag(Tag.CLASS_CONTEXT_SPECIFIC, false, _TAG_ASN);
				byte[] childData = getEncodeType();
				aos.writeLength(childData.length);
				aos.write(childData);
			} else if (octet) {
				aos.writeOctetString(Tag.CLASS_CONTEXT_SPECIFIC, _TAG_OCTET_ALIGNED, getEncodeType());
			} else if (arbitrary) {
				aos.writeBitString(Tag.CLASS_CONTEXT_SPECIFIC, _TAG_ARBITRARY, bitDataString);
			}
			aos.FinalizeContent(pos1);
		} catch (IOException e) {
			throw new AsnException(e);
		}
	}

	public byte[] getEncodeType() throws AsnException {
		return data;
	}

	public void setEncodeType(byte[] data) {
		this.data = data;
	}

	public BitSetStrictLength getEncodeBitStringType() throws AsnException {
		return bitDataString;
	}

	public void setEncodeBitStringType(BitSetStrictLength data) {
		bitDataString = data;
		setArbitrary(true);
	}

	public boolean isOid() {
		return oid;
	}

	public void setOid(boolean oid) {
		this.oid = oid;
	}

	public boolean isInteger() {
		return integer;
	}

	public void setInteger(boolean integer) {
		this.integer = integer;
	}

	public boolean isObjDescriptor() {
		return objDescriptor;
	}

	public void setObjDescriptor(boolean objDescriptor) {
		this.objDescriptor = objDescriptor;
	}

	public long[] getOidValue() {
		return oidValue;
	}

	public void setOidValue(long[] oidValue) {
		this.oidValue = oidValue;
	}

	public long getIndirectReference() {
		return indirectReference;
	}

	public void setIndirectReference(long indirectReference) {
		this.indirectReference = indirectReference;
	}

	public String getObjDescriptorValue() {
		return objDescriptorValue;
	}

	public void setObjDescriptorValue(String objDescriptorValue) {
		this.objDescriptorValue = objDescriptorValue;
	}

	public boolean isAsn() {
		return asn;
	}

	public void setAsn(boolean asn) {
		this.asn = asn;
		if (asn) {
			setArbitrary(false);
			setOctet(false);
		}
	}

	public boolean isOctet() {
		return octet;
	}

	public void setOctet(boolean octet) {
		this.octet = octet;
		if (octet) {
			setArbitrary(false);
			setAsn(false);
		}
	}

	public boolean isArbitrary() {
		return arbitrary;
	}

	public void setArbitrary(boolean arbitrary) {
		this.arbitrary = arbitrary;
		if (arbitrary) {
			setObjDescriptor(false);
			setAsn(false);
		}
	}
}
