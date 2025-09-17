package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.AncestorsCache;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.domain.ConcreteValue;
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
 * 
 * RP-723 Allowing report to have its claws back and actually create the concepts it proposes.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateMissingDrugConcepts extends DrugBatchFix implements ScriptConstants, ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(CreateMissingDrugConcepts.class);

	private Set<Concept> createMPFs = new HashSet<>();
	private Set<Concept> knownMPFs = new HashSet<>();

	private Set<Concept> createMPs = new HashSet<>();
	private Set<Concept> knownMPs = new HashSet<>();

	private Set<Concept> createMPOs = new HashSet<>();
	private Set<Concept> knownMPOs = new HashSet<>();

	private Set<Concept> createMPFOs = new HashSet<>();
	private Set<Concept> knownMPFOs = new HashSet<>();

	private String[] substanceExceptions = new String[] {}; //"liposome"
	private String[] complexExceptions = new String[] {}; // "lipid", "phospholipid", "cholesteryl"

	private Set<Concept> allowMoreSpecificDoseForms = new HashSet<>();

	private Set<String> suppress = new HashSet<>();
	protected boolean reportMissingUnderlyingCDs = false;
	
	private boolean newConceptsOnly = true;

	private Set<Concept> gaseousSubstances;

	public CreateMissingDrugConcepts() {
		super(null);
	}

	protected CreateMissingDrugConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(CreateMissingDrugConcepts.class, args, params);
	}

	@Override
	public void runJob() throws TermServerScriptException {
		processFile();
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(NEW_CONCEPTS_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true).withMandatory()
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.DRUGS))
				.withName("Missing MP MPF concepts")
				//.withDescription("This job creates missing MP/MPF concepts.  Options exist for only checking recently created CDs, and running as a 'Dry Run'")
				.withDescription("This job reports missing MP/MPF concepts.  Option exist for only checking recently created CDs.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withTag(INT)
				.withParameters(params)
				.build();
	}
	
	@Override
	protected void preInit() throws TermServerScriptException {
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
		populateEditPanel = true;
		populateTaskDescription = true;
		selfDetermining = true;
		classifyTasks = false;

		JobRun jobRun = getJobRun();
		newConceptsOnly = jobRun.getParamBoolean(NEW_CONCEPTS_ONLY);
		taskSize = 5;
		setDryRun(true);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Task, Desc, SctId, FSN, ConceptType, Severity, ActionType, Details, Details",
				"Task, Desc, SctId, FSN, ConceptType, Severity, ActionType, Details, Details",
				"Suppressed Concepts"};
		String[] tabNames = new String[] {
				"Missing MP/MPF Concepts",
				"Processing Issues",
				"Suppressed Concepts"};

		super.postInit(GFOLDER_DRUGS_MISSING, tabNames, columnHeadings, false);
		
		populateKnownConcepts();

		//We'll just report the results, no need to expose the working
		termGenerator.setQuiet(true);
	}

	private void populateKnownConcepts() throws TermServerScriptException {
		for (Concept c : MEDICINAL_PRODUCT.getDescendants(NOT_SET)) {
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
		suppress.add("Product containing only adenosine deaminase in parenteral dose form (medicinal product form)");
		suppress.add("Product containing only antigen of bacteria and antigen of virus (medicinal product)");
		suppress.add("Product containing only antigen of bacteria (medicinal product)");
		suppress.add("Product containing only antigen of virus (medicinal product)");
		suppress.add("Product containing only antigen of bacteria and botulinum toxoid (medicinal product)");
		suppress.add("Product containing only antigen of Rickettsia (medicinal product)");
		suppress.add("Product containing only antigen of Bordetella pertussis and antigen of Clostridium tetani and antigen of Corynebacterium diphtheriae and antigen of Hepatitis B virus (medicinal product)");
		suppress.add("Product containing only antigen of Bordetella pertussis and antigen of Clostridium tetani and antigen of Corynebacterium diphtheriae and antigen of Hepatitis B virus and antigen of Human poliovirus (medicinal product)");
		suppress.add("Product containing only antigen of Bordetella pertussis and antigen of Clostridium tetani toxoid and antigen of Corynebacterium diphtheriae toxoid and antigen of Haemophilus influenzae type B and antigen of Hepatitis B virus and antigen of Human poliovirus (medicinal product)");
		suppress.add("Product containing only antigen of Clostridium tetani and antigen of Corynebacterium diphtheriae and antigen of Haemophilus influenzae type B (medicinal product)");
		suppress.add("Product containing only antigen of Clostridium tetani and antigen of Haemophilus influenzae type B (medicinal product)");
		suppress.add("Product containing only antigen of Clostridium tetani toxoid adsorbed and antigen of Corynebacterium diphtheriae toxoid (medicinal product)");
		suppress.add("Product containing only antigen of Clostridium tetani toxoid adsorbed (medicinal product)");
		suppress.add("Product containing only antigen of Corynebacterium diphtheriae toxoid (medicinal product)");
		suppress.add("Product containing only antigen of Haemophilus influenzae type B and antigen of Hepatitis B virus (medicinal product)");
		suppress.add("Product containing only antigen of Junin virus (medicinal product)");
		suppress.add("Product containing only antigen of Leptospira (medicinal product)");
		suppress.add("Product containing only antigen of Measles morbillivirus and antigen of Mumps orthorubulavirus (medicinal product)");
		suppress.add("Product containing only antigen of Mumps orthorubulavirus and antigen of Rubella virus (medicinal product)");
		suppress.add("Product containing only antigen of Clostridium tetani toxoid adsorbed and antigen of Corynebacterium diphtheriae toxoid and antigen of whole cell Bordetella pertussis (medicinal product)");

		for (String suppressedConcept : suppress) {
			report(TERTIARY_REPORT, suppressedConcept);
		}
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
			case MEDICINAL_PRODUCT_FORM_ONLY :
				targetTypes = new ConceptType[]{	ConceptType.MEDICINAL_PRODUCT,
													ConceptType.MEDICINAL_PRODUCT_FORM };
				break;
			case MEDICINAL_PRODUCT : 
				targetTypes = new ConceptType[]{	ConceptType.MEDICINAL_PRODUCT_ONLY};
				break;
			case MEDICINAL_PRODUCT_ONLY:
				targetTypes = new ConceptType[]{	ConceptType.MEDICINAL_PRODUCT};
				break;
			default : throw new IllegalStateException("Unexpected driver concept type: " + concept.getConceptType());
		}
	
		for (ConceptType targetType : targetTypes) {
			Concept required = calculateDrugRequired(loadedConcept, targetType);
			//Need to check again if we need this concept, because an MPF could cause MP and MPFO to come into being
			if (targetType.equals(ConceptType.MEDICINAL_PRODUCT) && isContained(required, knownMPs)) {
				continue;
			}
			if (targetType.equals(ConceptType.MEDICINAL_PRODUCT_ONLY) && isContained(required, knownMPOs)) {
				continue;
			}
			conceptsRequired.add(required);
		}
		int changesMade = 0;
		for (Concept required : conceptsRequired) {
			termGenerator.ensureTermsConform(task, required, CharacteristicType.STATED_RELATIONSHIP);
			required.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
			report(task, concept, Severity.NONE, ReportActionType.INFO, "Concept suggests need for : " + required.getFsn());

			String expression = required.toExpression(CharacteristicType.STATED_RELATIONSHIP);
			required = createConcept(task, required, info);
			if (required.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT) || 
				required.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM)) {
				ConceptType invalidParentType = required.getConceptType();  //Up a level should have different type
				String currentParents = concept.getParents(CharacteristicType.INFERRED_RELATIONSHIP)
						.stream()
						.filter(parent -> parent.getConceptType().equals(invalidParentType))
						.map(Concept::toString)
						.collect(Collectors.joining(",\n"));
				//If we don't detect problems with the parents, then don't report anything
				if (!currentParents.isEmpty()) {
					report(task, concept, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Existing parents problematic due to same drug class level as this concept : " + currentParents);
				}
			}
			task.addAfter(required, concept);
			//With the CD reported, we don't actually need to load it in the edit panel
			task.remove(concept);
			report(task, required, Severity.LOW, ReportActionType.CONCEPT_ADDED, required);
			addSummaryInformation("Concept created: " +required, "");
			incrementSummaryInformation(ISSUE_COUNT);
			report(task, required, Severity.LOW, ReportActionType.INFO, expression);
		
			changesMade++;
		}
		return changesMade;
	}

	private boolean isContained(Concept needle, Set<Concept> haystack) {
		//Do all parents and stated relationships have an equivalent?
		//Check all stated attributes (this will include IS A rels)
		Set<Relationship> needleRels = needle.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
		nextStraw:
		for (Concept straw : haystack) {
			//Do a simple sum check to see if we can rule out a match early doors
			if (straw.getStatedAttribSum() != needle.getStatedAttribSum()) {
				continue;
			}
			
			//We need to filter out the "Plays role" attribute since we don't know when those might pop up and we
			//don't model them for our missing concepts
			Set<Relationship> strawRels = straw.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE).stream()
											.filter(r -> !r.getType().equals(PLAYS_ROLE))
											.collect(Collectors.toSet());

			for (Relationship thisRel : needleRels) {
				boolean relMatchFound = false;
				for (Relationship thatRel : strawRels) {
					//Active ingredient is now self-grouped, so just check type/value in that case
					//Since we can't be sure what group number will be used for multi-ingredients
					//Don't worry about matching with the originating axiom as well because
					//the IS A that's in the same axiom as the Role will throw this check out.
					//Also ignore group as ingredient may not be in expected order
					if (thisRel.equals(thatRel, true)) {
						relMatchFound = true;
						break;
					} else if (thisRel.getType().equals(HAS_ACTIVE_INGRED) || thisRel.getType().equals(HAS_PRECISE_INGRED)) {
						if (thisRel.equalsTypeAndTargetValue(thatRel)) {
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
		
		if (baseIngredients.isEmpty()) {
			throw new ValidationFailure(c,"Zero ingredients found.");
		}
		
		//Only if we're creating an "Only", include the ingredient count
		if (targetType.equals(ConceptType.MEDICINAL_PRODUCT_ONLY) || targetType.equals(ConceptType.MEDICINAL_PRODUCT_FORM_ONLY)) {
			String count = Integer.toString(baseIngredients.size());
			Relationship countRel = new Relationship (drug, COUNT_BASE_ACTIVE_INGREDIENT, count, UNGROUPED, ConcreteValue.ConcreteValueType.INTEGER);
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
	
	public boolean inScope(Concept c) {
		return !newConceptsOnly || (!c.hasEffectiveTime() && !c.isReleased());
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.debug("Identifying concepts to process");
		List<Concept> allAffected = new ArrayList<>();
		List<Concept> conceptsToReview = MEDICINAL_PRODUCT.getDescendants(NOT_SET).stream()
				.filter(this::inScope)
				.sorted(SnomedUtils::compareSemTagFSN)
				.toList();
		for (Concept c : conceptsToReview) {
			reviewConcept(c, allAffected);
		}
		LOGGER.info("Identified {} concepts to process", allAffected.size());
		allAffected.sort(Comparator.comparing(Concept::getFsn));
		return new ArrayList<>(allAffected);
	}

	private void reviewConcept(Concept c, List<Concept> allAffected) throws TermServerScriptException {
		if (containsGaseousIngredient(c)) {
			return;  //RP-915
		}
		try {
			if (c.getConceptType().equals(ConceptType.CLINICAL_DRUG)) {
				reviewClinicalDrug(c, allAffected);
			} else if (c.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM)) {
				reviewMPF(c, allAffected);
			} else if (c.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT)
					|| c.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM_ONLY)) {
				reviewMP(c, allAffected);
			} else if (c.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_ONLY)) {
				reviewMPO(c, allAffected);
			} else {
				report(SECONDARY_REPORT, (Task)null, c, Severity.HIGH, ReportActionType.SKIPPING, "Unexpected concept type: " + c.getConceptType());
			}
		} catch (Exception e) {
			String msg = ExceptionUtils.getExceptionCause("Unable to process " + c, e);
			report(SECONDARY_REPORT, (Task)null, c, Severity.HIGH, ReportActionType.SKIPPING, msg);
			LOGGER.warn(msg);
		}
	}

	private void reviewClinicalDrug(Concept c, List<Concept> allAffected) throws TermServerScriptException {
		if (containsGaseousIngredient(c)) {
			return;  //RP-915
		}
		Concept mpf = calculateDrugRequired(c, ConceptType.MEDICINAL_PRODUCT_FORM);
		//If the concept has a more specific mpf than the one we've calculated, warn
		List<Concept> currentMPFs = c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)
				.stream()
				.filter(parent -> parent.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM))
				.toList();
		for (Concept currentMPF : currentMPFs) {
			//Some dose forms are allowed to be more specific
			Concept doseForm = SnomedUtils.getTarget(currentMPF, new Concept[] { HAS_MANUFACTURED_DOSE_FORM }, UNGROUPED, CharacteristicType.INFERRED_RELATIONSHIP);
			if (!allowMoreSpecificDoseForms.contains(doseForm)) {
				if (SnomedUtils.hasMoreSpecificModel(currentMPF, mpf, AncestorsCache.getAncestorsCache())) {
					report(SECONDARY_REPORT, (Task)null, c, Severity.HIGH, ReportActionType.SKIPPING, "Existing parent : " + currentMPF + " is more specific than proposed (expression): " + mpf.toExpression(CharacteristicType.STATED_RELATIONSHIP));
					return;
				}
			}
		}
		//Do we already know about this concept or plan to create it?
		if (!isContained(mpf, knownMPFs) &&
				!isContained(mpf, createMPFs) &&
				!isSuppressed(mpf)) {
			createMPFs.add(mpf);
			allAffected.add(c);
		}
	}

	private boolean containsGaseousIngredient(Concept c) {
		try {
			if (gaseousSubstances == null) {
				gaseousSubstances = gl.getConcept("74947009 |Gaseous substance (substance)|").getDescendants(NOT_SET);
				gaseousSubstances.addAll(gl.getConcept("765040008 |Noble gas|").getDescendants(NOT_SET));
			}
			for (Concept ingredient : DrugUtils.getIngredients(c, CharacteristicType.INFERRED_RELATIONSHIP)) {
				if (gaseousSubstances.contains(ingredient)) {
					return true;
				}
			}
		} catch (Exception e) {
			//If anything about this check fails, we'll just say no it didn't, and then we'll get some false
			//positives in the output, but at least the report will complete
			LOGGER.error("Failure while checking {} for gaseous ingredients", c, e);
		}
		return false;
	}

	private void reviewMPF(Concept c, List<Concept> allAffected) throws TermServerScriptException {
		Concept mp = calculateDrugRequired(c, ConceptType.MEDICINAL_PRODUCT);
		//If the concept has a more specific mp than the one we've calculated, warn
		List<Concept> currentMPs = c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)
				.stream()
				.filter(parent -> parent.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT))
				.toList();
		for (Concept currentMP : currentMPs) {
			if (SnomedUtils.hasMoreSpecificModel(currentMP, mp, AncestorsCache.getAncestorsCache())) {
				report(SECONDARY_REPORT, (Task)null, c, Severity.HIGH, ReportActionType.SKIPPING, "Existing parent : " + currentMP + " is more specific that proposed: " + mp.toExpression(CharacteristicType.STATED_RELATIONSHIP));
				return;
			}
		}

		reviewCreatedMP(c, allAffected, mp);

		//For a given MPF, as well as looking for an MP, we may also need a sibling MPFO
		if (containsExceptionSubstance(c)) {
			return;
		}
		Concept mpfo = calculateDrugRequired(c, ConceptType.MEDICINAL_PRODUCT_FORM_ONLY);
		reviewCreatedMPFO(c, allAffected, mpfo);
	}

	private void reviewCreatedMPFO(Concept c, List<Concept> allAffected, Concept mpfo) throws TermServerScriptException {
		//Do we already know about this concept or already have a plan to create it?
		if (!isContained(mpfo, knownMPFOs) && !isContained(mpfo, createMPFOs)) {
			//RP-401 Only consider creating concepts for MPFs where there is an underlying clinical drug
			if (hasClinicalDrugDescendant(c)) {
				if (!isSuppressed(mpfo)) {
					createMPFOs.add(mpfo);
					if (!allAffected.contains(c)) {
						allAffected.add(c);
					}
				}
			} else if (reportMissingUnderlyingCDs) {
				report((Task)null, c, Severity.LOW, ReportActionType.VALIDATION_CHECK, "No underlying CD detected for MPFO", mpfo.toExpression(CharacteristicType.STATED_RELATIONSHIP));
			}
		}
	}

	private void reviewCreatedMP(Concept c, List<Concept> allAffected, Concept mp) throws TermServerScriptException {
		//Do we already know about this concept or already have a plan to create it?
		if (!isContained(mp, knownMPs) && !isContained(mp, createMPs)) {
			//RP-401 Only consider creating concepts for MPFs where there is an underlying clinical drug
			if (hasClinicalDrugDescendant(c)) {
				if (!isSuppressed(mp)) {
					createMPs.add(mp);
					allAffected.add(c);
				}
			} else if (reportMissingUnderlyingCDs) {
				report(SECONDARY_REPORT, (Task)null, c, Severity.LOW, ReportActionType.VALIDATION_CHECK, "No underlying CD detected for MP", mp.toExpression(CharacteristicType.STATED_RELATIONSHIP));
			}
		}
	}

	private void reviewMP(Concept c, List<Concept> allAffected) throws TermServerScriptException {
		if (containsExceptionSubstance(c)) {
			return;
		}
		Concept mpo = calculateDrugRequired(c, ConceptType.MEDICINAL_PRODUCT_ONLY);
		//Do we already know about this concept or already have a plan to create it?
		if (!isContained(mpo, knownMPOs) && !isContained(mpo, createMPOs)) {
			//RP-401 Only consider creating concepts for MPFs where there is an underlying clinical drug
			if (hasClinicalDrugDescendant(c)) {
				if (!isSuppressed(mpo)) {
					createMPOs.add(mpo);
					allAffected.add(c);
				}
			} else if (reportMissingUnderlyingCDs) {
				report((Task)null, c, Severity.LOW, ReportActionType.VALIDATION_CHECK, "No underlying CD detected for MPO", mpo.toExpression(CharacteristicType.STATED_RELATIONSHIP));
			}
		}
	}

	private void reviewMPO(Concept c, List<Concept> allAffected) throws TermServerScriptException {
		if (containsExceptionSubstance(c)) {
			return;
		}
		Concept mp = calculateDrugRequired(c, ConceptType.MEDICINAL_PRODUCT);
		//Do we already know about this concept or already have a plan to create it?
		if (!isContained(mp, knownMPs) && !isContained(mp, createMPs)) {
			//RP-401 Only consider creating concepts for MPFs where there is an underlying clinical drug
			if (hasClinicalDrugDescendant(c)) {
				if (!isSuppressed(mp)) {
					createMPs.add(mp);
					allAffected.add(c);
				}
			} else if (reportMissingUnderlyingCDs) {
				report((Task)null, c, Severity.LOW, ReportActionType.VALIDATION_CHECK, "No underlying CD detected for MP", mp.toExpression(CharacteristicType.STATED_RELATIONSHIP));
			}
		}
	}

	private boolean hasClinicalDrugDescendant(Concept c) throws TermServerScriptException {
		for (Concept descendant : gl.getDescendantsCache().getDescendants(c)) {
			if (descendant.getConceptType().equals(ConceptType.CLINICAL_DRUG)) {
				return true;
			}
		}
		return false;
	}

	private boolean isSuppressed (Concept c) throws TermServerScriptException {
		termGenerator.ensureTermsConform(null, c, CharacteristicType.STATED_RELATIONSHIP);
		//Are we suppressing this concept?
		//RP-915 Don't report suppressed concepts
		return suppress.contains(c.getFsn());
	}

	private boolean containsExceptionSubstance(Concept c) throws TermServerScriptException {
		for (Concept ingred : DrugUtils.getIngredients(c, CharacteristicType.INFERRED_RELATIONSHIP)) {
			for (String exceptionStr: substanceExceptions) {
				if (ingred.getFsn().toLowerCase().contains(exceptionStr)) {
					report(SECONDARY_REPORT, (Task)null, c, Severity.LOW, ReportActionType.SKIPPING, "MP contains exception substance: " + ingred);
					return true;
				}
			}
			
			if (ingred.getFsn().toLowerCase().contains("complex")) {
				for (String exceptionStr: complexExceptions) {
					if (ingred.getFsn().toLowerCase().contains(exceptionStr)) {
						report(SECONDARY_REPORT, (Task)null, c, Severity.LOW, ReportActionType.SKIPPING, "MP contains exception substance complex: " + ingred);
						return true;
					}
				}
			}
		}
		return false;
	}
}
