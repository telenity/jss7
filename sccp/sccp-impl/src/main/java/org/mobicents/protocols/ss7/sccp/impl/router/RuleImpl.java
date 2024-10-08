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

package org.mobicents.protocols.ss7.sccp.impl.router;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javolution.xml.XMLFormat;
import javolution.xml.stream.XMLStreamException;

import org.apache.log4j.Logger;
import org.mobicents.protocols.ss7.indicator.GlobalTitleIndicator;
import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.sccp.LoadSharingAlgorithm;
import org.mobicents.protocols.ss7.sccp.OriginationType;
import org.mobicents.protocols.ss7.sccp.Rule;
import org.mobicents.protocols.ss7.sccp.RuleType;
import org.mobicents.protocols.ss7.sccp.parameter.GT0001;
import org.mobicents.protocols.ss7.sccp.parameter.GT0010;
import org.mobicents.protocols.ss7.sccp.parameter.GT0011;
import org.mobicents.protocols.ss7.sccp.parameter.GT0100;
import org.mobicents.protocols.ss7.sccp.parameter.GlobalTitle;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;

/**
 * @author amit bhayani
 * @author kulikov
 * @author sergey vetyutnev
 */
public class RuleImpl implements Rule, Serializable {
	
	private static final char CHAR_WILD_CARD_ALL = '*';
	private static final char CHAR_WILD_CARD_SINGLE = '?';
	private static final char CHAR_MASK_SEPARATOR = '/';

	private static final String WILD_CARD_ALL = "*";
	private static final String WILD_CARD_SINGLE = "?";
	private static final String MASK_SEPARATOR = "/";
	private static final String MASK_KEEP = "K";
	private static final String MASK_REPLACE = "R";
	private static final String MASK_IGNORE = "-";

	private static final Logger logger = Logger.getLogger(RuleImpl.class);
	/**
	 * 
	 */
	private static final long serialVersionUID = 2147449454267320237L;

	private static final String RULETYPE = "ruleType";
	private static final String ORIGINATING_TYPE = "originatingType";
	private static final String LS_ALGO = "loadSharingAlgo";
	private static final String PATTERN = "patternSccpAddress";
	private static final String PRIMARY_ADDRESS = "paddress";
	private static final String SECONDARY_ADDRESS = "saddress";
	private static final String LS_TABLE = "alst";
	private static final String MASK = "mask";

	private static final String SEPARATOR = ";";

	private RuleType ruleType = RuleType.Solitary;
	private LoadSharingAlgorithm loadSharingAlgo = LoadSharingAlgorithm.Undefined;
	private OriginationType originationType = OriginationType.ALL;

	/** Pattern used for selecting rule */
	private SccpAddress pattern;

	private int ruleId;

	/** Translation method */
	private int primaryAddressId;

	private int secondaryAddressId;

	private List<Integer> loadShareTable;

	private String mask;

	private String[] maskPattern;

	public RuleImpl() {

	}

	/**
	 * Creates new routing rule.
	 * 
	 * @param the
	 *            order number of the rule.
	 * @param pattern
	 *            pattern for rule selection.
	 * @param translation
	 *            translation method.
	 * @param mtpInfo
	 *            MTP routing info
	 */
	public RuleImpl(RuleType ruleType, LoadSharingAlgorithm loadSharingAlgo, OriginationType originationType, SccpAddress pattern, String mask) {
		this.ruleType = ruleType;
		this.pattern = pattern;
		this.mask = mask;
		this.setLoadSharingAlgorithm(loadSharingAlgo);
		this.setOriginationType(originationType);

		configure();
	}

	/**
	 * @return the mask
	 */
	public String getMask() {
		return mask;
	}

	/**
	 * @return the ruleId
	 */
	protected int getRuleId() {
		return ruleId;
	}

	/**
	 * @param ruleId
	 *            the ruleId to set
	 */
	protected void setRuleId(int ruleId) {
		this.ruleId = ruleId;
	}

	public RuleType getRuleType() {
		return ruleType;
	}

	public void setRuleType(RuleType ruleType) {
		this.ruleType = ruleType;
	}

	public LoadSharingAlgorithm getLoadSharingAlgorithm() {
		return loadSharingAlgo;
	}

	public void setLoadSharingAlgorithm(LoadSharingAlgorithm loadSharingAlgo) {
		if (loadSharingAlgo == null)
			this.loadSharingAlgo = LoadSharingAlgorithm.Undefined;
		else
			this.loadSharingAlgo = loadSharingAlgo;
	}

	public OriginationType getOriginationType() {
		return originationType;
	}

