package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/*
SCTQA-135 Address inconsistency in "Influenza Virus" in all major hierarchies
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReTermInfluenza extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(ReTermInfluenza.class);

	public static final String INFLUENZA = "Influenza";
	public static final String PARAINFLUENZA = "Parainfluenza";
	public static final String INFLUENZAE = "influenzae";
	public static final String IMMUNOGLOBULIN = "Immunoglobulin";
	public static final String ANTIBODY = "antibody";
	public static final String VIRUS = "virus";
	
	Map<String, String> greekMap = new HashMap<>();
	String[] influenzas = new String[] {"A", "B", "C", "D" };
	String[] immunoglobulinClasses = new String[] {"A", "D", "E", "G", "M" };
	Set<String> exceptions;
	
	protected ReTermInfluenza(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ReTermInfluenza fix = new ReTermInfluenza(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
			fix.init(args);
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		
		greekMap.put("A", "Alpha");
		greekMap.put("B", "Beta");
		greekMap.put("C", "Gamma");
		greekMap.put("D", "Delta");
		
		exceptions = new HashSet<>();
		exceptions.add("312869001");
		exceptions.add("312870000");
	}

	@Override
	public int doFix(Task t, Concept cachedConcept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(cachedConcept, t.getBranchPath());
		int changesMade = reterm(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	private int reterm(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		if (c.getFsn().contains(IMMUNOGLOBULIN) && c.getFsn().contains(ANTIBODY)) {
			return reTermImmunoglobulin(t, c);
		} else {
			//Work through all descriptions and change any "influenza X" to "Influenza X virus"
			nextDescription:
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				for (String fluLetter : influenzas) {
					for (Map.Entry<String, String> replacement : generatePossibleReplacements(fluLetter).entrySet()) {
						if (d.getTerm().contains(replacement.getKey())) {
							String replacementTerm = d.getTerm().replace(replacement.getKey(), replacement.getValue());
							replaceDescription(t, c, d, replacementTerm, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
							changesMade++;
							continue nextDescription;
						}
					}
				}
			}
		}
		if (changesMade == 0) {
			LOGGER.warn ("No changes made to " + c);
		}
		return changesMade;
	}
	
	private Map<String, String> generatePossibleReplacements(String fluLetter) {
		Map<String, String> replacements = new HashMap<>();
		String normalizedText = INFLUENZA + " " + fluLetter + " virus";
		replacements.put (	INFLUENZA.toLowerCase() + " " + fluLetter + " virus", normalizedText);
		replacements.put (	INFLUENZA.toLowerCase() + " " + fluLetter, normalizedText);
		replacements.put (	INFLUENZA.toLowerCase() + " virus " + fluLetter, normalizedText);
		replacements.put (	INFLUENZA + " virus " + fluLetter, normalizedText);
		replacements.put (	INFLUENZA + " virus, type " + fluLetter, normalizedText);
		replacements.put (	INFLUENZA + "virus, type " + fluLetter, normalizedText);
		replacements.put (	INFLUENZA + "virus type " + fluLetter, normalizedText);
		replacements.put (	INFLUENZA + "virus " + fluLetter, normalizedText);
		return replacements;
	}

	private int reTermImmunoglobulin(Task t, Concept c) throws TermServerScriptException {
		for (String immunoLetter : immunoglobulinClasses) {
			for (String fluLetter : influenzas) {
				String potentialCurrentFSN = IMMUNOGLOBULIN + " " + immunoLetter + " " +
						ANTIBODY + " to " + INFLUENZA + " virus " + fluLetter + " (substance)";
				if (c.getFsn().equals(potentialCurrentFSN)) {
					return reterm (t, c, immunoLetter, fluLetter);
				}
			}
		}
		LOGGER.warn ("No changes made to Immunoglobulin: " + c);
		return NO_CHANGES_MADE;
	}

	private int reterm(Task t, Concept c, String immunoLetter, String fluLetter) throws TermServerScriptException {
		int changesMade = 0;
		//Keep a track of the descriptions we use, and inactivate the rest!
		List<Description> keepAll = new ArrayList<>();
		
		//Firstly the FSN
		String newFSNPart = IMMUNOGLOBULIN + " " + immunoLetter + " " + ANTIBODY + " to " 
				+ INFLUENZA + " " + fluLetter + " virus";
		String newFSN = newFSNPart + " (substance)";
		Description keepMe = replaceDescription(t, c, c.getFSNDescription(), newFSN, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
		keepAll.add(keepMe);
		
		//Preferred Term.  If US/GB variance, warn!
		List<Description> pts = c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
		if (pts.size() != 1) {
			throw new TermServerScriptException("Unexpected preferred term(s) in " + c);
		}
		String pt = INFLUENZA + " " + fluLetter + " virus Ig" + immunoLetter;
		keepMe = replaceDescription(t, c, pts.get(0), pt, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
		keepAll.add(keepMe);
		//Now for the two synonyms
		Description syn1 = Description.withDefaults(newFSNPart, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
		syn1.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
		addDescription(t, c, syn1);
		keepAll.add(syn1);
		
		String syn2Str = "Anti-" + INFLUENZA + " " + fluLetter + " " + VIRUS + " Ig" + immunoLetter;
		Description syn2 = Description.withDefaults(syn2Str, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
		syn2.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
		addDescription(t, c, syn2);
		keepAll.add(syn2);
		
		//Remove any leftover descriptions we're not keeping
		for (Description d : new ArrayList<>(c.getDescriptions(ActiveState.ACTIVE))) {
			if (!keepAll.contains(d)) {
				removeDescription(t, c, d, null, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			}
			//Count how many actually new descriptions we've added 
			if (StringUtils.isEmpty(d.getId())) {
				changesMade++;
			}
		}
		
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Concept> allAffected = new ArrayList<>();
		Set<Concept> organisms = gl.getDescendantsCache().getDescendants(ORGANISM);
		setQuiet(true);
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive() && !exceptions.contains(c.getConceptId())) {
				if (!organisms.contains(c) && containsTerm(c, INFLUENZA) && !containsTerm(c, PARAINFLUENZA) && !c.getFsn().contains(INFLUENZAE)) {
					if (reterm(null, c.cloneWithIds()) > 0) {
						allAffected.add(c);
					}
				}
			}
		}
		setQuiet(false);
		allAffected.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<>(allAffected);
	}

	private boolean containsTerm(Concept c, String term) {
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getTerm().toLowerCase().contains(term.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}
}
