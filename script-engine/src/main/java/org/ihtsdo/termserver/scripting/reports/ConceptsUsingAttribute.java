package org.ihtsdo.termserver.scripting.reports;

import java.io.PrintStream;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Report all active concepts using a particular attribute type
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConceptsUsingAttribute extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConceptsUsingAttribute.class);

	Concept attributeType;
	
	public static void main(String[] args) throws TermServerScriptException {
		ConceptsUsingAttribute report = new ConceptsUsingAttribute();
		try {
			report.additionalReportColumns = "Semtag, DefinitionStatus, Stated, Inferred";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			report.runConceptsUsingAttributeReport();
		} catch (Exception e) {
			LOGGER.info("Failed to produce ConceptsUsingAttribute Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}


	public void postInit() throws TermServerScriptException {
		attributeType = gl.getConcept("118170007");  // |Specimen source identity (attribute)|
		super.postInit();
	}


	private void runConceptsUsingAttributeReport() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				int stated = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE).size();
				int inferred = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, attributeType, ActiveState.ACTIVE).size();
				if (stated > 0 || inferred > 0) {
					String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
					report(c, semTag, c.getDefinitionStatus(), Integer.toString(stated), Integer.toString(inferred));
					incrementSummaryInformation(c.getDefinitionStatus().toString());
					incrementSummaryInformation(semTag);
					if (stated > 0) {
						incrementSummaryInformation("Concepts with relationship stated");
					}
					if (inferred > 0){
						incrementSummaryInformation("Concepts with relationship inferred");
					}
				}
				incrementSummaryInformation("Active Concepts checked");
			}
			
		}
	}

}
