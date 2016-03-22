package org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo;

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
		Assert.assertTrue(group.groupNumber == 1);
		Assert.assertTrue(group.relationships.size() == 3);
		Assert.assertEquals(group.relationships.get(2).getType().getConceptId(), expectedType);
		Assert.assertEquals(group.relationships.get(0).getTarget().getConceptId(), expectedValue);
		
		String testExpression2 = testExpression + testExpression + testExpression;
		StringBuffer testBuff2 = new StringBuffer(testExpression2);
		groups = BatchImportExpression.extractGroups(testBuff2);
		Assert.assertTrue(groups.size()==3);
	}

}
