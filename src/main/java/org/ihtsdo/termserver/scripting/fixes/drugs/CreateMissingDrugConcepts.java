package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.template.AncestorsCache;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
 * DRUGS-515 - Create MPF-containing concepts where required.  Identify missing MPFs
 * by looking at all CDs which do not have an MPF as an inferred parent
 * 
 * DRUGS-511 - Create MP-containing concepts where required.  Identify missing MPs
 * by looking at MPFs without an inferred parent containing the same ingredients
 */
public class CreateMissingDrugConcepts extends DrugBatchFix implements RF2Constants{
	
	DrugTermGenerator termGenerator = new DrugTermGenerator(this);
	
	Set<Concept> createMPFs = new HashSet<>();
	Set<Concept> knownMPFs = new HashSet<>();
	
	Set<Concept> createMPs = new HashSet<>();
	Set<Concept> knownMPs = new HashSet<>();
	
	Set<Concept> allowMoreSpecificDoseForms = new HashSet<>();
	
	enum Mode { CREATE_MP, CREATE_MPF }
	
	protected CreateMissingDrugConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		CreateMissingDrugConcepts fix = new CreateMissingDrugConcepts(null);
		try {
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
			fix.selfDetermining = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.postInit();
			fix.batchProcess(fix.formIntoBatch());
		} finally {
			fix.finish();
		}
	}
	
	private void postInit() throws TermServerScriptException {
		for (Concept c : MEDICINAL_PRODUCT.getDescendents(NOT_SET)) {
			SnomedUtils.populateConceptType(c);
			if (c.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM)) {
				knownMPFs.add(c);
			}
			if (c.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT)) {
				knownMPs.add(c);
			}
		}
		
		allowMoreSpecificDoseForms.add(gl.getConcept("764671009 | Sublingual dose form (dose form)|"));
		allowMoreSpecificDoseForms.add(gl.getConcept("766964003 | Oropharyngeal dose form (dose form)|"));
		allowMoreSpecificDoseForms.add(gl.getConcept("765167000 | Nasal or ocular or otic dose form (dose form)|"));
		allowMoreSpecificDoseForms.add(gl.getConcept("765166009 | Ocular or otic dose form (dose form)|"));
		allowMoreSpecificDoseForms.add(gl.getConcept("772805002 | Endotracheopulmonary dose form (dose form)|"));
		allowMoreSpecificDoseForms.add(gl.getConcept("772806001 | Buccal dose form (dose form)||"));
	}

	protected void init(String[] args) throws TermServerScriptException, IOException {
		super.init(args);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		Concept required;
		Mode mode;
		ConceptType targetType;
		
		//Do we require an MP or MPF?
		switch (concept.getConceptType()) {
			case CLINICAL_DRUG : mode = Mode.CREATE_MPF;
				targetType = ConceptType.MEDICINAL_PRODUCT_FORM;
				break;
			case MEDICINAL_PRODUCT_FORM : mode = Mode.CREATE_MP;
				targetType = ConceptType.MEDICINAL_PRODUCT;
				break;
			default : throw new IllegalStateException("Unexpected driver concept type: " + concept.getConceptType());
		}
		required = calculateDrugRequired(loadedConcept, mode);
		
		String currentParents = concept.getParents(CharacteristicType.INFERRED_RELATIONSHIP)
							.stream()
							.filter(parent -> parent.getConceptType().equals(targetType))
							.map(parent -> parent.toString())
							.collect(Collectors.joining(",\n"));
		if (required != null) {
			termGenerator.ensureDrugTermsConform(task, required, CharacteristicType.STATED_RELATIONSHIP, true);
			required.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
			required = createConcept(task, required, info);
			report (task, concept, Severity.LOW, ReportActionType.INFO, "Existing parents considered insufficient: " + (currentParents.isEmpty() ? "None detected" : currentParents));
			task.addAfter(required, concept);
			//With the CD reported, we don't actually need to load it in the edit panel
			task.remove(concept);
			report (task, required, Severity.LOW, ReportActionType.CONCEPT_ADDED, required);
			report (task, required, Severity.LOW, ReportActionType.INFO, required.toExpression(CharacteristicType.STATED_RELATIONSHIP));
			return CHANGE_MADE; 
		}
		return NO_CHANGES_MADE;
	}

	private boolean isContained(Concept needle, Set<Concept> haystack) {
		//Do all parents and stated relationships have an equivalent?
		//Check all stated attributes (this will include IS A rels)
		List<Relationship> needleRels = needle.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
		nextStraw:
		for (Concept straw : haystack) {
			List<Relationship> strawRels = straw.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
			if (needleRels.size() != strawRels.size()) {
				continue nextStraw;
			}
			for (Relationship thisRel : needleRels) {
				boolean relMatchFound = false;
				for (Relationship thatRel : strawRels) {
					if (thisRel.equals(thatRel)) {
						relMatchFound = true;
						break;
					}
				}
				if (!relMatchFound) {
					continue nextStraw;
				}
			}
			//If we get to here, then all needle stated rels have matched those of straw
			return true;
		}
		return false;
	}
	
	private Concept calculateDrugRequired(Concept c, Mode mode) throws TermServerScriptException {
		//For each ingredient, find the base substance (if relevant) and create 
		//an MPF using the dose form
		Concept drug = SnomedUtils.createConcept(null,null,MEDICINAL_PRODUCT);
		
		//Only if we're creating an MPF, include the dose form
		if (mode == Mode.CREATE_MPF) {
			Concept doseForm = SnomedUtils.getTarget(c, new Concept[] {HAS_MANUFACTURED_DOSE_FORM}, UNGROUPED, CharacteristicType.STATED_RELATIONSHIP);
			doseForm = SnomedUtils.getHighestAncestorBefore(doseForm, PHARM_DOSE_FORM);
			Relationship formRel = new Relationship (drug, HAS_MANUFACTURED_DOSE_FORM, doseForm, UNGROUPED);
			drug.addRelationship(formRel);
		}
		
		Set<Concept> baseIngredients = DrugUtils.getIngredients(c, CharacteristicType.INFERRED_RELATIONSHIP)
									.stream()
									.map(i -> DrugUtils.getBase(gl.getConceptSafely(i.getConceptId())))
									.collect(Collectors.toSet());
		if (baseIngredients.size() == 0) {
			throw new ValidationFailure(c,"Zero ingredients found.");
		}
		for (Concept base : baseIngredients) {
			Relationship ingredRel = new Relationship (drug, HAS_ACTIVE_INGRED, base, UNGROUPED);
			drug.addRelationship(ingredRel);
		}
		return drug;
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		debug("Identifying concepts to process");
		List<Concept> allAffected = new ArrayList<Concept>(); 
		nextConcept:
		for (Concept c : MEDICINAL_PRODUCT.getDescendents(NOT_SET)) {
			try {
				if (c.getConceptType().equals(ConceptType.CLINICAL_DRUG)) {
					Concept mpf = calculateDrugRequired(c, Mode.CREATE_MPF);
					//If the concept has a more specific mpf than the one we've calculated, warn 
					List<Concept> currentMPFs = c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)
							.stream()
							.filter(parent -> parent.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM))
							.collect(Collectors.toList());
					for (Concept currentMPF : currentMPFs) {
						//Some dose forms are allowed to be more specific
						Concept doseForm = SnomedUtils.getTarget(currentMPF, new Concept[] { HAS_MANUFACTURED_DOSE_FORM }, UNGROUPED, CharacteristicType.INFERRED_RELATIONSHIP);
						if (!allowMoreSpecificDoseForms.contains(doseForm)) {
							if (SnomedUtils.hasMoreSpecificModel(currentMPF, mpf, AncestorsCache.getAncestorsCache())) {
								report (null, c, Severity.HIGH, ReportActionType.SKIPPING, "Existing parent : " + currentMPF + " is more specific that proposed: " + mpf.toExpression(CharacteristicType.STATED_RELATIONSHIP));
								continue nextConcept;
							}
						}
					}
					//Do we already know about this concept or plan to create it?
					if (!isContained(mpf, knownMPFs) && !isContained(mpf, createMPFs)) {
						createMPFs.add(mpf);
						allAffected.add(c);
					}
				} else if (c.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM)) {
					Concept mp = calculateDrugRequired(c, Mode.CREATE_MP);
					//If the concept has a more specific mp than the one we've calculated, warn 
					List<Concept> currentMPs = c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)
							.stream()
							.filter(parent -> parent.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT))
							.collect(Collectors.toList());
					for (Concept currentMP : currentMPs) {
						if (SnomedUtils.hasMoreSpecificModel(currentMP, mp, AncestorsCache.getAncestorsCache())) {
							report (null, c, Severity.HIGH, ReportActionType.SKIPPING, "Existing parent : " + currentMP + " is more specific that proposed: " + mp.toExpression(CharacteristicType.STATED_RELATIONSHIP));
							continue nextConcept;
						}
					}
					//Do we already know about this concept or already have a plan to create it?
					if (!isContained(mp, knownMPs) && !isContained(mp, createMPs)) {
						createMPs.add(mp);
						allAffected.add(c);
					}
				} 
			} catch (Exception e) {
				warn ("Unable to process " + c + " because " + e.getMessage());
			}
		}
		info ("Identified " + allAffected.size() + " concepts to process");
		allAffected.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<Component>(allAffected);
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}
}
