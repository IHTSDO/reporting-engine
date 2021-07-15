package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

/**
 * Lists all active descriptions that have no acceptability
 */
public class DescriptionAnomalies extends TermServerReport implements ReportClass {
	
	private Map<String, Integer> issueSummaryMap = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(DescriptionAnomalies.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1PWtDYFfqLoUwk17HlFgNK648Mmra-1GA"; //General QA
		runStandAlone = false; //We need a proper path lookup for MS projects
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { 
				"Issue, Count",
				"SCTID, FSN, Semtag, Issue, Descendant Acceptable Description, Ancestor Preferred Description"
		};
		String[] tabNames = new String[] {	
				"Summary",
				"Issues"
		};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.GENERAL_QA))
				.withName("Description Anomalies")
				.withDescription("This report checks for a number of known possible issues such as: active descriptions with no acceptability and acceptable synonyms that are identical to the preferred term of an ancestor concept.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		Collection<Concept> concepts = StringUtils.isEmpty(subsetECL) ? gl.getAllConcepts() : findConcepts(subsetECL);
		for (Concept c : concepts) {
			if (c.isActive()) {
				checkUnacceptableDescriptions(c);
				checkAcceptableSynInAncestors(c);
			}
		}
		populateSummaryTab();
	}
	
	private void checkAcceptableSynInAncestors(Concept c) throws TermServerScriptException {
		String issueStr = "Acceptable synonym used as PT in ancestor";
		initialiseSummary(issueStr);
		Set<Concept> ancestors = c.getAncestors(NOT_SET);
		nextDescription:
		for (Description d : c.getDescriptions()) {
			if (d.isActive() && !d.isPreferred()) {
				String termLower = d.getTerm().toLowerCase();
				for (Concept ancestor : ancestors) {
					for (Description ad : ancestor.getDescriptions()) {
						if (ad.isActive() && ad.isPreferred() && 
								termLower.equals(ad.getTerm().toLowerCase())) {
							report(c, issueStr, d, ad);
							continue nextDescription;
						}
					}
				}
			}
		}
		
	}

	private void checkUnacceptableDescriptions(Concept c) throws TermServerScriptException {
		String issueStr = "Active term has no acceptability";
		initialiseSummary(issueStr);
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getLangRefsetEntries(ActiveState.ACTIVE).isEmpty()) {
				report(c, issueStr, d);
			}
		}
	}
	
	private void populateSummaryTab() throws TermServerScriptException {
		issueSummaryMap.entrySet().stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.forEach(e -> reportSafely (PRIMARY_REPORT, (Component)null, e.getKey(), e.getValue()));
		
		int total = issueSummaryMap.entrySet().stream()
				.map(e -> e.getValue())
				.collect(Collectors.summingInt(Integer::intValue));
		reportSafely (PRIMARY_REPORT, (Component)null, "TOTAL", total);
	}
	
	protected void initialiseSummary(String issue) {
		issueSummaryMap.merge(issue, 0, Integer::sum);
	}
	
	protected void report (Concept c, Object...details) throws TermServerScriptException {
		//First detail is the issue
		issueSummaryMap.merge(details[0].toString(), 1, Integer::sum);
		countIssue(c);
		super.report (SECONDARY_REPORT, c, details);
	}
}