	public void setOriginationType(OriginationType originationType) {
		this.originationType = originationType;
	}

	public SccpAddress getPattern() {
		return pattern;
	}

	private void configure() {
		this.maskPattern = this.mask.split("/");
	}

	public int getPrimaryAddressId() {
		return primaryAddressId;
	}

	public void setPrimaryAddressId(int primaryAddressId) {
		this.primaryAddressId = primaryAddressId;
	}

	public int getSecondaryAddressId() {
		return secondaryAddressId;
	}

	public void setSecondaryAddressId(int secondaryAddressId) {
		this.secondaryAddressId = secondaryAddressId;
	}

	public List<Integer> getLoadShareTable() {
		return loadShareTable;
	}

	public void setLoadShareTable(List<Integer> loadShareTable) {
		this.loadShareTable = loadShareTable;
	}

	/**
	 * Translate specified address according to the rule.
	 * 
	 * @param address
	 *            the origin address
	 * @param ruleAddress
	 *            the address from the rule
	 * @return translated address
	 */
	public SccpAddress translate(SccpAddress address, SccpAddress ruleAddress) {

		String digits = address.getGlobalTitle().getDigits();
		String patternDigits = pattern.getGlobalTitle().getDigits();

		String primaryDigits = ruleAddress.getGlobalTitle().getDigits();

		// step #1. translate digits
		String translatedDigits = translateDigits(digits, maskPattern, patternDigits.split(MASK_SEPARATOR), primaryDigits.split(MASK_SEPARATOR));
		// step #2. translate global title
		GlobalTitle gt = null;

		if (translatedDigits != null && !translatedDigits.isEmpty()) {
			GlobalTitleIndicator gti = ruleAddress.getAddressIndicator().getGlobalTitleIndicator();
			GlobalTitle primaryGt = ruleAddress.getGlobalTitle();
			gt = createNewGT(gti, primaryGt, translatedDigits);

			if (gt == null) {
				// lets use GT from received address
				gt = createNewGT(address.getAddressIndicator().getGlobalTitleIndicator(), address.getGlobalTitle(), translatedDigits);
			}
		}

		int ssn = address.getSubsystemNumber();
		if (ruleAddress.getSubsystemNumber() > 0)
			ssn = ruleAddress.getSubsystemNumber();
		SccpAddress sccpAddress = new SccpAddress(ruleAddress.getAddressIndicator().getRoutingIndicator(), ruleAddress.getSignalingPointCode(), gt, ssn);

		// This is translated message
		sccpAddress.setTranslated(true);
		return sccpAddress;
	}

	private GlobalTitle createNewGT(GlobalTitleIndicator gti, GlobalTitle primaryGt, String translatedDigits) {
		GlobalTitle gt = null;
		switch (gti) {
		case GLOBAL_TITLE_INCLUDES_NATURE_OF_ADDRESS_INDICATOR_ONLY:
			gt = GlobalTitle.getInstance(((GT0001) primaryGt).getNoA(), translatedDigits);
			break;
		case GLOBAL_TITLE_INCLUDES_TRANSLATION_TYPE_NUMBERING_PLAN_AND_ENCODING_SCHEME:
			gt = GlobalTitle.getInstance(((GT0011) primaryGt).getTranslationType(), ((GT0011) primaryGt).getNp(), translatedDigits);
			break;
		case GLOBAL_TITLE_INCLUDES_TRANSLATION_TYPE_NUMBERING_PLAN_ENCODING_SCHEME_AND_NATURE_OF_ADDRESS:
			gt = GlobalTitle.getInstance(((GT0100) primaryGt).getTranslationType(), ((GT0100) primaryGt).getNumberingPlan(),
					((GT0100) primaryGt).getNatureOfAddress(), translatedDigits);
			break;
		case GLOBAL_TITLE_INCLUDES_TRANSLATION_TYPE_ONLY:
			gt = GlobalTitle.getInstance(((GT0010) primaryGt).getTranslationType(), translatedDigits);
			break;
		case NO_GLOBAL_TITLE_INCLUDED:
			// Use Global Title from received aadress
			break;
		}
		return gt;
	}

