package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.PrintStream;
import java.util.List;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * DRUGS-457
 * Reports concepts (from a list supplied) that DO NOT have the specified 
 * attribute of interest
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MissingAttributeReport extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(MissingAttributeReport.class);

	List<Component> concepts;
	Concept attributeType;
	
	public static void main(String[] args) throws TermServerScriptException {
		MissingAttributeReport report = new MissingAttributeReport();
		try {
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.runMissingAttributeReport();
		} catch (Exception e) {
			LOGGER.info("Failed to produce MissingAttributeReport due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		attributeType = gl.getConcept("411116001"); // |Has manufactured dose form (attribute)|
	}

	private void runMissingAttributeReport() throws TermServerScriptException {
		List<Concept> concepts = asConcepts(processFile(getInputFile()));
		for (Concept c : concepts) {
			//If we don't have the FSN then project doesn't yet know about this concept
			if (c.getFsn() == null || c.getFsn().isEmpty()) {
				report(c, "Not yet available in project - status unknown");
				incrementSummaryInformation("Concepts unavailable in project");
			} else if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE).size()==0) {
				report(c, "Missing " + attributeType);
				incrementSummaryInformation("Issues encountered");
			}
		}
		addSummaryInformation("Concepts checked", concepts.size());
	}
	
}
