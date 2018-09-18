package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * INFRA-2704, INFRA-2793
 * Where a concept has duplicate inactivation indicators, list those along with the
 * historical associations (so we know which ones to delete!)
 */
public class DuplicateInactivationAssocationReport extends TermServerReport {
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		DuplicateInactivationAssocationReport report = new DuplicateInactivationAssocationReport();
		try {
			ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ";  //Release QA
			report.additionalReportColumns = "fsn, effectiveTime, data";
			report.init(args);
			report.loadProjectSnapshot(true);  
			report.reportMatchingInactivations();
		} catch (Exception e) {
			info("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void reportMatchingInactivations() throws TermServerScriptException {
		info ("Scanning all concepts...");
		addSummaryInformation("Concepts checked", gl.getAllConcepts().size());
		
		//For a change we're interested in inactive concepts!
		/*List<Concept> conceptsOfInterest = gl.getAllConcepts().stream()
				.filter(c -> c.isActive() == false)
				.filter(c -> c.getInactivationIndicatorEntries(ActiveState.BOTH).size() > 1)
				.collect(Collectors.toList());*/
		List<Concept> conceptsOfInterest = Collections.singletonList(gl.getConcept("198308002"));
		for (Concept c : conceptsOfInterest) {
			for (InactivationIndicatorEntry i : c.getInactivationIndicatorEntries(ActiveState.BOTH)) {
				report (c, i.getEffectiveTime(), i);
			}
			for (HistoricalAssociation h : c.getHistorialAssociations(ActiveState.BOTH)) {
				report (c, h.getEffectiveTime(), h);
			}
			incrementSummaryInformation("Concepts reported");
		}
		info ("Now checking descriptions..");
		//Or possibly there's an issue with descriptions?
		//For a change we're interested in inactive concepts!
		for (Concept c : gl.getAllConcepts()) {
			if (c.getConceptId().equals("262553000")) {  
				debug("CheckHere - Desc 2528910015");
			}
			List<Description> descriptionsOfInterest = c.getDescriptions().stream()
					.filter(d -> d.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() > 1)
					.collect(Collectors.toList());
			for (Description d : descriptionsOfInterest) {
				for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries(ActiveState.ACTIVE)) {
					report (c, i.getEffectiveTime(), d, i);
				}
				incrementSummaryInformation("Descriptions reported");
			}
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}
