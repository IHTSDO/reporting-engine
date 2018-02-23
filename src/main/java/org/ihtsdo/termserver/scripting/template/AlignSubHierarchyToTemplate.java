package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.ihtsdo.otf.authoringtemplate.domain.logical.LogicalTemplate;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.client.TemplateServiceClient;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class AlignSubHierarchyToTemplate extends TermServerReport {
	
	String subHierarchyStr = "46866001"; //|Fracture of lower limb (disorder)|
	Concept subHierarchy;
	LogicalTemplate template;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		AlignSubHierarchyToTemplate report = new AlignSubHierarchyToTemplate();
		try {
			report.additionalReportColumns = "CharacteristicType, Attribute";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			report.reportUnlignedConcepts();
		} catch (Exception e) {
			println("Failed to produce ConceptsWithOrTargetsOfAttribute Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postInit() throws TermServerScriptException, JsonParseException, JsonMappingException, IOException {
		subHierarchy = gl.getConcept(subHierarchyStr);
		TemplateServiceClient tsc = new TemplateServiceClient();
		template = tsc.loadLogicalTemplate("Fracture of Bone Structure.json");
	}
	


	private void reportUnlignedConcepts() throws TermServerScriptException {
		print("Template loaded successfully");
		//Get the template as an ECL Expression and recover concepts which do NOT meet this criteria
		String ecl = TemplateUtils.covertToECL(template);
		//Take the inverse to find all concepts that DO NOT match one of our templates
		String inverseEcl = "<<" + subHierarchyStr + " MINUS " + ecl;
		List<Concept> concepts = findConcepts("MAIN", inverseEcl);
		for (Concept c : concepts) {
			debug (c);
		}
	}
}
