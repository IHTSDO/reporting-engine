package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * MQI-5 For a specific list of concepts, switch  |Associated morphology (attribute)| value 
 * from |Cataract (morphologic abnormality)| to |Abnormally opaque structure (morphologic abnormality)|
 * DEVICES-152 Update to replace a set of attributes type/values
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplaceAttributes extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReplaceAttributes.class);

	Map<RelationshipTemplate, RelationshipTemplate> replacementMap;
	
	protected ReplaceAttributes(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ReplaceAttributes fix = new ReplaceAttributes(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = true;
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		replacementMap = new HashMap<>();
		//find = new RelationshipTemplate(ASSOC_MORPH, gl.getConcept("128306009 |Cataract (morphologic abnormality)|"));
		//replace = new RelationshipTemplate(ASSOC_MORPH, gl.getConcept("128305008 |Abnormally opaque structure (morphologic abnormality)|"));
		
		Concept hasDC = gl.getConcept("840562008 |Has device characteristic (attribute)|");
		Concept hasAb = gl.getConcept("1148969005 |Has absorbability quality (attribute)|");
		
		Concept bioAb = gl.getConcept("860574003|Bioabsorbable (qualifier value)|");
		Concept nonBioAb = gl.getConcept("863965006|Nonbioabsorbable (qualifier value)|");

		Concept sterile = gl.getConcept("261029002|Sterile (qualifier value)|");
		Concept nonSterile = gl.getConcept("863956004|Non-sterile (qualifier value)|");
		
		Concept coated = gl.getConcept("866168000|Coated with material (qualifier value)|");
		Concept nonCoated = gl.getConcept("860575002|Not coated with material (qualifier value)|");
		
		RelationshipTemplate find = new RelationshipTemplate(hasDC, bioAb);
		RelationshipTemplate replace = new RelationshipTemplate(hasAb, bioAb);
		replacementMap.put(find, replace);
		
		find = new RelationshipTemplate(hasDC, nonBioAb);
		replace = new RelationshipTemplate(hasAb, nonBioAb);
		replacementMap.put(find, replace);
		
		find = new RelationshipTemplate(hasDC, sterile);
		replace = new RelationshipTemplate(gl.getConcept("1148965004 |Is sterile (attribute)|"), gl.getConcept("31874001 |True (qualifier value)|"));
		replacementMap.put(find, replace);
		
		find = new RelationshipTemplate(hasDC, nonSterile);
		replace = new RelationshipTemplate(gl.getConcept("1148965004 |Is sterile (attribute)|"), gl.getConcept("64100000 |False (qualifier value)|"));
		replacementMap.put(find, replace);
		
		find = new RelationshipTemplate(hasDC, coated);
		replace = new RelationshipTemplate(gl.getConcept("1148968002 |Has surface quality (attribute)|"), coated);
		replacementMap.put(find, replace);
		
		find = new RelationshipTemplate(hasDC, nonCoated);
		replace = new RelationshipTemplate(gl.getConcept("1148968002 |Has surface quality (attribute)|"), nonCoated);
		replacementMap.put(find, replace);
		
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = switchValues(task, loadedConcept);
			updateConcept(task, loadedConcept, info);
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}
	
	private int switchValues(Task t, Concept c) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		for (RelationshipTemplate find : replacementMap.keySet()) {
			//Switch all stated relationships from the findValue to the replaceValue
			if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, find).size() > 0) {
				changesMade += replaceRelationship(t, c, find, replacementMap.get(find));
			}
		}
		return changesMade;
	}

	/*@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, find).size() == 0) {
			report((Task)null, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept did not contain expected stated relationship");
			return null;
		}
		return Collections.singletonList(gl.getConcept(lineItems[0]));
	}*/

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Concept> allAffected = new ArrayList<>();
		LOGGER.info("Identifying concepts to process");
		
		nextConcept:
		for (Concept c : gl.getAllConcepts()) {
			for (RelationshipTemplate rt : replacementMap.keySet()) {
				if (c.getRelationships(rt, ActiveState.ACTIVE).size() > 0) {
					allAffected.add(c);
					continue nextConcept;
				}
			}
		}
		LOGGER.info("Identified " + allAffected.size() + " concepts to process");
		allAffected.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<Component>(allAffected);
	}
}
