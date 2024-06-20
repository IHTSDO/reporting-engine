package org.ihtsdo.termserver.scripting.fixes.oneOffs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.util.List;
import java.util.stream.Collectors;

public class INFRA11620_RemoveICDOInactive extends BatchFix {

	private static final String RefsetOfInterest = "446608001"; // |SNOMED CT to ICD-O simple map|

	protected INFRA11620_RemoveICDOInactive(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		INFRA11620_RemoveICDOInactive fix = new INFRA11620_RemoveICDOInactive(null);
		try {
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.runStandAlone = true;
			fix.getArchiveManager().setLoadOtherReferenceSets(true);
			fix.getArchiveManager().setPopulateReleaseFlag(true);
			fix.populateTaskDescription = false;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(true);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		//Do we have any 'other' refset entries that we might be interested in?
		for (RefsetMember m : c.getOtherRefsetMembers()) {
			if (m.getRefsetId().equals(RefsetOfInterest)) {
				if (!c.isActive()) {
					changesMade += removeRefsetMember(t, c, m, c.getEffectiveTime());
				}
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return gl.getAllConcepts().parallelStream()
				.filter(c -> !c.isActive())
				.filter(c -> hasRefsetMemberOfInterest(c))
				.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
				.collect(Collectors.toList());
	}

	private boolean hasRefsetMemberOfInterest(Concept c) {
		return c.getOtherRefsetMembers().stream()
				.filter(m -> m.isActive())
				.anyMatch(m -> m.getRefsetId().equals(RefsetOfInterest));
	}

}
