package org.ihtsdo.termserver.scripting.delta.oneOffs;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * MSSP-1533 Remove FSNs from Patient Friendly Language Reference Set
 */
public class MSSP1533_RemoveFsnFromPatientFriendlyLRS extends DeltaGenerator {
	
	private static String targetLangRefsetId = "15551000146102"; // | Patiëntvriendelijk-Nederlandse taalreferentieset (foundation metadata concept) | 

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		MSSP1533_RemoveFsnFromPatientFriendlyLRS delta = new MSSP1533_RemoveFsnFromPatientFriendlyLRS();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			delta.newIdsRequired = false;
			delta.runStandAlone = false;  //We need to look up the project path for MS projects
			delta.init(args);
			delta.getArchiveManager().setPopulateReleasedFlag(true);
			delta.loadProjectSnapshot(false);
			delta.postInit();
			delta.process();
			delta.flushFiles(false); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}
	
	protected void process() throws TermServerScriptException {
		List<Concept> conceptsSorted = gl.getAllConcepts().stream()
				.sorted(Comparator.comparing(Concept::getSemTag)
						.thenComparing(Comparator.comparing(Concept::getFsn)))
				.collect(Collectors.toList());
		for (Concept c : conceptsSorted) {
			for (RefsetMember entry : getUnwantedLangRefsetMembers(c)) {
				entry.setActive(false);
				c.setModified();
				if (entry.isReleased()) {
					report(c, Severity.LOW, ReportActionType.REFSET_MEMBER_INACTIVATED, entry, gl.getDescription(entry.getReferencedComponentId()));
				} else {
					report(c, Severity.LOW, ReportActionType.REFSET_MEMBER_DELETED, entry, gl.getDescription(entry.getReferencedComponentId()));
				}
			}
			
			if (c.isModified()) {
				outputRF2(c);
			}
		}
	}

	private List<RefsetMember> getUnwantedLangRefsetMembers(Concept c) {
		List<RefsetMember> unwantedRefsetMembers = new ArrayList<>();
		for (Description d : c.getDescriptions()) {
			if (d.getType().equals(DescriptionType.FSN)) {
				unwantedRefsetMembers.addAll(d.getLangRefsetEntries(ActiveState.ACTIVE, targetLangRefsetId));
			}
		}
		return unwantedRefsetMembers;
	}

}
