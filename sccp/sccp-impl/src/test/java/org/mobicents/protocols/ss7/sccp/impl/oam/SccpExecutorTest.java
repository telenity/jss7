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

package org.mobicents.protocols.ss7.sccp.impl.oam;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.mobicents.protocols.ss7.indicator.GlobalTitleIndicator;
import org.mobicents.protocols.ss7.indicator.NatureOfAddress;
import org.mobicents.protocols.ss7.indicator.NumberingPlan;
import org.mobicents.protocols.ss7.indicator.RoutingIndicator;
import org.mobicents.protocols.ss7.mtp.Mtp3TransferPrimitive;
import org.mobicents.protocols.ss7.mtp.Mtp3TransferPrimitiveFactory;
import org.mobicents.protocols.ss7.mtp.Mtp3UserPart;
import org.mobicents.protocols.ss7.mtp.Mtp3UserPartListener;
import org.mobicents.protocols.ss7.mtp.RoutingLabelFormat;
import org.mobicents.protocols.ss7.sccp.ConcernedSignalingPointCode;
import org.mobicents.protocols.ss7.sccp.LoadSharingAlgorithm;
import org.mobicents.protocols.ss7.sccp.LongMessageRule;
import org.mobicents.protocols.ss7.sccp.LongMessageRuleType;
import org.mobicents.protocols.ss7.sccp.Mtp3Destination;
import org.mobicents.protocols.ss7.sccp.Mtp3ServiceAccessPoint;
import org.mobicents.protocols.ss7.sccp.RemoteSignalingPointCode;
import org.mobicents.protocols.ss7.sccp.RemoteSubSystem;
import org.mobicents.protocols.ss7.sccp.Router;
import org.mobicents.protocols.ss7.sccp.Rule;
import org.mobicents.protocols.ss7.sccp.RuleType;
import org.mobicents.protocols.ss7.sccp.SccpResource;
import org.mobicents.protocols.ss7.sccp.impl.SccpStackImpl;
import org.mobicents.protocols.ss7.sccp.parameter.GT0100;
import org.mobicents.protocols.ss7.sccp.parameter.SccpAddress;
import org.junit.AfterClass;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author amit bhayani
 * @author sergey vetyutnev
 *
 */
public class SccpExecutorTest {

	private Router router = null;
	private SccpResource sccpResource = null;
	private SccpStackImpl sccpStack = null;

	private SccpExecutor sccpExecutor = null;

	/**
	 *
	 */
	public SccpExecutorTest() {
	}

	@BeforeClass
	public static void setUpClass() throws Exception {
	}

	@AfterClass
	public static void tearDownClass() throws Exception {
	}

	@Before
	public void setUp() throws IllegalStateException {
		Mtp3UserPartImpl mtp3UserPartImpl = new Mtp3UserPartImpl();

		this.sccpStack = new SccpStackImpl("SccpExecutorTest");
		this.sccpStack.setMtp3UserPart(1, mtp3UserPartImpl);
		this.sccpStack.start();
		this.sccpStack.removeAllResourses();
		this.router = this.sccpStack.getRouter();
		this.sccpResource = this.sccpStack.getSccpResource();

		sccpExecutor = new SccpExecutor();
		sccpExecutor.setSccpStack(this.sccpStack);
	}

	@After
	public void tearDown() {
		this.sccpStack.stop();
	}

