package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.snomed.authoringtemplate.domain.logical.*;
import org.ihtsdo.termserver.scripting.template.TemplateUtils;

public class Template {

	char id;
	String name;
	String domain;
	LogicalTemplate logicalTemplate;
	List<AttributeGroup> attributeGroups;

	public Template (char id, LogicalTemplate logicalTemplate, String fileName) {
		this.id = id;
		this.logicalTemplate = logicalTemplate;
		this.name = fileName;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
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
		return id + ": " + name;
	}

	public Collection<AttributeGroup> getAttributeGroups() {
		//Does the logical template have any ungrouped attributes?  We can simplify the code by calling that 
		//a group.  Must store it though so that the object is recognised for comparison
		if (attributeGroups == null) {
			List<Attribute> ungrouped = logicalTemplate.getUngroupedAttributes();
			//Always add group 0 for consistency, even if it's empty
			List<AttributeGroup> combinedGroups = new ArrayList<>();
			AttributeGroup group0 = new AttributeGroup();
			group0.setAttributes(ungrouped);
			group0.setCardinalityMin("1");  //We'll always have a group 0 - a focus concept at least!
			group0.setCardinalityMax("1");
			combinedGroups.add(group0);
			combinedGroups.addAll(logicalTemplate.getAttributeGroups());
			attributeGroups = combinedGroups;
		}
		return attributeGroups;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}
}
