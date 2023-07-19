package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.springframework.util.StringUtils;

/**
 * INFRA-2704, INFRA-2793
 * Where a concept has duplicate inactivation indicators, list those along with the
 * historical associations (so we know which ones to delete!)
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuplicateInactivationAssocationReport extends TermServerReport {

	private static Logger LOGGER = LoggerFactory.getLogger(DuplicateInactivationAssocationReport.class);

	public static void main(String[] args) throws TermServerScriptException, IOException {
		DuplicateInactivationAssocationReport report = new DuplicateInactivationAssocationReport();
		try {
			ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ";  //Release QA
			report.additionalReportColumns = "fsn, effectiveTime, data";
			report.init(args);
			report.loadProjectSnapshot(false);  
			report.reportMatchingInactivations();
		} catch (Exception e) {
			LOGGER.info("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void reportMatchingInactivations() throws TermServerScriptException {
		LOGGER.info ("Scanning all concepts...");
		addSummaryInformation("Concepts checked", gl.getAllConcepts().size());
		
		/*
		 * For the assertion /importer/src/main/resources/scripts/file-centric/file-centric-snapshot-attribute-value-unique-pair.sql
		 * One of the historical assertions must be new, so we'll check that one of them has a blank effective time
		 */
		
		//For a change we're interested in inactive concepts!
		List<Concept> conceptsOfInterest = gl.getAllConcepts().stream()
				.filter(c -> c.isActive() == false)
				.filter(c -> c.getInactivationIndicatorEntries(ActiveState.BOTH).size() > 1)
				.filter(c -> hasNewInactivationIndicator(c))
				.collect(Collectors.toList());
		//List<Concept> conceptsOfInterest = Collections.singletonList(gl.getConcept("198308002"));
		for (Concept c : conceptsOfInterest) {
			for (InactivationIndicatorEntry i : c.getInactivationIndicatorEntries(ActiveState.BOTH)) {
				report (c, i.getEffectiveTime(), i);
			}
			for (AssociationEntry h : c.getAssociations(ActiveState.BOTH)) {
				report (c, h.getEffectiveTime(), h);
			}
			incrementSummaryInformation("Concepts reported");
		}
		LOGGER.info ("Now checking descriptions..");
		//Or possibly there's an issue with descriptions?
		//For a change we're interested in inactive concepts!
		for (Concept c : gl.getAllConcepts()) {
			if (c.getConceptId().equals("14816004")) {  
			//	LOGGER.debug("CheckHere - Desc 1221136011");
			}
			List<Description> descriptionsOfInterest = c.getDescriptions().stream()
					.filter(d -> d.getInactivationIndicatorEntries(ActiveState.BOTH).size() > 1)
					.filter(d -> hasNewInactivationIndicator(d))
					.collect(Collectors.toList());
			for (Description d : descriptionsOfInterest) {
				for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries(ActiveState.ACTIVE)) {
					report (c, i.getEffectiveTime(), d, i);
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

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}
