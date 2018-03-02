package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.otf.authoringtemplate.domain.logical.LogicalTemplate;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.client.TemplateServiceClient;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class AlignSubHierarchyToTemplate extends TermServerReport {
	
	String subHierarchyStr = "46866001"; //|Fracture of lower limb (disorder)|
	Concept subHierarchy;
	List<LogicalTemplate> templates = new ArrayList<>();
	DescendentsCache cache = new DescendentsCache();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		AlignSubHierarchyToTemplate report = new AlignSubHierarchyToTemplate();
		try {
			report.additionalReportColumns = "CharacteristicType, Attribute";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			//while (true) {
				try {
					report.reportUnlignedConcepts();
				} catch (Exception e) {
					debug ("Failure due to " + e.getMessage());
				}
			//}
		} catch (Exception e) {
			info("Failed to produce ConceptsWithOrTargetsOfAttribute Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postInit() throws TermServerScriptException, JsonParseException, JsonMappingException, IOException {
		subHierarchy = gl.getConcept(subHierarchyStr);
		TemplateServiceClient tsc = new TemplateServiceClient();
		templates.add(tsc.loadLogicalTemplate("Fracture of Bone Structure.json"));
		templates.add(tsc.loadLogicalTemplate("Fracture Dislocation of Bone Structure.json"));
		info(templates.size() + " Templates loaded successfully");
	}

	/*private void reportUnlignedConcepts() throws TermServerScriptException {	
		//Get the template as an ECL Expression and recover concepts which do NOT meet this criteria
		String ecl = TemplateUtils.covertToECL(template);
		//Take the inverse to find all concepts that DO NOT match one of our templates
		String inverseEcl = "<<" + subHierarchyStr + " MINUS (" + ecl + ")";
		List<Concept> concepts = findConcepts("MAIN", inverseEcl);
		for (Concept c : concepts) {
			debug (c);
		}
	}*/
	
	protected void reportUnlignedConcepts() throws TermServerScriptException {
		//Start with the whole subHierarchy and remove concepts that match each of our templates
		Set<Concept> unalignedConcepts = cache.getDescendentsOrSelf(subHierarchy);
		char templateId = 'A';
		for (LogicalTemplate template : templates) {
			Set<Concept> matches = findTemplateMatches(template, templateId);
			unalignedConcepts.removeAll(matches);
			templateId++;
		}
		
		for (Concept c : unalignedConcepts) {
			debug (c);
			for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				debug ("    " + g);
			}
		}
	}

	private Set<Concept> findTemplateMatches(LogicalTemplate t, char templateId) throws TermServerScriptException {
		Set<Concept> matches = new HashSet<Concept>();
		for (Concept c : cache.getDescendentsOrSelf(subHierarchy)) {
			if (c.getConceptId().equals("263093003")) {
				debug("Check Me");
			}
			if (TemplateUtils.matchesTemplate(c, t, cache, templateId)) {
				matches.add(c);
			}
		}
		return matches;
	}

}
