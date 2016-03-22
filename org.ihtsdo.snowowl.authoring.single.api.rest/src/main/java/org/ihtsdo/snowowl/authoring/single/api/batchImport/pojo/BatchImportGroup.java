package org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.rest.exception.ProcessingException;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.service.BatchImportService;

import com.b2international.snowowl.snomed.api.domain.browser.ISnomedBrowserRelationship;

public class BatchImportGroup {
	int groupNumber;
	List <ISnomedBrowserRelationship> relationships = new ArrayList<ISnomedBrowserRelationship>();
	
	public List<ISnomedBrowserRelationship> getRelationships() {
		return relationships;
	}

	private BatchImportGroup (int groupNumber) {
		this.groupNumber = groupNumber;
	}
	
	public static BatchImportGroup parse(int groupNumber, String expression) throws ProcessingException {
		BatchImportGroup thisGroup = new BatchImportGroup(groupNumber);
		String[] attributes = expression.split(BatchImportExpression.ATTRIBUTE_SEPARATOR);
		int attributeNumber = 0;
		for (String thisAttribute : attributes) {
			String tmpId = "rel_" + groupNumber + "." + (attributeNumber++);
			ISnomedBrowserRelationship relationship = parseAttribute(groupNumber, tmpId, thisAttribute);
			thisGroup.relationships.add(relationship);
		}
		return thisGroup;
	}

	private static ISnomedBrowserRelationship parseAttribute(int groupNum, String tmpId, String thisAttribute) throws ProcessingException {
		//Expected format  type=value so bomb out if we don't end up with two concepts
		String[] attributeParts = thisAttribute.split(BatchImportExpression.TYPE_SEPARATOR);
		if (attributeParts.length != 2) {
			throw new ProcessingException("Unable to detect type=value in attribute: " + thisAttribute);
		}
		return BatchImportService.createRelationship(groupNum, tmpId, null, attributeParts[0], attributeParts[1]);
	}
}
