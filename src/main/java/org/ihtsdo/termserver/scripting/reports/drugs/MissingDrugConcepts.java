package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.io.PrintStream;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * Reports to identify missing drug concepts.
 * 
 * DRUGS-511 : query to identify missing MP-containing concepts on regular 
 * basis (e.g. QPF-containing concept without inferred MP-containing parent) 
 * 
 * DRUGS-534 : Query to identify CD concepts that are sufficiently defined 
 * with stated parent |Medicinal product| and that do have an inferred MPF-only parent
 * 
 * DRUGS-535 : Query to identify MP concepts that are sufficiently defined 
 * with stated parent |Medicinal product| and that do have an inferred MP-only parent
 * 
 */
public class MissingDrugConcepts extends TermServerReport {
	
	private static final String CD = "(clinical drug)";
	private static final String MPF = "(medicinal product form)";
	private static final String MP = "(medicinal product)";
	private static final String ONLY = " only ";
	
	Concept subHierarchy;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		MissingDrugConcepts report = new MissingDrugConcepts();
		try {
			report.additionalReportColumns = "MPF Concept with no MP Inferred";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			//report.runIdentifyMissingMPConceptsReport();
			report.runIdentifyMissingMPFConceptsReport(); //DRUGS-511
			//report.runIdentifyMissingMPFOnlyConceptsReport(); //DRUGS-534
			//report.runIdentifyMissingMPOnlyConceptsReport(); //DRUGS-535
			//report.runIdentifyMissingDescendantReport(); //DRUGS-536
		} catch (Exception e) {
			info("Failed to produce MissingDrugConcepts Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept(PHARM_BIO_PRODUCT.getConceptId());
	}

	private void runIdentifyMissingMPConceptsReport() throws TermServerScriptException {
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			// MPF-containing concepts 
			if (c.getFsn().contains(MPF)) {
					//..that do not have an inferred MP parent.
				boolean mpParentFound = false;
				for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
					if (parent.getFsn().contains(MP)) {
						mpParentFound = true;
					}
				}
				if (!mpParentFound) {
					incrementSummaryInformation("Issues Found");
					report (c);
				}
				incrementSummaryInformation("MPFs checked");
			}
			incrementSummaryInformation("Concepts checked");
		}
	} 
	
	private void runIdentifyMissingMPFConceptsReport() throws TermServerScriptException {
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			// MPF concepts that are sufficiently defined
			if (c.isActive() && c.getFsn().contains(MPF) && c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				//...with stated parent |Medicinal product| 
				if (hasStatedParent (c, MEDICINAL_PRODUCT)) {
					//that DO NOT have an inferred parent with semantic tag (medicinal product)
					boolean hasMpParent = false;
					for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
						if (parent.getFsn().contains(MP)) {
							hasMpParent = true;
							break;
						}
					}
					
					if (!hasMpParent) {
						report (c);
						incrementSummaryInformation("Issue - MPFs with no MP parent");
					}
				}
				incrementSummaryInformation("FD MPFs checked");
			}
			incrementSummaryInformation("Concepts checked");
		}
	}
	
	private void runIdentifyMissingMPOnlyConceptsReport() throws TermServerScriptException {
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			// MP concepts that are sufficiently defined
			if (c.isActive() && c.getFsn().contains(MP) && c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				//...with stated parent |Medicinal product| 
				if (hasStatedParent (c, MEDICINAL_PRODUCT)) {
					//...and that have an inferred parent which is MP "Only"
					for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
						if (parent.getFsn().contains(MP) && parent.getFsn().contains(ONLY)) {
							report (c, parent.toString());
							incrementSummaryInformation("Concepts reported");
						}
					}
				}
				incrementSummaryInformation("FD MPs with |Medicinal Product| stated parent checked");
			}
			incrementSummaryInformation("Concepts checked");
		}
	}
	
	private void runIdentifyMissingMPFOnlyConceptsReport() throws TermServerScriptException {
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			// CD concepts that are sufficiently defined
			if (c.isActive() && c.getFsn().contains(CD) && c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				//...with stated parent |Medicinal product| 
				if (hasStatedParent (c, MEDICINAL_PRODUCT)) {
					//...and that have an inferred parent which is MPF "Only"
					for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
						if (parent.getFsn().contains(MPF) && parent.getFsn().contains(ONLY)) {
							report (c, parent.toString());
							incrementSummaryInformation("Concepts reported");
						}
					}
				}
				incrementSummaryInformation("FD MPs with |Medicinal Product| stated parent checked");
			}
			incrementSummaryInformation("Concepts checked");
		}
	} 
	
	/*
	 * MP and MPF concepts that have no descendants
	 */
	private void runIdentifyMissingDescendantReport() throws TermServerScriptException {
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			// MP, MPF concepts...
			if (c.getFsn().contains(MP) || c.getFsn().contains(MPF)) {
				//with no inferred children
				if (c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP).size() == 0) {
					report (c);
					incrementSummaryInformation("MP/MPF Concepts reported having no inferred descendants");
				}
				incrementSummaryInformation("MP/MPF Concepts checked");
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
