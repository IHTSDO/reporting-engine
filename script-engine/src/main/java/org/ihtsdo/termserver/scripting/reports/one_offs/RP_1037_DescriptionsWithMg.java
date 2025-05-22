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

	private List<MatchingSet> targetTexts = List.of(
			new MatchingSet("mg"),
			new MatchingSet("g"),
			new MatchingSet("ml")
	);
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
				"SCTID, FSN, SemTag, Text Matched, Descriptions, Case Significance",
				"SCTID, FSN, SemTag, Descriptions, Case Significance",
				"SCTID, FSN, SemTag, Text Matched, Descriptions, Case Significance"};
		String[] tabNames = new String[] {
				"Medicinal Product contains g/mg/ml",
				"CDs not listed in first tab",
				"Anything else contains g/mg"};
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
		List<Concept> drugsReported = new ArrayList<>();
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (!c.isActiveSafely()) {
				continue;
			}
			for (MatchingSet ms : targetTexts) {
				checkDescriptionsForTargetText(c, ms, drugsReported);
			}
		}
		reportCDsNotPreviouslyReported(drugsReported);
	}

	private void checkDescriptionsForTargetText(Concept c, MatchingSet ms, List<Concept> drugsReported) throws TermServerScriptException {
		List<Description> descriptionsContainingTargetText = c.getDescriptions(ActiveState.ACTIVE)
				.stream()
				.filter(d -> containsTargetText(d, ms))
				.toList();
		if (!descriptionsContainingTargetText.isEmpty()) {
			String descriptions = descriptionsContainingTargetText.stream()
					.map(Description::getTerm)
					.collect(Collectors.joining(",\n"));
			String caseSignificances = descriptionsContainingTargetText.stream()
					.map(d -> SnomedUtils.translateCaseSignificanceFromEnumSafely(d.getCaseSignificance()))
					.collect(Collectors.joining(",\n"));
			if (isMedicinalProduct(c)) {
				report(PRIMARY_REPORT, c, ms.targetText, descriptions, caseSignificances);
				drugsReported.add(c);
			} else {
				report(TERTIARY_REPORT, c, ms.targetText, descriptions, caseSignificances);
			}
			countIssue(c);
		}
	}

	private void reportCDsNotPreviouslyReported(List<Concept> drugsReported) throws TermServerScriptException {
		List<Concept> allCDs = gl.getAllConcepts().stream()
				.filter(c -> c.getFsn().contains("(clinical drug)"))
				.toList();
		List<Concept> notReported = allCDs.stream()
				.filter(c -> !drugsReported.contains(c))
				.toList();
		for (Concept c : notReported) {
			report(SECONDARY_REPORT, c, SnomedUtils.getDescriptionsFull(c));
			countIssue(c);
		}
	}

	private boolean isMedicinalProduct(Concept c) {
		try {
			return c.getAncestors(RF2Constants.NOT_SET).contains(MEDICINAL_PRODUCT);
		} catch (TermServerScriptException e) {
			throw new IllegalStateException(e);
		}
	}

	private boolean containsTargetText(Description d, MatchingSet ms) {
		//To avoid matching within terms, we'll use the target text padded with spaces.
		//But this doesn't work if it's the last word, so also check for ending
		String term = d.getTerm();
		String sp_term = " " + term;
		return d.getTerm().contains(ms.targetTextSpSp)
				|| d.getTerm().endsWith(ms.targetTextSp)
				|| sp_term.contains(ms.targetTextSpSlash)
				|| term.equals(ms.targetText);
	}

	class MatchingSet {
		private String targetTextSpSp;
		private String targetTextSp;
		private String targetText;
		private String targetTextSpSlash;

		public MatchingSet(String targetText) {
			this.targetText = targetText;
			this.targetTextSpSp = " " + targetText + " ";
			this.targetTextSp = " " + targetText;
			this.targetTextSpSlash = " " + targetText + "/";
		}
	}

}
