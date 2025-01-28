package org.ihtsdo.termserver.scripting.reports;

import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lists all active descriptions that have no acceptability
 */
public class ConceptsMovingElsewhere extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConceptsMovingElsewhere.class);

	private Map<Concept, String> knownNamespaceExtensionMap = new HashMap<>();
	private static final String THIS_RELEASE = "This Release";
	private Map<Concept, Integer> moveSummaryMap = new HashMap<>();
	private boolean loadPublishedPackage;
	private String thisEffectiveTime = null;
	private int missingIndicatorCount = 0;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(THIS_RELEASE, "xSnomedCT_InternationalRF2_BETA_20210731T120000Z.zip");
		TermServerScript.run(ConceptsMovingElsewhere.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"); //Release Validation
		runStandAlone = false; //We need a proper path lookup for MS projects
		
		super.init(run);
		
		if (!StringUtils.isEmpty(run.getParamValue(THIS_RELEASE))) {
			loadPublishedPackage = true;
			String projectKey = run.getParamValue(THIS_RELEASE);
			setProject(new Project(projectKey));
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { 
				"NameSpace, Extension, Count",
				"SCTID, FSN, Semtag, Inactivated, Destination, Destination Namespace"
		};
		String[] tabNames = new String[] {	
				"Summary",
				"Concepts Moved"
		};
		super.postInit(tabNames, columnHeadings);
		knownNamespaceExtensionMap.put(new Concept("416516009", "Extension Namespace {1000009}"), "Veterinary Extension");
		knownNamespaceExtensionMap.put(new Concept("370137002", "Extension Namespace {1000000}"), "UK Extension");
		if (loadPublishedPackage) {
			thisEffectiveTime = gl.getCurrentEffectiveTime();
			LOGGER.info("Detected this effective time as {}", thisEffectiveTime);
		}
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(THIS_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Concepts Moved Elsewhere")
				.withDescription("This report lists concepts moved or moving to another extension.  Leave the 'This Package' field blank unless querying a published build available in S3.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		Collection<Concept> concepts = StringUtils.isEmpty(subsetECL) ? gl.getAllConcepts() : findConcepts(subsetECL);
		for (Concept c : concepts) {
			if (!c.isActiveSafely() && inScope(c)) {
				Set<String> movedToSet = c.getAssociationTargets().getMovedTo();
				if (movedToSet.isEmpty() || movedToSet.size() > 1) {
					report(c, "Unexpected number of movedTo targets: " + movedToSet.size());
					continue;
				}
				Concept movedTo = gl.getConcept(movedToSet.iterator().next());
				//Do we know where we're moving to?
				String extension = knownNamespaceExtensionMap.get(movedTo);
				report(c, extension, movedTo);
			}
		}
		populateSummaryTab();
		LOGGER.warn("{} inactive concepts have no inactivation indicator", missingIndicatorCount);
	}
	
	private boolean inScope(Concept c) {
		if (c.getInactivationIndicator() == null) {
			LOGGER.warn("{} has no inactivation indicator", c);
			missingIndicatorCount++;
			return false;
		}
		return  c.getInactivationIndicator().equals(InactivationIndicator.MOVED_ELSEWHERE) &&
				(StringUtils.isEmpty(c.getEffectiveTime()) ||
				(loadPublishedPackage && c.getEffectiveTime().contentEquals(thisEffectiveTime)));
	}
	
	private void populateSummaryTab() {
		moveSummaryMap.entrySet().stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.forEach(e -> reportSafely (PRIMARY_REPORT, (Component)null, e.getKey(), knownNamespaceExtensionMap.get(e.getKey()), e.getValue()));
		
		int total = moveSummaryMap.values().stream()
				.mapToInt(Integer::intValue)
				.sum();
		reportSafely (PRIMARY_REPORT, (Component)null, "TOTAL", "", total);
	}
	

	
	protected void report(Concept c, String namespace, Concept movedTo) throws TermServerScriptException {
		//2nd detail is the issue
		moveSummaryMap.merge(movedTo, 1, Integer::sum);
		countIssue(c);
		super.report(SECONDARY_REPORT, c, c.getEffectiveTime(), namespace, movedTo);
	}
}
