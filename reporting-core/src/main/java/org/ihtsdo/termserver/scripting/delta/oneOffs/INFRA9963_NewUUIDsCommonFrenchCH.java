package org.ihtsdo.termserver.scripting.delta.oneOffs;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class INFRA9963_NewUUIDsCommonFrenchCH extends DeltaGenerator implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(INFRA9963_NewUUIDsCommonFrenchCH.class);

	public static String SCTID_CF_LRS = "21000241105";   //Common French Language Reference Set
	public static String SCTID_CF_MOD = "11000241103";   //Common French Module
	public static String SCTID_CH_MOD = "2011000195101"; //Swiss Module
	public static String SCTID_CH_LRS = "2021000195106"; //Swiss French Language Reference Set
	
	Map<String, LangRefsetEntry> cfLangRefsetMapSept = new HashMap<>();
	Map<String, List<Description>> cfConceptDescMapSept = new HashMap<>();
	Map<String, InactivationIndicatorEntry> cfInactivationIndicatorsMapSept = new HashMap<>();
	public static String ET = "20220930";
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		INFRA9963_NewUUIDsCommonFrenchCH delta = new INFRA9963_NewUUIDsCommonFrenchCH();
		try {
			delta.targetModuleId = SCTID_CH_MOD;
			delta.runStandAlone = true;
			//delta.inputFileHasHeaderRow = true;
			delta.newIdsRequired = false; 
			delta.init(args);
			delta.loadProjectSnapshot(false);
			delta.postInit();
			delta.process();
			delta.getRF2Manager().flushFiles(true);  //Flush and Close
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
			if (delta.descIdGenerator != null) {
				delta.descIdGenerator.finish();
			}
		}
	}
	
	public void process() throws TermServerScriptException {
		try {
			loadCommonFrenchRelease();
		} catch (IOException e) {
			throw new TermServerScriptException(e);
		}
		int conceptsProcessed = 0;
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (conceptsProcessed++%10000==0) {
				LOGGER.info("Concepts processed: " + (conceptsProcessed-1));
				getRF2Manager().flushFiles(false);
			}
			processConcept(c);
		}
		getRF2Manager().flushFiles(false);
	}
	
	private void processConcept(Concept c) throws TermServerScriptException {
		/*if (c.getId().equals("138875005")) {
			debug("here");
		}*/
		//Do we have a new / modified description for this concept?
		if (cfConceptDescMapSept.containsKey(c.getId())) {
			for (Description d : cfConceptDescMapSept.get(c.getId())) {
				if (d.getEffectiveTime().equals(ET)) {
					Description orig = c.getDescription(d.getId());
					String msg = "Is new";
					ReportActionType action = ReportActionType.DESCRIPTION_CHANGE_MADE;
					if (orig != null) {
						if (orig.isActive() && !d.isActive()) {
							action = ReportActionType.DESCRIPTION_INACTIVATED;
							msg = null;
							//And copy over the lang refset entries
							d.getLangRefsetEntries().addAll(orig.getLangRefsetEntries());
						} else {
							msg = "Was: " + orig;
						}
					}
					d.setDirty();
					c.addDescription(d);
					report(c, Severity.LOW, action, d, msg);
				}
			}
		}
		
		for (Description d : c.getDescriptions("fr", ActiveState.BOTH)) {
			//Do I have a Common French LRS for this description
			LangRefsetEntry cf = cfLangRefsetMapSept.get(d.getId());
			String alreadyReported = null;
			if (d.isActive() && d.getLangRefsetEntries(ActiveState.ACTIVE, SCTID_CH_LRS).size() == 0) {
				if (cf == null && d.getLangRefsetEntries(ActiveState.ACTIVE, SCTID_CF_LRS).size() == 0) {
					report(c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, d, "Active description has no Langrefset entry", d);
				} else {
					//Do we need to inactivate the existing lang refset entry?
					LangRefsetEntry lrsToClone = cf;
					String info = "From Snapshot, ";
					for (LangRefsetEntry cfExisting : d.getLangRefsetEntries(ActiveState.ACTIVE, SCTID_CF_LRS)) {
						lrsToClone = cfExisting;
						info = "From TS, ";
						cfExisting.setActive(false);
						report(c, Severity.LOW, ReportActionType.LANG_REFSET_INACTIVATED, d, cfExisting.toStringWithModule());
					}
					LangRefsetEntry ch = lrsToClone.clone(d.getId(), false);
					ch.setActive(true);
					ch.setRefsetId(SCTID_CH_LRS);
					ch.setModuleId(SCTID_CH_MOD);
					d.addLangRefsetEntry(ch, false);
					alreadyReported = ch.getId();
					report(c, Severity.LOW, ReportActionType.LANG_REFSET_CLONED, d, ch.toStringWithModule(), info + "was: " + lrsToClone.toStringWithModule());
				}
			}
			
			for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE, SCTID_CH_LRS)) {
				//Do I need to replace this LRS entry?
				if (l.getId().equals(alreadyReported)) {
					continue;
				} else if (cf != null && !cf.getId().equals(l.getId())) {
					report(c, Severity.NONE, ReportActionType.NO_CHANGE, d, "Existing CH LRS has unique ID", cf, l);
				} else if (cf != null) {
					LangRefsetEntry cloneLRS = l.clone(d.getId(), false);
					l.setActive(false);
					c.setModified();
					report(c, Severity.LOW, ReportActionType.LANG_REFSET_INACTIVATED, d, l.toStringWithModule());
					d.addLangRefsetEntry(cloneLRS, false);  //Is replacement, so don't modify existing refsets
					report(c, Severity.LOW, ReportActionType.LANG_REFSET_CREATED, d, cloneLRS.toStringWithModule());
					if (!cloneLRS.getModuleId().equals(SCTID_CH_MOD)) {
						report(c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, d, "CF LRS not in Swiss Module");
					}
				} else {
					report(c, Severity.NONE, ReportActionType.NO_CHANGE, d, "Existing CH LRS does not exist in CF", l);
				}
				
				//Do we have a modified inactivation indicator for this description?
				if (cfInactivationIndicatorsMapSept.containsKey(d.getId())) {
					if (d.isActive()) {
						LOGGER.warn("Check inactivation indicator on active description " + d);
					}
					InactivationIndicatorEntry i = cfInactivationIndicatorsMapSept.get(d.getId());
					if (i.getEffectiveTime().equals(ET)) {
						i.setDirty();
						d.addInactivationIndicator(i);
						report(c, Severity.LOW, ReportActionType.LANG_REFSET_INACTIVATED, d, l);
					}
				}
			}
		}
		
		if (c.isModified()) {
			incrementSummaryInformation("Concepts modified");
			outputRF2(c, true);  //Will only output dirty fields.
		}
	}
	
	private void loadCommonFrenchRelease() throws IOException, TermServerScriptException {
		LOGGER.info("Loading " + getInputFile());
		ZipInputStream zis = new ZipInputStream(new FileInputStream(getInputFile()));
		ZipEntry ze = zis.getNextEntry();
		try {
			while (ze != null) {
				if (!ze.isDirectory()) {
					Path path = Paths.get(ze.getName());
					loadFile(path, zis, SNAPSHOT);
				}
				ze = zis.getNextEntry();
			}
		}  finally {
			try{
				zis.closeEntry();
				zis.close();
			} catch (Exception e){} //Well, we tried.
		}
		LOGGER.info("Finished Loading " + getInputFile());
	}
	
	private void loadFile(Path path, InputStream is, String fileType)  {
		try {
			String fileName = path.getFileName().toString();
			if (fileName.contains("._")) {
				return;
			}
			
			if (fileName.contains(fileType)) {
				if (fileName.contains("sct2_Description_" )) {
					LOGGER.info("Loading Description " + fileType + " file.");
					loadDescriptionFile(is);
				} else if (fileName.contains("der2_cRefset_AttributeValue" )) {
					LOGGER.info("Loading Concept/Description Inactivation Indicators " + fileType + " file.");
					loadInactivationIndicatorFile(is);
				} else if (fileName.contains("Language")) {
					LOGGER.info("Loading " + fileType + " Language Reference Set File - " + fileName);
					loadLanguageFile(is);
				}
			}
		} catch (TermServerScriptException | IOException e) {
			throw new IllegalStateException("Unable to load " + path + " due to " + e.getMessage(), e);
		}
	}
	
	public void loadDescriptionFile(InputStream is) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				Description d = new Description();
				Description.fillFromRf2(d, lineItems);
				Concept c = gl.getConcept(d.getConceptId(), false, true);
				List<Description> descriptions = cfConceptDescMapSept.get(c.getId());
				if (descriptions == null) {
					descriptions = new ArrayList<>();
					cfConceptDescMapSept.put(c.getId(), descriptions);
				}
				descriptions.add(d);
			} else {
				isHeaderLine = false;
			}
		}
	}
	
	public void loadLanguageFile(InputStream is) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				LangRefsetEntry langRefsetEntry = LangRefsetEntry.fromRf2(lineItems);
				String descId = langRefsetEntry.getReferencedComponentId();
				if (cfLangRefsetMapSept.containsKey(descId)) {
					throw new IllegalStateException("Multiple LangRefsets Encountered for " + descId);
				}
				cfLangRefsetMapSept.put(descId, langRefsetEntry);
			} else {
				isHeaderLine = false;
			}
		}
	}
	
	public void loadInactivationIndicatorFile(InputStream is) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				InactivationIndicatorEntry i = InactivationIndicatorEntry.fromRf2(lineItems);
				cfInactivationIndicatorsMapSept.put(i.getReferencedComponentId(), i);
			} else {
				isHeaderLine = false;
			}
		}
	}

}
