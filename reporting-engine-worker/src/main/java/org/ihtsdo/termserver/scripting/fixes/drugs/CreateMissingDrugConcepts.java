package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.AncestorsCache;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.*;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

/**
 * DRUGS-515 - Create MPF-containing concepts where required.  Identify missing MPFs
 * by looking at all CDs which do not have an MPF as an inferred parent
 * 
 * DRUGS-511 - Create MP-containing concepts where required.  Identify missing MPs
 * by looking at MPFs without an inferred parent containing the same ingredients
 *
 * DRUGS-403 - Create MP-Only concepts where required.  Identify one-to-one mapping
 * between "MP-containing" and "P containing only".  Skip excepted substances
 * 
 * DRUGS-671 Create missing MP and MPFs across all drugs
 * 
 * DRUGS-814 Changes now that we're working with axioms.  Ingredients self grouped.
 */
public class CreateMissingDrugConcepts extends DrugBatchFix implements RF2Constants, ReportClass {
	
	DrugTermGenerator termGenerator = new DrugTermGenerator(this);
	
	Set<Concept> createMPFs = new HashSet<>();
	Set<Concept> knownMPFs = new HashSet<>();
	
	Set<Concept> createMPs = new HashSet<>();
	Set<Concept> knownMPs = new HashSet<>();
	
	Set<Concept> createMPOs = new HashSet<>();
	Set<Concept> knownMPOs = new HashSet<>();
	
	Set<Concept> createMPFOs = new HashSet<>();
	Set<Concept> knownMPFOs = new HashSet<>();
	
	String[] substanceExceptions = new String[] {}; //{"liposome"};
	String[] complexExceptions = new String[] {}; //{ "lipid", "phospholipid", "cholesteryl" };
	
	Set<Concept> allowMoreSpecificDoseForms = new HashSet<>();
	
	Set<String> suppress = new HashSet<>();
	
	public CreateMissingDrugConcepts() {
		super(null);
	}
	
