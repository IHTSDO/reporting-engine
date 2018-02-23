package org.ihtsdo.termserver.scripting.template;

import java.util.stream.Collectors;

import org.ihtsdo.otf.authoringtemplate.domain.logical.AttributeGroup;
import org.ihtsdo.otf.authoringtemplate.domain.logical.LogicalTemplate;

public class TemplateUtils {
	public static String covertToECL (LogicalTemplate template) {
		StringBuffer sb = new StringBuffer();
		
		//Comma separated focus concepts as descendants
		sb.append(template.getFocusConcepts().stream()
				.map( c -> "<<" + c)
				.collect (Collectors.joining(",")));
		sb.append(":");
		
		//Now add the ungrouped attributes
		sb.append(template.getUngroupedAttributes().stream()
				.map( a -> a.getType() + "=" + a.getValue())
				.collect (Collectors.joining(",")));
		
		//Now add the grouped attributes
		for (AttributeGroup group : template.getAttributeGroups()) {
			sb.append("{")
				.append(group.getAttributes().stream()
					.map( a -> a.getType() + "=" + a.getAllowableRangeECL())
					.collect (Collectors.joining(",")))
				.append("}");
		}
		
		return sb.toString();
	}
}
