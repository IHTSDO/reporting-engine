package org.ihtsdo.termserver.scripting.reports;

import java.io.PrintStream;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangesInEclSelectSinceRelease extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(ChangesInEclSelectSinceRelease.class);

	private String ecl = "<< 363687006|Endoscopic procedure (procedure)|";
	private String releaseET = "2023-03-31"; 
	
	public static void main(String[] args) throws TermServerScriptException {
		ChangesInEclSelectSinceRelease report = new ChangesInEclSelectSinceRelease();
		try {
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			report.runChangesInEclSelectSinceReleaseReport();
			report.summaryTabIdx = PRIMARY_REPORT;
		} catch (Exception e) {
			LOGGER.info("Failed to produce ContainedInSubhierarchyReport Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
			report.flushFiles(true);
		}
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { 
				"Item, Count",
				"SCTID, FSN, SemTag",
				"SCTID, FSN, SemTag",
				"SCTID, FSN, SemTag"};
		
		String[] tabNames = new String[] {	
				"Summary",
				"Gained", 
				"Lost" , 
				"Unchanged"};
		super.postInit(tabNames, columnHeadings);
	}

	private void runChangesInEclSelectSinceReleaseReport() throws TermServerScriptException {
		String releaseBranch = "MAIN/" + releaseET;
		Collection<Concept> releaseSelection = findConcepts(releaseBranch, ecl);
		Collection<Concept> currentSelection = findConcepts(ecl);
		
		Set<Concept> gained = new HashSet<>(currentSelection);
		gained.removeAll(releaseSelection);
		
		Set<Concept> lost = new HashSet<>(releaseSelection);
		lost.removeAll(currentSelection);
		
		Set<Concept> unchanged = new HashSet<>(releaseSelection);
		unchanged.retainAll(currentSelection);
		
		addSummaryInformation("ECL", ecl);
		addSummaryInformation("Selection " + releaseET, releaseSelection.size());
		addSummaryInformation(projectName, currentSelection.size());
		addSummaryInformation("Lost", lost.size());
		addSummaryInformation("Gained", gained.size());
		addSummaryInformation("Unchanged", unchanged.size());
		
		SnomedUtils.sort(gained).forEach(c -> reportSafely(SECONDARY_REPORT, c));
		SnomedUtils.sort(lost).forEach(c -> reportSafely(TERTIARY_REPORT, c));
		SnomedUtils.sort(unchanged).forEach(c -> reportSafely(QUATERNARY_REPORT, c));
	}
}