	public boolean matches(SccpAddress address, boolean isMtpOriginated) {

		// Rule is for GTT only
		if (address.getAddressIndicator().getRoutingIndicator() == RoutingIndicator.ROUTING_BASED_ON_DPC_AND_SSN) {
			if(logger.isInfoEnabled()){
				logger.info("RoutingIndicator == ROUTING_BASED_ON_DPC_AND_SSN. Return");
			}
			return false;
		}

		// checking firstly about rule OriginationType
		if (this.getOriginationType() == OriginationType.LOCAL && isMtpOriginated)
			return false;
		if (this.getOriginationType() == OriginationType.REMOTE && !isMtpOriginated)
			return false;

//		// SSN if present flag is set in pattern - must match address SSN & flag
//		// @TODO: disabled to avoid backward compatibility
//		if (!isSsnMatch(address, pattern)) {
//			return false;
//		}

		// Routing on GTT
		GlobalTitleIndicator gti = address.getAddressIndicator().getGlobalTitleIndicator();
		GlobalTitle patternGT = pattern.getGlobalTitle();
		switch (gti) {
		case GLOBAL_TITLE_INCLUDES_NATURE_OF_ADDRESS_INDICATOR_ONLY:
			GT0001 gt = (GT0001) address.getGlobalTitle();

			if (!(patternGT instanceof GT0001)) {
				if(logger.isInfoEnabled()){
					logger.info("patternGT not instanceof GT0001. Return False");
				}
				return false;
			}

			// nature of address must match
			if (((GT0001) patternGT).getNoA() != gt.getNoA()) {
				if(logger.isInfoEnabled()){
					logger.info(String.format("Noa didn't match. Pattern Noa=%s Address Noad=%s Return  False", ((GT0001) patternGT).getNoA(), gt.getNoA()));
				}
				return false;
			}

			// digits must match
			if (!matchPattern(gt.getDigits().toCharArray(), patternGT.getDigits().toCharArray())) {
				if(logger.isInfoEnabled()){
					logger.info(String.format("digits didn't match. Pattern digits=%s Address Digits=%s Return  False", patternGT.getDigits(), gt.getDigits()));
				}
				return false;
			}

			// all conditions passed
			return true;
		case GLOBAL_TITLE_INCLUDES_TRANSLATION_TYPE_NUMBERING_PLAN_AND_ENCODING_SCHEME:
			GT0011 gt1 = (GT0011) address.getGlobalTitle();

			if (!(patternGT instanceof GT0011)) {
				if(logger.isInfoEnabled()){
					logger.info("patternGT not instanceof GT0011. Return False");
				}
				return false;
			}

			// translation type should match
			if (((GT0011) patternGT).getTranslationType() != gt1.getTranslationType()) {
				if(logger.isInfoEnabled()){
					logger.info(String.format("TT didn't match. Pattern TT=%s Address TT=%s Return  False", ((GT0011) patternGT).getTranslationType(),
						gt1.getTranslationType()));
				}
				return false;
			}

			// numbering plan should match
			if (((GT0011) patternGT).getNp() != gt1.getNp()) {
				if(logger.isInfoEnabled()){
					logger.info(String.format("Np didn't match. Pattern Np=%s Address Np=%s Return  False", ((GT0011) patternGT).getNp(), gt1.getNp()));
				}
				return false;
			}

			// digits must match
			if (!matchPattern(gt1.getDigits().toCharArray(), patternGT.getDigits().toCharArray())) {
				if(logger.isInfoEnabled()){
					logger.info(String.format("digits didn't match. Pattern digits=%s Address Digits=%s Return  False", patternGT.getDigits(), gt1.getDigits()));
				}
				return false;
			}

			// all conditions passed
			return true;
		case GLOBAL_TITLE_INCLUDES_TRANSLATION_TYPE_NUMBERING_PLAN_ENCODING_SCHEME_AND_NATURE_OF_ADDRESS:
			GT0100 gt2 = (GT0100) address.getGlobalTitle();

			if (!(patternGT instanceof GT0100)) {
				if(logger.isInfoEnabled()){
					logger.info("patternGT not instanceof GT0100. Return False");
				}
				return false;
			}

			// translation type should match
			if (((GT0100) patternGT).getTranslationType() != gt2.getTranslationType()) {
				if(logger.isInfoEnabled()){
					logger.info(String.format("TT didn't match. Pattern TT=%s Address TT=%s Return  False", ((GT0100) patternGT).getTranslationType(),
						gt2.getTranslationType()));
				}
				return false;
			}

			// numbering plan should match
			if (((GT0100) patternGT).getNumberingPlan() != gt2.getNumberingPlan()) {
				if(logger.isInfoEnabled()){
					logger.info(String.format("Np didn't match. Pattern Np=%s Address Np=%s Return  False", ((GT0100) patternGT).getNumberingPlan(),
						gt2.getNumberingPlan()));
				}
				return false;
			}

			// nature of address must match
			if (((GT0100) patternGT).getNatureOfAddress() != gt2.getNatureOfAddress()) {
				if(logger.isInfoEnabled()){
					logger.info(String.format("Noa didn't match. Pattern Noa=%s Address Noa=%s Return  False", ((GT0100) patternGT).getNatureOfAddress(),
						gt2.getNatureOfAddress()));
				}
				return false;
			}

			// digits must match
			if (!matchPattern(gt2.getDigits().toCharArray(), pattern.getGlobalTitle().getDigits().toCharArray())) {
				if(logger.isInfoEnabled()){
					logger.info(String.format("digits didn't match. Pattern digits=%s Address Digits=%s Return  False", patternGT.getDigits(), gt2.getDigits()));
				}
				return false;
			}

			// all conditions passed
			return true;
		case GLOBAL_TITLE_INCLUDES_TRANSLATION_TYPE_ONLY:
			GT0010 gt3 = (GT0010) address.getGlobalTitle();

			if (!(patternGT instanceof GT0010)) {
				if(logger.isInfoEnabled()){
					logger.info("patternGT not instanceof GT0100. Return False");
				}
				return false;
			}

			// translation type should match
			if (((GT0010) patternGT).getTranslationType() != gt3.getTranslationType()) {
				if(logger.isInfoEnabled()){
					logger.info(String.format("TT didn't match. Pattern TT=%s Address TT=%s Return  False", ((GT0010) patternGT).getTranslationType(),
						gt3.getTranslationType()));
				}
				return false;
			}

			// digits must match
			if (!matchPattern(gt3.getDigits().toCharArray(), pattern.getGlobalTitle().getDigits().toCharArray())) {
				if(logger.isInfoEnabled()){
					logger.info(String.format("digits didn't match. Pattern digits=%s Address Digits=%s Return  False", patternGT.getDigits(), gt3.getDigits()));
				}
				return false;
			}

			// all conditions passed
			return true;
		default:
			return false;
		}
	}

