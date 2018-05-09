package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONObject;

/*
DRUG-436
For a specified concept, set the attribute of interest to the value that is also 
specified in a file.
*/
public class SetAttribute extends BatchFix implements RF2Constants{
	
	Concept attributeType = HAS_MANUFACTURED_DOSE_FORM;
	Map <Concept, Concept> targetValues = new HashMap<>();
	
	protected SetAttribute(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		SetAttribute fix = new SetAttribute(null);
		try {
			fix.reportNoChange = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = true;
			fix.additionalReportColumns = "ACTION_DETAIL, DEF_STATUS, PARENT_COUNT, ATTRIBUTE_COUNT";
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); 
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		setConceptType (loadedConcept);
		
		int changesMade = setAttributeValue(task, loadedConcept);
		
		try {
			String conceptSerialised = gson.toJson(loadedConcept);
			debug ((dryRun ?"Dry run ":"Updating state of ") + loadedConcept + info);
			if (!dryRun) {
				tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
			}
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	private int setAttributeValue(Task task, Concept loadedConcept) throws TermServerScriptException {
		int changesMade = 0;
		Concept targetValue = targetValues.get(loadedConcept); 
		
		//Get the target attribute relationship and ensure there is 0 or 1
		List<Relationship> attributesOfType = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
		if (!loadedConcept.isActive()) {
			String msg = "Concept now inactive ";
			report (task, loadedConcept, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, msg, loadedConcept.getDefinitionStatus().toString(), countParents(loadedConcept), countAttributes(loadedConcept));
		} else if (attributesOfType.size() > 1) {
			String msg = "Concept stating multiple " + attributeType;
			report (task, loadedConcept, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, msg, loadedConcept.getDefinitionStatus().toString(), countParents(loadedConcept), countAttributes(loadedConcept));
		} else if (relationshipExists(loadedConcept, targetValue)) {
			List<Relationship> allExisting = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
					attributeType,
					targetValue,
					ActiveState.BOTH);
			if (allExisting.size() > 1) {
				String msg = "Mix of active / inactive existing relationship with same type/value";
				report (task, loadedConcept, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, msg, loadedConcept.getDefinitionStatus().toString(), countParents(loadedConcept), countAttributes(loadedConcept));
			} else {
				Relationship existing = allExisting.get(0);
				//If this rel is active, nothing to do.  Otherwise, activate
				if (!existing.isActive()) {
					existing.setActive(true);
					String msg = "Reactivated " + existing;
					report (task, loadedConcept, Severity.MEDIUM, ReportActionType.RELATIONSHIP_MODIFIED, msg, loadedConcept.getDefinitionStatus().toString(), countParents(loadedConcept), countAttributes(loadedConcept));
					changesMade++;
				}
				//Are there any other relationships here that need to be inactivated?
				for (Relationship checkMe : attributesOfType) {
					if (!checkMe.getTarget().equals(targetValue)) {
						checkMe.setActive(false);
						String msg = "Inactivated " + checkMe;
						report (task, loadedConcept, Severity.MEDIUM, ReportActionType.RELATIONSHIP_MODIFIED, msg, loadedConcept.getDefinitionStatus().toString(), countParents(loadedConcept), countAttributes(loadedConcept));
						changesMade++;
					}
				}
			}
		} else if ( attributesOfType.size() == 0 ) {
			//No existing attribute, we'll have to create one
			Relationship r = new Relationship (loadedConcept, attributeType, targetValue, 0);
			loadedConcept.addRelationship(r);
			changesMade++;
			report (task, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, targetValues.get(loadedConcept).toString(), loadedConcept.getDefinitionStatus().toString(), countParents(loadedConcept), countAttributes(loadedConcept));
		} else {
			//Case where yes we have 1 existing active relationship, but not with the correct target value
			Relationship current = attributesOfType.get(0);
			//Are we deleting or inactivating the current relationship?
			if (current.getEffectiveTime() == null || current.getEffectiveTime().isEmpty()) {
				//Delete unpublished relationship - we can just hijack it 
				current.setTarget(targetValues.get(loadedConcept));
				current.setRelationshipId(null);
			} else {
				//Inactivate and add a new one
				current.setActive(false);
				Relationship r = new Relationship (loadedConcept, attributeType, targetValue, 0);
				loadedConcept.addRelationship(r);
				changesMade++;
				String msg = "Replaced target " + current.getTarget() + " with " + targetValue;
				report (task, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_REPLACED, msg, loadedConcept.getDefinitionStatus().toString(), countParents(loadedConcept), countAttributes(loadedConcept));
			}
		}
		return changesMade;
	}

	private boolean relationshipExists(Concept loadedConcept, Concept targetValue) {
		List<Relationship> existing = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
															attributeType,
															targetValue,
															ActiveState.BOTH);
		return existing.size() > 0;
	}

	private void setConceptType(Concept loadedConcept) {
		String semTag = SnomedUtils.deconstructFSN(loadedConcept.getFsn())[1];
		switch (semTag) {
			case "(medicinal product form)" : loadedConcept.setConceptType(ConceptType.MEDICINAL_PRODUCT_FORM);
												break;
			case "(product)" : loadedConcept.setConceptType(ConceptType.PRODUCT);
								break;
			case "(medicinal product)" : loadedConcept.setConceptType(ConceptType.MEDICINAL_PRODUCT);
										 break;
			case "(clinical drug)" : loadedConcept.setConceptType(ConceptType.CLINICAL_DRUG);
										break;
			default : loadedConcept.setConceptType(ConceptType.UNKNOWN);
		}
	}
	
	private String countAttributes(Concept c) {
		int attributeCount = 0;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (!r.getType().equals(IS_A)) {
				attributeCount++;
			}
		}
		return Integer.toString(attributeCount);
	}
	
	private String countParents(Concept c) {
		int parentCount = 0;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getType().equals(IS_A)) {
				parentCount++;
			}
		}
		return  Integer.toString(parentCount);
	}

	@Override
	protected List<Concept> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		//Target Atribute value.   We might be missing a space, so replace pipes with spaces
		String field3 = lineItems[2].replace("|"," ");
		String targetStr = field3.split(" ")[0];
		Concept targetValue = gl.getConcept(targetStr);
		targetValues.put(c, targetValue);
		
		//If the concept has exactly one active relationship of the specified type, and the target is as desired, then 
		//we don't need to make any changes
		List<Relationship> existingTypes = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
		if (existingTypes.size() == 1 && existingTypes.get(0).getTarget().equals(targetValue)) {
			String msg = "Concept appears to be in desired state already";
			report (null, c, Severity.LOW, ReportActionType.NO_CHANGE, msg, targetValue.toString());
			c = null;
		}
		return Collections.singletonList(c);
	}

}
