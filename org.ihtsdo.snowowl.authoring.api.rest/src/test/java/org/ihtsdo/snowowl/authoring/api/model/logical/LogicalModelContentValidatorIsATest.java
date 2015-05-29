package org.ihtsdo.snowowl.authoring.api.model.logical;

import org.ihtsdo.snowowl.authoring.api.model.work.ContentValidationResult;
import org.ihtsdo.snowowl.authoring.api.model.work.WorkingConcept;
import org.ihtsdo.snowowl.authoring.api.model.work.ConceptValidationResult;
import org.ihtsdo.snowowl.authoring.api.model.work.WorkingContent;
import org.ihtsdo.snowowl.authoring.api.services.TestContentServiceImpl;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"/test-context.xml"})
public class LogicalModelContentValidatorIsATest {

	@Autowired
	private LogicalModelContentValidator validator;

	@Autowired
	private TestContentServiceImpl testContentService;

	@Test
	public void testValidateContentSingleIsASelfValid() throws Exception {
		LogicalModel logicalModel = new LogicalModel("", new IsARestriction("123", RangeRelationType.SELF));
		WorkingConcept content = new WorkingConcept().addIsA("123");

		ConceptValidationResult result = validateSingle(logicalModel, content);

		Assert.assertEquals("", result.getParentsMessages().get(0));
	}

	@Test
	public void testValidateContentSingleIsASelfInvalid() throws Exception {
		LogicalModel logicalModel = new LogicalModel("", new IsARestriction("123", RangeRelationType.SELF));
		WorkingConcept content = new WorkingConcept().addIsA("124");

		ConceptValidationResult result = validateSingle(logicalModel, content);

		Assert.assertEquals("IsA relation must be '123'.", result.getParentsMessages().get(0));
	}

	@Test
	public void testValidateContentSingleIsADescendantsValid() throws Exception {
		LogicalModel logicalModel = new LogicalModel("", new IsARestriction("123", RangeRelationType.DESCENDANTS));
		testContentService.putDescendantIds("123", new String[]{"1234", "12345", "123456"});
		WorkingConcept content = new WorkingConcept().addIsA("12345");

		ConceptValidationResult result = validateSingle(logicalModel, content);

		Assert.assertEquals("", result.getParentsMessages().get(0));
	}

	@Test
	public void testValidateContentSingleIsADescendantsInvalid() throws Exception {
		LogicalModel logicalModel = new LogicalModel("", new IsARestriction("123", RangeRelationType.DESCENDANTS));
		testContentService.putDescendantIds("123", new String[]{"1234", "12345", "123456"});
		WorkingConcept content = new WorkingConcept().addIsA("12377");

		ConceptValidationResult result = validateSingle(logicalModel, content);

		Assert.assertEquals("IsA relation must be a descendant of '123'.", result.getParentsMessages().get(0));
	}

	@Test
	public void testValidateContentSingleIsADescendantOrSelfValid() throws Exception {
		LogicalModel logicalModel = new LogicalModel("", new IsARestriction("123", RangeRelationType.DESCENDANTS_AND_SELF));
		testContentService.putDescendantIds("123", new String[]{"1234", "12345", "123456"});
		WorkingConcept content = new WorkingConcept().addIsA("123");

		ConceptValidationResult result = validateSingle(logicalModel, content);

		Assert.assertEquals("", result.getParentsMessages().get(0));
	}

	@Test
	public void testValidateContentSingleIsADescendantOrSelfInvalid() throws Exception {
		LogicalModel logicalModel = new LogicalModel("", new IsARestriction("123", RangeRelationType.DESCENDANTS_AND_SELF));
		testContentService.putDescendantIds("123", new String[]{"1234", "12345", "123456"});
		WorkingConcept content = new WorkingConcept().addIsA("12377");

		ConceptValidationResult result = validateSingle(logicalModel, content);

		Assert.assertEquals("IsA relation must be a descendant of or equal to '123'.", result.getParentsMessages().get(0));
	}

	@Test
	public void testValidateContentNotEnoughIsA() throws Exception {
		LogicalModel logicalModel = new LogicalModel("", new IsARestriction("123", RangeRelationType.SELF))
				.addIsARestriction(new IsARestriction("12345", RangeRelationType.SELF));
		WorkingConcept content = new WorkingConcept().addIsA("124");

		ConceptValidationResult result = validateSingle(logicalModel, content);

		Assert.assertEquals("IsA relation must be '123'.\n" +
				"There are less isA relationships than in the logical model.", result.getParentsMessages().get(0));
	}

	@Test
	public void testValidateContentTooManyIsA() throws Exception {
		LogicalModel logicalModel = new LogicalModel("", new IsARestriction("123", RangeRelationType.SELF));
		WorkingConcept content = new WorkingConcept()
				.addIsA("124")
				.addIsA("125");

		ConceptValidationResult result = validateSingle(logicalModel, content);

		List<String> isARelationshipsMessages = result.getParentsMessages();
		Assert.assertEquals(2, isARelationshipsMessages.size());
		Assert.assertEquals("IsA relation must be '123'.", isARelationshipsMessages.get(0));
		Assert.assertEquals("There are more isA relationships than in the logical model.", isARelationshipsMessages.get(1));
	}

	private ConceptValidationResult validateSingle(LogicalModel logicalModel, WorkingConcept content) {
		ContentValidationResult results = new ContentValidationResult();
		validator.validate(new WorkingContent("").addConcept(content), logicalModel, results);
		return results.getConceptResults().get(0);
	}

}