	@Test
	public void testManageRule() {

		String prim_addressCmd = "sccp primary_add create 1 71 2 8 0 0 3 123456789";
		String result = this.sccpExecutor.execute(prim_addressCmd.split(" "));
		assertTrue(result.equals(SccpOAMMessage.ADDRESS_SUCCESSFULLY_ADDED));
		assertEquals(1, this.router.getPrimaryAddresses().size());

		String createRuleCmd = "sccp rule create 1 R 71 2 8 0 0 3 123456789 solitary 1";
		// <id> <mask> <address-indicator> <point-code> <subsystem-number>
		// <translation-type> <numbering-plan> <nature-of-address-indicator>
		// <digits>
		// <ruleType> <primary-address-id> <backup-address-id>
		result = this.sccpExecutor.execute(createRuleCmd.split(" "));
		assertTrue(result.equals(SccpOAMMessage.RULE_SUCCESSFULLY_ADDED));
		assertEquals(1, this.router.getRules().size());
		assertEquals(1, this.router.getRules().get(1).getPrimaryAddressId());

		createRuleCmd = "sccp rule create 2 K 18 0 180 0 1 4 * solitary 1";
		result = this.sccpExecutor.execute(createRuleCmd.split(" "));
		assertTrue(result.equals(SccpOAMMessage.RULE_SUCCESSFULLY_ADDED));
		assertEquals(2, this.router.getRules().size());
		Rule rule = this.router.getRules().get(2);
		assertNotNull(rule);
		SccpAddress pattern = rule.getPattern();
		assertNotNull(pattern);
		assertEquals(18, (int) pattern.getAddressIndicator().getValue());
		assertEquals(RoutingIndicator.ROUTING_BASED_ON_GLOBAL_TITLE, pattern.getAddressIndicator().getRoutingIndicator());
		assertEquals(GlobalTitleIndicator.GLOBAL_TITLE_INCLUDES_TRANSLATION_TYPE_NUMBERING_PLAN_ENCODING_SCHEME_AND_NATURE_OF_ADDRESS, pattern.getGlobalTitle().getIndicator());
		GT0100 gt = (GT0100) pattern.getGlobalTitle();
		assertEquals(0, gt.getTranslationType());
		assertEquals(NumberingPlan.ISDN_TELEPHONY, gt.getNumberingPlan());
		assertEquals(NatureOfAddress.INTERNATIONAL, gt.getNatureOfAddress());
		assertEquals(RuleType.Solitary, rule.getRuleType());

		String sec_addressCmd = "sccp backup_add create 1 71 3 8 0 0 3 123456789";
		result = this.sccpExecutor.execute(sec_addressCmd.split(" "));
		assertTrue(result.equals(SccpOAMMessage.ADDRESS_SUCCESSFULLY_ADDED));
		assertEquals(1, this.router.getBackupAddresses().size());

		String createRuleCmd2 = "sccp rule create 3 R 71 2 8 0 0 3 123456789 dominant 1 1";
		result = this.sccpExecutor.execute(createRuleCmd2.split(" "));
		assertTrue(result.equals(SccpOAMMessage.RULE_SUCCESSFULLY_ADDED));
		assertEquals(3, this.router.getRules().size());
		assertEquals(RuleType.Dominant, this.router.getRule(3).getRuleType());

		createRuleCmd2 = "sccp rule create 4 R 71 2 8 0 0 3 123456789 loadshared 1 1 bit3";
		result = this.sccpExecutor.execute(createRuleCmd2.split(" "));
		assertTrue(result.equals(SccpOAMMessage.RULE_SUCCESSFULLY_ADDED));
		assertEquals(4, this.router.getRules().size());
		assertEquals(RuleType.Loadshared, this.router.getRule(4).getRuleType());
		assertEquals(LoadSharingAlgorithm.Bit3, this.router.getRule(4).getLoadSharingAlgorithm());

		createRuleCmd2 = "sccp rule modify 1 R 71 2 8 0 0 3 123456789 loadshared 1 1 bit4";
		result = this.sccpExecutor.execute(createRuleCmd2.split(" "));
		assertTrue(result.equals(SccpOAMMessage.RULE_SUCCESSFULLY_MODIFIED));
		assertEquals(4, this.router.getRules().size());
		assertEquals(RuleType.Loadshared, this.router.getRule(1).getRuleType());
		assertEquals(LoadSharingAlgorithm.Bit4, this.router.getRule(1).getLoadSharingAlgorithm());

		createRuleCmd2 = "sccp rule modify 1 R 71 2 8 0 0 3 123456789 dominant 1 1";
		result = this.sccpExecutor.execute(createRuleCmd2.split(" "));
		assertTrue(result.equals(SccpOAMMessage.RULE_SUCCESSFULLY_MODIFIED));
		assertEquals(4, this.router.getRules().size());
		assertEquals(RuleType.Dominant, this.router.getRule(1).getRuleType());

		createRuleCmd2 = "sccp rule modify 1 R 71 2 8 0 0 3 123456789 solitary 1";
		result = this.sccpExecutor.execute(createRuleCmd2.split(" "));
		assertTrue(result.equals(SccpOAMMessage.RULE_SUCCESSFULLY_MODIFIED));
		assertEquals(4, this.router.getRules().size());
		assertEquals(RuleType.Solitary, this.router.getRule(1).getRuleType());

		createRuleCmd2 = "sccp rule modify 1 R 71 2 8 0 0 3 123456789 dominant 1";
		result = this.sccpExecutor.execute(createRuleCmd2.split(" "));
		assertTrue(result.equals(SccpOAMMessage.RULETYPE_NOT_SOLI_SEC_ADD_MANDATORY));
		assertEquals(4, this.router.getRules().size());
		assertEquals(RuleType.Solitary, this.router.getRule(1).getRuleType());

		createRuleCmd2 = "sccp rule modify 1 R 71 2 8 0 0 3 123456789 dominant 1 2";
		result = this.sccpExecutor.execute(createRuleCmd2.split(" "));
		assertTrue(result.substring(0, 10).equals(SccpOAMMessage.NO_BACKUP_ADDRESS.substring(0, 10)));
		assertEquals(4, this.router.getRules().size());

		createRuleCmd2 = "sccp rule modify 1 R 71 2 8 0 0 3 123456789 dominant 2 1";
		result = this.sccpExecutor.execute(createRuleCmd2.split(" "));
		assertTrue(result.substring(0, 10).equals(SccpOAMMessage.NO_PRIMARY_ADDRESS.substring(0, 10)));
		assertEquals(4, this.router.getRules().size());

		createRuleCmd2 = "sccp rule modify 15 R 71 2 8 0 0 3 123456789 dominant 1 1";
		result = this.sccpExecutor.execute(createRuleCmd2.split(" "));
		assertTrue(result.equals(SccpOAMMessage.RULE_DOESNT_EXIST));
		assertEquals(4, this.router.getRules().size());

		createRuleCmd2 = "sccp rule create 11 R 71 2 8 0 0 3 123456789 dominant 1 2";
		result = this.sccpExecutor.execute(createRuleCmd2.split(" "));
		assertTrue(result.substring(0, 10).equals(SccpOAMMessage.NO_BACKUP_ADDRESS.substring(0, 10)));
		assertEquals(4, this.router.getRules().size());

		createRuleCmd2 = "sccp rule create 11 R 71 2 8 0 0 3 123456789 dominant 2 1";
		result = this.sccpExecutor.execute(createRuleCmd2.split(" "));
		assertTrue(result.substring(0, 10).equals(SccpOAMMessage.NO_PRIMARY_ADDRESS.substring(0, 10)));
		assertEquals(4, this.router.getRules().size());

		createRuleCmd2 = "sccp rule delete 15";
		result = this.sccpExecutor.execute(createRuleCmd2.split(" "));
		assertTrue(result.equals(SccpOAMMessage.RULE_DOESNT_EXIST));
		assertEquals(4, this.router.getRules().size());

		createRuleCmd2 = "sccp rule delete 1";
		result = this.sccpExecutor.execute(createRuleCmd2.split(" "));
		assertTrue(result.equals(SccpOAMMessage.RULE_SUCCESSFULLY_REMOVED));
		assertEquals(3, this.router.getRules().size());

		createRuleCmd2 = "sccp rule show 2";
		result = this.sccpExecutor.execute(createRuleCmd2.split(" "));

		createRuleCmd2 = "sccp rule show";
		result = this.sccpExecutor.execute(createRuleCmd2.split(" "));



//		String createRuleCmd3 = "sccp rule create 3 K 18 0 180 0 1 4 * 1";
//		this.sccpExecutor.execute(createRuleCmd3.split(" "));
//		assertEquals(SccpOAMMessage.RULE_SUCCESSFULLY_ADDED, result);
//		assertEquals(3, this.router.getRules().size());

	}

