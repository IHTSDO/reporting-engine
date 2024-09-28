package org.ihtsdo.termserver.scripting.reports;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

/**
 * Lists all active descriptions that have no acceptability
 */

public class DescriptionAnomalies extends TermServerReport implements ReportClass {

	private Map<String, Integer> descriptionIssueSummaryMap = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(DescriptionAnomalies.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		runStandAlone = false; //We need a proper path lookup for MS projects
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { 
				"Issue, Count",
				"SCTID, FSN, Semtag, Issue, Descendant Acceptable Description, Ancestor Preferred Description"
		};
		String[] tabNames = new String[] {	
				"Summary",
				"Issues"
		};
		super.postInit(GFOLDER_QI, tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Description Anomalies")
				.withDescription("This report checks for a number of known possible issues such as: active descriptions with no acceptability and acceptable synonyms that are identical to the preferred term of an ancestor concept.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		Collection<Concept> concepts = StringUtils.isEmpty(subsetECL) ? gl.getAllConcepts() : findConcepts(subsetECL);
		for (Concept c : concepts) {
			if (c.isActiveSafely()) {
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
		for (Description d : c.getDescriptions()) {
			if (d.isActiveSafely() && !d.isPreferred()) {
				checkAncestorSynonyms(c, d, ancestors, issueStr);
			}
		}
	}

	private void checkAncestorSynonyms(Concept c, Description d, Set<Concept> ancestors, String issueStr) throws TermServerScriptException {
		String termLower = d.getTerm().toLowerCase();
		for (Concept ancestor : ancestors) {
			for (Description ad : ancestor.getDescriptions()) {
				if (ad.isActiveSafely() && ad.isPreferred() &&
						termLower.equals(ad.getTerm().toLowerCase())) {
					report(c, issueStr, d, ad);
					return;
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
	
	private void populateSummaryTab() {
		descriptionIssueSummaryMap.entrySet().stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.forEach(e -> reportSafely (PRIMARY_REPORT, (Component)null, e.getKey(), e.getValue()));
		
		int total = descriptionIssueSummaryMap.entrySet().stream()
				.map(Map.Entry::getValue)
				.collect(Collectors.summingInt(Integer::intValue));
		reportSafely (PRIMARY_REPORT, (Component)null, "TOTAL", total);
	}

	@Override
	protected void initialiseSummary(String issue) {
		descriptionIssueSummaryMap.merge(issue, 0, Integer::sum);
	}

	@Override
	public boolean report(Concept c, Object...details) throws TermServerScriptException {
		//First detail is the issue
		descriptionIssueSummaryMap.merge(details[0].toString(), 1, Integer::sum);
		countIssue(c);
		return super.report(SECONDARY_REPORT, c, details);
	}
}
