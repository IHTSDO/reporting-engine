package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.io.PrintStream;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * DRUG-469 
 * Query to identify MPF-containing concepts that are sufficiently defined with 
 * stated parent |Medicinal product| and that have an inferred parent with 
 * semantic tag other than (medicinal product).
 * 
 * DRUG-470
 * Query to identify CD concepts that are sufficiently defined with 
 * stated parent |Medicinal product| and that have an inferred parent with 
 * semantic tag other than (medicinal product form).
 */
public class IdentifyMissingDrugConcepts extends TermServerReport {
	
	private static final String CD = "(clinical drug)";
	private static final String MPF = "(medicinal product form)";
	private static final String MP = "(medicinal product)";
	
	Concept subHierarchy;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		IdentifyMissingDrugConcepts report = new IdentifyMissingDrugConcepts();
		try {
			report.additionalReportColumns = "InferredParent";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			//report.runIdentifyMissingMPConceptsReport();
			report.runIdentifyMissingMPFConceptsReport();
		} catch (Exception e) {
			println("Failed to produce ConceptsWithOrTargetsOfAttribute Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept(PHARM_BIO_PRODUCT.getConceptId());
	}

	/*private void runIdentifyMissingMPConceptsReport() throws TermServerScriptException {
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			// MPF-containing concepts that are sufficiently defined
			if (c.isActive() && c.getFsn().contains(MPF) && c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				//...with stated parent |Medicinal product| 
				if (hasStatedParent (c, MEDICINAL_PRODUCT)) {
					//...and that have an inferred parent with semantic tag other than (medicinal product).
					for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
						if (!parent.getFsn().contains(MP)) {
							report (c, parent.toString());
							incrementSummaryInformation("Parents reported");
						}
					}
				}
				incrementSummaryInformation("FD MPFs with |Medicinal Product| stated parent checked");
			}
			incrementSummaryInformation("Concepts checked");
		}
	} */
	
	private void runIdentifyMissingMPFConceptsReport() throws TermServerScriptException {
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			// CD concepts that are sufficiently defined
			if (c.isActive() && c.getFsn().contains(CD) && c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				//...with stated parent |Medicinal product| 
				if (hasStatedParent (c, MEDICINAL_PRODUCT)) {
					//...and that have an inferred parent with semantic tag other than (medicinal product).
					for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
						if (!parent.getFsn().contains(MPF)) {
							report (c, parent.toString());
							incrementSummaryInformation("Non-MPF Parents reported");
						}
					}
				}
				incrementSummaryInformation("FD CDs with |Medicinal Product| stated parent checked");
			}
			incrementSummaryInformation("Concepts checked");
		}
	}

	private boolean hasStatedParent(Concept c, Concept targetParent) {
		for (Concept parent : c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
			if (parent.equals(targetParent)) {
				return true;
			}
		}
		return false;
	}

}
