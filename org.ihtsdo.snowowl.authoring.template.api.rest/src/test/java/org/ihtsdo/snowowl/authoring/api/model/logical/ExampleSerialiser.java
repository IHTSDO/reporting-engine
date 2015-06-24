package org.ihtsdo.snowowl.authoring.single.api.model.logical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.ihtsdo.snowowl.authoring.single.api.model.work.WorkingConcept;
import org.ihtsdo.snowowl.authoring.single.api.model.Template;
import org.ihtsdo.snowowl.authoring.single.api.model.lexical.LexicalModel;
import org.ihtsdo.snowowl.authoring.single.api.model.lexical.Term;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class doesn't test anything, it's just for generating example JSON.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"/test-context.xml"})
public class ExampleSerialiser {

	@Autowired
	private ObjectMapper objectMapper;

	@Before
	public void setup() {
		objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
	}

	@Test
	public void serialiseLogicalModelExample() throws IOException {
		LogicalModel logicalModel = new LogicalModel();
		logicalModel.setName("test-logical-model");
		logicalModel.addIsARestriction(new IsARestriction("71388002", RangeRelationType.DESCENDANTS));
		List<AttributeRestriction> attributeGroupA = logicalModel.newAttributeGroup();
		attributeGroupA.add(new AttributeRestriction("260686004", RangeRelationType.SELF, "312251004"));
		attributeGroupA.add(new AttributeRestriction("405813007", RangeRelationType.DESCENDANTS_AND_SELF, "442083009"));
		List<AttributeRestriction> attributeGroupB = logicalModel.newAttributeGroup();
		attributeGroupB.add(new AttributeRestriction("260686004", RangeRelationType.SELF, "312251004"));
		attributeGroupB.add(new AttributeRestriction("405813007", RangeRelationType.DESCENDANTS_AND_SELF, "442083009"));
		attributeGroupB.add(new AttributeRestriction("363703001", RangeRelationType.DESCENDANTS, "429892002"));

		printJsonObject(logicalModel);
	}

	@Test
	public void serialiseLexicalModelExample() throws IOException {
		LexicalModel lexicalModel = new LexicalModel();
		lexicalModel.setName("test-lexical-model");
		lexicalModel.setFsn(new Term("Computed tomography of ", " (procedure)", false));
		lexicalModel.setPreferredTerm(new Term("CT of ", "", false));
		lexicalModel.setSynonom(new Term("Computed tomography of ", "", false));

		printJsonObject(lexicalModel);
	}

	@Test
	public void serialiseTemplateExample() throws IOException {
		Template template = new Template();
		template.setName("test-template");
		template.setLogicalModelName("test-logical-model");
		template.setLexicalModelName("test-lexical-model");
		printJsonObject(template);
	}

	@Test
	public void serialiseContentExample() throws IOException {
		List<WorkingConcept> list = new ArrayList<>();
		WorkingConcept content = new WorkingConcept();
		content.setTerm("Test");
		content.addIsA("128927009");
		Map<String, String> attributeGroupA = content.newAttributeGroup();
		attributeGroupA.put("260686004", "312251004");
		attributeGroupA.put("405813007", "442083009");
		Map<String, String> attributeGroupB = content.newAttributeGroup();
		attributeGroupB.put("260686004", "312251004");
		attributeGroupB.put("405813007", "442083009");
		attributeGroupB.put("363703001", "429892002");
		list.add(content);
		printJsonObject(list);
	}

	private void printJsonObject(Object model) throws IOException {
		StringWriter stringWriter = new StringWriter();
		objectMapper.writeValue(stringWriter, model);
		System.out.println(stringWriter);
	}

}
