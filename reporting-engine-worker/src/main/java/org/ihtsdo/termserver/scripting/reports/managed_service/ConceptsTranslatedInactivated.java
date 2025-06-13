package org.ihtsdo.termserver.scripting.reports.managed_service;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.apache.commons.lang.StringUtils;

public class ConceptsTranslatedInactivated extends TermServerReport implements ReportClass {

	private String intEffectiveTime;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(INCLUDE_ALL_LEGACY_ISSUES, "N");
		TermServerScript.run(ConceptsTranslatedInactivated.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"); //Release QA Reports
		includeLegacyIssues = run.getParameters().getMandatoryBoolean(INCLUDE_ALL_LEGACY_ISSUES);
		subsetECL = run.getParamValue(ECL);
		super.init(run);
		if (project.getKey().equals("MAIN")) {
			throw new TermServerScriptException("Inactivated Translated Concepts report cannot be run against MAIN");
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		
		if (project.getMetadata() != null && project.getMetadata().getDependencyRelease() != null) {
			intEffectiveTime = project.getMetadata().getDependencyRelease();
		} else {
			throw new TermServerScriptException ("MS Project expected. " + project.getKey() + " is not configured with a dependency release effectiveTime");
		}
		
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Translation(s), Reason, Assoc Type, Assoc Value, Translation(s)"};
		String[] tabNames = new String[] {	
				"Concepts Inactivated"};
		super.postInit(tabNames, columnHeadings);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(INCLUDE_ALL_LEGACY_ISSUES)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(false)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.MS_RELEASE_VALIDATION))
				.withName("Inactivated Translated Concepts")
				.withDescription("This report lists translated International concepts which have been inactivated in the latest release along with historically associated replacements which may or may not hold translations.  The issue count here is the total number of concepts inactivated where the replacements require a translation.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(MS)
				.withTag(INT)
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
			boolean reported = false;
			InactivationIndicator i = c.getInactivationIndicator();
			String translations = getTranslations(c);
			for (AssociationEntry a : c.getAssociationEntries(ActiveState.ACTIVE, true)) {
				String assocType = SnomedUtils.getAssociationType(a);
				Concept assocValue = gl.getConcept(a.getTargetComponentId());
				String assocTranslations = getTranslations(assocValue);
				if (StringUtils.isEmpty(assocTranslations)) {
					countIssue(c);
				}
				report(c, translations, i, assocType, assocValue, assocTranslations);
				reported = true;
				
			}
			if (!reported) {
				report(c, i , "N/A");
			}
			
		}
	}
	
	private boolean inScope(Concept c) {
		//For this report we're interested in International Concepts inactivated
		//in the last (dependency) release which have translations in the target module
		return (!c.isActive() 
			&& (c.getEffectiveTime().equals(intEffectiveTime) || includeLegacyIssues)
			&& hasTranslation(c));
	}
	
	private boolean hasTranslation(Concept c) {
		return !StringUtils.isEmpty(getTranslations(c));
	}

	private String getTranslations(Concept c) {
		return c.getDescriptions(ActiveState.ACTIVE).stream()
			.filter(this::inScope)
			.map(Description::getTerm)
			.collect(Collectors.joining(", \n"));
	}

	private List<Concept> scopeAndSort(Collection<Concept> superSet) {
		//We're going to sort on top level hierarchy, then alphabetically
		//filter for appropriate scope at the same time - avoids problems with FSNs without semtags
		return superSet.stream()
		.filter (this::inScope)
		.sorted(SnomedUtils::compareSemTagFSN)
		.toList();
	}
	
}
