package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.springframework.util.StringUtils;

public class ConceptsTranslatedInactivated extends TermServerReport implements ReportClass {
	
	private String intEffectiveTime;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		TermServerReport.run(ConceptsTranslatedInactivated.class, args, new HashMap<>());
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA Reports
		subHierarchyECL = run.getParamValue(ECL);
		super.init(run);
		if (project.getKey().equals("MAIN")) {
			throw new TermServerScriptException("Inactivated Translated Concepts report cannot be run against MAIN");
		}
	}
	
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
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("Inactivated Translated Concepts")
				.withDescription("This report lists translated International concepts which have been inactivated in the latest release along with historically associated replacements which may or may not hold translations.  The issue count here is the total number of concepts inactivated where the replacements require a translation.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(MS)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		Collection<Concept> conceptsOfInterest;
		if (subHierarchyECL != null && !subHierarchyECL.isEmpty()) {
			conceptsOfInterest = findConcepts(subHierarchyECL);
		} else {
			conceptsOfInterest = gl.getAllConcepts();
		}
		
		for (Concept c : scopeAndSort(conceptsOfInterest)) {
			boolean reported = false;
			InactivationIndicator i = c.getInactivationIndicator();
			String translations = getTranslations(c);
			for (AssociationEntry a : c.getAssociations(ActiveState.ACTIVE, true)) {
				String assocType = SnomedUtils.getAssociationType(a);
				Concept assocValue = gl.getConcept(a.getTargetComponentId());
				String assocTranslations = getTranslations(assocValue);
				if (StringUtils.isEmpty(assocTranslations)) {
					countIssue(c);
				}
				report (c, translations, i, assocType, assocValue, assocTranslations);
				reported = true;
				
			}
			if (!reported) {
				report (c, i , "N/A");
			}
			
		}
	}
	
	private boolean inScope(Concept c) {
		//For this report we're interested in International Concepts inactivated
		//in the last (dependency) release which have translations in the target module
		if (!c.isActive() && c.getEffectiveTime().equals(intEffectiveTime) 
			&& hasTranslation(c)) {
			return true;
		}
		return false;
	}
	
	private boolean hasTranslation(Concept c) {
		return !StringUtils.isEmpty(getTranslations(c));
	}

	private String getTranslations(Concept c) {
		return c.getDescriptions(ActiveState.ACTIVE).stream()
			.filter(d -> inScope(d))
			.map(d -> d.getTerm())
			.collect(Collectors.joining(", \n"));
	}

	private List<Concept> scopeAndSort(Collection<Concept> superSet) {
		//We're going to sort on top level hierarchy, then alphabetically
		//filter for appropriate scope at the same time - avoids problems with FSNs without semtags
		return superSet.stream()
		.filter (c -> inScope(c))
		.sorted((c1, c2) -> compareSemTagFSN(c1,c2))
		.collect(Collectors.toList());
	}

	private int compareSemTagFSN(Concept c1, Concept c2) {
		String[] fsnSemTag1 = SnomedUtils.deconstructFSN(c1.getFsn());
		String[] fsnSemTag2 = SnomedUtils.deconstructFSN(c2.getFsn());
		
		if (fsnSemTag1[1] == null || fsnSemTag2[1] == null) {
			System.out.println("FSN Encountered without semtag: " + fsnSemTag1[1] == null ? c1 : c2);
			return fsnSemTag1[0].compareTo(fsnSemTag2[0]);
		} 
		
		if (fsnSemTag1[1].equals(fsnSemTag2[1])) {
			return fsnSemTag1[0].compareTo(fsnSemTag2[0]);
		}
		return fsnSemTag1[1].compareTo(fsnSemTag2[1]);
	}
	
}
