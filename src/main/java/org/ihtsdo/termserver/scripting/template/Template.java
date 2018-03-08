package org.ihtsdo.termserver.scripting.template;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.ihtsdo.otf.authoringtemplate.domain.logical.Attribute;
import org.ihtsdo.otf.authoringtemplate.domain.logical.AttributeGroup;
import org.ihtsdo.otf.authoringtemplate.domain.logical.LogicalTemplate;

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

	public Collection<AttributeGroup> getAttributeGroups() {
		//Does the logical template have any ungrouped attributes?  We can simplify the code by calling that 
		//a group.  Must store it though so that the object is recognised for comparison
		if (attributeGroups == null) {
			List<Attribute> ungrouped = logicalTemplate.getUngroupedAttributes();
			if (ungrouped != null && ungrouped.size() > 0) {
				List<AttributeGroup> combinedGroups = new ArrayList<>();
				AttributeGroup group0 = new AttributeGroup();
				group0.setAttributes(ungrouped);
				group0.setCardinalityMin("1");
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