	@Test
	public void testMaskSectionsValidations() {

		String incorrect_prim_addressCmd = "sccp primary_add create 1 71 6535 8 0 0 12 93707100007";
		String incorrect_prim_address_deleteCmd = "sccp primary_add delete 1";
		String correct_prim_addressCmd = "sccp primary_add create 1 71 6535 8 0 0 12 -/-";

		String incorrectCreateRuleCmd = "sccp rule create 2 R/K 18 0 180 0 1 4 * solitary 1";
		String correctCreateRuleCmd = "sccp rule create 2 R/K 18 0 180 0 1 4 937/* solitary 1";

		String incorrect_sec_addressCmd = "sccp backup_add create 1 71 6535 8 0 0 12 93707100007";
		String correctCreateRuleCmdWithSecId = "sccp rule create 2 R/K 18 0 180 0 1 4 937/* solitary 1";


		String result = this.sccpExecutor.execute(incorrectCreateRuleCmd.split(" "));
		assertEquals(SccpOAMMessage.SEC_MISMATCH_PATTERN, result);

		this.sccpExecutor.execute(incorrect_prim_addressCmd.split(" "));
		result = this.sccpExecutor.execute(correctCreateRuleCmd.split(" "));
		assertEquals(SccpOAMMessage.SEC_MISMATCH_PRIMADDRESS, result);

//		this.sccpExecutor.execute(incorrect_prim_address_deleteCmd.split(" "));
//		this.sccpExecutor.execute(correct_prim_addressCmd.split(" "));
//		this.sccpExecutor.execute(incorrect_sec_addressCmd.split(" "));
//		result = this.sccpExecutor.execute(correctCreateRuleCmdWithSecId.split(" "));
//
//		assertEquals(SccpOAMMessage.SEC_MISMATCH_SECADDRESS, result);
	}

	/**
	 * Test for bug http://code.google.com/p/mobicents/issues/detail?id=3057
	 * NPE when creating SCCP primary address via CLI
	 */
	@Test
	public void testManageAddress() {
		String prim_addressCmd = "sccp primary_add create 1 71 6535 8 0 0 12 93707100007";
		String result = this.sccpExecutor.execute(prim_addressCmd.split(" "));
		assertEquals(SccpOAMMessage.ADDRESS_SUCCESSFULLY_ADDED, result);
		assertEquals(1, this.router.getPrimaryAddresses().size());
	}