	/**
	 * Checks if SSN matches between rule address pattern and provided destination address. SSN is assumed to always match in
	 * case it has insignificant value or pattern AI SSNPresent flag is set to false.
	 *
	 * @param address - a provided address to match
	 * @param pattern - a rule pattern address
	 * @return true if SSN is present in both pattern and received addresses and they are the same or pattern has SSN flag unset
	 *         in AI (bit 7)(isSsnPresent = false for pattern) or pattern SSN value is insignificant
	 */
	private boolean isSsnMatch(SccpAddress address, SccpAddress pattern) {
		if (!isSsnSignificant(pattern.getSubsystemNumber()) || !pattern.getAddressIndicator().ssnPresent()) {
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("SSN is not present or insignificant [%s]. Assume SSN matches. Return True",
						pattern.getSubsystemNumber()));
			}
			return true;
		}
		if (pattern.getAddressIndicator().ssnPresent() && address.getAddressIndicator().ssnPresent()) {
			if (address.getSubsystemNumber() == pattern.getSubsystemNumber()) {
				return true;
			}
		}

		if (logger.isDebugEnabled()) {
			logger.debug(String.format(
					"SSN didn't match. Pattern: isSsnPresent=%s, SSN=%s Address: isSsnPresent=%s, SSN=%s Return  False",
					pattern.getAddressIndicator().ssnPresent(), pattern.getSubsystemNumber(), address.getAddressIndicator()
							.ssnPresent(), address.getSubsystemNumber()));
		}

		return false;
	}

	/**
	 * Checks if provided SSN value is a meaningful value = between 1 and 255. SSN=0 is for management messages and is not
	 * perceived as significant value
	 *
	 * @param ssn SSN value to check
	 * @return true if SSN value is within significant range
	 */
	private boolean isSsnSignificant(int ssn) {
		return (1 <= ssn && ssn <= 255);
	}

	private String translateDigits(String digits, String[] masks, String[] patternDigits, String[] addressDigits) {
		StringBuffer translatedDigits = new StringBuffer();
		String[] digitComponents = new String[patternDigits.length];
		int offset = 0;
		for (int count = 0; count < patternDigits.length; count++) {
			if (patternDigits[count].equals(WILD_CARD_ALL)) {
				digitComponents[count] = digits.substring(offset);
				break;
			} else {
				digitComponents[count] = digits.substring(offset, offset + patternDigits[count].length());
			}
			offset += patternDigits[count].length();
		}

		for (int count = 0; count < patternDigits.length; count++) {
			if (masks[count].equals(MASK_KEEP)) {
				if (digitComponents[count] != null) {
					// Check if digits is null? This is possible if user
					// specified pattern like */5555 where all the digits are
					// consumed
					translatedDigits.append(digitComponents[count]);
				}
			} else if (masks[count].equals(MASK_REPLACE)) {
				// If its not padding
				if (!addressDigits[count].contains(MASK_IGNORE)) {
					translatedDigits.append(addressDigits[count]);
				}
			} else {
				// TODO Throw exception or return original digits only?
			}
		}
		return translatedDigits.toString();
	}

	private boolean matchPattern(char[] digits, char[] pattern) {
		int j = 0;
		for (int i = 0; i < pattern.length; i++) {

			if (j >= digits.length) {
				// Pattern has more digits to match than digits; 
				
				//special case where pattern can be xxxxx/* and digits are xxxxx so this should match. 
				if(pattern[j] == CHAR_MASK_SEPARATOR && pattern[j+1] == CHAR_WILD_CARD_ALL){
					return true;
				}
				
				//return false
				return false;
			}

			if (pattern[i] == CHAR_WILD_CARD_ALL) {
				return true;
			} else if (pattern[i] == CHAR_WILD_CARD_SINGLE) {
				j++;
			} else if (pattern[i] == CHAR_MASK_SEPARATOR) {
				continue;
			} else if (pattern[i] != digits[j]) {
				return false;
			} else {
				j++;
			}
		}

		if (j == digits.length) {
			// We compared all the digits and all of them matched, this Rule
			// matches.
			return true;
		}

		return false;
	}

	/**
	 * XML Serialization/Deserialization
	 */
	protected static final XMLFormat<RuleImpl> RULE_XML = new XMLFormat<RuleImpl>(RuleImpl.class) {

		public void read(javolution.xml.XMLFormat.InputElement xml, RuleImpl rule) throws XMLStreamException {
			rule.ruleType = RuleType.getInstance(xml.getAttribute(RULETYPE, RuleType.Solitary.getType()));
			rule.loadSharingAlgo = LoadSharingAlgorithm.getInstance(xml.getAttribute(LS_ALGO, LoadSharingAlgorithm.Undefined.getAlgo()));
			rule.originationType = OriginationType.getInstance(xml.getAttribute(ORIGINATING_TYPE, OriginationType.ALL.getValue()));
			rule.mask = xml.getAttribute(MASK).toString();
			rule.primaryAddressId = xml.getAttribute(PRIMARY_ADDRESS).toInt();
			rule.secondaryAddressId = xml.getAttribute(SECONDARY_ADDRESS).toInt();

			String alst = xml.getAttribute(LS_TABLE, "");
			List<Integer> convertedList = new LinkedList<Integer>();
			if (alst.length() > 0) {
				String[] convertedArray = alst.split(",");
				for (String number : convertedArray) {
					convertedList.add(Integer.parseInt(number.trim()));
				}
			}
			rule.loadShareTable = convertedList;

			rule.pattern = xml.get(PATTERN, SccpAddress.class);
			rule.configure();
		}

		public void write(RuleImpl rule, javolution.xml.XMLFormat.OutputElement xml) throws XMLStreamException {
			xml.setAttribute(RULETYPE, rule.ruleType.toString());
			xml.setAttribute(LS_ALGO, rule.loadSharingAlgo.toString());
			xml.setAttribute(ORIGINATING_TYPE, rule.originationType.getValue());
			xml.setAttribute(MASK, rule.mask);
			xml.setAttribute(PRIMARY_ADDRESS, rule.primaryAddressId);
			xml.setAttribute(SECONDARY_ADDRESS, rule.secondaryAddressId);

			if (rule.loadShareTable != null && rule.loadShareTable.size() > 0) {
				StringBuilder alst = new StringBuilder();
				Iterator<Integer> iter = rule.loadShareTable.iterator();
				while (iter.hasNext()) {
					alst.append(iter.next());
					if(iter.hasNext()) {
						alst.append(',');
					}
				}
				xml.setAttribute(LS_TABLE, alst);
			}

			xml.add(rule.pattern, PATTERN, SccpAddress.class);
		}
	};

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("RuleImpl{");
		sb.append("pattern=").append(pattern);
		sb.append(", ruleId=").append(ruleId);
		sb.append(", primaryAddressId=").append(primaryAddressId);
		sb.append(", secondaryAddressId=").append(secondaryAddressId);
		sb.append(", loadShareTable=").append(loadShareTable);
		sb.append(", mask='").append(mask).append('\'');
		sb.append(", maskPattern=").append(Arrays.toString(maskPattern));
		sb.append('}');
		return sb.toString();
	}

}
