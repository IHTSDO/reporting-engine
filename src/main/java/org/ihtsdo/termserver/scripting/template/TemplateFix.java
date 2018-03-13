package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.otf.authoringtemplate.domain.logical.LogicalTemplate;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TemplateServiceClient;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

abstract public class TemplateFix extends BatchFix {
	
	String subHierarchyStr = "46866001"; //|Fracture of lower limb (disorder)|
	Concept subHierarchy;
	List<String> templateNames;
	List<Template> templates = new ArrayList<>();
	String[] ignoreFSNsContaining = new String[] { "avulsion" };
	TemplateServiceClient tsc = new TemplateServiceClient();
	
	Map<Concept, Template> conceptToTemplateMap = new HashMap<>();

	protected TemplateFix(BatchFix clone) {
		super(clone);
	}

	protected void postInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept(subHierarchyStr);
		templates.add(loadTemplate('A', "Fracture of Bone Structure.json"));
		templates.add(loadTemplate('B', "Fracture Dislocation of Bone Structure.json"));
		templates.add(loadTemplate('C', "Pathologic fracture of bone due to Disease.json"));
		info(templates.size() + " Templates loaded successfully");
		
		/*//Seems to be an issue with parsing cardianality.  Add this in manually.
		for (LogicalTemplate template : templates ) {
			for (AttributeGroup group : template.getAttributeGroups()) {
				group.setCardinalityMin("1");
				group.setCardinalityMax("*");
			}
		} */
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
			
			if (c.getConceptId().equals("704293000")) {
				debug ("Checking concept 704293000");
			}
			
			if (TemplateUtils.matchesTemplate(c, t, descendantsCache, CharacteristicType.INFERRED_RELATIONSHIP)) {
				//Do we already have a template for this concept?  
				//TODO Assign the most specific template if so
				if (conceptToTemplateMap.containsKey(c)) {
					throw new IllegalArgumentException("Concept matches two templates: " + t.getId() + " & " + conceptToTemplateMap.get(c).getId());
				}
				conceptToTemplateMap.put(c, t);
				matches.add(c);
			}
		}
		return matches;
	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
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
		char relevantTemplate = templates.get(0).getId();
		if (conceptToTemplateMap != null && conceptToTemplateMap.containsKey(c)) {
			relevantTemplate = conceptToTemplateMap.get(c).getId();
		}
		super.report (task, component, severity, actionType, c.getDefinitionStatus(), relevantTemplate, details);
	}
}
