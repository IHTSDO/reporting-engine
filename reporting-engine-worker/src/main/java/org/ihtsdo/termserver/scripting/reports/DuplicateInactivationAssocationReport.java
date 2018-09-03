package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * INFRA-2704
 * Where a concept has duplicate inactivation indicators, list those along with the
 * historical associations (so we know which ones to delete!)
 */
public class DuplicateInactivationAssocationReport extends TermServerReport {
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		DuplicateInactivationAssocationReport report = new DuplicateInactivationAssocationReport();
		try {
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
		List<Concept> conceptsOfInterest = gl.getAllConcepts().stream()
				.filter(c -> c.isActive() == false)
				.filter(c -> c.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() > 1)
				.collect(Collectors.toList());
		for (Concept c : conceptsOfInterest) {
			for (InactivationIndicatorEntry i : c.getInactivationIndicatorEntries(ActiveState.ACTIVE)) {
				report (c, i.getEffectiveTime(), i);
			}
			for (HistoricalAssociation h : c.getHistorialAssociations(ActiveState.ACTIVE)) {
				report (c, h.getEffectiveTime(), h);
			}
			incrementSummaryInformation("Rows reported");
		}
	}
	
	private String simpleName(String sctid) throws TermServerScriptException {
		Concept c = gl.getConcept(sctid);
		return SnomedUtils.deconstructFSN(c.getFsn())[0];
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}
