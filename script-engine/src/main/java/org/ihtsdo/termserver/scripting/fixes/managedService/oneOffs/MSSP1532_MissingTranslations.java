package org.ihtsdo.termserver.scripting.fixes.managedService.oneOffs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * MSSP-1532 We have a file that was loaded in.  Check what happened to the descriptions
 */
public class MSSP1532_MissingTranslations extends BatchFix {
	
	//private static String previousReleaseBranch = "MAIN/SNOMEDCT-NL/2022-03-31";
	
	private Map<Concept, List<Description>> importedFileConceptMap = new HashMap<>();
	private Map<String, Description> importedFileDescriptionMap = new HashMap<>();
	//private boolean langRefsetLoaded =  false;
	
	protected MSSP1532_MissingTranslations(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		MSSP1532_MissingTranslations fix = new MSSP1532_MissingTranslations(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail";
			fix.inputFileHasHeaderRow = true;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		/*if (!langRefsetLoaded) {
			loadLangRefsetFile();
			langRefsetLoaded = true;
		}*/
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = checkDescriptions(task, loadedConcept);
			if (changesMade > 0) {
				updateConcept(task, loadedConcept, info);
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to process concept: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}
	
	private int checkDescriptions(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : importedFileConceptMap.get(c)) {
			//Does this concept have this description, and if so, does the term match
			Description loadedDesc = c.getDescription(d.getId());
			if (loadedDesc == null) {
				//Well is it known anywhere to the system?
				Description foundDesc = gl.getDescription(d.getId(), false, false);  //Don't create if not found.  Don't validate exists.
				if (foundDesc != null) {
					report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Expected: " + d, "Found " + foundDesc);
				} else {
					report(t, c, Severity.HIGH, ReportActionType.INFO, "Description not found, creating", d);
					c.addDescription(d);
					changesMade++;
				}
			} else {
				//Does the term match what was imported?
				if (loadedDesc.getTerm().equals(d.getTerm())) {
					report(t, c, Severity.NONE, ReportActionType.INFO, "Description imported as intended", d);
				} else {
					report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Expected: " + d, "Found " + loadedDesc);
				}
			}
		}
		return changesMade;
	}

	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		Description d = new Description(lineItems[IDX_ID]);
		Description.fillFromRf2(d, lineItems);
		importedFileDescriptionMap.put(lineItems[IDX_ID], d);
		
		Concept c = gl.getConcept(lineItems[DES_IDX_CONCEPTID], false, false);  //Don't create concept if we don't have it
		if (c == null) {
			report ((Task)null, null, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Description offered for concept not found in project", d);
			return null;
		} else {
			List<Description> descriptions = importedFileConceptMap.get(c);
			if (descriptions == null) {
				descriptions = new ArrayList<>();
				importedFileConceptMap.put(c, descriptions);
			}
			descriptions.add(d);
			return Collections.singletonList(c);
		}
		
		/*if (!d.isActive()) {
			checkPreviousStateOfComponent(c, d);
		}
		return null;
	}
	

	private void checkPreviousStateOfComponent(Concept c, Description d) throws TermServerScriptException {
		Description loadedDescription = loadDescription(d.getId(), previousReleaseBranch);
		if (loadedDescription == null) {
			report((Task)null,c, Severity.MEDIUM, ReportActionType.INFO, "Description inactive in Delta was not found in previous release", d);
		} else {
			//Now if the description was previously active and is now inactive, that's fine
			if (loadedDescription.isActive()) {
				report((Task)null,c, Severity.NONE, ReportActionType.INFO, "Description previously active now inactive.  All Good", loadedDescription);
			} else {
				report((Task)null,c, Severity.HIGH, ReportActionType.INFO, "System anomaly", loadedDescription);
			}
		}*/
	}

	private void loadLangRefsetFile() throws TermServerScriptException {
		String langRefFilePath = "G:\\My Drive\\036_ManagedService\\2022\\NL\\MSSP-1532_Missing_Translations\\Delta\\Refset\\Language\\der2_cRefset_LanguageDelta_NL_20220511.txt";
		info("Loading Lang Refset File " + langRefFilePath);
		try {
			//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
			BufferedReader br = new BufferedReader(new FileReader(langRefFilePath, StandardCharsets.UTF_8));
			String line;
			boolean isHeader = true;
			while ((line = br.readLine()) != null) {
				if (!isHeader) {
					String[] lineItems = line.split(FIELD_DELIMITER);
					LangRefsetEntry l = LangRefsetEntry.fromRf2(lineItems);
					//Do we have this description in the file we're looking at?
					String descId = l.getReferencedComponentId();
					Description d = importedFileDescriptionMap.get(descId);
					if (d == null) {
						warn(l + " relates to description not being modified in import");
					} else {
						d.addLangRefsetEntry(l);
					}
				} else {
					isHeader = false;
				}
			}
			br.close();
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to load " + langRefFilePath, e);
		}
	}

}