	protected CreateMissingDrugConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException, InterruptedException {
		CreateMissingDrugConcepts fix = new CreateMissingDrugConcepts(null);
		try {
			ReportSheetManager.targetFolderId="1SQw8vYXeB-LYPfoVzWwyGFjGp1yre2cT";  //Content Reporting Artefacts/Drugs/CreateMissingDrugConcepts
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
			fix.selfDetermining = true;
			fix.classifyTasks = false;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	public void runJob() throws TermServerScriptException {
		processFile();
	}
	
	@Override
	public Job getJob() {
		return new Job(	new JobCategory(JobType.REPORT, JobCategory.DRUGS),
						"Missing MP MPF concepts",
						"This report lists MP/MPF concepts which should be there, but aren't.",
						new JobParameters(), ProductionStatus.PROD_READY);
	}
	
	protected void init(JobRun jobRun) throws TermServerScriptException {
		ReportSheetManager.targetFolderId="1SQw8vYXeB-LYPfoVzWwyGFjGp1yre2cT";  //Content Reporting Artefacts/Drugs/CreateMissingDrugConcepts
		populateEditPanel = true;
		populateTaskDescription = true;
		selfDetermining = true;
		classifyTasks = false;
		
		//Have we been called via the reporting platform?
		if (jobRun != null) {
			super.init(jobRun);
		}
	}
	
	public void postInit() throws TermServerScriptException {
		for (Concept c : MEDICINAL_PRODUCT.getDescendents(NOT_SET)) {
			/*if (c.getConceptId().equals("774313006")) {
				debug("Here");
			}*/
			SnomedUtils.populateConceptType(c);
			if (c.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM)) {
				knownMPFs.add(c);
			} else if (c.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT)) {
				knownMPs.add(c);
			} else if (c.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_ONLY)) {
				knownMPOs.add(c);
			} else if (c.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM_ONLY)) {
				knownMPFOs.add(c);
			}
		}
		
		allowMoreSpecificDoseForms.add(gl.getConcept("764671009 | Sublingual dose form (dose form)|"));
		allowMoreSpecificDoseForms.add(gl.getConcept("766964003 | Oropharyngeal dose form (dose form)|"));
		allowMoreSpecificDoseForms.add(gl.getConcept("765167000 | Nasal or ocular or otic dose form (dose form)|"));
		allowMoreSpecificDoseForms.add(gl.getConcept("765166009 | Ocular or otic dose form (dose form)|"));
		allowMoreSpecificDoseForms.add(gl.getConcept("772805002 | Endotracheopulmonary dose form (dose form)|"));
		allowMoreSpecificDoseForms.add(gl.getConcept("772806001 | Buccal dose form (dose form)||"));
		
		suppress.add("Product containing human alpha1 proteinase inhibitor (medicinal product)");
		suppress.add("Product containing Influenza virus vaccine (medicinal product)");
		suppress.add("Product containing amino acid (medicinal product)");
		suppress.add("Product containing pituitary follicle stimulating hormone (medicinal product)");
		suppress.add("Product containing recombinant antihemophilic factor (medicinal product)");
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		List<Concept> conceptsRequired = new ArrayList<>();
		ConceptType[] targetTypes;
		
		//What do we expect to create based on this type of this input concept?
		switch (concept.getConceptType()) {
			case CLINICAL_DRUG : 
				targetTypes = new ConceptType[]{	ConceptType.MEDICINAL_PRODUCT_FORM};
				break;
			case MEDICINAL_PRODUCT_FORM : 
				targetTypes = new ConceptType[]{	ConceptType.MEDICINAL_PRODUCT,
													ConceptType.MEDICINAL_PRODUCT_FORM_ONLY };
				break;
			case MEDICINAL_PRODUCT : 
				targetTypes = new ConceptType[]{	ConceptType.MEDICINAL_PRODUCT_ONLY};
				break;
			default : throw new IllegalStateException("Unexpected driver concept type: " + concept.getConceptType());
		}
	
		for (ConceptType targetType : targetTypes) {
			Concept required = calculateDrugRequired(loadedConcept, targetType);
			//Need to check again if we need this concept, because an MPF could cause MP and MPFO to come into being
			if (targetType.equals(ConceptType.MEDICINAL_PRODUCT) && isContained(required, knownMPs)) {
				continue;
			}
			conceptsRequired.add(required);
		}
		
		for (Concept required : conceptsRequired) {
			termGenerator.ensureTermsConform(task, required, CharacteristicType.STATED_RELATIONSHIP);
			required.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
			report (task, concept, Severity.NONE, ReportActionType.INFO, "Concepts suggests need for :" + required);
			
			//Are we suppressing this concept?
			if (suppress.contains(required.getFsn())) {
				report (task, concept, Severity.MEDIUM, ReportActionType.INFO, "Concept suppressed", required);
				//And remove from the task
				task.remove(loadedConcept);
			} else {
				String expression = required.toExpression(CharacteristicType.STATED_RELATIONSHIP);
				required = createConcept(task, required, info);
				if (required.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT) || 
					required.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM)) {
					ConceptType invalidParentType = required.getConceptType();  //Up a level should have different type
					String currentParents = concept.getParents(CharacteristicType.INFERRED_RELATIONSHIP)
							.stream()
							.filter(parent -> parent.getConceptType().equals(invalidParentType))
							.map(parent -> parent.toString())
							.collect(Collectors.joining(",\n"));
					report (task, concept, Severity.LOW, ReportActionType.INFO, "Existing parents considered insufficient: " + (currentParents.isEmpty() ? "None detected" : currentParents));
				}
				task.addAfter(required, concept);
				//With the CD reported, we don't actually need to load it in the edit panel
				task.remove(concept);
				report (task, required, Severity.LOW, ReportActionType.CONCEPT_ADDED, required);
				addSummaryInformation("Concept created: " +required, "");
				incrementSummaryInformation(ISSUE_COUNT);
				report (task, required, Severity.LOW, ReportActionType.INFO, expression);
			}
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
/*			if (straw.getConceptId().equals("41193000")) {
				debug ("debug here also");
			}*/
			
			//Do a simple sum check to see if we can rule out a match early doors
			if (straw.getStatedRelSum() != needle.getStatedRelSum()) {
				continue;
			}
			
			//We need to filter out the "Plays role" attribute since we don't know when those might pop up and we
			//don't model them for our missing concepts
			List<Relationship> strawRels = straw.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE).stream()
											.filter(r -> !r.getType().equals(PLAYS_ROLE))
											.collect(Collectors.toList());

			for (Relationship thisRel : needleRels) {
				boolean relMatchFound = false;
				for (Relationship thatRel : strawRels) {
					//Active ingredient is now self-grouped, so just check type/value in that case
					//Since we can't be sure what group number will be used for multi-ingredients
					if (thisRel.equals(thatRel)) {
						relMatchFound = true;
						break;
					} else if (thisRel.getType().equals(HAS_ACTIVE_INGRED) || thisRel.getType().equals(HAS_PRECISE_INGRED)) {
						if (thisRel.equalsTypeValue(thatRel)) {
							relMatchFound = true;
							break;
						}
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
	
	private Concept calculateDrugRequired(Concept c, ConceptType targetType) throws TermServerScriptException {
		//For each ingredient, find the base substance (if relevant) and create 
		//an MPF using the dose form
		Concept drug = SnomedUtils.createConcept(null,null,MEDICINAL_PRODUCT);
		drug.setConceptType(targetType);
		
		//Only if we're creating an MPF, include the dose form
		if (targetType.equals(ConceptType.MEDICINAL_PRODUCT_FORM) || targetType.equals(ConceptType.MEDICINAL_PRODUCT_FORM_ONLY) ) {
			Concept doseForm = SnomedUtils.getTarget(c, new Concept[] {HAS_MANUFACTURED_DOSE_FORM}, UNGROUPED, CharacteristicType.STATED_RELATIONSHIP);
			if (doseForm == null) {
				throw new IllegalStateException("MPF with no active stated dose form: " + c);
			}
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
		
		//Only if we're creating an "Only", include the ingredient count
		if (targetType.equals(ConceptType.MEDICINAL_PRODUCT_ONLY) || targetType.equals(ConceptType.MEDICINAL_PRODUCT_FORM_ONLY)) {
			Relationship countRel = new Relationship (drug, COUNT_BASE_ACTIVE_INGREDIENT, DrugUtils.getNumberAsConcept(baseIngredients.size()), UNGROUPED);
			drug.addRelationship(countRel);
		}
		
		//Active ingredient is now self grouped
		int groupId = 0;
		for (Concept base : baseIngredients) {
			Relationship ingredRel = new Relationship (drug, HAS_ACTIVE_INGRED, base, ++groupId);
			drug.addRelationship(ingredRel);
		}
		return drug;
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		debug("Identifying concepts to process");
		List<Concept> allAffected = new ArrayList<Concept>(); 
		nextConcept:
		for (Concept c : MEDICINAL_PRODUCT.getDescendents(NOT_SET)) {
		//for (Concept c : Collections.singleton(gl.getConcept("350210009"))) {
			try {
				if (c.getConceptType().equals(ConceptType.CLINICAL_DRUG)) {
					Concept mpf = calculateDrugRequired(c, ConceptType.MEDICINAL_PRODUCT_FORM);
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
								report ((Task)null, c, Severity.HIGH, ReportActionType.SKIPPING, "Existing parent : " + currentMPF + " is more specific than proposed (expression): " + mpf.toExpression(CharacteristicType.STATED_RELATIONSHIP));
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
					Concept mp = calculateDrugRequired(c, ConceptType.MEDICINAL_PRODUCT);
					//If the concept has a more specific mp than the one we've calculated, warn 
					List<Concept> currentMPs = c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)
							.stream()
							.filter(parent -> parent.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT))
							.collect(Collectors.toList());
					for (Concept currentMP : currentMPs) {
						if (SnomedUtils.hasMoreSpecificModel(currentMP, mp, AncestorsCache.getAncestorsCache())) {
							report ((Task)null, c, Severity.HIGH, ReportActionType.SKIPPING, "Existing parent : " + currentMP + " is more specific that proposed: " + mp.toExpression(CharacteristicType.STATED_RELATIONSHIP));
							continue nextConcept;
						}
					}
					//Do we already know about this concept or already have a plan to create it?
					if (!isContained(mp, knownMPs) && !isContained(mp, createMPs)) {
						createMPs.add(mp);
						allAffected.add(c);
					}
					
					//For a given MPF, as well as looking for an MP, we may also need a sibling MPFO
					if (containsExceptionSubstance(c)) {
						continue nextConcept;
					}
					Concept mpfo = calculateDrugRequired(c, ConceptType.MEDICINAL_PRODUCT_FORM_ONLY);
					//Do we already know about this concept or already have a plan to create it?
					if (!isContained(mpfo, knownMPFOs) && !isContained(mpfo, createMPFOs)) {
						createMPFOs.add(mpfo);
						if (!allAffected.contains(c)) {
							allAffected.add(c);
						}
					}
				} else if (c.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT)) {
					if (containsExceptionSubstance(c)) {
						continue nextConcept;
					}
					Concept mpo = calculateDrugRequired(c, ConceptType.MEDICINAL_PRODUCT_ONLY);
					//Do we already know about this concept or already have a plan to create it?
					if (!isContained(mpo, knownMPOs) && !isContained(mpo, createMPOs)) {
						createMPOs.add(mpo);
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

	private boolean containsExceptionSubstance(Concept c) throws TermServerScriptException {
		for (Concept ingred : DrugUtils.getIngredients(c, CharacteristicType.INFERRED_RELATIONSHIP)) {
			for (String exceptionStr: substanceExceptions) {
				if (ingred.getFsn().toLowerCase().contains(exceptionStr)) {
					report ((Task)null, c, Severity.LOW, ReportActionType.SKIPPING, "MP contains exception substance: " + ingred);
					return true;
				}
			}
			
			if (ingred.getFsn().toLowerCase().contains("complex")) {
				for (String exceptionStr: complexExceptions) {
					if (ingred.getFsn().toLowerCase().contains(exceptionStr)) {
						report ((Task)null, c, Severity.LOW, ReportActionType.SKIPPING, "MP contains exception substance complex: " + ingred);
						return true;
					}
				}
			}
		}
		return false;
	}
}
