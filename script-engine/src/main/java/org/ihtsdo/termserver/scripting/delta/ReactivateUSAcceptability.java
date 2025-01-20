package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to reactivate langrefset entries when they have been inactivated after the international edition has activated ones for the same concept
 */
@Deprecated
public class ReactivateUSAcceptability extends DeltaGenerator implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReactivateUSAcceptability.class);

	String[] refsets = new String[] {US_ENG_LANG_REFSET};
	
	public static void main(String[] args) throws TermServerScriptException {
		ReactivateUSAcceptability delta = new ReactivateUSAcceptability();
		try {
			delta.newIdsRequired = false; // We'll only be reactivating exisiting langrefset entries
			delta.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(false);  
			//We won't include the project export in our timings
			delta.startTimer();
			delta.process();
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}

	@Override
	protected void process() throws TermServerScriptException {
		LOGGER.info("Processing concepts to find issues with US acceptability.");
		for (Concept concept : GraphLoader.getGraphLoader().getAllConcepts()) {
			fixFsnAcceptability(concept);
			if (concept.isModified()) {
				incrementSummaryInformation("Concepts modified");
				outputRF2(concept);  //Will only output dirty fields.
			}
		}
	}
	
	//Confirm that the active FSN has 1 x US acceptability == preferred
	private void fixFsnAcceptability(Concept c) throws TermServerScriptException {
		List<Description> fsns = c.getDescriptions(Acceptability.BOTH, DescriptionType.FSN, ActiveState.ACTIVE);
		if (fsns.size() != 1) {
			String msg = "Concept has " + fsns.size() + " active fsns";
			report(c, c.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
		} else {
			String msg = "[" + fsns.get(0).getDescriptionId() + "]: ";
			List<LangRefsetEntry> langRefEntries = fsns.get(0).getLangRefsetEntries(ActiveState.BOTH, US_ENG_LANG_REFSET);
			if (langRefEntries.size() != 1) {
				if (langRefEntries.size() == 2) {
					List<LangRefsetEntry> uslangRefEntries = fsns.get(0).getLangRefsetEntries(ActiveState.BOTH, refsets, SCTID_US_MODULE);
					List<LangRefsetEntry> corelangRefEntries = fsns.get(0).getLangRefsetEntries(ActiveState.BOTH, refsets, SCTID_CORE_MODULE);
					if (uslangRefEntries.size() > 1 || corelangRefEntries.size() >1) {
						msg += "Two acceptabilities in the same module";
						report(c, c.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
					} else {
						if (!uslangRefEntries.get(0).isActive() && corelangRefEntries.get(0).isActive() ) {
							long usET = Long.parseLong(uslangRefEntries.get(0).getEffectiveTime());
							long coreET = Long.parseLong(corelangRefEntries.get(0).getEffectiveTime());
							msg += "US langrefset entry inactivated " + (usET > coreET ? "after":"before") + " core row activated - " + usET;
							report(c, c.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
							
							//If the US inactivated AFTER the core activated, then this is the case we need to fix
							uslangRefEntries.get(0).setActive(true);  //Changing active state will reset effectiveTime
							c.setModified();
							String action = "Reactivated US FSN LangRefset entry";
							report(c, c.getFSNDescription(), Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, action);
							
						} else {
							msg += "Unexpected configuration of us and core lang refset entries";
							report(c, c.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
						}
					}
				} else {
					msg += "FSN has " + langRefEntries.size() + " US acceptability values.";
					report(c, c.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
				}
			} else if (!langRefEntries.get(0).getAcceptabilityId().equals(SCTID_PREFERRED_TERM)) {
				msg += "FSN has an acceptability that is not Preferred.";
				report(c, c.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
			} else if (!langRefEntries.get(0).isActive()) {
				msg += "FSN's US acceptability is inactive.";
				report(c, c.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
			}
		}
	}

}
