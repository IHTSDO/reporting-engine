package org.ihtsdo.termserver.scripting.fixes.managedService.oneOffs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.utils.ExceptionUtils;
import org.apache.tomcat.util.buf.StringUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

public class MSSP1638_CloneAndReplaceLangRefsetDescriptions extends BatchFix {
	
	Map<Description, Description> clonedDescriptions = new HashMap<>();
	List<String> enLangRefsets = Arrays.asList(ENGLISH_DIALECTS);
 	
	protected MSSP1638_CloneAndReplaceLangRefsetDescriptions(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		MSSP1638_CloneAndReplaceLangRefsetDescriptions fix = new MSSP1638_CloneAndReplaceLangRefsetDescriptions(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "DescriptionId, Action Detail, Details";
			fix.runStandAlone = true;
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = checkDescriptions(task, loadedConcept, concept);
			if (changesMade > 0) {
				updateConcept(task, loadedConcept, info);
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to process concept: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}
	
	private int checkDescriptions(Task t, Concept c, Concept localConcept) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions("en", ActiveState.ACTIVE)) {
			//Is this description en with a non-en langrefset? Get local in-memory copy so we have access to RF2 LangRefsetEntries.
			Set<String> nonEnLangRefsets = getNonEnLangRefsets(localConcept.getDescription(d.getId()));
			if (nonEnLangRefsets.size() > 0) {
				Description clone = d.clone(null);
				//Remove the EN dialect acceptability from the clone's map
				//The rest of the values can be left 'as is'
				clone.getAcceptabilityMap().remove(GB_ENG_LANG_REFSET);
				clone.getAcceptabilityMap().remove(US_ENG_LANG_REFSET);
				//And add the new description to our concept
				c.addDescription(clone);
				report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, d.getId(), clone, SnomedUtils.toString(clone.getAcceptabilityMap(), true));
				
				//And remove the non-EN langrefset entries from the original EN description
				d.getAcceptabilityMap().keySet().removeAll(nonEnLangRefsets);
				String msg = "Removed: " + StringUtils.join(nonEnLangRefsets, ',');
				msg += "\nRemaining: " +  SnomedUtils.toString(d.getAcceptabilityMap(), true);
				report(t, c, Severity.LOW, ReportActionType.LANG_REFSET_MODIFIED, d.getId(), d, msg);
				changesMade++;
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return gl.getAllConcepts().parallelStream()
				//.filter(c -> c.getFsn().toLowerCase().contains(inclusionText))
				.filter(c -> hasEnglishDescriptionInNonEnglishLangRefset(c))
				//.filter(c -> !gl.isOrphanetConcept(c))
				//.filter(c -> c.getRelationships(relTemplate, ActiveState.ACTIVE).size() == 0)
				.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
				.collect(Collectors.toList());
	}

	private boolean hasEnglishDescriptionInNonEnglishLangRefset(Concept c) {
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (isEnglishDescriptionInNonEnglishLangRefset(d)) {
				return true;
			}
		}
		return false;
	}

	private boolean isEnglishDescriptionInNonEnglishLangRefset(Description d) {
		if (!d.getLang().equals("en")) {
			return false;
		}
		//Check all the lang refset entries to see if they exist for non-en langrefsets
		Set<String> nonEnLangRefset = getNonEnLangRefsets(d);
		return nonEnLangRefset.size() > 0;
	}

	private Set<String> getNonEnLangRefsets(Description d) {
		Set<String> nonEnLangRefsets = new HashSet<>();
		for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
			if (!enLangRefsets.contains(l.getRefsetId())) {
				nonEnLangRefsets.add(l.getRefsetId());
			}
		}
		return nonEnLangRefsets;
	}
}
