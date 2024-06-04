package org.ihtsdo.termserver.scripting.fixes.oneOffs;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

public class INFRA7059_SEP_Reterm extends BatchFix {

	public static final String SKIN = "Change to ‘skin structure'";
	public static final String BONE = "Change to ‘bone structure'";
	public static final String STRUCTURE = "Change to ‘Structure of”";
	
	Map<Concept, String> changeTypeMap = new HashMap<>();
	Map<String, Map<String,String>> changePatterns = new HashMap<>();
	Set<Concept> exclusions = new HashSet<>();
	
	protected INFRA7059_SEP_Reterm(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		INFRA7059_SEP_Reterm fix = new INFRA7059_SEP_Reterm(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = false;
			fix.reportNoChange = true;
			fix.inputFileHasHeaderRow = true;
			fix.expectNullConcepts = true;
			fix.validateConceptOnUpdate = false;
			fix.init(args);
			fix.getArchiveManager().setPopulateReleaseFlag(true);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Task, Desc, SCTID,FSN,ConceptType,Severity,Action, Detail,Details,",
				"Id, FSN, SemTag, CheckMe, Remark, Detail"};
		String[] tabNames = new String[] {
				"Processed",
				"Not Processed"};
		super.postInit(tabNames, columnHeadings, false);
		
		Map<String, String> boneStructurePatterns = new LinkedHashMap<>();
		boneStructurePatterns.put("bones", "bones structure");
		boneStructurePatterns.put("Bone tissue", "Bone tissue structure");
		boneStructurePatterns.put("bone tissue", "bone tissue structure");
		boneStructurePatterns.put("bone", "bone structure");
		
		Map<String, String> skinStructurePatterns = new LinkedHashMap<>();
		skinStructurePatterns.put("Skin", "Skin structure");
		skinStructurePatterns.put("skin", "skin structure");
		
		changePatterns.put(BONE, boneStructurePatterns);
		changePatterns.put(SKIN, skinStructurePatterns);
		
		exclusions.add(gl.getConcept("181791008 |Skull and/or spine and/or bones structure and/or joints (body structure)|"));
		exclusions.add(gl.getConcept("70657000 |Structure of gastrointestinal spaces (body structure)|"));
		exclusions.add(gl.getConcept("257472005 |Structure of aVF (body structure)|"));
		exclusions.add(gl.getConcept("257473000 |Structure of aVL (body structure)|"));
		
		exclusions.addAll(findConcepts("<< 245643006|Tooth surface (body structure)|"));
		exclusions.addAll(findConcepts("<< 698969006|Material anatomical point (body structure)|"));
		exclusions.addAll(findConcepts("<<279012004 Structure of vertical reference line (body structure)"));
		exclusions.addAll(findConcepts("<<272743000|Acupuncture point (body structure)|"));
		
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = modifyDescriptions(task, loadedConcept);
			/*String fsnMinusTag = SnomedUtils.deconstructFSN(loadedConcept.getFsn())[0];
			String usPT = loadedConcept.getPreferredSynonym(US_ENG_LANG_REFSET).getTerm();
			if (!usPT.equals(fsnMinusTag)) {
				report(task, concept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "PT is not FSN minus Semtag", usPT);
			}*/
			if (changesMade > 0) {
				updateConcept(task, loadedConcept, info);
			} 
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	private int modifyDescriptions(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		List<Description> originalDescriptions = new ArrayList<>(c.getDescriptions(ActiveState.ACTIVE));
		for (Description d : originalDescriptions) {
			if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
				continue;
			}
			
			String term = d.getTerm().toLowerCase();
			if (d.getType().equals(DescriptionType.FSN)) {
				term = SnomedUtils.deconstructFSN(term)[0].toLowerCase();
			} else {
				continue;  //Only changing FSNs + matching Synonym
			}
			
			String newTerm;
			if (term.contains("structure")) {
				continue;
			}
			
			String patternType = changeTypeMap.get(c);
			switch (patternType) {
				case SKIN: 
				case BONE: newTerm = getNewTerm(changePatterns.get(patternType), d.getTerm());
							break;
				case STRUCTURE: newTerm = formStructureTerm(d.getTerm(), d.getCaseSignificance());
								break;
				default:  report(t, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Unknown pattern");
						return NO_CHANGES_MADE;
			}
			
			newTerm = newTerm.replaceAll("the ", "").replaceAll("The ", "").replaceAll("  ", " ");
			
			if (!d.getTerm().equals(newTerm)) {
				//Do not demote the PT, replace absolutely
				replaceDescription(t, c, d, newTerm, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, false);
				changesMade++;
				
				//And we're also to add a synonym without the semtag
				String matchingSynonymTerm = SnomedUtils.deconstructFSN(newTerm)[0];
				Description matchingSynonym = Description.withDefaults(matchingSynonymTerm, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
				matchingSynonym.setCaseSignificance(d.getCaseSignificance());
				addDescription(t, c, matchingSynonym);
			}
		}
		return changesMade;
	}

	private String formStructureTerm(String term, CaseSignificance cs) {
		String origTerm = term;
		if (!cs.equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
			origTerm = StringUtils.decapitalizeFirstLetter(term);
		}
		return "Structure of " + origTerm;
	}

	private String getNewTerm(Map<String, String> changeMap, String term) throws TermServerScriptException {
		if (changeMap == null || changeMap.size() == 0) {
			throw new TermServerScriptException("Unable to get new term - no change map received.");
		}
		
		for (Map.Entry<String, String> entry : changeMap.entrySet()) {
			String find = entry.getKey();
			String replace = entry.getValue();
			if (term.contains(find)) {
				return term.replace(find, replace);
			}
		}
		return term;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[1], false, true);
		if (exclusions.contains(c)) {
			report (SECONDARY_REPORT, c, "", "Explicit exclusion");
			return null;
		}
		
		if (!SnomedUtils.deconstructFSN(c.getFsn())[0].toLowerCase().contains("structure")) {
			changeTypeMap.put(c, lineItems[0]);
			return new ArrayList<>(Collections.singletonList(c));
		} else {
			return null;
		}
	}
}
