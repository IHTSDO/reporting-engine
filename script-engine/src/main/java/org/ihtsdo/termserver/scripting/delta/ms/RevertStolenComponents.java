package org.ihtsdo.termserver.scripting.delta.ms;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * MSSP-1425 Find eg descriptions with core namespace SCTIDs which have switched
 * module and have no effective time and check with a published branch if we can
 * revert those back to the core module along with any langrefset entries.
 *
 * Ah, we've a problem here in that these components jumped module without losing their
 * effective time.  So we need a snapshot export to fix it, because the delta won't give
 * you any component that still has an effective tim
 */
public class RevertStolenComponents extends DeltaGenerator {
	
	String intReleaseBranch="MAIN/2022-01-31";
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		RevertStolenComponents delta = new RevertStolenComponents();
		try {
			delta.getArchiveManager().setPopulateReleasedFlag(true);
			delta.runStandAlone = false;
			delta.inputFileHasHeaderRow = true;
			delta.newIdsRequired = false;
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
			if (delta.descIdGenerator != null) {
				info(delta.descIdGenerator.finish());
			}
		}
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setReleasedFlagPopulated(true);
		ReportSheetManager.targetFolderId = "1mvrO8P3n94YmNqlWZkPJirmFKaFUnE0o"; //Managed Service
		subsetECL = run.getParamValue(ECL);
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Action, ComponentType, Component Reasserted"};
		String[] tabNames = new String[] {	
				"Reassertions"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	public void process() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			if (hasStolenComponent(c)) {
				Concept published = loadConcept(c, intReleaseBranch);
				revertDescriptionsIfRequired(c, published);
				outputRF2(c, true);  //Will only output dirty fields.
			}
		}
	}

	private void revertDescriptionsIfRequired(Concept c, Concept published) throws TermServerScriptException {
		for (Description d : c.getDescriptions()) {
			if (StringUtils.isEmpty(d.getEffectiveTime())
					&& !SnomedUtils.hasExtensionSCTID(d) 
					&& !SnomedUtils.isCore(d)) {
				Description publishedDesc = published.getDescription(d.getDescriptionId());
				d.setModuleId(publishedDesc.getModuleId());
				d.setEffectiveTime(publishedDesc.getEffectiveTime());
				d.setDirty();
				report(c, ReportActionType.MODULE_CHANGE_MADE, d.getComponentType(), d);
				for (LangRefsetEntry l : d.getLangRefsetEntries()) {
					if (StringUtils.isEmpty(l.getEffectiveTime())
							&& !SnomedUtils.isCore(l)
							&& SnomedUtils.isEnglishDialect(l)) {
						//Need to find from members endpoint as concept does not have full details of refset members
						RefsetMember publishedLRS = loadRefsetMember(l.getId(), intReleaseBranch);
						l.setModuleId(publishedLRS.getModuleId());
						l.setEffectiveTime(publishedLRS.getEffectiveTime());
						l.setDirty();
						report(c, ReportActionType.MODULE_CHANGE_MADE, l.getComponentType(), l);
					}
				}
			}
		}
	}

	private boolean hasStolenComponent(Concept c) throws TermServerScriptException {
		if (StringUtils.isEmpty(c.getEffectiveTime())
				&& !SnomedUtils.hasExtensionSCTID(c) 
				&& !SnomedUtils.isCore(c)) {
			report(c, ReportActionType.VALIDATION_CHECK, "Core concept moved to extension - check me");
		}
		
		for (Description d : c.getDescriptions()) {
			if (StringUtils.isEmpty(d.getEffectiveTime())
					&& !SnomedUtils.hasExtensionSCTID(d) 
					&& !SnomedUtils.isCore(d)) {
				return true;
			}
		}
		return false;
	}

}
