package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

/**
 * NB See also ValidationInactivationsWithAssociations report to run general cross field validadtion between 
 * indicators and associations
 * 
 * Reports all concepts inactivated with the target inactivation reason, 
 * which also has an active association
 * Run a query to find inactive concepts using WAS A as the Association type for LIMITED, OUTDATED or ERRONEOUS inactivation reasons.
 */
public class InactivationAssocationReport extends TermServerScript implements ReportClass {
	
	public static String NEW_INACTIVATIONS_ONLY = "New Inactivations Only";
	String[] targetInactivationReasons = new String[] {SCTID_INACT_LIMITED, SCTID_INACT_OUTDATED, SCTID_INACT_ERRONEOUS};
	String[] targetAssocationRefsetIds = new String[] {SCTID_ASSOC_WAS_A_REFSETID};
	boolean newInactivationsOnly = false;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(SUB_HIERARCHY, ROOT_CONCEPT.toString());
		params.put(NEW_INACTIVATIONS_ONLY, "Y");
		TermServerReport.run(InactivationAssocationReport.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		super.init(run);
		newInactivationsOnly = run.getMandatoryParamValue(NEW_INACTIVATIONS_ONLY).equals("Y");
		additionalReportColumns = "inact_effective, inactivation_reason, assocation_effective, association";
	}

	@Override
	public Job getJob() {
		String[] parameterNames = new String[] { "SubHierarchy" };
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("List Inactivated Concepts")
				.withDescription("Lists all concepts for the specified inactivation reasons (TODO) along with the historical associations used.")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(new JobParameters(parameterNames))
				.withTag(INT)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		int rowsReported = 0;
		info ("Scanning all concepts...");
		for (Concept c : gl.getAllConcepts()) {
			//For a change we're interested in inactive concepts!
			if (!c.isActive()) {
				//Does this inactivated concept have one of our target inactivation reasons?
				for (String inactivationReasonSctId : targetInactivationReasons) {
					for (InactivationIndicatorEntry inactivationIndicator : c.getInactivationIndicatorEntries(ActiveState.ACTIVE)) {
						if (inactivationIndicator.getInactivationReasonId().equals(inactivationReasonSctId)) {
							//Now does the concept have one of our target historical associations?
							for (AssociationEntry histAssoc : c.getAssociations(ActiveState.ACTIVE)) {
								for (String targetAssocationRefsetId : targetAssocationRefsetIds) {
									if (histAssoc.getRefsetId().equals(targetAssocationRefsetId)) {
										report(c,inactivationIndicator, histAssoc);
										rowsReported++;
									}
								}
							}
						}
					}
				}
			}
		}
		addSummaryInformation("Concepts checked", gl.getAllConcepts().size());
		addSummaryInformation("Rows reported", rowsReported);
	}

	protected void report (Concept c, InactivationIndicatorEntry inact, AssociationEntry assoc) throws TermServerScriptException {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA_QUOTE + 
						inact.getEffectiveTime() + QUOTE_COMMA_QUOTE + 
						simpleName(inact.getInactivationReasonId()) + QUOTE_COMMA_QUOTE +
						assoc.getEffectiveTime() + QUOTE_COMMA_QUOTE + 
						simpleName(assoc.getRefsetId()) + " -> " + gl.getConcept(assoc.getTargetComponentId())+ QUOTE;
		writeToReportFile(line);
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
