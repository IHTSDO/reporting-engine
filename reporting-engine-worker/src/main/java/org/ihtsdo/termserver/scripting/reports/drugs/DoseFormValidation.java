package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DoseFormValidation extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(DoseFormValidation.class);

	private List<Concept> allDrugs;
	private static final String RECENT_CHANGES_ONLY = "Recent Changes Only";
	
	private Concept[] doseFormTypes = new Concept[] {HAS_MANUFACTURED_DOSE_FORM};
	private Map<Concept, Boolean> acceptableMpfDoseForms = new HashMap<>();
	private Map<Concept, Boolean> acceptableCdDoseForms = new HashMap<>();	
	private Map<String, Integer> issueSummaryMap = new HashMap<>();
	
	private boolean isRecentlyTouchedConceptsOnly = false;
	private Set<Concept> recentlyTouchedConcepts;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(RECENT_CHANGES_ONLY, "true");
		TermServerScript.run(DoseFormValidation.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1wtB15Soo-qdvb0GHZke9o_SjFSL_fxL3");  //DRUGS/Validation
		additionalReportColumns = "FSN, SemTag, Issue, Data, Detail";  //DRUGS-267
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { "SCTID, FSN, Semtag, Issue, Details, Details, Details, Further Details",
				"Issue, Count"};
		String[] tabNames = new String[] {
				"Issues",
				"Summary"};
		allDrugs = SnomedUtils.sort(gl.getDescendantsCache().getDescendants(MEDICINAL_PRODUCT));
		populateAcceptableDoseFormMaps();
		
		super.postInit(tabNames, columnHeadings);
		
		if (jobRun.getParamBoolean(RECENT_CHANGES_ONLY)) {
			isRecentlyTouchedConceptsOnly = true;
			recentlyTouchedConcepts = SnomedUtils.getRecentlyTouchedConcepts(gl.getAllConcepts());
		}
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(RECENT_CHANGES_ONLY)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(true)
			.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.DRUGS))
				.withName("Dose Form Validation")
				.withDescription("This report checks CD and MPF Dose forms against a curated file of acceptable dose forms.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withTag(INT)
				.withParameters(params)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		validateDrugsModeling();
		populateSummaryTab(SECONDARY_REPORT);
		LOGGER.info("Summary tab complete, all done.");
	}

	private void validateDrugsModeling() throws TermServerScriptException {
		double conceptsConsidered = 0;
		//for (Concept c : Collections.singleton(gl.getConcept("776935006"))) {
		for (Concept c : allDrugs) {
			if (isRecentlyTouchedConceptsOnly && !recentlyTouchedConcepts.contains(c)) {
				continue;
			}
			
			DrugUtils.setConceptType(c);
			
			double percComplete = (conceptsConsidered++/allDrugs.size())*100;
			if (conceptsConsidered%4000==0) {
				LOGGER.info("Percentage Complete " + (int)percComplete);
			}
			
			//DRUGS-784
			if (isCD(c) || isMPF(c)) {
				validateAcceptableDoseForm(c);
			}
		}
		LOGGER.info("Drugs validation complete");
	}
	

	private void validateAcceptableDoseForm(Concept c) throws TermServerScriptException {
		String issueStr1 = c.getConceptType() + " uses unlisted dose form";
		String issueStr2 = c.getConceptType() + " uses unacceptable dose form";
		initialiseSummary(issueStr1);
		initialiseSummary(issueStr2);
		
		Map<Concept, Boolean> acceptableDoseForms;
		if (isMPF(c)) {
			acceptableDoseForms = acceptableMpfDoseForms;
		} else {
			acceptableDoseForms = acceptableCdDoseForms;
		}
		
		acceptableDoseForms.put(gl.getConcept("785898006 |Conventional release solution for irrigation (dose form)|"), Boolean.TRUE);
		acceptableDoseForms.put(gl.getConcept("785910004 |Prolonged-release intralesional implant (dose form)|"), Boolean.TRUE);
		
		Concept thisDoseForm = SnomedUtils.getTarget(c, doseFormTypes, UNGROUPED, CharacteristicType.INFERRED_RELATIONSHIP);
		//Is this dose form acceptable?
		if (acceptableDoseForms.containsKey(thisDoseForm)) {
			if (acceptableDoseForms.get(thisDoseForm).equals(Boolean.FALSE)) {
				report(c, issueStr2, thisDoseForm);
			}
		} else {
			report(c, issueStr1, thisDoseForm);
		}
	}

	@Override
	protected boolean report(Concept c, Object...details) throws TermServerScriptException {
		//First detail is the issue
		issueSummaryMap.merge(details[0].toString(), 1, Integer::sum);
		countIssue(c);
		return super.report(PRIMARY_REPORT, c, details);
	}
	
	private void populateAcceptableDoseFormMaps() throws TermServerScriptException {
		String fileName = "resources/acceptable_dose_forms.tsv";
		LOGGER.debug("Loading {}", fileName);
		try {
			List<String> lines = Files.readLines(new File(fileName), StandardCharsets.UTF_8);
			boolean isHeader = true;
			for (String line : lines) {
				String[] items = line.split(TAB);
				if (!isHeader) {
					Concept c = gl.getConcept(items[0]);
					acceptableMpfDoseForms.put(c, items[2].equals("yes"));
					acceptableCdDoseForms.put(c, items[3].equals("yes"));
				} else {
					isHeader = false;
				}
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to read " + fileName, e);
		}
	}
	
	private boolean isMPF(Concept concept) {
		return concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM) || 
				concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM_ONLY);
	}
	
	private boolean isCD(Concept concept) {
		return concept.getConceptType().equals(ConceptType.CLINICAL_DRUG);
	}
	
}
