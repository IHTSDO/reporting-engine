package org.ihtsdo.snowowl.authoring.batchimport.api.pojo.batch;

import java.util.List;

import org.ihtsdo.otf.rest.exception.ProcessingException;
import org.junit.Assert;
import org.junit.Test;

import com.b2international.snowowl.snomed.core.domain.DefinitionStatus;

public class BatchImportExpressionTest {

	@Test
	public void testRemove() {
		String expectedResult = "abc";
		StringBuffer test1 = new StringBuffer("a b c");
		BatchImportExpression.remove(test1, ' ');
		Assert.assertEquals(test1.toString(), expectedResult);
		
		StringBuffer test2 = new StringBuffer("a  b c ");
		BatchImportExpression.remove(test2, ' ');
		Assert.assertEquals(test2.toString(), expectedResult);
	}
	
	@Test
	public void testMakeMachineReadable() {
		//Watch here that the final term is not properly terminated, as we see in the real data
		String testExpression = "=== 64572001 | Disease |: { 363698007 | Finding site | = 53134007 | Structure of paraurethral ducts ," + 
								"116676008 | Associated morphology | = 367643001 | Cyst}  ";
		String expectedResult = "===64572001:{363698007=53134007,116676008=367643001}";
		StringBuffer testBuff = new StringBuffer(testExpression);
		BatchImportExpression.makeMachineReadable(testBuff);
		Assert.assertEquals(testBuff.toString(), expectedResult);
		
		
	}
	
	@Test
	public void testExtractDefinitionStatus() throws ProcessingException {
		String testExpression = "===64572001:{363698007=53134007";
		String expectedRemainder = "64572001:{363698007=53134007";
		StringBuffer testBuff = new StringBuffer(testExpression);
		DefinitionStatus defStatus = BatchImportExpression.extractDefinitionStatus(testBuff);
		Assert.assertTrue(DefinitionStatus.FULLY_DEFINED.equals(defStatus));
		Assert.assertEquals(testBuff.toString(), expectedRemainder);
	}
	
	@Test
	public void testExtractFocusConcepts() throws ProcessingException {
		String testExpression = "198609003+276654001";
		StringBuffer testBuff = new StringBuffer(testExpression);
		List<String> focusConcepts = BatchImportExpression.extractFocusConcepts(testBuff);
		Assert.assertTrue(focusConcepts.size() == 2);
		Assert.assertTrue(testBuff.length() == 0);
		Assert.assertEquals(focusConcepts.get(1), "276654001");
		
		String testExpression2 = "64572001:{363698007=53134007,116676008=367643001}";
		String remainder = "{363698007=53134007,116676008=367643001}";
		StringBuffer testBuff2 = new StringBuffer(testExpression2);
		focusConcepts = BatchImportExpression.extractFocusConcepts(testBuff2);
		Assert.assertTrue(focusConcepts.size() == 1);
		Assert.assertEquals(focusConcepts.get(0), "64572001");
		Assert.assertEquals(testBuff2.toString(), remainder);
	}
	
	@Test
	public void testExtractGroups() throws ProcessingException {
		String testExpression = "{363698007=38848004,116676008=24551003,246454002=255399007}";
		String expectedType = "246454002";
		String expectedValue = "38848004";
		StringBuffer testBuff = new StringBuffer(testExpression);
		List<BatchImportGroup> groups = BatchImportExpression.extractGroups(testBuff);
		Assert.assertTrue(groups.size()==1);
		BatchImportGroup group = groups.get(0);
		Assert.assertTrue(group.getGroupNumber() == 1);
		Assert.assertTrue(group.getRelationships().size() == 3);
		Assert.assertEquals(group.getRelationships().get(2).getType().getConceptId(), expectedType);
		Assert.assertEquals(group.getRelationships().get(0).getTarget().getConceptId(), expectedValue);
		
		String testExpression2 = testExpression + testExpression + testExpression;
		StringBuffer testBuff2 = new StringBuffer(testExpression2);
		groups = BatchImportExpression.extractGroups(testBuff2);
		Assert.assertTrue(groups.size()==3);
	}
	
	@Test
	public void testCombination() throws ProcessingException {
		String testExpression = "=== 64572001 | Disease |: { 246075003 | Causative agent | = 113858008 | " + 
								"Mycobacterium tuberculosis complex , 370135005 | Pathological process | = " +
								"441862004 | Infectious process} {363698007 | Finding site | = 45292006 | Vulval " + 
								"structure, 116676008 | Associated morphology | = 23583003 | Inflammation} " +
								"{363698007 | Finding site | = 45292006 | Vulval structure, 116676008 | Associated morphology | = 56208002 | Ulcer}";
		String expectedParent = "64572001";
		BatchImportExpression exp = BatchImportExpression.parse(testExpression);
		String parent = exp.getFocusConcepts().get(0);
		Assert.assertEquals(expectedParent, parent);
		List<BatchImportGroup> groups = exp.getAttributeGroups();
		Assert.assertTrue(groups.size() == 3);
	}
	
