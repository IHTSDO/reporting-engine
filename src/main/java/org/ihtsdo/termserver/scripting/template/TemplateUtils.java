package org.ihtsdo.termserver.scripting.template;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.authoringtemplate.domain.logical.Attribute;
import org.ihtsdo.otf.authoringtemplate.domain.logical.AttributeGroup;
import org.ihtsdo.otf.authoringtemplate.domain.logical.LogicalTemplate;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.ActiveState;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.CharacteristicType;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;

public class TemplateUtils {
	
	public static String ECL_DESCENDANT_OR_SELF = "<<";
	public static Pattern p = Pattern.compile("[0-9]+");
	
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
		
		//And we want zero groups that contain any other attributes
		//Now add the grouped attributes
		for (AttributeGroup group : template.getAttributeGroups()) {
			sb.append(",[0..0]{ (<< 246061005 |Attribute (attribute)| MINUS (")
				.append(group.getAttributes().stream()
					.map( a -> a.getType() )
					.collect (Collectors.joining(" AND ")))
				.append(")) = * }");
		}
		
		return sb.toString();
	}

	public static boolean matchesTemplate(Concept c, LogicalTemplate t, DescendentsCache cache, char templateId) throws TermServerScriptException {
		//TODO Check the focus concept
		//TODO Check the ungrouped attributes
		
		//Work through each group (except 0) and check it matches one of the groups in the template
		nextRelGroup:
		for (RelationshipGroup relGroup : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE, false)) {
			//Work through each template group and confirm that one of them matches
			for (AttributeGroup templateGroup : t.getAttributeGroups()) {
				if (matchesTemplateGroup (relGroup, templateGroup, cache)) {
					//We can go straight on to check the next relationship group now
					continue nextRelGroup;
				}
			}
			relGroup.addIndicator(templateId);
			return false;
		}
		return true;
	}

	private static boolean matchesTemplateGroup(RelationshipGroup relGroup, AttributeGroup templateGroup, DescendentsCache cache) throws TermServerScriptException {
		//For each attribute, check if there's a match in the template
		nextRel:
		for (Relationship r : relGroup.getRelationships()) {
			for (Attribute a : templateGroup.getAttributes()) {
				if (matchesAttribute(r, a, cache)) {
					//We can check the next relationship
					continue nextRel;
				}
			}
			return false;
		}
		return true;
	}

	private static boolean matchesAttribute(Relationship r, Attribute a, DescendentsCache cache) throws TermServerScriptException {
		if (matchesAttributeType(r.getType(), a.getType())) {
			//Is the value within the allowable ECL?
			return matchesAttributeValue(r.getTarget(), a.getAllowableRangeECL().trim(), cache);
		}
		return false;
	}

	private static boolean matchesAttributeValue(Concept target, String ecl, DescendentsCache cache) throws TermServerScriptException {
		//We'll only handle the simplest of ECL here
		if (ecl.startsWith(ECL_DESCENDANT_OR_SELF)) {
			String valueRangeSctId = recoverSctId(ecl);
			Concept valueRange = GraphLoader.getGraphLoader().getConcept(valueRangeSctId);
			if (valueRange != null) {
				return cache.getDescendentsOrSelf(valueRange).contains(target);
			}
		} else {
			//TODO Call the server to resolve this, and cache result
			throw new NotImplementedException("Unable to handle ecl: " + ecl);
		}
		return false;
	}

	private static String recoverSctId(String str) {
		Matcher m = p.matcher(str);
		if (m.find()) {
			return m.group();
		}
		return null;
	}

	private static boolean matchesAttributeType(Concept c1, String c2Str) throws TermServerScriptException {
		Concept c2 = GraphLoader.getGraphLoader().getConcept(c2Str);
		return c1.equals(c2);
	}
}
