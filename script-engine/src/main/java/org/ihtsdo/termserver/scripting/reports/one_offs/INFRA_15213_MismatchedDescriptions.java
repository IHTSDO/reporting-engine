package org.ihtsdo.termserver.scripting.reports.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

public class INFRA_15213_MismatchedDescriptions extends TermServerReport implements ReportClass {

	private static final List<String> WORDS_OF_INTEREST = List.of("neonate", "neonatal", "newborn");
	
	public static void main(String[] args) throws TermServerScriptException {
		TermServerScript.run(INFRA_15213_MismatchedDescriptions.class, new HashMap<>(), args);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
		ReportSheetManager.setTargetFolderId(GFOLDER_ADHOC_REPORTS);
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"SCTID, FSN, SemTag, Descriptions,"};
		String[] tabNames = new String[] {
				"Mismatched descriptions"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List descriptions with mismatched words")
				.withDescription("")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(MS)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (!c.isActiveSafely()) {
				continue;
			}
			List<String> wordsOfInterestFound = new ArrayList<>();
			for (String word : WORDS_OF_INTEREST) {
				for (Description description : c.getDescriptions()) {
					if (!description.isActiveSafely() || description.getType().equals(DescriptionType.TEXT_DEFINITION)) {
						continue; // Skip inactive descriptions and text definitions
					}
					if (description.getTerm().toLowerCase().contains(word)) {
						wordsOfInterestFound.add(word);
						break;
					}
				}
			}
			if (wordsOfInterestFound.size() > 1) {
				String descriptionsStr = SnomedUtils.getDescriptions(c, false);
				String wordsOfInterestStr = String.join(", ", wordsOfInterestFound);
				report(c, descriptionsStr, wordsOfInterestStr);
				incrementSummaryInformation(ISSUE_COUNT);
			}
		}
	}


}
