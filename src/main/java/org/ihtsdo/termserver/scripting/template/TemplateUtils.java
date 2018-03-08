package org.ihtsdo.termserver.scripting.template;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import org.ihtsdo.termserver.scripting.domain.RF2Constants.CharacteristicType;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;

public class TemplateUtils {
	
	public static String ECL_DESCENDANT_OR_SELF = "<<";
	public static String ECL_OR = "OR";
	public static Pattern p = Pattern.compile("[0-9]+");
	
	public static String covertToECL (LogicalTemplate template, boolean restrictive) {
		StringBuffer sb = new StringBuffer();
		
		//Comma separated focus concepts as descendants
		sb.append(template.getFocusConcepts().stream()
				.map( c -> "<<" + c)
				.collect (Collectors.joining(",")));
		sb.append(":");
		
		//Now add the ungrouped attributes
		sb.append(template.getUngroupedAttributes().stream()
				.map( a -> a.getType() + "=" + a.getAllowableRangeECL())
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
		if (restrictive) {
			for (AttributeGroup group : template.getAttributeGroups()) {
				sb.append(",[0..0]{ (<< 246061005 |Attribute (attribute)| MINUS (")
					.append(group.getAttributes().stream()
						.map( a -> a.getType() )
						.collect (Collectors.joining(" AND ")))
					.append(")) = * }");
			}
		}
		
		return sb.toString();
	}

	public static boolean matchesTemplate(Concept c, Template t, DescendentsCache cache, CharacteristicType charType) throws TermServerScriptException {
		//TODO Check the focus concept
		//TODO Check the ungrouped attributes

		//Map relGroups to template attribute groups, and visa versa
		Map<RelationshipGroup, List<AttributeGroup>> relGroupMatchesTemplateGroups = new HashMap<>();
		Map<AttributeGroup,  List<RelationshipGroup>> templateGroupMatchesRelGroups = new HashMap<>();
		
		//Pre-populate the attributeGroups in case we have no relationship groups, and the relationship groups in case we have no matching template groups
		t.getAttributeGroups().stream().forEach(attributeGroup -> templateGroupMatchesRelGroups.put(attributeGroup, new ArrayList<RelationshipGroup>()));
		//Include group 0
		c.getRelationshipGroups(charType).stream().forEach(relGroup -> relGroupMatchesTemplateGroups.put(relGroup, new ArrayList<AttributeGroup>()));
		
		//Work through each group (including 0) and check which of the groups in the template it matches
		for (RelationshipGroup relGroup : c.getRelationshipGroups(charType)) {
			//Work through each template group and confirm that one of them matches
			for (AttributeGroup templateGroup : t.getAttributeGroups()) {
				if (matchesTemplateGroup (relGroup, templateGroup, cache)) {
					//Update map of concept relationship groups matching template attribute groups
					List<AttributeGroup> matchedAttributeGroups = relGroupMatchesTemplateGroups.get(relGroup);
					matchedAttributeGroups.add(templateGroup);
					
					//Update map of template attribute groups matching concept relationship groups  
					List<RelationshipGroup> matchedRelGroups = templateGroupMatchesRelGroups.get(templateGroup);
					matchedRelGroups.add(relGroup);
				}
			}
		}
		return validateCardinality(relGroupMatchesTemplateGroups, templateGroupMatchesRelGroups, c, t.getId());
	}

	private static boolean validateCardinality(
			Map<RelationshipGroup, List<AttributeGroup>> relGroupMatchesTemplateGroups,
			Map<AttributeGroup, List<RelationshipGroup>> templateGroupMatchesRelGroups,
			Concept c,
			char templateId) {
		boolean isValid = true;
		//Does every relationship group match at least one attribute group?  If not, record that failure
		for (Entry<RelationshipGroup, List<AttributeGroup>> entry : relGroupMatchesTemplateGroups.entrySet()) {
			if (entry.getValue().size() == 0) {
				isValid = false;
				entry.getKey().addIndicator(templateId);
			}
		}
		
		//Are there the correct number of relationship groups for each template attribute group?
		for (Entry<AttributeGroup, List<RelationshipGroup>> entry : templateGroupMatchesRelGroups.entrySet()) {
			Cardinality cardinality = getCardinality(entry.getKey());
			int count = entry.getValue().size();
			if (count < cardinality.getMin() || count > cardinality.getMax()) {
				isValid = false;
				c.addIssue(templateId + "(" + count + "!=" + getCardinalityStr(entry.getKey()) + ")");
				break;
			}
		}
		return isValid;
	}

	private static boolean matchesTemplateGroup(RelationshipGroup relGroup, AttributeGroup templateGroup, DescendentsCache cache) throws TermServerScriptException {
		//For each attribute, check if there's a match in the template
		nextRel:
		for (Relationship r : relGroup.getRelationships()) {
			//TODO Check attribute cardinality here
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
		//TODO Parse the ECL properly
		String[] eclAlternatives = ecl.split(ECL_OR);  //Any of the alternatives can match
		for (String thisEcl : eclAlternatives) {
			thisEcl = thisEcl.trim();
			if (thisEcl.startsWith(ECL_DESCENDANT_OR_SELF)) {
				String valueRangeSctId = recoverSctId(thisEcl);
				Concept valueRange = GraphLoader.getGraphLoader().getConcept(valueRangeSctId);
				if (valueRange != null && cache.getDescendentsOrSelf(valueRange).contains(target)) {
					return true;
				}
			} else {
				//TODO Call the server to resolve this, and cache result
				throw new NotImplementedException("Unable to handle ecl: " + ecl);
			}
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

	public static Cardinality getCardinality(AttributeGroup group) {
		Cardinality cardinality = new Cardinality();
		cardinality.setMin(getCardinality(group.getCardinalityMin()));
		cardinality.setMax(getCardinality(group.getCardinalityMax()));
		return cardinality;
	}

	private static int getCardinality(String cStr) {
		if (cStr == null || cStr.isEmpty()) {
			return 0;
		}
		//The ~ character indicates a replacement.  Not needed for testing
		cStr = cStr.replace("~", "");
		
		if (cStr.equals("*")) {
			return Integer.MAX_VALUE;
		} else {
			return Integer.parseInt(cStr);
		}
	}
	
	private static String getCardinalityStr(AttributeGroup g) {
		StringBuffer sb = new StringBuffer();
		sb.append("[")
		.append(g.getCardinalityMin())
		.append("..")
		.append(g.getCardinalityMax())
		.append("]");
		return sb.toString();
	}

	public static String toString(LogicalTemplate logicalTemplate) {
		return covertToECL (logicalTemplate, false);
	}
}
