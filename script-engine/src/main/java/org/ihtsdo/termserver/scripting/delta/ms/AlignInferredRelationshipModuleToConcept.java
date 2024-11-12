package org.ihtsdo.termserver.scripting.delta.ms;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobRun;

import java.io.File;

public class AlignInferredRelationshipModuleToConcept extends DeltaGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(AlignInferredRelationshipModuleToConcept.class);

	public static void main(String[] args) throws TermServerScriptException {
		AlignInferredRelationshipModuleToConcept delta = new AlignInferredRelationshipModuleToConcept();
		try {
			delta.getArchiveManager().setPopulateReleasedFlag(true);
			delta.runStandAlone = false;
			delta.inputFileHasHeaderRow = true;
			delta.newIdsRequired = false;
			delta.gl.setRecordPreviousState(true);
			delta.init(args);
			delta.loadProjectSnapshot(false);
			delta.postInit();
			delta.process();
			delta.getRF2Manager().flushFiles(true);
			
			if (!dryRun) {
				SnomedUtils.createArchive(new File(delta.outputDirName));
			}
		} finally {
			delta.finish();
		}
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setReleasedFlagPopulated(true);
		subsetECL = run.getParamValue(ECL);
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Action, ComponentType, Component Reasserted"};
		String[] tabNames = new String[] {	
				"Reassertions"};
		super.postInit(GFOLDER_MS, tabNames, columnHeadings, false);
	}
	
	public void process() throws TermServerScriptException {
		int processedCount = 0;
		for (Concept c : gl.getAllConcepts()) {
			if (++processedCount%100000==0) {
				LOGGER.debug("Processed: {}", processedCount);
			}
			if (!inScope(c)) {
				continue;
			}
			for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (!c.getModuleId().equals(r.getModuleId())) {
					switchInferredRelModule(c, r);
				}
			}
			
			if (c.isModified()) {
				outputRF2(c, true);  //Will only output dirty fields.
			}
		}
	}

	private void switchInferredRelModule(Concept c, Relationship r) throws TermServerScriptException {
		String origModule = r.getModuleId();
		r.setModuleId(c.getModuleId());
		c.setModified();
		report(c, ReportActionType.MODULE_CHANGE_MADE, origModule +" -> " + r.getModuleId(), r);
	}

}
