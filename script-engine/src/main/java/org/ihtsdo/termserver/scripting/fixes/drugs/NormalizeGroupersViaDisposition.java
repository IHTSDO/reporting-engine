package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.*;

/*
For DRUG-398
Driven by a text file of concepts, add the active ingredient (disposition) specified in the substance map
Normalize the terms (using the disposition) and set the semantic tag to (product)
*/
public class NormalizeGroupersViaDisposition extends DrugBatchFix implements ScriptConstants{

	Map<String, String> substancesMap = new HashMap<>();
	Relationship newParentRel;
	String newParent = "763158003"; // |Medicinal product (product)| 
	
	protected NormalizeGroupersViaDisposition(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		NormalizeGroupersViaDisposition fix = new NormalizeGroupersViaDisposition(null);
		try {
			fix.inputFileHasHeaderRow = true;
			fix.reportNoChange = true;
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
			fix.additionalReportColumns = "ACTION_DETAIL, ATTRIBUTE_COUNT, SYNONYM_COUNT";
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Need full descriptions so we can get PT of target (not loaded from TS)
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		Concept parentConcept =  gl.getConcept(newParent);
		newParentRel = new Relationship(null, IS_A, parentConcept, 0);
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changes = modelGroupers (t, loadedConcept);
		changes += normalizeGrouperTerms (t, loadedConcept);
		
		if (loadedConcept.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
			if (countAttributes(loadedConcept) > 0) {
				loadedConcept.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
				changes++;
				report(t, loadedConcept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept marked as fully defined");
			} else {
				report(t, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Unable to mark fully defined - no attributes!");
			}
		}
		
		updateConcept(t, loadedConcept, info);
		return changes;
	}

	private int modelGroupers(Task task, Concept loadedConcept) throws TermServerScriptException {
		
		int changeCount = setProximalPrimitiveParent(task, loadedConcept);
		changeCount += setActiveIngredient(task, loadedConcept);
		return changeCount;
	}

	private int setProximalPrimitiveParent(Task task, Concept loadedConcept) throws TermServerScriptException {
		
		int changeCount = 0;
		Set<Relationship> parentRels = new HashSet<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																	IS_A,
																	ActiveState.ACTIVE));
		
		boolean replacementRequired = true;
		for (Relationship parentRel : parentRels) {
			//Do we already have the new parent?
			if (parentRel.getTarget().equals(newParentRel.getTarget())) {
				replacementRequired = false;
				report(task, loadedConcept, Severity.LOW, ReportActionType.INFO, "Proximal primitive parent already present");
			} else {
				removeParentRelationship (task, parentRel, loadedConcept, newParentRel.getTarget().toString(), null);
				changeCount++;
			}
		}
		
		if (replacementRequired) {
			Relationship thisNewParentRel = newParentRel.clone(null);
			thisNewParentRel.setSource(loadedConcept);
			loadedConcept.addRelationship(thisNewParentRel);
		}
		return changeCount;
	}
	
	private int setActiveIngredient(Task task, Concept loadedConcept) throws TermServerScriptException {
		String attributeCount = Integer.toString(countAttributes(loadedConcept));
		//What is our new active ingredient?   Do we have that relationship already?  If not, add ungrouped.
		Concept target = getTarget(loadedConcept);
		Relationship targetRel = new Relationship (loadedConcept, HAS_ACTIVE_INGRED, target, 0);
		Set<Relationship> existingRels = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
				HAS_ACTIVE_INGRED,
				target,
				ActiveState.BOTH);
		
		if (existingRels.size() > 0) {
			//If the existing relationship is active then we have nothing more to do
			if (existingRels.iterator().next().isActive()) {
				report(task, loadedConcept, Severity.LOW, ReportActionType.NO_CHANGE, "Specified relationship already exists: " + targetRel);
				return 0;
			} else {
				//Otherwise, we'll activate it
				existingRels.iterator().next().setActive(true);
				String msg = "Reactivating inactive relationshiop - active ingredient " + target;
				report(task, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_MODIFIED, msg, attributeCount);
			}
		} else {
			loadedConcept.addRelationship(targetRel);
			String msg = "Added new active ingredient " + target;
			report(task, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, msg, attributeCount);
		}
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
		Description xDesc = getTarget(loadedConcept).getPreferredSynonym(US_ENG_LANG_REFSET);
		String X = xDesc.getTerm();
		
		//Do we have a gb_X ?
		String gbX = null;
		Description gbDescX = getTarget(loadedConcept).getPreferredSynonym(GB_ENG_LANG_REFSET);
		if (gbDescX != null && !gbDescX.getTerm().equals(X)) {
			gbX = gbDescX.getTerm();
		}
		
		CaseSignificance csOfX = xDesc.getCaseSignificance();
		
		String xInSentence = csOfX.equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE) ? X : StringUtils.deCapitalize(X);
		String fsnPartner = "Product containing " + xInSentence;
		String fsn = fsnPartner + " (product)";
		String pt = X + " product";
		String gbPT = null;
		if (gbX != null) {
			gbPT = gbX + " product";
		}
		
