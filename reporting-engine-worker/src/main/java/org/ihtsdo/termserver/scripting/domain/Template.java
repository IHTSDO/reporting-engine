package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.snomed.authoringtemplate.domain.logical.*;
import org.ihtsdo.termserver.scripting.template.TemplateUtils;

public class Template {

	char id;
	String fileName;
	LogicalTemplate logicalTemplate;
	List<AttributeGroup> attributeGroups;

	public Template (char id, LogicalTemplate logicalTemplate, String fileName) {
		this.id = id;
		this.logicalTemplate = logicalTemplate;
		this.fileName = fileName;
	}
	
	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public char getId() {
		return id;
	}

	public LogicalTemplate getLogicalTemplate() {
		return logicalTemplate;
	}
	
	public String toString () {
		return id + ": " + TemplateUtils.toString(logicalTemplate);
	}
	
	public String toIdString () {
		return id + ": " + fileName;
	}

	public Collection<AttributeGroup> getAttributeGroups() {
		//Does the logical template have any ungrouped attributes?  We can simplify the code by calling that 
		//a group.  Must store it though so that the object is recognised for comparison
		if (attributeGroups == null) {
			List<Attribute> ungrouped = logicalTemplate.getUngroupedAttributes();
			if (ungrouped != null && ungrouped.size() > 0) {
				List<AttributeGroup> combinedGroups = new ArrayList<>();
				AttributeGroup group0 = new AttributeGroup();
				group0.setAttributes(ungrouped);
				group0.setCardinalityMin("0");
				group0.setCardinalityMax("1");
				combinedGroups.add(group0);
				combinedGroups.addAll(logicalTemplate.getAttributeGroups());
				attributeGroups = combinedGroups;
			} else {
				attributeGroups = logicalTemplate.getAttributeGroups();
			}
		}
		return attributeGroups;
	}
}
