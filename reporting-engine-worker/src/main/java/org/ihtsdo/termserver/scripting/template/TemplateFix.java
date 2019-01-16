package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.util.*;

import org.snomed.authoringtemplate.domain.logical.*;
import org.springframework.util.StringUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TemplateServiceClient;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

abstract public class TemplateFix extends BatchFix {
	
	Set<Concept> exclusions;
	List<String> exclusionWords;
	boolean includeComplexTemplates = false;
	List<Concept> complexTemplateAttributes;
	boolean includeDueTos = false;
	
	String[] templateNames;
	List<Template> templates = new ArrayList<>();
	TemplateServiceClient tsc = new TemplateServiceClient(null, null);
	
	Map<Concept, Template> conceptToTemplateMap = new HashMap<>();

	protected TemplateFix(BatchFix clone) {
		super(clone);
	}
	
	protected void init(String[] args) throws TermServerScriptException {
		AttributeGroup.useDefaultValues = true;
		//We'll check these now so we know if there's some parsing error
		char id = 'A';
		for (int x = 0; x < templateNames.length; x++, id++) {
			loadTemplate(id, templateNames[x]);
			info ("Validated template: " + templateNames[x]);
		}
		super.init(args);
	}

	public void postInit() throws TermServerScriptException {
		if (subHierarchyStr != null) {
			subHierarchy = gl.getConcept(subHierarchyStr);
		}
		char id = 'A';
		for (int x = 0; x < templateNames.length; x++, id++) {
			templates.add(loadTemplate(id, templateNames[x]));
			info ("Loaded template: " + templates.get(x).toIdString());
		}
		info(templates.size() + " Templates loaded successfully");
		
		if (exclusions == null) {
			exclusions = new HashSet<>();
		}
		
		if (excludeHierarchies == null) {
			excludeHierarchies = new String[] {};
		}

		for (String thisExclude : excludeHierarchies) {
			info("Setting exclusion of " + thisExclude + " subHierarchy.");
			exclusions.addAll(gl.getConcept(thisExclude).getDescendents(NOT_SET));
		}
		
		if (exclusionWords == null) {
			exclusionWords = new ArrayList<>();
		}
		exclusionWords.add("subluxation");
		exclusionWords.add("avulsion");
		exclusionWords.add("associated");
		exclusionWords.add("co-occurrent");
		
		if (!includeComplexTemplates) {
			if (!includeDueTos) {
				exclusionWords.add("due to");
			}
			exclusionWords.add("with");
			exclusionWords.add("without");
		} else {
			warn ("Including complex templates");
		}
		
		complexTemplateAttributes = new ArrayList<>();
		if (!includeDueTos) {
			complexTemplateAttributes.add(DUE_TO);
		}
		complexTemplateAttributes.add(AFTER);
		complexTemplateAttributes.add(gl.getConcept("726633004")); //|Temporally related to (attribute)|
		complexTemplateAttributes.add(gl.getConcept("288556008")); //|Before (attribute)|
		complexTemplateAttributes.add(gl.getConcept("371881003")); //|During (attribute)|
		complexTemplateAttributes.add(gl.getConcept("363713009")); //|Has interpretation (attribute)|
		complexTemplateAttributes.add(gl.getConcept("363714003")); //|Interprets (attribute)|
		super.postInit();
		info ("Post initialisation complete");
	}
	
	protected Template loadTemplate (char id, String fileName) throws TermServerScriptException {
		try {
			LogicalTemplate lt = tsc.loadLogicalTemplate(fileName);
			return new Template(id, lt, fileName);
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to load template " + fileName, e);
		}
	}
	
	protected Set<Concept> findTemplateMatches(Template t) throws TermServerScriptException {
		Set<Concept> matches = new HashSet<Concept>();
		for (Concept c : findConcepts(project.getBranchPath(), subHierarchyECL)) {
			if (c.getConceptId().equals("415771000")) {
				debug ("Check template match here");
			}
			if (!c.isActive()) {
				warn ("Ignoring inactive concept returned by ECL: " + c);
				continue;
			}
			info ("Searching for concepts in " + subHierarchyECL + " matching template " + t);
			if (TemplateUtils.matchesTemplate(c, t, gl.getDescendantsCache(), CharacteristicType.INFERRED_RELATIONSHIP)) {
				//Do we already have a template for this concept?  
				//TODO Assign the most specific template if so
				if (conceptToTemplateMap.containsKey(c)) {
					Template existing = conceptToTemplateMap.get(c);
					Template moreSpecific = t.getId() > existing.getId() ? t : existing; 
					warn( c + "matches two templates: " + t.getId() + " & " + existing.getId() + " using most specific " + moreSpecific.getId());
					conceptToTemplateMap.put(c, moreSpecific);
				} else {
					conceptToTemplateMap.put(c, t);
				}
				matches.add(c);
			}
		}
		return matches;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}
	
	protected boolean isExcluded(Concept c) {
		//These hierarchies have been excluded
		if (exclusions.contains(c)) {
			incrementSummaryInformation("Concepts excluded due to hierarchial exclusion");
			return true;
		}
		
		if (StringUtils.isEmpty(c.getFsn())) {
			warn("Skipping concept with no FSN: " + c.getConceptId());
			return true;
		}
		
		//We could ignore on the basis of a word, or SCTID
		String fsn = " " + c.getFsn().toLowerCase();
		for (String word : exclusionWords) {
			//word = " " + word + " ";
			if (fsn.contains(word)) {
				debug (c + "ignored due to fsn containing:" + word);
				incrementSummaryInformation("Concepts excluded due to lexical match");
				return true;
			}
		}
		
		//We're excluding complex templates that have a due to, or "after" attribute
		if (!includeComplexTemplates) {
			for (Concept excludedType : complexTemplateAttributes) {
				for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (r.getType().equals(excludedType)) {
						incrementSummaryInformation("Concepts excluded due to complexity");
						return true;
					}
				}
			}
		}
		return false;
	}
	
	@Override
	public void report (Task task, Component component, Severity severity, ReportActionType actionType, Object... details) throws TermServerScriptException {
		Concept c = (Concept)component;
		char relevantTemplate = ' ';
		if (conceptToTemplateMap != null && conceptToTemplateMap.containsKey(c)) {
			relevantTemplate = conceptToTemplateMap.get(c).getId();
		}
		super.report (task, component, severity, actionType, SnomedUtils.translateDefnStatus(c.getDefinitionStatus()), relevantTemplate, details);
	}
	
	protected int removeRedundandGroups(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		List<RelationshipGroup> originalGroups = new ArrayList<>(c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP));
		for (RelationshipGroup originalGroup : originalGroups) {
			for (RelationshipGroup potentialRedundancy : originalGroups) {
				//Don't compare self
				if (originalGroup.getGroupId() == potentialRedundancy.getGroupId()) {
					continue;
				}
				if (SnomedUtils.isSameOrMoreSpecific(originalGroup, potentialRedundancy, gl.getAncestorsCache())) {
					report (t, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_GROUP_REMOVED, "Redundant relationship group removed", potentialRedundancy);
					for (Relationship r : potentialRedundancy.getRelationships()) {
						changesMade += removeRelationship(t, c, r);
					}
				}
			}
		}
		return changesMade;
	}
}
