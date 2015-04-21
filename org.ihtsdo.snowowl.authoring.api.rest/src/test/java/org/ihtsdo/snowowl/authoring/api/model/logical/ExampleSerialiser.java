package org.ihtsdo.snowowl.authoring.api.model.logical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

/**
 * This class doesn't test anything, it's just for generating example JSON.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"/test-context.xml"})
public class ExampleSerialiser {

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	public void serialiseLogicalModelExample() throws IOException {
		LogicalModel logicalModel = new LogicalModel();
		logicalModel.setName("example-logical-model");
		logicalModel.addIsARestriction(new IsARestriction("71388002", RangeRelationType.DESCENDANTS));
		List<AttributeRestriction> attributeGroupA = logicalModel.newAttributeGroup();
		attributeGroupA.add(new AttributeRestriction("260686004", RangeRelationType.SELF, "312251004"));
		attributeGroupA.add(new AttributeRestriction("405813007", RangeRelationType.DESCENDANTS_AND_SELF, "442083009"));
		List<AttributeRestriction> attributeGroupB = logicalModel.newAttributeGroup();
		attributeGroupB.add(new AttributeRestriction("260686004", RangeRelationType.SELF, "312251004"));
		attributeGroupB.add(new AttributeRestriction("405813007", RangeRelationType.DESCENDANTS_AND_SELF, "442083009"));
		attributeGroupB.add(new AttributeRestriction("363703001", RangeRelationType.DESCENDANTS, "429892002"));

		StringWriter stringWriter = new StringWriter();
		objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
		objectMapper.writeValue(stringWriter, logicalModel);
		System.out.println(stringWriter);

	}

}
