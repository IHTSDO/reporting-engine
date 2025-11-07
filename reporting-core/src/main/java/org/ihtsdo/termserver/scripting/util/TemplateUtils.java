package org.ihtsdo.termserver.scripting.util;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.authoringtemplate.domain.logical.*;
import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;


public class TemplateUtils implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(TemplateUtils.class);

	public static Pattern p = Pattern.compile("[0-9]+");
	
	public static boolean SLOT_NAME_WARNING_MADE = false;
	
	public static String covertToECL (LogicalTemplate template, boolean restrictive) {
		StringBuffer sb = new StringBuffer();
		
		//Comma separated focus concepts as descendants
		sb.append(template.getFocusConcepts().stream()
				.map( c -> "<<" + c)
				.collect (Collectors.joining(",")));
		sb.append(":");
		
		//Now add the ungrouped attributes
		sb.append(template.getUngroupedAttributes().stream()
				.map( a -> a.getType() + "=" + (a.getValueAllowableRangeECL() == null? a.getValue(): a.getValueAllowableRangeECL()))
				.collect (Collectors.joining(",")));
		
		//Now add the grouped attributes
		for (AttributeGroup group : template.getAttributeGroups()) {
			sb.append("{")
				.append(group.getAttributes().stream()
					.map( a -> a.getType() + "=" + (a.getValueAllowableRangeECL() == null? a.getValue(): a.getValueAllowableRangeECL()))
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
	
	public static boolean matchesTemplate(Concept c, Template t, TermServerScript ts, CharacteristicType charType) throws TermServerScriptException {
		//Do a check here that unspecified cardinality on a group should be clarified as [[0..*]]
		for (AttributeGroup g : t.getAttributeGroups()) {
			if (g.getCardinalityMin() == null || g.getCardinalityMax() == null) {
				LOGGER.warn("Template {} failed to specify cardinality in group {} clarifying as [[1..1]]", t.getName(), g);
				g.setCardinalityMin("1");
				g.setCardinalityMax("1");
			}
		}
		
		//Default to not allowing additional attributes
		return matchesTemplate(c, t, ts, charType, false);
	}

	public static boolean matchesTemplate(Concept c, Template t, TermServerScript ts, CharacteristicType charType, boolean allowAdditional) throws TermServerScriptException {
		//TODO Check the focus concept
		try {
			//Map relGroups to template attribute groups, and visa versa
			Map<RelationshipGroup, List<AttributeGroup>> relGroupMatchesTemplateGroups = new HashMap<>();
			Map<AttributeGroup, List<RelationshipGroup>> templateGroupMatchesRelGroups = new HashMap<>();
			Map<String, List<Concept>> namedSlots = new HashMap<>();
			
			//Pre-populate the attributeGroups in case we have no relationship groups, and the relationship groups in case we have no matching template groups
			t.getAttributeGroups().stream().forEach(attributeGroup -> templateGroupMatchesRelGroups.put(attributeGroup, new ArrayList<RelationshipGroup>()));
			//Include group 0
			c.getRelationshipGroups(charType).stream().forEach(relGroup -> relGroupMatchesTemplateGroups.put(relGroup, new ArrayList<AttributeGroup>()));
			
			//Work through each group (including 0) and check which of the groups in the template it matches
			//However, template groups should not match ungrouped concept attributes
			nextRelGroup:
			for (RelationshipGroup relGroup : c.getRelationshipGroups(charType)) {
				//Work through each template group and confirm that one of them matches
				for (AttributeGroup templateGroup : t.getAttributeGroups()) {
					if (matchesTemplateGroup (relGroup, templateGroup, namedSlots, ts)) {
						//Update map of concept relationship groups matching template attribute groups
						List<AttributeGroup> matchedAttributeGroups = relGroupMatchesTemplateGroups.get(relGroup);
						matchedAttributeGroups.add(templateGroup);
						
						//Update map of template attribute groups matching concept relationship groups  
						List<RelationshipGroup> matchedRelGroups = templateGroupMatchesRelGroups.get(templateGroup);
						matchedRelGroups.add(relGroup);
						continue nextRelGroup;
					}
				}
			}
			
			boolean isValid =  validateCardinality(relGroupMatchesTemplateGroups, templateGroupMatchesRelGroups, c, t.getId());
			if (isValid) {
				isValid = validateNamedSlots(c, t, namedSlots);
			}
			//if (true);
			return isValid;
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to validate concept " + c + " against template '" + t.getName() + "'", e);
		}
	}

	private static boolean validateNamedSlots(Concept c, Template t, Map<String, List<Concept>> namedSlots) {
		//TODO   This is actually really complicated.  We're only checking named slots when there
		//is more than one instance of that slot name in the template, and in that case, we need
		//to match an attribute group to the template group and ensure we're only checking 
		//for matches between two template groups.   For now, we'll check only when there's more than one 
		//template group
		boolean isValid = true;
		Set<String> repeatedTemplateSlots = findRepeatedSlots(t);
		for (String slotName : repeatedTemplateSlots) {
			Concept firstValue = null;
			if (namedSlots.containsKey(slotName)) {
				for (Concept value : namedSlots.get(slotName)) {
					if (firstValue == null) {
						firstValue = value;
					} else if (!value.equals(firstValue)) {
						c.addIssue("Repeated slot '" + slotName + "' in template '" + t.getId() + "' encountered different values " + firstValue + " and " + value);
						isValid = false;
					}
				}
			}
		}
		return isValid;
	}

	private static Set<String> findRepeatedSlots(Template t) {
		//Ensure that any repeated instances of identically named slots are the same
		Set<String> namedSlots = new HashSet<>();
		Set<String> repeatedSlots = new HashSet<>();
		
		for (AttributeGroup g : t.getAttributeGroups()) {
			for (Attribute a : g.getAttributes()) {
				//Does this attribute have a named slot?
				if (!StringUtils.isEmpty(a.getValueSlotName())) {
					if (namedSlots.contains(a.getValueSlotName())) {
						repeatedSlots.add(a.getValueSlotName());
					} else {
						namedSlots.add(a.getValueSlotName());
					}
				}
			}
		}
		return repeatedSlots;
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
				//Group 0 can have optional cardinality if all its attributes are optional
				if (entry.getKey().getGroupId() == UNGROUPED && containsAllOptional(entry.getKey())) {
					continue;
				} else {
					isValid = false;
					c.addIssue(templateId + " " + count + " found != " + getCardinalityStr(entry.getKey()) + " required. " + entry.getKey().toString());
					break;
				}
			}
		}
		return isValid;
	}

	private static boolean containsAllOptional(AttributeGroup group) {
		for (Attribute a : group.getAttributes()) {
			if (a.getCardinalityMin() != null && !a.getCardinalityMin().equals("0")) {
				return false;
			}
		}
		return true;
	}

	private static boolean matchesTemplateGroup(RelationshipGroup relGroup, AttributeGroup templateGroup, Map<String, List<Concept>> namedSlots, TermServerScript ts) throws TermServerScriptException {
		//Grouped template groups (ie > 0) cannot match ungrouped concept attributes and visa versa
		if (relGroup.isGrouped() != templateGroup.isGrouped()) {
			return false;
		}
		//For each attribute, check if there's a match in the template
		nextRel:
		for (Relationship r : relGroup.getRelationships()) {
			for (Attribute a : templateGroup.getAttributes()) {
				if (matchesAttribute(r, a, namedSlots, ts)) {
					//We can check the next relationship
					continue nextRel;
				}
			}
			return false;
		}
	
		//For each template element, check its cardinality is satisfied
		for (Attribute a : templateGroup.getAttributes()) {
			int count = 0;
			for (Relationship r : relGroup.getRelationships()) {
				if (matchesAttribute(r, a, null, ts)) {
					count++;
				}
			}
			if (!satisfiesCardinality(count, a)) {
				return false;
			}
		}
		return true;
	}

	public static boolean matchesAttribute(Relationship r, Attribute a, Map<String, List<Concept>> namedSlots, TermServerScript ts) throws TermServerScriptException {
		boolean matchesAttributeValue = false;
		if (matchesAttributeType(r.getType(), a, ts)) {
			//Is the value within the allowable ECL, or do we have a fixed value?
			if (a.getValueAllowableRangeECL() != null) {
				if (r.isConcrete()) {
					matchesAttributeValue = matchesAttributeConcreteValue(r.getConcreteValue(), a.getValueAllowableRangeECL().trim(), ts);
				} else {
					matchesAttributeValue = matchesAttributeValue(r.getTarget(), a.getValueAllowableRangeECL().trim(), ts);
				}
			} else if (a.getValue() != null) {
				matchesAttributeValue = r.getTarget().getConceptId().equals(a.getValue());
			} else if (a.getValueSlotReference() != null) {
				if (!SLOT_NAME_WARNING_MADE) {
					LOGGER.warn("TODO - maintain list of matched slot name values to pass in");
					SLOT_NAME_WARNING_MADE = true;
				}
				matchesAttributeValue = true;
			} else {
				throw new IllegalArgumentException ("Template segment has neither ECL, Value nor SlotReference: " + a);
			}
		}
		
		//If we got a match, record that this slot has been filled
		if (namedSlots != null && matchesAttributeValue && !StringUtils.isEmpty(a.getValueSlotName())) {
			List<Concept> slotValues = namedSlots.get(a.getValueSlotName());
			if (slotValues == null) {
				slotValues = new ArrayList<>();
				namedSlots.put(a.getValueSlotName(), slotValues);
			}
			slotValues.add(r.getTarget());
		}
		
		return matchesAttributeValue;
	}
	
	public static boolean containsMatchingRelationship (RelationshipGroup group , Attribute a , Map<String, List<Concept>> namedSlots, TermServerScript ts) throws TermServerScriptException {
		for (Relationship r : group.getRelationships()) {
			if (matchesAttribute(r, a, namedSlots, ts)) {
				return true;
			}
		}
		return false;
	}

	private static boolean matchesAttributeValue(Concept target, String ecl, TermServerScript ts) throws TermServerScriptException {
		if (ecl.equals("*")) {
			return true;
		}
		Collection<Concept> permittedConcepts = ts.findConcepts(ecl, true, true);
		return permittedConcepts.contains(target);
	}
	
	private static boolean matchesAttributeConcreteValue(Object value, String ecl, TermServerScript ts) throws TermServerScriptException {
		if (ecl.equals("*")) {
			return true;
		}
		throw new NotImplementedException("Not yet able to check specific concrete values in templates");
	}
	private static boolean matchesAttributeType(Concept c1, Attribute a, TermServerScript ts) throws TermServerScriptException {
		//Are we matching a simple type, or a range?
		if (a.getType() != null) {
			return c1.equals(GraphLoader.getGraphLoader().getConcept(a.getType()));
		} else {
			String ecl = a.getTypeAllowableRangeECL();
			for (Concept c2 : ts.findConcepts(ecl, true, true)) {
				if (c1.equals(c2)) {
					return true;
				}
			}
		}
		return false;
	}

	public static Cardinality getCardinality(AttributeGroup g) {
		int min = g.getCardinalityMin()==null?1:getCardinality(g.getCardinalityMin());
		int max = g.getCardinalityMax()==null?Integer.MAX_VALUE:getCardinality(g.getCardinalityMax());
		
		Cardinality cardinality = new Cardinality();
		cardinality.setMin(min);
		cardinality.setMax(max);
		return cardinality;
	}
	
	public static boolean satisfiesCardinality (int count, Attribute a) {
		int min = a.getCardinalityMin()==null?1:getCardinality(a.getCardinalityMin());
		int max = a.getCardinalityMax()==null?Integer.MAX_VALUE:getCardinality(a.getCardinalityMax());
		return count >= min && count <= max;
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
