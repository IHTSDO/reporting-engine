package org.ihtsdo.termserver.scripting.delta.ms;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.tomcat.util.buf.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

public class MSSP1638_CloneAndReplaceLangRefsetDescriptions extends DeltaGenerator {
	
	Map<Description, Description> clonedDescriptions = new HashMap<>();
	List<String> enLangRefsets = Arrays.asList(ENGLISH_DIALECTS);
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		MSSP1638_CloneAndReplaceLangRefsetDescriptions delta = new MSSP1638_CloneAndReplaceLangRefsetDescriptions();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			delta.getArchiveManager().setPopulateReleasedFlag(true);
			delta.runStandAlone = false;
			delta.inputFileHasHeaderRow = true;
			delta.newIdsRequired = true; // We'll only be inactivating existing relationships
			delta.init(args);
			delta.moduleId = "11000146104";
			delta.loadProjectSnapshot(false);
			delta.postInit();
			delta.process();
			delta.getRF2Manager().flushFiles(true);  //Flush and Close
			if (!dryRun) {
				SnomedUtils.createArchive(new File(delta.outputDirName));
			}
		} finally {
			delta.finish();
			if (delta.descIdGenerator != null) {
				info(delta.descIdGenerator.finish());
			}
		}
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Severity, Action, Detail, Detail, Detail"};
		String[] tabNames = new String[] {	
				"Processing Report"};
		super.postInit(tabNames, columnHeadings, false);
	}

	public void process() throws TermServerScriptException {
		for (Concept c : identifyComponentsToProcess()) {
			cloneDescriptions(c);
			outputRF2(c, true);
		}
	}
	
	private void cloneDescriptions(Concept c) throws TermServerScriptException {
		for (Description d : c.getDescriptions("en", ActiveState.ACTIVE)) {
			//Is this description en with a non-en langrefset? Get local in-memory copy so we have access to RF2 LangRefsetEntries.
			Set<String> nonEnLangRefsets = getNonEnLangRefsets(c.getDescription(d.getId()));
			if (nonEnLangRefsets.size() > 0) {
				Description clone = d.clone(descIdGenerator.getSCTID());
				clone.setDirty();
				clone.setLang("nl");
				clone.setModuleId(moduleId);
				//Remove the EN dialect acceptability from the clone's map
				//The rest of the values can be left 'as is'
				for (LangRefsetEntry l : new ArrayList<>(clone.getLangRefsetEntries())) {
					if (enLangRefsets.contains(l.getRefsetId())) {
						clone.getLangRefsetEntries().remove(l);
					} else {
						l.setModuleId(moduleId);
						l.setDirty();
					}
				}
				//And add the new description to our concept
				c.addDescription(clone);
				clone.calculateAcceptabilityMap();
				report(c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, d.getId(), clone, SnomedUtils.toString(clone.getAcceptabilityMap(), true));
				
				//And inactivate the non-EN langrefset entries from the original EN description
				for (LangRefsetEntry l : d.getLangRefsetEntries()) {
					if (!enLangRefsets.contains(l.getRefsetId())) {
						l.setActive(false);
						l.setModuleId(moduleId);
						l.setDirty();
					}
				}
				d.calculateAcceptabilityMap();
				String msg = "Removed: " + StringUtils.join(nonEnLangRefsets, ',');
				msg += "\nRemaining: " +  SnomedUtils.toString(d.getAcceptabilityMap(), true);
				report(c, Severity.LOW, ReportActionType.LANG_REFSET_MODIFIED, d.getId(), d, msg);
			}
		}
	}

	protected List<Concept> identifyComponentsToProcess() throws TermServerScriptException {
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
