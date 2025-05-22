package org.ihtsdo.termserver.scripting.reports.one_offs;

import org.ihtsdo.otf.RF2Constants;
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
import java.util.stream.Collectors;

public class RP_1037_DescriptionsWithMg extends TermServerReport implements ReportClass {

	private static final String TARGET_TEXT_SP_SP = " mg ";
	private static final String TARGET_TEXT_SP = " mg";
	private static final String TARGET_TEXT = "mg";
	private static final String TARGET_TEXT_SP_SLASH = " mg/";
	
	public static void main(String[] args) throws TermServerScriptException {
		TermServerScript.run(RP_1037_DescriptionsWithMg.class, new HashMap<>(), args);
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
				"SCTID, FSN, SemTag, Descriptions, Case Significance",
				"SCTID, FSN, SemTag, Descriptions, Case Significance"};
		String[] tabNames = new String[] {
				"Medicinal Product contains mg",
				"Anything else contains mg"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List descriptions with mg")
				.withDescription("")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(MS)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		initialiseSummaryInformation(ISSUE_COUNT);
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (!c.isActiveSafely()) {
				continue;
			}
			List<Description> descriptionsContainingTargetText = c.getDescriptions(ActiveState.ACTIVE)
					.stream()
					.filter(this::containsTargetText)
					.toList();
			if (!descriptionsContainingTargetText.isEmpty()) {
				String descriptions = descriptionsContainingTargetText.stream()
						.map(d -> d.getTerm())
						.collect(Collectors.joining(",\n"));
				String caseSignificances = descriptionsContainingTargetText.stream()
						.map(d -> SnomedUtils.translateCaseSignificanceFromEnumSafely(d.getCaseSignificance()))
						.collect(Collectors.joining(",\n"));
				if (isMedicinalProduct(c)) {
					report(PRIMARY_REPORT, c, descriptions, caseSignificances);
				} else {
					report(SECONDARY_REPORT, c, descriptions, caseSignificances);
				}
				countIssue(c);
			}

		}
	}

	private boolean isMedicinalProduct(Concept c) throws TermServerScriptException {
		return c.getAncestors(RF2Constants.NOT_SET).contains(MEDICINAL_PRODUCT);
	}

	private boolean containsTargetText(Description d) {
		//To avoid matching within terms, we'll use the target text padded with spaces.
		//But this doesn't work if it's the last word, so also check for ending
		String term = d.getTerm();
		String sp_term = " " + term;
		return d.getTerm().contains(TARGET_TEXT_SP_SP)
				|| d.getTerm().endsWith(TARGET_TEXT_SP)
				|| sp_term.contains(TARGET_TEXT_SP_SLASH)
				|| term.equals(TARGET_TEXT)
				|| sp_term.contains(TARGET_TEXT_SP);
	}

}
