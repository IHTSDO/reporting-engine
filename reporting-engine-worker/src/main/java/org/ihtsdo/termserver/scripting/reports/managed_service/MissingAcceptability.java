package org.ihtsdo.termserver.scripting.reports.managed_service;

import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.scheduler.domain.JobParameter.Type;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * RP-565
 */
public class MissingAcceptability extends TermServerReport implements ReportClass {

	private static final String INCLUDE_INACTIVE_CONCEPTS = "Include inactive concepts";
	private static final String TRACK_DIALECT = "Track dialect";
	private static final String EXPECT_FSN_TRANSLATION = "Expect FSN translation";

	private Map<String, String> defaultLangRefsets = null;
	private boolean includeInactiveConcepts = false;
	private String trackDialect = null;

	private boolean expectFsnTranslation = false;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "< 71388002 |Procedure| OR < 404684003 |Clinical finding|");
		//params.put(ECL, "*")
		params.put(INCLUDE_INACTIVE_CONCEPTS, "false");
		//params.put(TRACK_DIALECT, "GB")
		TermServerScript.run(MissingAcceptability.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1mvrO8P3n94YmNqlWZkPJirmFKaFUnE0o"); //Managed Service
		subsetECL = run.getParamValue(ECL);
		includeInactiveConcepts = run.getParamBoolean(INCLUDE_INACTIVE_CONCEPTS);
		expectFsnTranslation = run.getParamBoolean(EXPECT_FSN_TRANSLATION);
		
		String trackDialectStr = run.getParamValue(TRACK_DIALECT);
		if (!StringUtils.isEmpty(trackDialectStr)) {
			switch(trackDialectStr) {
				case "US" : trackDialect = US_ENG_LANG_REFSET;
					break;
				case "GB" : trackDialect = GB_ENG_LANG_REFSET;
					break;
				default : throw new IllegalArgumentException("Unable to identify refsetId from '" + trackDialectStr + "'");
			}
		}
		
		super.init(run);
		if (project.getKey().equals("MAIN")) {
			throw new TermServerScriptException("Missing Acceptability Report cannot be run against MAIN");
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		defaultLangRefsets = project.getMetadata().getLangLangRefsetMapping();
		String[] columnHeadings = new String[] {"SCTID, FSN, SemTag, Descriptions, Issue"};
		String[] tabNames = new String[] {"Missing LangRefset Entry"};
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(Type.ECL)
				.add(INCLUDE_INACTIVE_CONCEPTS).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.add(EXPECT_FSN_TRANSLATION).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.add(TRACK_DIALECT).withType(Type.DROPDOWN).withOptions("US", "GB")
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.MS_RELEASE_VALIDATION))
				.withName("Terms Missing Acceptability")
				.withDescription("This reports lists all descriptions which are missing a lang refset acceptability in the default language reference set." +
						"Note that specifying a dialect to track (GB or US) only makes sense for countries that add 'en' descriptions to their own language reference sets (eg NZ, AU, IE)")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(MS)
				.withTag(INT)
				.withExpectedDuration(30)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		Collection<Concept> conceptsOfInterest;
		if (subsetECL != null && !subsetECL.isEmpty()) {
			conceptsOfInterest = findConcepts(subsetECL);
		} else {
			conceptsOfInterest = gl.getAllConcepts();
		}
		
		for (Concept c : scopeAndSort(conceptsOfInterest)) {
			//Are we working with a single default langrefset, or (like BE) a set of them?
			for (Map.Entry<String,String> entry : defaultLangRefsets.entrySet()) {
				checkConceptForMissingAcceptability(c, entry.getValue(), entry.getKey());
			}
		}
	}
	
	private void checkConceptForMissingAcceptability(Concept c, String checkThisLangRefset, String refsetName) throws TermServerScriptException {

		DescriptionAnalysisResults descriptionAnalysisResults = new DescriptionAnalysisResults(c);
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			analyseDescription(d, descriptionAnalysisResults, checkThisLangRefset);
		}
		reportIssues(descriptionAnalysisResults, refsetName);
	}

	private void analyseDescription(Description d, DescriptionAnalysisResults descriptionAnalysisResults, String checkThisLangRefset) throws TermServerScriptException {
		//Are we considering translations of FSNs?
		if (!expectFsnTranslation && d.getType().equals(DescriptionType.FSN)) {
			return;
		}
		//Are we tracking some existing English Dialect?
		if (trackDialect != null
				&& d.getAcceptability(trackDialect) != null
				&& d.getAcceptability(checkThisLangRefset) == null) {
			if (!descriptionAnalysisResults.descriptionsToReport.isEmpty()) {
				descriptionAnalysisResults.descriptionsToReport.append("\n");
			}
			descriptionAnalysisResults.descriptionsToReport.append(d);
		} else {
			if (d.hasAcceptability(Acceptability.BOTH, checkThisLangRefset)) {
				descriptionAnalysisResults.anyTermDetected = true;
			}
			//Have we detected the preferred term?
			if (d.hasAcceptability(Acceptability.PREFERRED, checkThisLangRefset)) {
				descriptionAnalysisResults.preferredTermDetected = true;
			}
		}
	}

	private void reportIssues(DescriptionAnalysisResults descriptionAnalysisResults, String refsetName) throws TermServerScriptException {
		if (!descriptionAnalysisResults.descriptionsToReport.isEmpty()) {
			report(descriptionAnalysisResults.c, descriptionAnalysisResults.descriptionsToReport, "Descriptions have acceptability in tracked dialect, but not in the '" + refsetName + "' language reference set");
			countIssue(descriptionAnalysisResults.c);
		} else if (!descriptionAnalysisResults.anyTermDetected) {
			report(descriptionAnalysisResults.c, "", "Concept has no term with any acceptability in the '" + refsetName + "' language reference set.");
			countIssue(descriptionAnalysisResults.c);
		}else if (!descriptionAnalysisResults.preferredTermDetected) {
			report(descriptionAnalysisResults.c, "", "Concept has no term marked as preferred in the '" + refsetName + "' language reference set.");
			countIssue(descriptionAnalysisResults.c);
		}
	}

	private List<Concept> scopeAndSort(Collection<Concept> superSet) {
		return superSet.stream()
		.filter(this::inScope)
		.sorted(SnomedUtils::compareSemTagFSN)
		.toList();
	}
	
	private boolean inScope(Concept c) {
		return includeInactiveConcepts || c.isActive();
	}

	class DescriptionAnalysisResults {
		DescriptionAnalysisResults(Concept c) {
			this.c = c;
		}
		Concept c;
		StringBuilder descriptionsToReport = new StringBuilder();
		boolean preferredTermDetected = false;
		boolean anyTermDetected = false;
	}

}
