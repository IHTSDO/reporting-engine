package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

import us.monoid.json.JSONObject;

/*
For DRUG-398
Driven by a text file of concepts, add the active ingredient (disposition) specified in the substance map
Normalize the terms (using the disposition) and set the semantic tag to (product)
*/
public class NormalizeGroupersViaDisposition extends DrugBatchFix implements RF2Constants{
	
	Map<String, String> substancesMap = new HashMap<>();
	
	protected NormalizeGroupersViaDisposition(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		NormalizeGroupersViaDisposition fix = new NormalizeGroupersViaDisposition(null);
		try {
			fix.reportNoChange = true;
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
			fix.additionalReportColumns = "ACTION_DETAIL, ATTRIBUTE_COUNT";
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); 
			fix.postLoadInit();
			fix.startTimer();
			fix.processFile();
			println ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changes = modelGroupers (task, loadedConcept);
		changes += normalizeGrouperTerms (task, loadedConcept);
		
		if (loadedConcept.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
			if (countAttributes(loadedConcept) > 0) {
				loadedConcept.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
				changes++;
				report (task, loadedConcept, Severity.MEDIUM, ReportActionType.CONCEPT_CHANGE_MADE, "Concept market as fully defined");
			} else {
				report (task, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Unable to mark fully defined - no attributes!");
			}
		}
		
		try {
			String conceptSerialised = gson.toJson(loadedConcept);
			debug ((dryRun ?"Dry run ":"Updating state of ") + loadedConcept + info);
			if (!dryRun) {
				tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
			}
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changes;
	}

	private int modelGroupers(Task task, Concept loadedConcept) throws TermServerScriptException {
		
		String attributeCount = Integer.toString(countAttributes(loadedConcept));
		
		//What is our new active ingredient?   Do we have that relationship already?  If not, add ungrouped.
		Concept target = getTarget(loadedConcept);
		Relationship targetRel = new Relationship (loadedConcept, HAS_ACTIVE_INGRED, target, 0);
		List<Relationship> existingRels = new ArrayList<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
				target,
				ActiveState.ACTIVE));
		
		if (existingRels.size() > 0) {
			report(task, loadedConcept, Severity.LOW, ReportActionType.NO_CHANGE, "Specified relationship already exists: " + targetRel);
			return 0;
		}
		
		loadedConcept.addRelationship(targetRel);
		String msg = "Added new active ingredient " + target;
		report (task, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, msg, attributeCount);
		return 1;
	}


	private Concept getTarget(Concept loadedConcept) throws TermServerScriptException {
		String targetStr = substancesMap.get(loadedConcept.getConceptId());
		if (targetStr == null) {
			throw new TermServerScriptException("Target substance incorrectly specified for " + loadedConcept);
		}

		return gl.getConcept(targetStr);
	}

	private int normalizeGrouperTerms(Task task, Concept loadedConcept) throws TermServerScriptException {
		//The FSN will be Product containing X (product)
		//The PT will be X product
		//A synonym will match the FSN (without semantic tag)
		
		//So work out what X is - the PT of the target substance
		String X = getTarget(loadedConcept).getPreferredSynonym(US_ENG_LANG_REFSET).getTerm();
		
		//Do we have a gb_X ?
		String gbX = null;
		Description gbDescX = getTarget(loadedConcept).getPreferredSynonym(GB_ENG_LANG_REFSET);
		if (gbDescX != null) {
			gbX = gbDescX.getTerm();
		}
		
		String fsnPartner = "Product containing " + X;
		String fsn = fsnPartner + " (product)";
		String pt = X + " product";
		String gbPT = null;
		if (gbX != null) {
			gbPT = gbX + " product";
		}
		
		replaceFSN(task, loadedConcept, fsn);
		replacePT(task, loadedConcept, pt, gbPT);
		
		//And add the fsnPartner if it doesn't already exist
		if (!termAlreadyExists(loadedConcept, fsnPartner)) {
			Description d = Description.withDefaults(fsnPartner, DescriptionType.SYNONYM);
			loadedConcept.addDescription(d);
			report (task, loadedConcept, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, fsnPartner);
		}
		return 1;
 	}

	private void replaceFSN(Task t, Concept c, String fsn) {
		//Get the FSN and - check we're making a change - replace it.
		Description fsnDesc = c.getFSNDescription();
		if (!fsnDesc.getTerm().equals(fsn)) {
			replaceTerm (t, c, fsnDesc, fsn, AcceptabilityMode.PREFERRED_BOTH);
		} else {
			report (t, c, Severity.LOW, ReportActionType.NO_CHANGE, "No change required to FSN");
		}
	}

	private void replacePT(Task t, Concept c, String pt, String gbPT) throws TermServerScriptException {
		AcceptabilityMode mode = AcceptabilityMode.PREFERRED_BOTH;
		if (gbPT != null) {
			mode = AcceptabilityMode.PREFERRED_US;
		}
		List<Description> existingPTs = c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
		if (existingPTs.size() == 1) {
			//We're replacing one PT with two
			replaceTerm(t, c, existingPTs.get(0), pt, mode);
			if (gbPT != null) {
				Description gbDesc = existingPTs.get(0).clone(null);
				gbDesc.setTerm(gbPT);
				gbDesc.setActive(true);
				gbDesc.setAcceptabilityMap(createAcceptabilityMap(AcceptabilityMode.PREFERRED_GB));
			}
		} else {
			//We're replacing each PT with its appropriate partner
			Description usPTDesc = c.getPreferredSynonym(US_ENG_LANG_REFSET);
			replaceTerm(t, c, usPTDesc, pt, AcceptabilityMode.PREFERRED_US);
			Description gbPTDesc = c.getPreferredSynonym(GB_ENG_LANG_REFSET);
			replaceTerm(t, c, gbPTDesc, gbPT, AcceptabilityMode.PREFERRED_GB);
		}
	}

	private void replaceTerm(Task t, Concept c, Description d, String newTerm, AcceptabilityMode mode) {
		
		if (termAlreadyExists(c, newTerm)) {
			report (t, c, Severity.LOW, ReportActionType.NO_CHANGE, "Term already exists: " + newTerm);
			return;
		} 
		
		Description newDesc = d.clone(null);
		newDesc.setTerm(newTerm);
		
		//Are we deleting or inactivating the old description?
		String action = "";
		if (d.getEffectiveTime() == null || d.getEffectiveTime().isEmpty()) {
			c.removeDescription(d);
			action = "Deleted ";
		} else {
			d.setActive(false);
			d.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			action = "Inactivated ";
		}
		c.addDescription(newDesc);
		newDesc.setAcceptabilityMap(createAcceptabilityMap(mode));
		String msg = action + d + " in favour of " + newTerm + " (" + mode + ")";
		report (t, c, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
	}

	private Integer countAttributes(Concept c) {
		int attributeCount = 0;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (!r.getType().equals(IS_A)) {
				attributeCount++;
			}
		}
		return attributeCount;
	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		String targetStr = lineItems[2];
		//Take off the PT from the column to leave just the SCTID
		int pipe = targetStr.indexOf(PIPE);
		if (pipe != -1) {
			substancesMap.put(lineItems[0], targetStr.substring(0, pipe).trim());
		}
		return c;
	}
}
