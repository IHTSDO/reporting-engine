package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;


public class CharacterConversionFixUp extends BatchFix implements ScriptConstants, ReportClass {
	
	enum KnownCharacterConversions { ß_TO_SS };
	
	private static final String CHARACTER_CONVERSION = "Character Conversion";
	private String match;
	private String replace;
	private KnownCharacterConversions currentConversionType;
	
	public CharacterConversionFixUp() {
		super(null);
	}
	
	protected CharacterConversionFixUp(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		CharacterConversionFixUp fix = new CharacterConversionFixUp(null);
		try {
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
			fix.getArchiveManager().setRunIntegrityChecks(false);
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(CHARACTER_CONVERSION).withType(JobParameter.Type.DROPDOWN)
					.withOptions(KnownCharacterConversions.ß_TO_SS.name()).withDefaultValue(KnownCharacterConversions.ß_TO_SS.name()).withMandatory()
				.add(DRY_RUN).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true).withMandatory()
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.DEVOPS))
				.withName("Character Conversion Fix-up")
				.withDescription("This report creates a single task which replaces descriptions containing the selected ")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withTag(MS).withTag(INT)
				.withParameters(params)
				.build();
	}
	
	@Override
	protected void preInit() throws TermServerScriptException {
		ReportSheetManager.targetFolderId="1MDEvSDJ1EW2Piy2AfaO1UIEPAGhGbG4j";  //User Initiated Batch Updates
		populateEditPanel = true;
		populateTaskDescription = true;
		selfDetermining = true;
		classifyTasks = false;

		if (projectName.length() != 2) {
			throw new TermServerScriptException("This script should only be run against a country's master project eg 'CH'");
		}

		JobRun jobRun = getJobRun();
		dryRun = jobRun.getParamBoolean(DRY_RUN);
		taskSize = Integer.MAX_VALUE;
		String characterConversionStr = jobRun.getMandatoryParamValue(CHARACTER_CONVERSION);
		currentConversionType = KnownCharacterConversions.valueOf(characterConversionStr);
		if (currentConversionType == KnownCharacterConversions.ß_TO_SS) {
			match = "ß";
			replace = "ss";
		} else {
			throw new TermServerScriptException("Unknown Character Conversion specified: " + characterConversionStr);
		}
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = replaceTerms(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	private int replaceTerms(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (inScope(d) && d.getTerm().contains(match)) {
				String newTerm = d.getTerm().replace(match, replace);
				replaceDescription(t, c, d, newTerm, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, false, "");
				changesMade++;
				countIssue(c);
			}
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		
		List<Concept> allPotential = SnomedUtils.sort(gl.getAllConcepts());
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		List<DescriptionType> descTypes = new ArrayList<>();
		descTypes.add(DescriptionType.FSN);
		descTypes.add(DescriptionType.SYNONYM);
		info("Identifying concepts to process");
		for (Concept c : allPotential) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE, descTypes)) {
				if (inScope(d) && d.getTerm().contains(match)) {
					allAffected.add(c);
					break;
				}
			}
		}
		info ("Identified " + allAffected.size() + " concepts to process");
		return new ArrayList<Component>(allAffected);
	}
}
