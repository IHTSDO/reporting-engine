package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.*;

/*
INFRA-4924 I'm running into issues where I can't fix historical associations because Drools is objecting
to concepts that do not have FSNs!   Need to fix those first
*/
public class FixMissingFSNs extends BatchFix implements ScriptConstants{
	
	Set<String> knownTags = new HashSet<>();
	HistAssocUtils histAssocUtils;
	Map<String, Acceptability> preferredBoth;
	List<DescriptionType> fsnOnly = Collections.singletonList(DescriptionType.FSN);
	Map<String, String> semanticTagMap = new HashMap<>();
	
	protected FixMissingFSNs(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		FixMissingFSNs fix = new FixMissingFSNs(null);
		try {
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.init(args);
			fix.additionalReportColumns = "Active, Details";
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); 
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	public void postInit() throws TermServerScriptException {
		super.postInit();
		histAssocUtils = new HistAssocUtils(this);
		preferredBoth = SnomedUtils.createAcceptabilityMap(AcceptabilityMode.PREFERRED_BOTH);
		//Get a set of all current semantic tags
		for (Concept concept : gl.getAllConcepts()) {
			knownTags.add(SnomedUtils.deconstructFSN(concept.getFsn())[1]);
		}
		
		semanticTagMap.put("catheter", "(physical object)");
		semanticTagMap.put("ctomy", "(procedure)");
		semanticTagMap.put("drug", "(product)");
	}

	@Override
	public int doFix(Task task, Concept c, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(c, task.getBranchPath());
		int changesMade = sortFSN(task, loadedConcept);
		changesMade += checkSemTag(task, loadedConcept);
		changesMade += checkPT(task, loadedConcept);
		if (changesMade > 0) {
			try {
				updateConcept(task, loadedConcept, "");
			} catch (Exception e) {
				report(task, c, Severity.CRITICAL, ReportActionType.API_ERROR, c.isActive(), "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int sortFSN(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		
		//If we have an FSN, we don't need to do this step
		if (c.getDescriptions(ActiveState.ACTIVE, fsnOnly).size() > 0) {
			//Just check that the FSN is preferred in both dialects
			Description fsn = c.getFSNDescription();
			if (!fsn.isPreferred(US_ENG_LANG_REFSET) || !fsn.isPreferred(GB_ENG_LANG_REFSET)) {
				report(t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_ACCEPTABILIY_CHANGED, c.isActive(), "FSN was not preferred in both dialects.  Setting.");
				fsn.setAcceptabilityMap(preferredBoth);
				changesMade++;
			}
			return changesMade;
		}
		
		//Best thing would be a preferred term with a recognisable semantic tag
		Description pt = c.getPreferredSynonym(US_ENG_LANG_REFSET);
		if (pt == null) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, c.isActive(), "Concept doesn't have a PT either!" , "Promoting random term to PT");
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				pt = d;
				String parts[] = SnomedUtils.deconstructFSN(pt.getTerm(), true);  //quietly!
				if (knownTags.contains(parts[1])) {
					//This will do, otherwise we'll take a random last one
					break;
				}
			}
			pt.setAcceptabilityMap(preferredBoth);
		}
		
		Description fsn = pt.clone(null);
		fsn.setType(DescriptionType.FSN);
		fsn.setAcceptabilityMap(preferredBoth);
		
		String parts[] = SnomedUtils.deconstructFSN(pt.getTerm(), true);  //quietly!
		if (parts[1] != null) {
			//Is this a recognisable semantic tag?
			if (knownTags.contains(parts[1])) {
				//The TypeId is not mutable according to https://confluence.ihtsdotools.org/display/WIPRELFMT/4.2.2+Description+File+Specification
				report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, c.isActive(), "PT with SemTag recreated as FSN", fsn.getTerm());
				replaceDescription(t, c, pt, parts[0], InactivationIndicator.ERRONEOUS);
				c.addDescription(fsn);
				return CHANGE_MADE;
			} else {
				report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, c.isActive(), "SemTag of PT not currently used, removing", parts[1]);
			}
		} else {
			//Lets get a semantic tag from one of our replacements
			Set<Concept> replacements = histAssocUtils.getReplacements(c);
			if (replacements.size() > 0) {
				Concept replacement = replacements.iterator().next();
				String[] replacementParts = SnomedUtils.deconstructFSN(replacement.getFsn(), true);
				if (replacementParts[1] != null) {
					fsn.setTerm (pt.getTerm() + " " + replacementParts[1]);
					report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, c.isActive(), "PT term used for replacement FSN with SemTag taken from Historical Association", fsn);
					fsn.setTerm (pt.getTerm() + " " + replacementParts[1]);
					c.addDescription(fsn);
					return CHANGE_MADE;
				}
			} else {
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, c.isActive(), "Can't even get a Semantic Tag from a Historical association.  I give up!");
				return NO_CHANGES_MADE;
			}
		}
		
		return changesMade;
	}
	

	private int checkSemTag(Task t, Concept c) throws TermServerScriptException {
		if (!missingSemTag(c)) {
			return NO_CHANGES_MADE;
		}
		String semTag = null;
		if (c.isActive()) {
			Concept firstParent = c.getParents(CharacteristicType.STATED_RELATIONSHIP).iterator().next();
			semTag = SnomedUtils.deconstructFSN(firstParent.getFsn())[1];
		} else {
			Set<Concept> replacements = histAssocUtils.getReplacements(c);
			if (replacements.size() > 0) {
				Concept replacement = replacements.iterator().next();
				semTag = SnomedUtils.deconstructFSN(replacement.getFsn())[1];
				report(t, c, Severity.LOW, ReportActionType.INFO, c.isActive(), "SemTag for FSN taken from Historical Association", replacement);
			}
		}
		
		if (semTag == null) {
			//Last ditch effort, can we get one usign lexical methods?
			for (Map.Entry<String, String> entry : semanticTagMap.entrySet()) {
				if (c.getFsn().contains(entry.getKey())) {
					semTag = entry.getValue();
					report(t, c, Severity.LOW, ReportActionType.INFO, c.isActive(), "SemTag for FSN found via lexical map", entry.getKey() + " -> " + entry.getValue());
				}
			}
		}
		
		if (semTag != null) {
			String newFSN = c.getFsn() + " " + semTag;
			replaceDescription (t, c, c.getFSNDescription(), newFSN, InactivationIndicator.ERRONEOUS);
			return CHANGE_MADE;
		} else {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, c.isActive(), "Can't even get a Semantic Tag from a Historical association.  I give up!");
		}
		return NO_CHANGES_MADE;
	}

	
	//Make sure we've PTs in both dialects
	private int checkPT(Task t, Concept c) throws TermServerScriptException {
		Description usPT = c.getPreferredSynonym(US_ENG_LANG_REFSET);
		Description gbPT = c.getPreferredSynonym(GB_ENG_LANG_REFSET);
		
		if (usPT != null && gbPT == null) {
			usPT.setAcceptabilityMap(preferredBoth);
			report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_ACCEPTABILIY_CHANGED, c.isActive(), "US PT made preferred in GB", usPT);
			return CHANGE_MADE;
		} else if (usPT == null && gbPT != null) {
			gbPT.setAcceptabilityMap(preferredBoth);
			report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_ACCEPTABILIY_CHANGED, c.isActive(), "GB PT made preferred in US", gbPT);
			return CHANGE_MADE;
		} if (usPT == null && gbPT == null) {
			//We'll use the FSN with the SemTag stripped in this case
			String term = SnomedUtils.deconstructFSN(c.getFsn(), true)[0];
			
			//Do we in fact already have this description and just need to promote it?
			Description existing = c.getDescription(term, ActiveState.BOTH);
			if (existing != null) {
				if (!existing.isActive()) {
					existing.setActive(true);
				}
				existing.setAcceptabilityMap(preferredBoth);
				report(t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_ACCEPTABILIY_CHANGED, c.isActive(), "Existing Synonym promoted to PT", term);
			} else {
				Description pt = Description.withDefaults(term, DescriptionType.SYNONYM, preferredBoth);
				pt.setAcceptabilityMap(preferredBoth);
				c.addDescription(pt);
				report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, c.isActive(), "New PT created based on FSN minus SemTag", term);
			}
			return CHANGE_MADE;
		}
		return NO_CHANGES_MADE;
	}


	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		for (Concept c : gl.getAllConcepts()) {
			if (	c.getDescriptions(ActiveState.ACTIVE, fsnOnly).size() == 0 ||
					c.getPreferredSynonym(US_ENG_LANG_REFSET) == null ||
					c.getPreferredSynonym(GB_ENG_LANG_REFSET) == null ||
					missingSemTag(c)) {
				allAffected.add(c);
			}
		}
		return new ArrayList<Component>(allAffected);
	}

	private boolean missingSemTag(Concept c) {
		if (c.getFsn() != null && SnomedUtils.deconstructFSN(c.getFsn(), true)[1] == null) {
			return true;
		}
		return false;
	}
	
}