	@Test
	public void testPrimAddress() {

		String rspCmd = "sccp primary_add create 11 71 6535 8 0 0 12 93707100007";
		// <id> <address-indicator> <point-code> <subsystem-number> <translation-type> <numbering-plan> <nature-of-address-indicator> <digits>
		String res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.router.getPrimaryAddresses().size());
		SccpAddress addr = this.router.getPrimaryAddress(11);
		assertEquals(71, addr.getAddressIndicator().getValue());
		assertEquals(6535, addr.getSignalingPointCode());
		assertEquals(8, addr.getSubsystemNumber());
		assertTrue(addr.getGlobalTitle().getDigits().equals("93707100007"));

		rspCmd = "sccp primary_add create 11 71 6536 8 0 0 12 93707100007";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.ADDRESS_ALREADY_EXIST));
		assertEquals(1, this.router.getPrimaryAddresses().size());
		addr = this.router.getPrimaryAddress(11);
		assertEquals(6535, addr.getSignalingPointCode());

		rspCmd = "sccp primary_add modify 11 71 6537 8 0 0 12 93707100007";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.router.getPrimaryAddresses().size());
		addr = this.router.getPrimaryAddress(11);
		assertEquals(6537, addr.getSignalingPointCode());

		rspCmd = "sccp primary_add modify 12 71 6538 8 0 0 12 93707100007";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.ADDRESS_DOESNT_EXIST));
		assertEquals(1, this.router.getPrimaryAddresses().size());
		addr = this.router.getPrimaryAddress(11);
		assertEquals(6537, addr.getSignalingPointCode());

		rspCmd = "sccp primary_add show 11";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp primary_add show";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp primary_add delete 12";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.ADDRESS_DOESNT_EXIST));
		assertEquals(1, this.router.getPrimaryAddresses().size());

		rspCmd = "sccp primary_add delete 11";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(0, this.router.getPrimaryAddresses().size());
	}

	@Test
	public void testBackupAddress() {

		String rspCmd = "sccp backup_add create 11 71 6535 8 0 0 12 93707100007";
		// <id> <address-indicator> <point-code> <subsystem-number> <translation-type> <numbering-plan> <nature-of-address-indicator> <digits>
		String res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.router.getBackupAddresses().size());
		SccpAddress addr = this.router.getBackupAddress(11);
		assertEquals(71, addr.getAddressIndicator().getValue());
		assertEquals(6535, addr.getSignalingPointCode());
		assertEquals(8, addr.getSubsystemNumber());
		assertTrue(addr.getGlobalTitle().getDigits().equals("93707100007"));

		rspCmd = "sccp backup_add create 11 71 6536 8 0 0 12 93707100007";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.ADDRESS_ALREADY_EXIST));
		assertEquals(1, this.router.getBackupAddresses().size());
		addr = this.router.getBackupAddress(11);
		assertEquals(6535, addr.getSignalingPointCode());

		rspCmd = "sccp backup_add modify 11 71 6537 8 0 0 12 93707100007";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.router.getBackupAddresses().size());
		addr = this.router.getBackupAddress(11);
		assertEquals(6537, addr.getSignalingPointCode());

		rspCmd = "sccp backup_add modify 12 71 6538 8 0 0 12 93707100007";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.ADDRESS_DOESNT_EXIST));
		assertEquals(1, this.router.getBackupAddresses().size());
		addr = this.router.getBackupAddress(11);
		assertEquals(6537, addr.getSignalingPointCode());

		rspCmd = "sccp backup_add show 11";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp backup_add show";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp backup_add delete 12";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.ADDRESS_DOESNT_EXIST));
		assertEquals(1, this.router.getBackupAddresses().size());

		rspCmd = "sccp backup_add delete 11";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(0, this.router.getBackupAddresses().size());
	}

	@Test
	public void testLmr() {

		String rspCmd = "sccp lmr create 1 11 12 udt";
		String res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.router.getLongMessageRules().size());
		LongMessageRule lmr = this.router.getLongMessageRule(1);
		assertEquals(11, lmr.getFirstSpc());
		assertEquals(12, lmr.getLastSpc());
		assertEquals(LongMessageRuleType.LongMessagesForbidden, lmr.getLongMessageRuleType());

		rspCmd = "sccp lmr create 2 13 14 xudt";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(2, this.router.getLongMessageRules().size());
		lmr = this.router.getLongMessageRule(2);
		assertEquals(13, lmr.getFirstSpc());
		assertEquals(14, lmr.getLastSpc());
		assertEquals(LongMessageRuleType.XudtEnabled, lmr.getLongMessageRuleType());

		rspCmd = "sccp lmr create 3 15 16 ludt";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(3, this.router.getLongMessageRules().size());
		lmr = this.router.getLongMessageRule(3);
		assertEquals(15, lmr.getFirstSpc());
		assertEquals(16, lmr.getLastSpc());
		assertEquals(LongMessageRuleType.LudtEnabled, lmr.getLongMessageRuleType());

		rspCmd = "sccp lmr create 4 17 18 ludt_segm";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(4, this.router.getLongMessageRules().size());
		lmr = this.router.getLongMessageRule(4);
		assertEquals(17, lmr.getFirstSpc());
		assertEquals(18, lmr.getLastSpc());
		assertEquals(LongMessageRuleType.LudtEnabled_WithSegmentationField, lmr.getLongMessageRuleType());

		rspCmd = "sccp lmr create 4 19 20 ludt_segm";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.LMR_ALREADY_EXIST));
		assertEquals(4, this.router.getLongMessageRules().size());
		lmr = this.router.getLongMessageRule(4);

		rspCmd = "sccp lmr modify 4 21 22 udt";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(4, this.router.getLongMessageRules().size());
		lmr = this.router.getLongMessageRule(4);
		assertEquals(21, lmr.getFirstSpc());
		assertEquals(22, lmr.getLastSpc());
		assertEquals(LongMessageRuleType.LongMessagesForbidden, lmr.getLongMessageRuleType());

		rspCmd = "sccp lmr modify 4 21 22 xudt";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(4, this.router.getLongMessageRules().size());
		lmr = this.router.getLongMessageRule(4);
		assertEquals(21, lmr.getFirstSpc());
		assertEquals(22, lmr.getLastSpc());
		assertEquals(LongMessageRuleType.XudtEnabled, lmr.getLongMessageRuleType());

		rspCmd = "sccp lmr modify 4 21 22 ludt";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(4, this.router.getLongMessageRules().size());
		lmr = this.router.getLongMessageRule(4);
		assertEquals(21, lmr.getFirstSpc());
		assertEquals(22, lmr.getLastSpc());
		assertEquals(LongMessageRuleType.LudtEnabled, lmr.getLongMessageRuleType());

		rspCmd = "sccp lmr modify 4 21 22 ludt_segm";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(4, this.router.getLongMessageRules().size());
		lmr = this.router.getLongMessageRule(4);
		assertEquals(21, lmr.getFirstSpc());
		assertEquals(22, lmr.getLastSpc());
		assertEquals(LongMessageRuleType.LudtEnabled_WithSegmentationField, lmr.getLongMessageRuleType());

		rspCmd = "sccp lmr modify 5 23 24 udt";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.LMR_DOESNT_EXIST));
		assertEquals(4, this.router.getLongMessageRules().size());
		lmr = this.router.getLongMessageRule(4);
		assertEquals(21, lmr.getFirstSpc());
		assertEquals(22, lmr.getLastSpc());
		assertEquals(LongMessageRuleType.LudtEnabled_WithSegmentationField, lmr.getLongMessageRuleType());

		rspCmd = "sccp lmr show 1";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp lmr show";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp lmr delete 10";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.LMR_DOESNT_EXIST));
		assertEquals(4, this.router.getLongMessageRules().size());

		rspCmd = "sccp lmr delete 4";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(3, this.router.getLongMessageRules().size());
	}

	@Test
	public void testSap() {

		String rspCmd = "sccp sap create 5 101 11 2";
		String res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.MUP_DOESNT_EXIST));
		assertEquals(0, this.router.getMtp3ServiceAccessPoints().size());

		rspCmd = "sccp sap create 5 1 11 2";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.router.getMtp3ServiceAccessPoints().size());
		Mtp3ServiceAccessPoint sap = this.router.getMtp3ServiceAccessPoint(5);
		assertEquals(11, sap.getOpc());
		assertEquals(0, sap.getMtp3Destinations().size());

		rspCmd = "sccp sap create 5 1 11 2";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.SAP_ALREADY_EXIST));
		assertEquals(1, this.router.getMtp3ServiceAccessPoints().size());
		sap = this.router.getMtp3ServiceAccessPoint(5);
		assertEquals(11, sap.getOpc());
		assertEquals(0, sap.getMtp3Destinations().size());

		rspCmd = "sccp sap modify 5 2 12 2";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.MUP_DOESNT_EXIST));
		assertEquals(1, this.router.getMtp3ServiceAccessPoints().size());
		sap = this.router.getMtp3ServiceAccessPoint(5);
		assertEquals(11, sap.getOpc());
		assertEquals(0, sap.getMtp3Destinations().size());

		rspCmd = "sccp sap modify 5 1 13 2";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.router.getMtp3ServiceAccessPoints().size());
		sap = this.router.getMtp3ServiceAccessPoint(5);
		assertEquals(13, sap.getOpc());
		assertEquals(0, sap.getMtp3Destinations().size());

		rspCmd = "sccp sap modify 6 2 14 2";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.SAP_DOESNT_EXIST));
		assertEquals(1, this.router.getMtp3ServiceAccessPoints().size());
		sap = this.router.getMtp3ServiceAccessPoint(5);
		assertEquals(13, sap.getOpc());
		assertEquals(0, sap.getMtp3Destinations().size());


		rspCmd = "sccp dest create 1 7 31 32 3 4 255";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.SAP_DOESNT_EXIST));
		assertEquals(1, this.router.getMtp3ServiceAccessPoints().size());
		sap = this.router.getMtp3ServiceAccessPoint(5);
		assertEquals(13, sap.getOpc());
		assertEquals(0, sap.getMtp3Destinations().size());

		rspCmd = "sccp dest create 5 7 31 32 3 4 255";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.router.getMtp3ServiceAccessPoints().size());
		sap = this.router.getMtp3ServiceAccessPoint(5);
		assertEquals(13, sap.getOpc());
		assertEquals(1, sap.getMtp3Destinations().size());
		Mtp3Destination dest = sap.getMtp3Destination(7);
		assertEquals(31, dest.getFirstDpc());
		assertEquals(32, dest.getLastDpc());
		assertEquals(3, dest.getFirstSls());
		assertEquals(4, dest.getLastSls());
		assertEquals(255, dest.getSlsMask());

		rspCmd = "sccp dest create 5 7 33 34 3 4 255";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.DEST_ALREADY_EXIST));
		assertEquals(1, this.router.getMtp3ServiceAccessPoints().size());
		sap = this.router.getMtp3ServiceAccessPoint(5);
		assertEquals(13, sap.getOpc());
		assertEquals(1, sap.getMtp3Destinations().size());
		dest = sap.getMtp3Destination(7);
		assertEquals(31, dest.getFirstDpc());

		rspCmd = "sccp dest modify 1 7 35 36 3 4 15";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.SAP_DOESNT_EXIST));
		assertEquals(1, this.router.getMtp3ServiceAccessPoints().size());
		sap = this.router.getMtp3ServiceAccessPoint(5);
		assertEquals(13, sap.getOpc());
		assertEquals(1, sap.getMtp3Destinations().size());
		dest = sap.getMtp3Destination(7);
		assertEquals(31, dest.getFirstDpc());

		rspCmd = "sccp dest modify 5 9 38 39 3 4 15";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.DEST_DOESNT_EXIST));
		assertEquals(1, this.router.getMtp3ServiceAccessPoints().size());
		sap = this.router.getMtp3ServiceAccessPoint(5);
		assertEquals(13, sap.getOpc());
		assertEquals(1, sap.getMtp3Destinations().size());
		dest = sap.getMtp3Destination(7);
		assertEquals(31, dest.getFirstDpc());

		rspCmd = "sccp dest modify 5 7 40 41 3 4 15";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.router.getMtp3ServiceAccessPoints().size());
		sap = this.router.getMtp3ServiceAccessPoint(5);
		assertEquals(13, sap.getOpc());
		assertEquals(1, sap.getMtp3Destinations().size());
		dest = sap.getMtp3Destination(7);
		assertEquals(40, dest.getFirstDpc());
		assertEquals(15, dest.getSlsMask());

		rspCmd = "sccp dest show 5 7";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp dest show 5";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp sap show 5";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp sap show";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp dest delete 1 7";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.SAP_DOESNT_EXIST));
		assertEquals(1, this.router.getMtp3ServiceAccessPoints().size());
		sap = this.router.getMtp3ServiceAccessPoint(5);
		assertEquals(13, sap.getOpc());
		assertEquals(1, sap.getMtp3Destinations().size());
		dest = sap.getMtp3Destination(7);
		assertEquals(40, dest.getFirstDpc());

		rspCmd = "sccp dest delete 5 9";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.DEST_DOESNT_EXIST));
		assertEquals(1, this.router.getMtp3ServiceAccessPoints().size());
		sap = this.router.getMtp3ServiceAccessPoint(5);
		assertEquals(13, sap.getOpc());
		assertEquals(1, sap.getMtp3Destinations().size());
		dest = sap.getMtp3Destination(7);
		assertEquals(40, dest.getFirstDpc());

		rspCmd = "sccp dest delete 5 7";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.router.getMtp3ServiceAccessPoints().size());
		sap = this.router.getMtp3ServiceAccessPoint(5);
		assertEquals(13, sap.getOpc());
		assertEquals(0, sap.getMtp3Destinations().size());

		rspCmd = "sccp sap delete 1";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.SAP_DOESNT_EXIST));
		assertEquals(1, this.router.getMtp3ServiceAccessPoints().size());
		sap = this.router.getMtp3ServiceAccessPoint(5);
		assertEquals(13, sap.getOpc());
		assertEquals(0, sap.getMtp3Destinations().size());

		rspCmd = "sccp sap delete 5";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(0, this.router.getMtp3ServiceAccessPoints().size());
	}

	@Test
	public void testRsp() {

		String rspCmd = "sccp rsp create 1 11 0 0";
		String res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.sccpResource.getRemoteSpcs().size());
		RemoteSignalingPointCode spc = this.sccpResource.getRemoteSpc(1);
		assertEquals(11, spc.getRemoteSpc());

		rspCmd = "sccp rsp create 1 12 0 0";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.RSPC_ALREADY_EXIST));
		assertEquals(1, this.sccpResource.getRemoteSpcs().size());
		spc = this.sccpResource.getRemoteSpc(1);
		assertEquals(11, spc.getRemoteSpc());

		rspCmd = "sccp rsp modify 2 12 0 0";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.RSPC_DOESNT_EXIST));
		assertEquals(1, this.sccpResource.getRemoteSpcs().size());
		spc = this.sccpResource.getRemoteSpc(1);
		assertEquals(11, spc.getRemoteSpc());

		rspCmd = "sccp rsp modify 1 12 0 0";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.sccpResource.getRemoteSpcs().size());
		spc = this.sccpResource.getRemoteSpc(1);
		assertEquals(12, spc.getRemoteSpc());

		rspCmd = "sccp rsp show 1";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp rsp show";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp rsp delete 5";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.RSPC_DOESNT_EXIST));
		assertEquals(1, this.sccpResource.getRemoteSpcs().size());
		spc = this.sccpResource.getRemoteSpc(1);
		assertEquals(12, spc.getRemoteSpc());

		rspCmd = "sccp rsp delete 1";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(0, this.sccpResource.getRemoteSpcs().size());
	}

	@Test
	public void testRss() {

		String rspCmd = "sccp rss create 2 11 8 0";
		String res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.sccpResource.getRemoteSsns().size());
		RemoteSubSystem rss = this.sccpResource.getRemoteSsn(2);
		assertEquals(11, rss.getRemoteSpc());
		assertEquals(8, rss.getRemoteSsn());
		assertFalse(rss.getMarkProhibitedWhenSpcResuming());

		rspCmd = "sccp rss delete 5";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.RSS_DOESNT_EXIST));
		assertEquals(1, this.sccpResource.getRemoteSsns().size());
		rss = this.sccpResource.getRemoteSsn(2);
		assertEquals(11, rss.getRemoteSpc());

		rspCmd = "sccp rss delete 2";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(0, this.sccpResource.getRemoteSsns().size());

		rspCmd = "sccp rss create 2 12 8 0 prohibitedWhenSpcResuming";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.sccpResource.getRemoteSsns().size());
		rss = this.sccpResource.getRemoteSsn(2);
		assertEquals(12, rss.getRemoteSpc());
		assertEquals(8, rss.getRemoteSsn());
		assertTrue(rss.getMarkProhibitedWhenSpcResuming());

		rspCmd = "sccp rss create 2 12 8 0";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.RSS_ALREADY_EXIST));
		assertEquals(1, this.sccpResource.getRemoteSsns().size());
		rss = this.sccpResource.getRemoteSsn(2);
		assertEquals(12, rss.getRemoteSpc());
		assertEquals(8, rss.getRemoteSsn());
		assertTrue(rss.getMarkProhibitedWhenSpcResuming());

		rspCmd = "sccp rss modify 2 13 18 0";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.sccpResource.getRemoteSsns().size());
		rss = this.sccpResource.getRemoteSsn(2);
		assertEquals(13, rss.getRemoteSpc());
		assertEquals(18, rss.getRemoteSsn());
		assertFalse(rss.getMarkProhibitedWhenSpcResuming());

		rspCmd = "sccp rss modify 2 14 19 0 prohibitedWhenSpcResuming";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.sccpResource.getRemoteSsns().size());
		rss = this.sccpResource.getRemoteSsn(2);
		assertEquals(14, rss.getRemoteSpc());
		assertEquals(19, rss.getRemoteSsn());
		assertTrue(rss.getMarkProhibitedWhenSpcResuming());

		rspCmd = "sccp rss modify 3 15 19 0 prohibitedWhenSpcResuming";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.RSS_DOESNT_EXIST));
		assertEquals(1, this.sccpResource.getRemoteSsns().size());
		rss = this.sccpResource.getRemoteSsn(2);
		assertEquals(14, rss.getRemoteSpc());
		assertEquals(19, rss.getRemoteSsn());
		assertTrue(rss.getMarkProhibitedWhenSpcResuming());

		rspCmd = "sccp rss show 1";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp rss show 2";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp rss show";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
	}

	@Test
	public void testConcernedSpc() {

		String rspCmd = "sccp csp create 3 21";
		String res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.sccpResource.getConcernedSpcs().size());
		ConcernedSignalingPointCode cspc = this.sccpResource.getConcernedSpc(3);
		assertEquals(21, cspc.getRemoteSpc());

		rspCmd = "sccp csp create 3 22";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.CS_ALREADY_EXIST));
		assertEquals(1, this.sccpResource.getConcernedSpcs().size());
		cspc = this.sccpResource.getConcernedSpc(3);
		assertEquals(21, cspc.getRemoteSpc());

		rspCmd = "sccp csp modify 3 23";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1, this.sccpResource.getConcernedSpcs().size());
		cspc = this.sccpResource.getConcernedSpc(3);
		assertEquals(23, cspc.getRemoteSpc());

		rspCmd = "sccp csp modify 33 24";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.CS_DOESNT_EXIST));
		assertEquals(1, this.sccpResource.getConcernedSpcs().size());
		cspc = this.sccpResource.getConcernedSpc(3);
		assertEquals(23, cspc.getRemoteSpc());

		rspCmd = "sccp csp show 3";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp csp show";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp csp delete 33";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.CS_DOESNT_EXIST));
		assertEquals(1, this.sccpResource.getConcernedSpcs().size());
		cspc = this.sccpResource.getConcernedSpc(3);
		assertEquals(23, cspc.getRemoteSpc());

		rspCmd = "sccp csp delete 3";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(0, this.sccpResource.getConcernedSpcs().size());
	}

	@Test
	public void testParameters() {

		String rspCmd = "sccp set xxx 200";
		String res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertTrue(res.equals(SccpOAMMessage.INVALID_COMMAND));

		rspCmd = "sccp set zMarginXudtMessage 200";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(200, this.sccpStack.getZMarginXudtMessage());

		rspCmd = "sccp set reassemblyTimerDelay 10000";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(10000, this.sccpStack.getReassemblyTimerDelay());

		rspCmd = "sccp set maxDataMessage 3000";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(3000, this.sccpStack.getMaxDataMessage());

		rspCmd = "sccp set removeSpc false";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(false, this.sccpStack.isRemoveSpc());

		rspCmd = "sccp set sstTimerDuration_Min 6000";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(6000, this.sccpStack.getSstTimerDuration_Min());

		rspCmd = "sccp set sstTimerDuration_Max 1000000";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(1000000, this.sccpStack.getSstTimerDuration_Max());

		rspCmd = "sccp set sstTimerDuration_IncreaseFactor 2.55";
		res = this.sccpExecutor.execute(rspCmd.split(" "));
		assertEquals(2.55, this.sccpStack.getSstTimerDuration_IncreaseFactor(), 0.0);


		rspCmd = "sccp get zMarginXudtMessage";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp get reassemblyTimerDelay";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp get maxDataMessage";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp get removeSpc";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp get sstTimerDuration_Min";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp get sstTimerDuration_Max";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp get sstTimerDuration_IncreaseFactor";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

		rspCmd = "sccp get";
		res = this.sccpExecutor.execute(rspCmd.split(" "));

	}


	class Mtp3UserPartImpl implements Mtp3UserPart {

		public void addMtp3UserPartListener(Mtp3UserPartListener arg0) {
			// TODO Auto-generated method stub

		}

		public int getMaxUserDataLength(int arg0) {
			// TODO Auto-generated method stub
			return 0;
		}

		public void removeMtp3UserPartListener(Mtp3UserPartListener arg0) {
			// TODO Auto-generated method stub

		}

		public void sendMessage(Mtp3TransferPrimitive arg0) throws IOException {
			// TODO Auto-generated method stub

		}

		/* (non-Javadoc)
		 * @see org.mobicents.protocols.ss7.mtp.Mtp3UserPart#getMtp3TransferPrimitiveFactory()
		 */
		@Override
		public Mtp3TransferPrimitiveFactory getMtp3TransferPrimitiveFactory() {
			// TODO Auto-generated method stub
			return null;
		}

		/* (non-Javadoc)
		 * @see org.mobicents.protocols.ss7.mtp.Mtp3UserPart#getRoutingLabelFormat()
		 */
		@Override
		public RoutingLabelFormat getRoutingLabelFormat() {
			// TODO Auto-generated method stub
			return null;
		}

		/* (non-Javadoc)
		 * @see org.mobicents.protocols.ss7.mtp.Mtp3UserPart#setRoutingLabelFormat(org.mobicents.protocols.ss7.mtp.RoutingLabelFormat)
		 */
		@Override
		public void setRoutingLabelFormat(RoutingLabelFormat arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public boolean isUseLsbForLinksetSelection() {
			return false;
		}

		@Override
		public void setUseLsbForLinksetSelection(boolean arg0) {

		}
	}
}
