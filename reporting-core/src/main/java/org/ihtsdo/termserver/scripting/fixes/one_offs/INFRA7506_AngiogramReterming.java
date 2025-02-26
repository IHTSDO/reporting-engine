package org.ihtsdo.termserver.scripting.fixes.one_offs;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

public class INFRA7506_AngiogramReterming extends BatchFix {

	String ecl = "<< 71388002 |Procedure (procedure)|";
	Concept[] types = new Concept[] { FINDING_SITE, PROCEDURE_SITE, PROCEDURE_SITE_DIRECT, PROCEDURE_SITE_INDIRECT };
	String[] targetTexts = new String[] { "angiography", "angiogram", "arteriography", "arteriogram"};
	String[] exceptionTexts = new String[] { "anesthesia", "follow-up", "cholangiography", "cholangiogram", "lymphangiography", "lymphangiogram"};
	
	Map<String, String> termTranslationPT = new HashMap<>();
	Map<String, String> termTranslationAll = new HashMap<>();
	Map<String, String> removeWhenPresent = new HashMap<>();
	
	protected INFRA7506_AngiogramReterming(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		INFRA7506_AngiogramReterming fix = new INFRA7506_AngiogramReterming(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.inputFileHasHeaderRow = true;
			fix.expectNullConcepts = true;
			fix.validateConceptOnUpdate = true;
			fix.init(args);
			fix.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	public void postInit() throws TermServerScriptException {
		termTranslationPT.put("arteriogram", "arteriography");
		termTranslationAll.put("MRI", "MR");
		termTranslationAll.put("arteriography", "angiography");
		removeWhenPresent.put("magnetic resonance", "imaging");
		String[] columnHeadings = new String[] {
				"Task, Desc, SCTID,FSN,ConceptType,Severity,Action, Detail,Details,Sites",
				"Id, FSN, SemTag, Has 'Artery' Site, Sites, Detail"};
		String[] tabNames = new String[] {
				"Processed",
				"Not Processed"};
		super.postInit(tabNames, columnHeadings, false);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = modifyDescriptions(task, loadedConcept);
			if (changesMade > 0) {
				checkSpaces(loadedConcept);
				updateConcept(task, loadedConcept, info);
			} 
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	private void checkSpaces(Concept c) {
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			d.setTerm(d.getTerm().trim().replaceAll("  ", " "));
		}
	}

	private int modifyDescriptions(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		
		//Does our finding site include some sort of artery?
		boolean hasArtery = false;
		Set<Concept> sites = SnomedUtils.getTargets(c, types, CharacteristicType.INFERRED_RELATIONSHIP);
		String sitesStr = sites.stream()
				.map(f -> f.getFsn())
				.collect(Collectors.joining(",\n"));
		String descriptionsStr = c.getDescriptions(ActiveState.ACTIVE)
				.stream().map(d -> d.toString())
				.collect(Collectors.joining(",\n"));
		for (Concept findingSite : sites) {
			//Need our local copy of this concept
			findingSite = gl.getConcept(findingSite.getId());
			if (identifiesAsArtery(findingSite.getFSNDescription())) {
				hasArtery = true;
				break;
			}
		}
		
		//If we're missing an "of", it might be that we're X <type of procedure> when we want to be
		//<type of procedure> of X
		if (!c.getFsn().contains(" of ")) {
			changesMade += shiftXOfIfRequired(t, c, sites, sitesStr, hasArtery);
		}
		
		changesMade += removeIfRequired(t, c);
		changesMade += doTranslationIfRequired(t, c, termTranslationPT, true, sitesStr);
		changesMade += doTranslationIfRequired(t, c, termTranslationAll, false, sitesStr);
		if (hasArtery) {
			changesMade += ensureArteriographySynonym(t, c);
		}
		
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (!hasArtery && identifiesAsArtery(d) ) {
				if (d.isPreferred()) {
					//report (t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Description contains 'arteri' but is preferred. Advise?", d);
					//Only report in first pass
					if (t == null) {
						report (SECONDARY_REPORT, c, hasArtery?"Y":"N", sitesStr, "Description contains 'arter(i/y)|aorta' without artery site, but is preferred?", descriptionsStr);
					}
				} else {
					if (t != null) {
						report (t, c, Severity.LOW, ReportActionType.DESCRIPTION_INACTIVATED, "Description contains 'arter(i/y)|aorta' without artery finding site", descriptionsStr, sitesStr);
						removeDescription(t, c, d, InactivationIndicator.INAPPROPRIATE);
					}
					changesMade++;
				}
			} 
		}
		
		//Only report in first pass
		if (t == null && changesMade == NO_CHANGES_MADE) {
			report (SECONDARY_REPORT, c,  hasArtery?"Y":"N", sitesStr, "No issues detected", descriptionsStr);
		}
		return changesMade;
	}
	
	private int shiftXOfIfRequired(Task t, Concept c, Set<Concept> sites, String sitesStr, boolean hasArtery) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			String newTerm = null;
			boolean isFsn = d.getType().equals(DescriptionType.FSN);
			String term = d.getTerm();
			String semTag = "";
			if (isFsn) {
				String[] parts = SnomedUtils.deconstructFSN(term);
				term = parts[0].trim();
				semTag = " " + parts[1];
			}
			if (d.isPreferred()) {
				String site = null;
				if (term.endsWith("angiography")) {
					int cutPoint = term.length() - "angiography".length();
					site = term.substring(0, cutPoint).toLowerCase();
					newTerm = "Angiography of " + site;
				}
				
				if (term.endsWith("arteriography")) {
					int cutPoint = term.length() - "arteriography".length();
					site = term.substring(0, cutPoint).toLowerCase();
					newTerm = "Angiography of " + site;
				}
				
				if (term.endsWith("arteriogram")) {
					int cutPoint = term.length() - "arteriogram".length();
					site = term.substring(0, cutPoint).toLowerCase();
					newTerm = "Angiography of " + site;
				}
				
				if (newTerm != null) {
					if (sites.size() == 1) {
						String betterSite = sites.iterator().next().getPreferredSynonym();
						if (betterSite.startsWith("Structure of")) {
							betterSite = betterSite.substring("Structure of ".length());
							newTerm = newTerm.replace(site, betterSite);
						}
					} else if (hasArtery & !newTerm.contains("arter")) {
						//Also matches arteries
						newTerm += " artery";
					}
					newTerm += semTag;
					newTerm = swapAround(newTerm, new String[] {"Isotope ", "Intravenous "});
					newTerm = newTerm.replaceAll("  ", " ");
					if (t != null) {
						replaceDescription(t, c, d, newTerm, InactivationIndicator.ERRONEOUS, true, sitesStr);
					}
					changesMade++;
				}
			}
		}
		return changesMade;
	}

	private String swapAround(String newTerm, String[] swaps) {
		for (String swap : swaps) {
			if (newTerm.contains(swap)) {
				String initialLower = StringUtils.decapitalizeFirstLetter(newTerm);
				newTerm = swap + initialLower.replace(swap, "");
			}
		}
		return newTerm;
	}

	private int doTranslationIfRequired(Task t, Concept c, Map<String, String> translations, boolean onlyPreferred, String sitesStr) throws TermServerScriptException {
		int changesMade = 0;
		for (Entry<String, String> entry : translations.entrySet()) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.isPreferred()) {
					String term = d.getTerm().toLowerCase();
					if (term.contains(entry.getKey())) {
						String newTerm = term.replace(entry.getKey(), entry.getValue());
						newTerm = sortMrAcronym(newTerm);
						newTerm = StringUtils.capitalizeFirstLetter(newTerm);
						newTerm = newTerm.replaceAll("Ct ", "CT ");
						if (t != null) {
							replaceDescription(t, c, d, newTerm, InactivationIndicator.ERRONEOUS, true, sitesStr);
						}
						changesMade++;
					}
				}
			}
		}
		return changesMade;
	}

	private int removeIfRequired(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Entry<String, String> entry : removeWhenPresent.entrySet()) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				String term = d.getTerm().toLowerCase();
				if (term.contains(entry.getKey()) && term.contains(entry.getValue())) {
					String newTerm = term.replace(entry.getValue() + " ", "");
					newTerm = StringUtils.capitalize(newTerm);
					newTerm = sortMrAcronym(newTerm);
					if (t != null) {
						replaceDescription(t, c, d, newTerm, InactivationIndicator.ERRONEOUS);
					}
					changesMade++;
				}
			}
		}
		return changesMade;
	}

	private String sortMrAcronym(String term) {
		if (term.contains("mri ")) {
			term = term.replaceAll("mri ", "MR ");
		}
		
		if (term.contains("mr ")) {
			term = term.replaceAll("mr ", "MR");
		}
		
		if (term.contains("(mri)")) {
			term = term.replaceAll("\\(mri\\)", "(MR)");
		}
		return term;
	}

	private int ensureArteriographySynonym(Task t, Concept c) throws TermServerScriptException {
		//Take the US angiography  preferred term, and ensure we have an arteriography synonym
		String usPT = c.getPreferredSynonym(US_ENG_LANG_REFSET).getTerm();
		String artSynStr = usPT.replace("angiography", "arteriography").replace("Angiography", "Arteriography");
		artSynStr = StringUtils.capitalizeFirstLetter(artSynStr);
		if (c.getDescription(artSynStr, ActiveState.ACTIVE) == null) {
			Description artSyn = Description.withDefaults(artSynStr, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
			if (t != null) {
				report (t, c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, artSyn);
				c.addDescription(artSyn);
			}
			return CHANGE_MADE;
		}
		return NO_CHANGES_MADE;
	}
	

	private boolean identifiesAsArtery(Description d) {
		String term = d.getTerm().toLowerCase();
		
		//If the artery thing comes after a 'for' like in 
		// Computed tomography angiography with contrast for transcatheter aortic valve implantation planning (procedure)
		//ignore anything after " for ";
		int cutPoint = term.indexOf(" for ");
		if (cutPoint != NOT_FOUND) {
			term = term.substring(0, cutPoint);
		}
		
		return (term.contains("arteri") 
				|| term.contains("artery")
				|| term.contains("aorta")
				|| term.contains("aortic"));
	}
	/*
	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return Collections.singletonList(gl.getConcept("352689002"));
	}
*/
	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> process = new ArrayList<>();
		List<Concept> conceptsSorted = PROCEDURE.getDescendants(NOT_SET).stream()
				.sorted(Comparator.comparing(Concept::getSemTag)
						.thenComparing(Comparator.comparing(Concept::getFsn)))
				.collect(Collectors.toList());
		
		for (Concept c : conceptsSorted) {
			if (containsText(targetTexts,c) && !containsText(exceptionTexts,c)) {
				if (c.getFsn().contains("CT") || c.getFsn().contains("MRI")) {
					report((Task)null, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "FSN contains acronym.");
				}
				if (modifyDescriptions(null, c) > NO_CHANGES_MADE) {
					process.add(c);
				}
			}
		}
		return process;
	}

	private boolean containsText(String[] texts, Concept c) {
		String fsn = c.getFsn().toLowerCase();
		for (String text : texts) {
			if (fsn.contains(text)) {
				return true;
			}
		}
		return false;
	}
}