		replaceFSN(task, loadedConcept, fsn, csOfX);
		replacePT(task, loadedConcept, pt, gbPT, csOfX);
		
		//And add the fsnPartner if it doesn't already exist
		if (!SnomedUtils.termAlreadyExists(loadedConcept, fsnPartner)) {
			Description d = Description.withDefaults(fsnPartner, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
			d.setCaseSignificance(csOfX);
			loadedConcept.addDescription(d);
			report(task, loadedConcept, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, fsnPartner);
		}
		return 1;
 	}

	private void replaceFSN(Task t, Concept c, String fsn, CaseSignificance cs) throws TermServerScriptException {
		//Get the FSN and - check we're making a change - replace it.
		Description fsnDesc = c.getFSNDescription();
		if (!fsnDesc.getTerm().equals(fsn)) {
			replaceTerm (t, c, fsnDesc, fsn, AcceptabilityMode.PREFERRED_BOTH, cs);
		} else {
			report(t, c, Severity.LOW, ReportActionType.NO_CHANGE, "No change required to FSN");
		}
	}

	private void replacePT(Task t, Concept c, String pt, String gbPT, CaseSignificance cs) throws TermServerScriptException {
		AcceptabilityMode mode = AcceptabilityMode.PREFERRED_BOTH;
		if (gbPT != null) {
			mode = AcceptabilityMode.PREFERRED_US;
		}
		List<Description> existingPTs = c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
		if (existingPTs.size() == 1) {
			//We're replacing one PT with two
			replaceTerm(t, c, existingPTs.get(0), pt, mode, cs);
			if (gbPT != null) {
				Description gbDesc = existingPTs.get(0).clone(null);
				gbDesc.setTerm(gbPT);
				gbDesc.setActive(true);
				gbDesc.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.PREFERRED_GB));
				gbDesc.setCaseSignificance(cs);
			}
		} else {
			Description usPTDesc = c.getPreferredSynonym(US_ENG_LANG_REFSET);
			Description gbPTDesc = c.getPreferredSynonym(GB_ENG_LANG_REFSET);
			if (gbPT != null) {
				//If we have a replacement gbPt, we're replacing each PT with its appropriate partner
				replaceTerm(t, c, usPTDesc, pt, AcceptabilityMode.PREFERRED_US, cs);
				replaceTerm(t, c, gbPTDesc, gbPT, AcceptabilityMode.PREFERRED_GB, cs);
			} else {
				//Otherwise we're replacing the US preferred term, and inactivating the other.  Warn about this!
				replaceTerm(t, c, usPTDesc, pt, AcceptabilityMode.PREFERRED_BOTH, cs);
				gbPTDesc.setActive(false);
				gbPTDesc.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Replaced US/GB variants with single.  Please check");
			}
		}
	}

	private void replaceTerm(Task t, Concept c, Description d, String newTerm, AcceptabilityMode mode, CaseSignificance cs) throws TermServerScriptException {
		
		if (SnomedUtils.termAlreadyExists(c, newTerm)) {
			report(t, c, Severity.LOW, ReportActionType.NO_CHANGE, "Term already exists: " + newTerm);
			return;
		}

		//Report count of active acceptable synonyms
		String activeAcceptableCount = Integer.toString(c.getDescriptions(Acceptability.ACCEPTABLE, DescriptionType.SYNONYM, ActiveState.ACTIVE).size());
		
		Description newDesc = d.clone(null);
		newDesc.setTerm(newTerm);
		newDesc.setCaseSignificance(cs);
		
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
		newDesc.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(mode));
		String msg = action + d + " in favour of '" + newTerm + "' (" + mode.toString().toLowerCase() + ")";
		report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, msg, "", activeAcceptableCount);
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
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		if (lineItems.length > 2) {
			String targetStr = lineItems[2];
			//Take off the PT from the column to leave just the SCTID
			int pipe = targetStr.indexOf(PIPE);
			if (pipe != -1) {
				substancesMap.put(lineItems[0], targetStr.substring(0, pipe).trim());
			}
		}
		return Collections.singletonList(c);
	}
}
