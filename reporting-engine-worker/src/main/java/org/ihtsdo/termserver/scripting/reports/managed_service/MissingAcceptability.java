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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RP-565
 */
public class MissingAcceptability extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(MissingAcceptability.class);

	private static final String INCLUDE_INACTIVE_CONCEPTS = "Include inactive concepts";
	private static final String TRACK_DIALECT = "Track dialect";

	private String defaultLangRefset = null;
	private boolean includeInactiveConcepts = false;
	private String trackDialect = null;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(INCLUDE_INACTIVE_CONCEPTS, "false");
		params.put(TRACK_DIALECT, "GB");
		TermServerScript.run(MissingAcceptability.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1mvrO8P3n94YmNqlWZkPJirmFKaFUnE0o"); //Managed Service
		subsetECL = run.getParamValue(ECL);
		includeInactiveConcepts = run.getParamBoolean(INCLUDE_INACTIVE_CONCEPTS);
		
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
		try {
			defaultLangRefset = project.getMetadata().getDefaultLangRefset();
		} catch (IllegalStateException e) {
			LOGGER.error("Failed to determine default LangRefset.  Assuming en-us.",e);
			defaultLangRefset = US_ENG_LANG_REFSET;
		}
		String[] columnHeadings = new String[] {"SCTID, FSN, SemTag, Descriptions"};
		String[] tabNames = new String[] {"Missing LangRefset Entry"};
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(Type.ECL)
				.add(INCLUDE_INACTIVE_CONCEPTS).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.add(TRACK_DIALECT).withType(Type.DROPDOWN).withOptions("US", "GB")
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.MS_RELEASE_VALIDATION))
				.withName("Terms Missing Acceptability")
				.withDescription("This reports lists all descriptions which are missing a lang refset acceptability in the default language reference set.")
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
			checkConceptForMissingAcceptability(c);
		}
	}
	
	private void checkConceptForMissingAcceptability(Concept c) throws TermServerScriptException {
		StringBuilder descriptionsToReport = new StringBuilder();
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (!inScope(d)) {
				continue;
			}
			//Are we tracking some existing English Dialect?
			if ((trackDialect == null || d.getAcceptability(trackDialect) != null) 
				&& d.getAcceptability(defaultLangRefset) == null) {
				if (!descriptionsToReport.isEmpty()) {
					descriptionsToReport.append("\n");
				}
				descriptionsToReport.append(d);
			}
		}
		
		if (!descriptionsToReport.isEmpty()) {
			report(c, descriptionsToReport);
			countIssue(c);
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

}
