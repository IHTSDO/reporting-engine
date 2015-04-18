package org.ihtsdo.snowowl.authoring.api.model.logical;

import org.ihtsdo.snowowl.authoring.api.model.AuthoringContent;
import org.ihtsdo.snowowl.authoring.api.model.AuthoringContentValidationResult;
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
public class LogicalModelValidatorTest {

	@Autowired
	private LogicalModelValidator validator;

	@Autowired
	private TestContentServiceImpl testContentService;

	@Test
	public void testValidateContentSingleIsASelfValid() throws Exception {
		LogicalModel logicalModel = new LogicalModel("", new IsARestriction("123", RangeRelationType.SELF));
		AuthoringContent content = new AuthoringContent().addIsA("123");

		AuthoringContentValidationResult result = validator.validate(content, logicalModel);

		Assert.assertEquals("", result.getIsARelationshipsMessages().get(0));
	}

	@Test
	public void testValidateContentSingleIsASelfInvalid() throws Exception {
		LogicalModel logicalModel = new LogicalModel("", new IsARestriction("123", RangeRelationType.SELF));
		AuthoringContent content = new AuthoringContent().addIsA("124");

		AuthoringContentValidationResult result = validator.validate(content, logicalModel);

		Assert.assertEquals("IsA relation must be '123'", result.getIsARelationshipsMessages().get(0));
	}

	@Test
	public void testValidateContentSingleIsADescendantsValid() throws Exception {
		LogicalModel logicalModel = new LogicalModel("", new IsARestriction("123", RangeRelationType.DESCENDANTS));
		testContentService.putDescendantIds("123", new String[]{"1234", "12345", "123456"});
		AuthoringContent content = new AuthoringContent().addIsA("12345");

		AuthoringContentValidationResult result = validator.validate(content, logicalModel);

		Assert.assertEquals("", result.getIsARelationshipsMessages().get(0));
	}

	@Test
	public void testValidateContentSingleIsADescendantsInvalid() throws Exception {
		LogicalModel logicalModel = new LogicalModel("", new IsARestriction("123", RangeRelationType.DESCENDANTS));
		testContentService.putDescendantIds("123", new String[]{"1234", "12345", "123456"});
		AuthoringContent content = new AuthoringContent().addIsA("12377");

		AuthoringContentValidationResult result = validator.validate(content, logicalModel);

		Assert.assertEquals("IsA relation must be a descendant of '123'", result.getIsARelationshipsMessages().get(0));
	}

	@Test
	public void testValidateContentSingleIsADescendantOrSelfValid() throws Exception {
		LogicalModel logicalModel = new LogicalModel("", new IsARestriction("123", RangeRelationType.DESCENDANTS_AND_SELF));
		testContentService.putDescendantIds("123", new String[]{"1234", "12345", "123456"});
		AuthoringContent content = new AuthoringContent().addIsA("123");

		AuthoringContentValidationResult result = validator.validate(content, logicalModel);

		Assert.assertEquals("", result.getIsARelationshipsMessages().get(0));
	}

	@Test
	public void testValidateContentSingleIsADescendantOrSelfInvalid() throws Exception {
		LogicalModel logicalModel = new LogicalModel("", new IsARestriction("123", RangeRelationType.DESCENDANTS_AND_SELF));
		testContentService.putDescendantIds("123", new String[]{"1234", "12345", "123456"});
		AuthoringContent content = new AuthoringContent().addIsA("12377");

		AuthoringContentValidationResult result = validator.validate(content, logicalModel);

		Assert.assertEquals("IsA relation must be a descendant of or equal to '123'", result.getIsARelationshipsMessages().get(0));
	}

	@Test
	public void testValidateContentNotEnoughIsA() throws Exception {
		LogicalModel logicalModel = new LogicalModel("", new IsARestriction("123", RangeRelationType.SELF))
				.addIsARestriction(new IsARestriction("12345", RangeRelationType.SELF));
		AuthoringContent content = new AuthoringContent().addIsA("124");

		AuthoringContentValidationResult result = validator.validate(content, logicalModel);

		Assert.assertEquals("IsA relation must be '123'\n" +
				"There are less isA relationships than in the logical model.", result.getIsARelationshipsMessages().get(0));
	}

	@Test
	public void testValidateContentTooManyIsA() throws Exception {
		LogicalModel logicalModel = new LogicalModel("", new IsARestriction("123", RangeRelationType.SELF));
		AuthoringContent content = new AuthoringContent()
				.addIsA("124")
				.addIsA("125");

		AuthoringContentValidationResult result = validator.validate(content, logicalModel);

		List<String> isARelationshipsMessages = result.getIsARelationshipsMessages();
		Assert.assertEquals(2, isARelationshipsMessages.size());
		Assert.assertEquals("IsA relation must be '123'", isARelationshipsMessages.get(0));
		Assert.assertEquals("There are more isA relationships than in the logical model.", isARelationshipsMessages.get(1));
	}

}
