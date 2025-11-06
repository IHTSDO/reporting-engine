package org.ihtsdo.termserver.scripting.delta.negative;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClient;

import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * Class to reactivate langrefset entries when they have been inactivated after the international edition has activated ones for the same concept
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Delete_US_Issues extends NegativeDeltaGenerator implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(Delete_US_Issues.class);

	String[] refsets = new String[] {US_ENG_LANG_REFSET};

	static final String deletionEffectiveTime = "20170901";
	List<Concept> affectedConcepts = new ArrayList<Concept>();

	public static void main(String[] args) throws TermServerScriptException {
		Delete_US_Issues delta = new Delete_US_Issues();
		try {
			delta.newIdsRequired = false; // We'll only be reactivating exisiting langrefset entries
			TermServerClient.supportsIncludeUnpublished = false;   //This code not yet available in MS
			delta.tsRoot="MAIN/2017-01-31/SNOMEDCT-US/";
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
	
	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		boolean fileLoaded = false;
		for (int i=0; i < args.length; i++) {
			if (args[i].equalsIgnoreCase("-z")) {
				loadConcepts(args[i+1]);
				fileLoaded = true;
			}
		}
		
		if (!fileLoaded) {
			LOGGER.info("Failed to concepts affected.  Specify path with 'z' command line parameter");
			System.exit(1);
		}
	}
	
	private void loadConcepts(String fileName) throws TermServerScriptException {
		try {
			File affectedConceptFile = new File(fileName);
			List<String> lines = Files.readLines(affectedConceptFile, Charsets.UTF_8);
			LOGGER.info("Loading selected Concepts from " + fileName);
			for (String line : lines) {
				affectedConcepts.add(gl.getConcept(line));
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to import concept selection file " + fileName, e);
		}
	}

	@Override
	protected void process() throws TermServerScriptException {
		LOGGER.info("Processing concepts to find issues with US acceptability.");
		//First touch all concepts who were erroneously inactivated to remove those rows
		//Only if the concept is still 
		for (Concept concept : affectedConcepts) {
			Concept loadedConcept = gl.getConcept(concept.getConceptId());
			if (loadedConcept.getModuleId().equals(SCTID_US_MODULE)) {
				String action = "Deleting last US concept update";
				report(concept, concept.getFSNDescription(), Severity.MEDIUM, ReportActionType.CONCEPT_CHANGE_MADE, action);
				concept.delete(deletionEffectiveTime);
				concept.setModified();
			}
		}
		//Now go through all concepts to find FSN Acceptability issues.
		for (Concept concept : gl.getAllConcepts()) {
			deleteUnwantedFsnAcceptability(concept);
			if (concept.isModified()) {
				incrementSummaryInformation("Concepts modified");
				outputRF2(concept);  //Will only output deleted rows
			}
		}
	}
	
	//Confirm that the active FSN has 1 x US acceptability == preferred
	private void deleteUnwantedFsnAcceptability(Concept c) throws TermServerScriptException {
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
							uslangRefEntries.get(0).delete(deletionEffectiveTime);
							String action = "Deleted US FSN LangRefset entry";
							report(c, c.getFSNDescription(), Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, action);
							c.setModified();
						} if (uslangRefEntries.get(0).isActive() && corelangRefEntries.get(0).isActive() ) {
							msg += "Both US and Core module lang refset entries are inactive";
							report(c, c.getFSNDescription(), Severity.LOW, ReportActionType.VALIDATION_CHECK, msg);
						
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

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}

}
