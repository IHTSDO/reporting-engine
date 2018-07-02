package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.snomed.authoringtemplate.domain.logical.LogicalTemplate;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TemplateServiceClient;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.domain.Template;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

abstract public class TemplateFix extends BatchFix {
	
	String subHierarchyStr;
	String [] excludeHierarchies = new String[] {};
	List<Concept> exclusions;
	
	Concept subHierarchy;
	String[] templateNames;
	
	List<Template> templates = new ArrayList<>();
	String[] ignoreFSNsContaining = new String[] { "avulsion" , "without"};
	TemplateServiceClient tsc = new TemplateServiceClient();
	
	Map<Concept, Template> conceptToTemplateMap = new HashMap<>();

	protected TemplateFix(BatchFix clone) {
		super(clone);
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
		for (Concept c : descendantsCache.getDescendentsOrSelf(subHierarchy)) {
			if (c.getConceptId().equals("713251003")) {
				debug ("Check template match here");
			}
			if (TemplateUtils.matchesTemplate(c, t, descendantsCache, CharacteristicType.INFERRED_RELATIONSHIP)) {
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
	
	protected boolean isIngnored(Concept c) {
		//We could ignore on the basis of a word, or SCTID
		for (String word : ignoreFSNsContaining) {
			if (c.getFsn().toLowerCase().contains(word)) {
				debug (c + "ignored due to fsn containing: " + word);
				incrementSummaryInformation("Ignored concepts");
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void report (Task task, Component component, Severity severity, ReportActionType actionType, Object... details) {
		Concept c = (Concept)component;
		char relevantTemplate = ' ';
		if (conceptToTemplateMap != null && conceptToTemplateMap.containsKey(c)) {
			relevantTemplate = conceptToTemplateMap.get(c).getId();
		}
		super.report (task, component, severity, actionType, SnomedUtils.translateDefnStatus(c.getDefinitionStatus()), relevantTemplate, details);
	}
}
