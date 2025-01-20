package org.ihtsdo.termserver.scripting.fixes.managedService;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;


/* MSSP-1761 Donated content from EE to INT has had descriptions inactivated, but because the 
 * import is done with null effective times, this actually results in the EE descriptions being
 * entirely deleted in the International Edition.  When EE is next upgraded, the new FSN and PTs
 * come through from INT, but there is no instruction to inactivated the existing ones, so 
 * we end up with multiple FSNs & PTs
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FixMultipleFsnPTs extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(FixMultipleFsnPTs.class);

	protected FixMultipleFsnPTs(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		FixMultipleFsnPTs fix = new FixMultipleFsnPTs(null);
		try {
			ReportSheetManager.setTargetFolderId("1u6YLvJWX2GwAVJazFqJeKcVTwBbw96cc");  //MS Ad-Hoc Batch fixes
			fix.selfDetermining = true;
			fix.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); //Just 
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept tsConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = removeDuplicateFSNs(task, tsConcept,  getFsnMap(concept));
		changesMade += removeDuplicatePTs(task, tsConcept,  getPTMap(concept));
		if (changesMade > 0) {
			updateConcept(task, tsConcept, info);
		}
		return changesMade;
	}

	private int removeDuplicateFSNs(Task t, Concept c, Map<String, List<Description>> fsns) throws TermServerScriptException {
		int changesMade = 0;
		for (Map.Entry<String, List<Description>> entry : fsns.entrySet()) {
			String langCode = entry.getKey();
			List<Description> fsnsForLang = entry.getValue();
			List<Description> intFSNs = filter(fsnsForLang,true);
			List<Description> extFSNs = filter(fsnsForLang,false);
			
			if (!langCode.equals("en") && fsnsForLang.size() > 1) {
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Multiple FSNs found in '" + langCode + "' - unexpected.");
				return NO_CHANGES_MADE;
			} else if (intFSNs.size() == 0) {
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "No FSN description found in International Module");
				return NO_CHANGES_MADE;
			} else if (intFSNs.size() > 1) {
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Multiple FSN descriptions found in International Module");
				return NO_CHANGES_MADE;
			} else if (extFSNs.size() > 1) {
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Multiple FSN descriptions found in Extension Module");
				return NO_CHANGES_MADE;
			}
			
			//Inactivate all extension FSNs, allow International to 
			for (Description d : extFSNs) {
				removeDescription(t, c, d, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
				changesMade++;
			}
		}
		return changesMade;
	}
	
	private int removeDuplicatePTs(Task t, Concept c, Map<String, List<Description>> pts) throws TermServerScriptException {
		int changesMade = 0;
		for (Map.Entry<String, List<Description>> entry : pts.entrySet()) {
			String refsetId = entry.getKey();
			Concept langRefset = gl.getConcept(refsetId);
			List<Description> ptsForLangRefset = entry.getValue();
			
			if (ptsForLangRefset.size() == 1) {
				//No problem for this langrefset
				continue;
			}
			
			List<Description> intPTs = filter(ptsForLangRefset,true);
			List<Description> extPTs = filter(ptsForLangRefset,false);
			
			if (!refsetId.equals(US_ENG_LANG_REFSET) && ptsForLangRefset.size() > 1) {
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Multiple PTs found for " + langRefset + " - unexpected.");
				return NO_CHANGES_MADE;
			} else if (intPTs.size() == 0) {
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "No PTs description found in International Module");
				return NO_CHANGES_MADE;
			} else if (intPTs.size() > 1) {
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Multiple PTs descriptions found in International Module");
				return NO_CHANGES_MADE;
			} else if (extPTs.size() > 1) {
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Multiple PTs descriptions found in Extension Module");
				return NO_CHANGES_MADE;
			}
			
			//Inactivate all extension FSNs, allow International to 
			for (Description d : extPTs) {
				//Recover description object from loaded concept, not local cache - won't affect save
				Description loadedDescription = c.getDescription(d.getDescriptionId());
				loadedDescription.setAcceptability(refsetId, Acceptability.ACCEPTABLE);
				report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_ACCEPTABILIY_CHANGED, "P -> A in " + refsetId, d);
				changesMade++;
			}
		}
		return changesMade;
	}

	private List<Description> filter(List<Description> descriptions, boolean isInternational) {
		return descriptions.stream()
				.filter(d -> isInternational == SnomedUtils.isInternational(d))
				.collect(Collectors.toList());
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.info("Identifying concepts to process");
		List<Concept> processMe = new ArrayList<>();
		
		nextConcept:
		for (Concept c : gl.getAllConcepts()) {
		//for (Concept c : Collections.singletonList(gl.getConcept("3381000181101"))) {
			Map<String, List<Description>> fsns = getFsnMap(c);
			for (List<Description> descs : fsns.values()) {
				if (descs.size() > 1) {
					processMe.add(c);
					continue nextConcept;
				}
			}
			
			//Now check preferred terms for each 
			Map<String, List<Description>> pts = getPTMap(c);
			for (List<Description> descs : pts.values()) {
				if (descs.size() > 1) {
					processMe.add(c);
					continue nextConcept;
				}
			}
		}
		
		LOGGER.info("Identified " + processMe.size() + " concepts to process");
		processMe.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<Component>(processMe);
	}

	private Map<String, List<Description>> getFsnMap(Concept c) {
		//Map of languages to FSN descriptions for that language
		Map<String, List<Description>> fsnMap = new HashMap<>();
		for(Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getType().equals(DescriptionType.FSN)) {
				List<Description> descriptionList = fsnMap.get(d.getLang());
				if (descriptionList == null) {
					descriptionList = new ArrayList<>();
					fsnMap.put(d.getLang(), descriptionList);
				}
				descriptionList.add(d);
			}
		}
		return fsnMap;
	}
	
	private Map<String, List<Description>> getPTMap(Concept c) {
		//Map of langRefSets to PT descriptions for that language
		Map<String, List<Description>> ptMap = new HashMap<>();
		for(Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getType().equals(DescriptionType.SYNONYM)) {
				for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
					if (!l.getAcceptabilityId().equals(SCTID_PREFERRED_TERM)) {
						continue;
					}
					List<Description> descriptionList = ptMap.get(l.getRefsetId());
					if (descriptionList == null) {
						descriptionList = new ArrayList<>();
						ptMap.put(l.getRefsetId(), descriptionList);
					}
					descriptionList.add(d);
				}

			}
		}
		return ptMap;
	}

}