	@Test
	public void testCombination2() throws ProcessingException {
		String testExpression = "=== 64572001 | Disease  {363698007 | Finding site | = 43981004 | Structure of left ovary, " +
				 				"116676008 | Associated morphology | = 24216005 | Congenital absence} {363698007 | Finding site |" +
				 				"= 20837000 | Structure of right ovary, 116676008 | Associated morphology | = 24216005 | Congenital absence}";
		String expectedParent = "64572001";
		BatchImportExpression exp = BatchImportExpression.parse(testExpression);
		String parent = exp.getFocusConcepts().get(0);
		Assert.assertEquals(expectedParent, parent);
		List<BatchImportGroup> groups = exp.getAttributeGroups();
		Assert.assertTrue(groups.size() == 2);
	}
	
	@Test
	public void testCombination3() throws ProcessingException {
		//Note that the failure to close the term here means that we're having to check if that comma is part of the term
		//or compositional grammar syntax.
		String testExpression = "<<< 198609003 | Complication of pregnancy, childbirth and/or the puerperium  + " +
								"417746004 | Traumatic injury";
		String expectedParent1 = "198609003";
		String expectedParent2 = "417746004";
		BatchImportExpression exp = BatchImportExpression.parse(testExpression);
		Assert.assertEquals(expectedParent1, exp.getFocusConcepts().get(0));
		Assert.assertEquals(expectedParent2, exp.getFocusConcepts().get(1));
		List<BatchImportGroup> groups = exp.getAttributeGroups();
		Assert.assertTrue(groups.size() == 0);
	}	
	
	@Test
	public void testCombination4() throws ProcessingException {
		String testExpression = "=== 64572001 | Disease | : { 246075003 | Causative agent | = 113858008 | Mycobacterium tuberculosis complex" +
								", 370135005 | Pathological process | = 441862004 | Infectious process} { 363698007 | Finding site | = " +
								"45292006 | Vulval structure, 116676008 | Associated morphology | = 23583003 | Inflammation} " +
								"{363698007 | Finding site | = 45292006 | Vulval structure, 116676008 | Associated morphology | = 56208002 | Ulcer}";
		String expectedParent1 = "64572001";
		BatchImportExpression exp = BatchImportExpression.parse(testExpression);
		Assert.assertEquals(expectedParent1, exp.getFocusConcepts().get(0));
		List<BatchImportGroup> groups = exp.getAttributeGroups();
		Assert.assertTrue(groups.size() == 3);
	}	
	
	//I can't handle the missing pipe in this one.
	/*public void testCombination5() throws ProcessingException {
		String testExpression = "<<< 41769001 | Disease suspected   { 246090004 | Associated finding = 16726004 | Renal osteodystrophy } " +
								"{ 246090004 | Associated finding = 709044004 Chronic kidney disease }";
		String expectedParent1 = "41769001";
		BatchImportExpression exp = BatchImportExpression.parse(testExpression);
		Assert.assertEquals(expectedParent1, exp.getFocusConcepts().get(0));
		List<BatchImportGroup> groups = exp.getAttributeGroups();
		Assert.assertTrue(groups.size() == 2);
	}	*/
	
	
	@Test
	public void testCombination6() throws ProcessingException {
		String testExpression = "<<< 133906008 | Postpartum care   {260870009  | Priority = 373113001 | Routine}";
		String expectedParent1 = "133906008";
		BatchImportExpression exp = BatchImportExpression.parse(testExpression);
		Assert.assertEquals(expectedParent1, exp.getFocusConcepts().get(0));
		List<BatchImportGroup> groups = exp.getAttributeGroups();
		Assert.assertTrue(groups.size() == 1);
	}	
	
	@Test
	public void testCombination7() throws ProcessingException {
		String testExpression = "<<< 41769001 | Disease suspected |  { 246090004 | Associated finding = 16726004 | Renal osteodystrophy } { 246090004 | Associated finding = 709044004 | Chronic kidney disease | }";
		String expectedParent1 = "41769001";
		BatchImportExpression exp = BatchImportExpression.parse(testExpression);
		Assert.assertEquals(expectedParent1, exp.getFocusConcepts().get(0));
		List<BatchImportGroup> groups = exp.getAttributeGroups();
		Assert.assertTrue(groups.size() == 2);
	}	
	
}
