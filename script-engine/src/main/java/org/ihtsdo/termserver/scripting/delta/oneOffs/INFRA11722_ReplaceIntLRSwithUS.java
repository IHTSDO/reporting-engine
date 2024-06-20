package org.ihtsdo.termserver.scripting.delta.oneOffs;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/*
	We have an issue with US Concepts being donated to the International Edition where erroneous
	inactive en-gb langreference set entries are NOT being promoted over.   These are then recreated
	as required in the International Edition with a different UUID.  Then when the US is upgraded
	we end up with two language reference set entries for the same description, one active, one inactive.
	The authoring platform then starts updating these, choosing which one to update at random.
	So we end up with state and module jumping all over the place.
*/
public class INFRA11722_ReplaceIntLRSwithUS extends DeltaGenerator implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(INFRA11722_ReplaceIntLRSwithUS.class);
	public static String US_MODULE = "731000124108";
	public static List<String> US_NAMESPACES = Arrays.asList(new String[]{"1000124", "1000119"});

	public Map<String, List<LangRefsetEntry>> descriptionToGbLangRefset = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		INFRA11722_ReplaceIntLRSwithUS delta = new INFRA11722_ReplaceIntLRSwithUS();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			delta.getArchiveManager().setPopulateReleasedFlag(true);
			delta.targetModuleId = US_MODULE;
			delta.newIdsRequired = false; // We'll only be modifying existing descriptions
			delta.init(args);
			delta.loadProjectSnapshot(false); //Need all descriptions loaded.
			delta.loadUSLangRefsets();
			delta.postInit();
			delta.process();
			delta.createOutputArchive();
		} finally {
			delta.finish();
		}
	}

	private void loadUSLangRefsets() throws IOException, TermServerScriptException {
		ZipInputStream zis = new ZipInputStream(new FileInputStream(getInputFile()));
		ZipEntry ze = zis.getNextEntry();
		try {
			while (ze != null) {
				if (!ze.isDirectory()) {
					String fileName = Paths.get(ze.getName()).getFileName().toString();
					if(fileName.contains("Language") && fileName.contains("Snapshot")) {
						LOGGER.info("Loading Language Reference Set File - " + fileName);
						loadLanguageFile(zis);
					}
				}
				ze = zis.getNextEntry();
			}
		}  finally {
			try{
				zis.closeEntry();
				zis.close();
			} catch (Exception e){} //Well, we tried.
		}
	}

	public void loadLanguageFile(InputStream is) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String line;
		int linesLoaded = 0;
		while ((line = br.readLine()) != null) {
			String[] lineItems = line.split(FIELD_DELIMITER);
			if (!lineItems[IDX_MODULEID].equals(US_MODULE)
					|| !lineItems[LANG_IDX_REFSETID].equals(GB_ENG_LANG_REFSET)) {
				continue;
			}
			LangRefsetEntry lrs = LangRefsetEntry.fromRf2(lineItems);
			List<LangRefsetEntry> entries = descriptionToGbLangRefset.get(lrs.getReferencedComponentId());
			if (entries == null) {
				entries = new java.util.ArrayList<>();
				descriptionToGbLangRefset.put(lrs.getReferencedComponentId(), entries);
			}
			linesLoaded++;
			entries.add(lrs);
		}
		LOGGER.info("Loaded {} lines for {} descriptions", linesLoaded, descriptionToGbLangRefset.size());
	}


	private void process() throws ValidationFailure, TermServerScriptException {
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (c.getId().equals("1076391000119106")) {
				LOGGER.debug("Check what's happening here");
			}
			//Is the concept in the US namespace?
			if (hasUSNamespace(c.getId())) {
				continue;
			}

			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getId().equals("800611000124115")) {
					LOGGER.debug("Check what's happening here");
				}
				//For each new gb langrefset, check if we have one for this description
				//in the US edition and if so, swap it in.
				for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE, RF2Constants.GB_ENG_LANG_REFSET)) {
					if (l.isReleased() || !descriptionToGbLangRefset.containsKey(d.getDescriptionId())) {
						continue;
					}
					for (LangRefsetEntry usGbLRS : descriptionToGbLangRefset.get(d.getDescriptionId())) {
						//We're expecting the US GB version to be inactive and have a different UUID from the INT GB one.
						if (!usGbLRS.getId().equals(l.getId())) {
							//Check that the US GB version is inactive
							if (usGbLRS.isActive()) {
								LOGGER.debug("Check what's happening here");
							}
							//Make sure we don't already have this UUID in the International Edition
							if (gl.getComponentMap().containsKey(usGbLRS.getId())) {
								LOGGER.debug("Check what's happening here");
							}
							//Inactivate the INT version (this should result in a delta) and promote the US one.
							l.setActive(false);
							report (c, Severity.MEDIUM, ReportActionType.LANG_REFSET_DELETED, l);
							usGbLRS.setActive(true);
							usGbLRS.setEffectiveTime(null);
							usGbLRS.setModuleId(RF2Constants.SCTID_CORE_MODULE);
							d.getLangRefsetEntries().add(usGbLRS);
							report (c, Severity.MEDIUM, ReportActionType.LANG_REFSET_MODIFIED, usGbLRS);
							incrementSummaryInformation("Lang Refsets swapped out");
						}
					}
				}
			}
		}
	}

	private boolean hasUSNamespace(String sctId) {
		for (String ns : US_NAMESPACES) {
			if (sctId.contains(ns)) {
				return true;
			}
		}
		return false;
	}

}
