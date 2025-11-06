package org.ihtsdo.termserver.scripting.reports;

import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.apache.commons.lang.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INFRA-2704, INFRA-2793
 * Where a concept has duplicate inactivation indicators, list those along with the
 * historical associations (so we know which ones to delete!)
 */
public class DuplicateInactivationAssocationReport extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(DuplicateInactivationAssocationReport.class);

	public static void main(String[] args) throws TermServerScriptException {
		DuplicateInactivationAssocationReport report = new DuplicateInactivationAssocationReport();
		try {
			ReportSheetManager.setTargetFolderId("15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ");  //Release QA
			report.additionalReportColumns = "fsn, effectiveTime, data";
			report.init(args);
			report.loadProjectSnapshot(false);  
			report.reportMatchingInactivations();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
		} finally {
			report.finish();
		}
	}

	private void reportMatchingInactivations() throws TermServerScriptException {
		LOGGER.info("Scanning all concepts...");
		addSummaryInformation("Concepts checked", gl.getAllConcepts().size());
		
		/*
		 * For the assertion /importer/src/main/resources/scripts/file-centric/file-centric-snapshot-attribute-value-unique-pair.sql
		 * One of the historical assertions must be new, so we'll check that one of them has a blank effective time
		 */
		
		//For a change we're interested in inactive concepts!
		List<Concept> conceptsOfInterest = gl.getAllConcepts().stream()
				.filter(c -> !c.isActiveSafely())
				.filter(c -> c.getInactivationIndicatorEntries(ActiveState.BOTH).size() > 1)
				.filter(this::hasNewInactivationIndicator)
				.toList();
		for (Concept c : conceptsOfInterest) {
			for (InactivationIndicatorEntry i : c.getInactivationIndicatorEntries(ActiveState.BOTH)) {
				report(c, i.getEffectiveTime(), i);
			}
			for (AssociationEntry h : c.getAssociationEntries(ActiveState.BOTH)) {
				report(c, h.getEffectiveTime(), h);
			}
			incrementSummaryInformation("Concepts reported");
		}
		LOGGER.info("Now checking descriptions..");
		//Or possibly there's an issue with descriptions?
		//For a change we're interested in inactive concepts!
		for (Concept c : gl.getAllConcepts()) {
			List<Description> descriptionsOfInterest = c.getDescriptions().stream()
					.filter(d -> d.getInactivationIndicatorEntries(ActiveState.BOTH).size() > 1)
					.filter(this::hasNewInactivationIndicator)
					.toList();
			for (Description d : descriptionsOfInterest) {
				for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries(ActiveState.ACTIVE)) {
					report(c, i.getEffectiveTime(), d, i);
				}
				incrementSummaryInformation("Descriptions reported.");
			}
		}
	}

	private boolean hasNewInactivationIndicator(Concept c) {
		for (InactivationIndicatorEntry i : c.getInactivationIndicatorEntries(ActiveState.BOTH)) {
			if (StringUtils.isEmpty(i.getEffectiveTime())) {
				return true;
			}
		}
		return false;
	}
	
	private boolean hasNewInactivationIndicator(Description d) {
		for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries(ActiveState.BOTH)) {
			if (StringUtils.isEmpty(i.getEffectiveTime())) {
				return true;
			}
		}
		return false;
	}
}
