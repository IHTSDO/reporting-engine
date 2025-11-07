package org.ihtsdo.termserver.scripting.fixes.managed_service.one_offs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

public class INFRA9963_DeleteLangRefsetInDelta extends BatchFix {
	
	public static String SCTID_CF_LRS = "21000241105";   //Common French Language Reference Set
	public static String SCTID_CF_MOD = "11000241103";   //Common French Module
	public static String SCTID_CH_MOD = "2011000195101"; //Swiss Module
	public static String SCTID_CH_LRS = "2021000195106"; //Swiss French Language Reference Set
 	
	protected INFRA9963_DeleteLangRefsetInDelta(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		INFRA9963_DeleteLangRefsetInDelta fix = new INFRA9963_DeleteLangRefsetInDelta(null);
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
			changesMade = deleteInactivatedLRS(task, concept);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to process concept: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}
	
	private int deleteInactivatedLRS(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions()) {
			for (LangRefsetEntry l : d.getLangRefsetEntries()) {
				if (l.getRefsetId().equals(SCTID_CF_LRS) &&
						SnomedUtils.isEmpty(l.getEffectiveTime())) {
					report(t, c, Severity.LOW, ReportActionType.LANG_REFSET_DELETED, l);
					deleteRefsetMember(t, l.getId());
					changesMade++;
				}
			}
		}
		return changesMade;
	}

	

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return gl.getAllConcepts().parallelStream()
				//.filter(c -> c.getFsn().toLowerCase().contains(inclusionText))
				.filter(c -> hasLRSInactivation(c))
				//.filter(c -> !gl.isOrphanetConcept(c))
				//.filter(c -> c.getRelationships(relTemplate, ActiveState.ACTIVE).size() == 0)
				.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
				.collect(Collectors.toList());
	}

	private boolean hasLRSInactivation(Concept c) {
		for (Description d : c.getDescriptions()) {
			for (LangRefsetEntry l : d.getLangRefsetEntries()) {
				if (l.getRefsetId().equals(SCTID_CF_LRS) &&
						SnomedUtils.isEmpty(l.getEffectiveTime())) {
					return true;
				}
			}
		}
		return false;
	}

}
