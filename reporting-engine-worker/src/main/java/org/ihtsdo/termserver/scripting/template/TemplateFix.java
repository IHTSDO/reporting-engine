package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.snomed.authoringtemplate.domain.logical.*;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TemplateServiceClient;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.domain.Template;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

abstract public class TemplateFix extends BatchFix {
	
	String [] excludeHierarchies = new String[] {};
	List<Concept> exclusions;
	List<String> exclusionWords;
	boolean includeComplexTemplates = false;
	List<Concept> complexTemplateAttributes;
	
	String[] templateNames;
	List<Template> templates = new ArrayList<>();
	TemplateServiceClient tsc = new TemplateServiceClient();
	
	Map<Concept, Template> conceptToTemplateMap = new HashMap<>();

	protected TemplateFix(BatchFix clone) {
		super(clone);
	}
	
	protected void init(String[] args) throws TermServerScriptException, IOException {
		AttributeGroup.useDefaultValues = true;
		//We'll check these now so we know if there's some parsing error
		char id = 'A';
		for (int x = 0; x < templateNames.length; x++, id++) {
			loadTemplate(id, templateNames[x]);
			info ("Validated template: " + templateNames[x]);
		}
		super.init(args);
	}

	protected void postInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept(subHierarchyStr);
		char id = 'A';
		for (int x = 0; x < templateNames.length; x++, id++) {
			templates.add(loadTemplate(id, templateNames[x]));
			info ("Loaded template: " + templates.get(x).toIdString());
		}
		info(templates.size() + " Templates loaded successfully");
		
		exclusions = new ArrayList<>();
		for (String thisExclude : excludeHierarchies) {
			exclusions.addAll(gl.getConcept(thisExclude).getDescendents(NOT_SET));
		}
		
		exclusionWords = new ArrayList<>();
		exclusionWords.add("subluxation");
		exclusionWords.add("avulsion");
		exclusionWords.add("associated");
		exclusionWords.add("co-occurrent");
		
		if (!includeComplexTemplates) {
			exclusionWords.add("due to");
			//exclusionWords.add("with");
			exclusionWords.add("without");
		}
		
		complexTemplateAttributes = new ArrayList<>();
		complexTemplateAttributes.add(DUE_TO);
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
		for (Concept c : gl.getDescendantsCache().getDescendentsOrSelf(subHierarchy)) {
			if (c.getConceptId().equals("19378003")) {
				debug ("Check template match here");
			}
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
		//We could ignore on the basis of a word, or SCTID
		for (String word : exclusionWords) {
			word = " " + word + " ";
			if (c.getFsn().toLowerCase().contains(word)) {
				debug (c + "ignored due to fsn containing: " + word);
				incrementSummaryInformation("Excluded concepts");
				return true;
			}
		}
		
		//We're excluding complex templates that have a due to, or "after" attribute
		if (!includeComplexTemplates) {
			for (Concept excludedType : complexTemplateAttributes) {
				for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (r.getType().equals(excludedType)) {
						incrementSummaryInformation("Excluded concepts");
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
}
