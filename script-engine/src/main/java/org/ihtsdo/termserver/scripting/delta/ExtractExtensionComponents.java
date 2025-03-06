package org.ihtsdo.termserver.scripting.delta;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.AxiomUtils;
import org.ihtsdo.termserver.scripting.client.TermServerClient;

import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.snapshot.SnapshotGenerator;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.owltoolkit.conversion.AxiomRelationshipConversionService;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class form a delta of specified concepts from some edition and 
 * promote those (along with attribute values and necessary ancestors)
 * into the core module.
 * TODO Load in the MRCM and properly populate the Never Group attributes
 */
public class ExtractExtensionComponents extends DeltaGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExtractExtensionComponents.class);

	private List<Component> allIdentifiedConcepts;
	private Set<Component> allModifiedConcepts = new HashSet<>();
	private List<Component> noMoveRequired = new ArrayList<>();
	private boolean includeDependencies = true;
	private boolean includeInferredParents = false;  //DO NOT CHECK IN AS TRUE - NEEDED ONLY FOR DRUGS
	private boolean copyInferredParentRelsToStated = false;
	
	private Map<String, Concept> loadedConcepts = new HashMap<>();
	TermServerClient secondaryConnection;
	private static String secondaryCheckPath = "MAIN";
	private AxiomRelationshipConversionService axiomService = new AxiomRelationshipConversionService(NEVER_GROUPED_ATTRIBUTES);
	
	private Integer conceptsPerArchive = 9999;
	Queue<List<Component>> archiveBatches = null;
	private boolean ensureConceptsHaveBeenReleased = false;

	protected boolean containsReplacementFSNs = false;
	Map<String, String> replacementFSNs = new HashMap<>();
	
	Map<Concept, Concept> knownReplacements = new HashMap<>();
	Set<String> knownMapToCoreLangRefsets = Sets.newHashSet("999001261000000100"); //|National Health Service realm language reference set (clinical part)|

	protected boolean copyInferredRelationshipsToStatedWhereMissing = true;

	protected String[] componentIdsToProcess = null; //If it's just a couple, no need for a file, just specify here.
	protected String componentsToProcessEcl = null; // (<<64572001 |Disease|:116676008 |Associated morphology|=46360000 |Abnormal curvature|) {{ C moduleId = 890108001 }}

	public static void main(String[] args) throws TermServerScriptException {
		ExtractExtensionComponents delta = new ExtractExtensionComponents();
		// ExtractExtensionComponents delta = new ExtractExtensionComponentsAndLateralize();
		try {
			ReportSheetManager.setTargetFolderId("12ZyVGxnFVXZfsKIHxr3Ft2Z95Kdb7wPl"); //Extract and Promote
			delta.runStandAlone = false;
			delta.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			//delta.sourceModuleIds = SCTID_CORE_MODULE; //NEBCSR are using core module these days.
			//delta.sourceModuleIds = "32506021000036107"; //AU Module
			//delta.sourceModuleIds = "11000181102"; //Estonia
			//delta.sourceModuleIds = "911754081000004104"; //Nebraska Lexicon Pathology Synoptic module
			//delta.sourceModuleIds = "51000202101"; //Norway Module
			//delta.sourceModuleIds = Set.of("57091000202101");  //Norway module for medicines
			//delta.sourceModuleIds = "999000011000000103"; // UK Clinical Extension
			//delta.sourceModuleIds = "83821000000107"; //UK Composition Module
			//delta.sourceModuleIds = Set.of("999000011000001104"); //UK Drug Extension
			//delta.sourceModuleIds = Set.of("731000124108");  //US Module
			//delta.sourceModuleIds = "332351000009108"; //Vet Extension

			delta.init(args);
			SnapshotGenerator.setSkipSave(true);
			if (delta.getProject().getKey().contains("uk_sct2")) {
				LOGGER.warn("UK Edition detected, will not check for OWL axiom / stated relationships");
				delta.getArchiveManager().setRunIntegrityChecks(false);
				delta.copyInferredParentRelsToStated = true;
				delta.getArchiveManager().setExpectStatedParents(false); //UK Edition doesn't do stated modeling
			}
			delta.getGraphLoader().setAllowIllegalSCTIDs(true);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			//We won't include the project export in our timings
			delta.additionalReportColumns = "Defn Status, Stated Parent(s), Additional Detail, , ";
			delta.postInit(GFOLDER_EXTRACT_AND_PROMOTE);
			delta.startTimer();
			delta.preProcessFile();
			//delta.selectConceptsViaReview();
			delta.processFile();
			if (delta.archiveBatches == null) {
				delta.createOutputArchive();
			}
		} finally {
			delta.finish();
		}
	}

	@Override
	protected void init (String[] args) throws TermServerScriptException {
		super.init(args);
		LOGGER.info("Select an environment for live secondary checking ");
		for (int i=0; i < environments.length; i++) {
			println("  " + i + ": " + environments[i]);
		}
		print ("Choice: ");
		String choice = STDIN.nextLine().trim();
		int envChoice = Integer.parseInt(choice);
		String secondaryURL = environments[envChoice];
		
		if (!secondaryURL.equals(url)) {
			print("Please enter your authenticated cookie for connection to " + url + " : ");
			String secondaryCookie = STDIN.nextLine().trim();
			secondaryConnection = createTSClient(url, secondaryCookie);
		} else {
			println("Existing authentication cookie will be used for secondary connection");
			secondaryConnection = createTSClient(url, authenticatedCookie);
		}
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		knownReplacements.put(gl.getConcept("261231004|Local flap|"), gl.getConcept("256683004|Flap|"));
		knownReplacements.put(gl.getConcept("367651003 |Malignant neoplasm of p, s, or u origin|"), gl.getConcept("1240414004 |Malignant neoplasm|"));
		initialiseSummaryInformation("Unexpected dependencies included");
		super.postInit(googleFolder);
	}
	
	private void selectConceptsViaReview() throws TermServerScriptException {
		preProcessConcepts(getConceptsInReview(), true);
	}
	
	private void preProcessFile() throws TermServerScriptException {
		//Are we working with ecl, a hard coded list, or pulling from a file?
		List<Component> conceptsToProcess;
		if (componentsToProcessEcl != null) {
			conceptsToProcess = findConcepts(componentsToProcessEcl).stream()
					.map(c -> (Component)c)
					.collect(Collectors.toList()); //Not 'toList' here because we need a mutable collection
		} else if (componentIdsToProcess != null && componentIdsToProcess.length > 0) {
			conceptsToProcess = Arrays.stream(componentIdsToProcess)
					.map(s -> (Component)gl.getConceptSafely(s))
					.collect(Collectors.toList());  //Not 'toList' here because we need a mutable collection
		} else {
			conceptsToProcess = super.processFile();
		}
		preProcessConcepts(conceptsToProcess, false);
	}

	private void preProcessConcepts(List<Component> componentsOfInterest, boolean viaReview) throws TermServerScriptException {
		archiveBatches = new ArrayDeque<>();
		Set<Component> excludeComponents = new HashSet<>();
		Set<Component> componentsSeen = new HashSet<>();
		for (Component c : componentsOfInterest) {
			if (componentsSeen.contains(c)) {
				LOGGER.warn("Duplicate concept identified for transfer: " + c);
				continue;
			} else {
				componentsSeen.add(c);
			}

			if (c.getModuleId() == null) {
				//if (viaReview) {
					String msg = viaReview ? "Concept appearing in review has been deleted?" : "Concept not found in target location";
					report((Concept)c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
					excludeComponents.add(c);
				/*} else { 
					throw new IllegalArgumentException("Concept " + c + " doesn't exist.  Check list of concepts to transfer");
				}*/
			}
			
			if (ensureConceptsHaveBeenReleased && !c.isReleased()) {
				throw new IllegalStateException(c + " has not been released");
			}
		}
		
		componentsOfInterest.removeAll(excludeComponents);
		
		//If we can fit everything we're loading into a single batch file, then we don't need
		//to worry about what concepts go in what Zip file.
		if (batchDelimitersDetected) {
			assignConceptsToBatchesUsingDelimiter(componentsOfInterest);
		} else if (componentsOfInterest.size() < conceptsPerArchive) {
			archiveBatches.add(componentsOfInterest);
		} else {
			assignConceptsToBatches(componentsOfInterest);
		}
	}

	private void assignConceptsToBatchesUsingDelimiter(List<Component> componentsOfInterest) {
		List<Component> thisBatch = new ArrayList<>();
		for (Component c : componentsOfInterest) {
			if (c instanceof BatchEndMarker) {
				archiveBatches.add(thisBatch);
				thisBatch = new ArrayList<>();
			} else {
				thisBatch.add(c);
			}
		}
		
		if (!thisBatch.isEmpty()) {
			archiveBatches.add(thisBatch);
		}
	}

	private void assignConceptsToBatches(List<Component> componentsOfInterest) throws TermServerScriptException {
		//Work through these and group parents with children.
		//However, if a parent has more children than we have concepts in a task,
		//then we have a problem.
		Map<Concept, Set<Concept>> parentChildMap = new HashMap<>();
		Set<Concept> allocatedConcepts = new HashSet<>();
		
		//Concepts that have no ancestors to group in with can be placed in any set
		Queue<Concept> footlooseConcepts = new ArrayDeque<>();
		Set<Concept> requiresFirstPassLoad = new HashSet<>();
		
		for (Component component : componentsOfInterest) {
			Concept c = (Concept)component;
			assignConceptToBatch(c, componentsOfInterest, parentChildMap, allocatedConcepts, footlooseConcepts);
		}
		
		//Now work through the list and see if we can fit any together
		Set<Concept> batchesAvailableToMerge = new HashSet<>(parentChildMap.keySet());
		mergeBatches(parentChildMap, batchesAvailableToMerge);
		
		//Now allocate the footloose concepts to fill up existing or new batches
		allocateFootLooseConcepts(parentChildMap, componentsOfInterest, footlooseConcepts);
		
		//Add these batches into our queue, as we log the size of each one
		LOGGER.info("Batches Formed:");
		int batchNo = 0;
		for (Concept batchParent : new HashSet<>(parentChildMap.keySet())) {
			++batchNo;
			List<Component> thisBatch = new ArrayList<>();
			thisBatch.add(batchParent);
			thisBatch.addAll(parentChildMap.get(batchParent));
			LOGGER.info("{}: {} - {}", batchNo, batchParent, thisBatch.size());
			archiveBatches.add(thisBatch);
		}
		
		//Any concepts left footloose can now be added into additional batches
		List<Component> thisBatch = new ArrayList<>();
		while (!footlooseConcepts.isEmpty()) {
			thisBatch.add(footlooseConcepts.remove());
			if (thisBatch.size() == conceptsPerArchive) {
				archiveBatches.add(thisBatch);
				LOGGER.info(++batchNo + ": No Hierarchy - " + thisBatch.size());
				thisBatch = new ArrayList<>();
			}
		}
		
		if (thisBatch.size() > 0) {
			archiveBatches.add(thisBatch);
			LOGGER.info(++batchNo + ": No Hierarchy - " + thisBatch.size());
		}
		
		long totalConceptsInBatches = archiveBatches.stream()
				.flatMap(l -> l.stream())
				.count();
		LOGGER.info("Total concepts pre-processed into batches: {}", totalConceptsInBatches);
		
		if ((int)totalConceptsInBatches != componentsOfInterest.size()) {
			throw new TermServerScriptException("Expected to allocated " + componentsOfInterest.size() + " concepts.");
		}
		
	}

	private void allocateFootLooseConcepts(Map<Concept, Set<Concept>> parentChildMap, List<Component> componentsOfInterest, Queue<Concept> footlooseConcepts) throws TermServerScriptException {
		LOGGER.info("Allocating " + footlooseConcepts.size() + " loose concepts to " + parentChildMap.size() + " existing batches");
		for (Concept batchParent : new HashSet<>(parentChildMap.keySet())) {
			int thisBatchSize = parentChildMap.get(batchParent).size() + 1;
			if (thisBatchSize >= conceptsPerArchive) {
				continue;
			}
			do {
				Concept topUp = footlooseConcepts.remove();
				parentChildMap.get(batchParent).add(topUp);
				thisBatchSize = parentChildMap.get(batchParent).size() + 1;
			} while (thisBatchSize < conceptsPerArchive && !footlooseConcepts.isEmpty());
		}

		//The number of concepts in the map plus the footloose concepts should add up to our total
		long mapCount = parentChildMap.values().stream()
				.flatMap(l -> l.stream())
				.count();
		mapCount += parentChildMap.size();  //Also add 1 for each parent
		if (((int)mapCount + footlooseConcepts.size()) != componentsOfInterest.size()) {
			//Which concepts have gone missing?
			Set<Concept> allocated = parentChildMap.values().stream()
					.flatMap(l -> l.stream())
					.collect(Collectors.toSet());
			allocated.addAll(parentChildMap.keySet());
			allocated.addAll(footlooseConcepts);
			componentsOfInterest.removeAll(allocated);
			componentsOfInterest.stream()
					.forEach(c -> LOGGER.error("Gone missing: {}", c));
			throw new TermServerScriptException("Batched count " + mapCount + " + " +  footlooseConcepts.size() + " does not equal expected " + componentsOfInterest.size() + " concepts.");
		}
	}

	private void assignConceptToBatch(Concept c, List<Component> componentsOfInterest, Map<Concept, Set<Concept>> parentChildMap, Set<Concept> allocatedConcepts, Queue<Concept> footlooseConcepts) throws TermServerScriptException {
		boolean alsoImportingAncestors = false;
		boolean alsoImportingDescendants = false;

		Set<Concept> ancestorsToImport = gl.getAncestorsCache().getAncestors(c, true);  //Need a mutable set
		//If no ancestors in common with what we're loading, then concept is free
		ancestorsToImport.retainAll(componentsOfInterest);
		if (ancestorsToImport.size() > 0) {
			alsoImportingAncestors = true;
			//We'll stop here and allow that ancestor to pick up its descendants when its turn comes
			return;
		}

		//Does concept have descendants that we want to group with it?
		//We need the parents to be imported before any children, so rework any set as one that maintains the order input
		Set<Concept> descendantsToImport = gl.getDescendantsCache().getDescendants(c, true);
		descendantsToImport.retainAll(componentsOfInterest);
		if (!descendantsToImport.isEmpty()) {
			alsoImportingDescendants = true;
			LOGGER.info("{} has {} descendants to import", c, descendantsToImport.size());
			if (descendantsToImport.size() >= conceptsPerArchive) {
				//We'll need to load this concept in its own import and promote to the project
				//so it's available as a parent for subsequent loads
				//requiresFirstPassLoad.add(c);
				//continue;
				int excess = descendantsToImport.size() - conceptsPerArchive;
				LOGGER.warn("Exceeding batch size by {} for {} ({} descendants).  Consider pre-importing and promoting first.", excess, c, descendantsToImport.size());
			}
			//Don't want to import a concept twice, but if we do, we have a problem
			int origSize = descendantsToImport.size();
			descendantsToImport.removeAll(allocatedConcepts);
			if (origSize != descendantsToImport.size()) {
				throw new TermServerScriptException(c + " has descendants already being imported by another concept - investigate");
			}

			allocatedConcepts.addAll(descendantsToImport);
			parentChildMap.put(c, descendantsToImport);
		}

		if (!alsoImportingAncestors && !alsoImportingDescendants) {
			footlooseConcepts.add(c);
		}
	}

	private void mergeBatches(Map<Concept, Set<Concept>> parentChildMap, Set<Concept> batchesAvailableToMerge) {
		for (Concept batchParent : new HashSet<>(parentChildMap.keySet())) {
			//Have we already merged this batch?
			if (!batchesAvailableToMerge.contains(batchParent)) {
				continue;
			}
			batchesAvailableToMerge.remove(batchParent);

			Concept bestMerge = null;
			do {
				//How many concepts are in this batch, +1 for the parent itself
				Set<Concept> batchContents = parentChildMap.get(batchParent);
				int thisBatchSize = batchContents.size() + 1;
				int topUpSize = conceptsPerArchive - thisBatchSize;
				bestMerge = findBestMerge(parentChildMap, batchesAvailableToMerge, topUpSize);
				if (bestMerge != null) {
					batchesAvailableToMerge.remove(bestMerge);
					Set<Concept> thisBatch = parentChildMap.get(batchParent);
					thisBatch.add(bestMerge);
					thisBatch.addAll(parentChildMap.get(bestMerge));
					LOGGER.info("Adding " + bestMerge + "(" + (parentChildMap.get(bestMerge).size() + 1) + ") to batch with parent " + batchParent);
					parentChildMap.remove(bestMerge);
				}
			} while (bestMerge != null);
		}
	}

	private Concept findBestMerge(Map<Concept, Set<Concept>> parentChildMap, Set<Concept> batchesAvailableToMerge,
			int maxTopUpSize) {
		Concept closestToIdealTopUp = null;
		int closestTopUpSize = 0;
		for (Concept thisTopUpBatch : batchesAvailableToMerge) {
			int thisTopUpSize = parentChildMap.get(thisTopUpBatch).size() + 1; //The parent itself getsIncluded
			if (thisTopUpSize > closestTopUpSize && thisTopUpSize <= maxTopUpSize) {
				closestToIdealTopUp = thisTopUpBatch;
				closestTopUpSize = thisTopUpSize;
			}
		}
		return closestToIdealTopUp;
	}

	protected List<Component> processFile() throws TermServerScriptException {
		if (archiveBatches != null) {
			int batchNum = 1;
			while (!archiveBatches.isEmpty()) {
				batchNum++;
				LOGGER.info("Processing archive batch {}", batchNum);
				process(archiveBatches.remove());
				createOutputArchive();
				gl.setAllComponentsClean();
				if (!archiveBatches.isEmpty()) {
					outputDirName = "output"; //Reset so we don't end up with _1_1_1
					initialiseOutputDirectory();
					initialiseFileHeaders();
				}
			}
		} else {
			if (StringUtils.isEmpty(eclSubset)) {
				allIdentifiedConcepts = super.processFile();
			} else {
				allIdentifiedConcepts = new ArrayList<>(findConcepts(eclSubset));
			}
			return process(allIdentifiedConcepts);
		}
		return null;
	}

	private List<Component> process(List<Component> componentsToProcess) throws TermServerScriptException {
		incrementSummaryInformation("Concepts specified", componentsToProcess.size());
		LOGGER.info("Extracting specified concepts");
		for (Component thisComponent : componentsToProcess) {
			extractComponent(thisComponent, componentsToProcess, true);
		}
		return componentsToProcess;
	}

	protected void extractComponent(Component thisComponent, List<Component> componentsToProcess, boolean doAdditionalProcessing) throws TermServerScriptException {
		Concept thisConcept = (Concept)thisComponent;

		if (copyInferredRelationshipsToStatedWhereMissing) {
			restateInferredRelationships(thisConcept, copyInferredParentRelsToStated);
		}

		//If we don't have a module id for this identified concept, then it doesn't properly exist in this release
		if (thisConcept.getModuleId() == null) {
			report(thisConcept, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Concept specified for extract not found in input Snapshot");
			return;
		}

		if (!thisConcept.isActiveSafely()) {
			report(thisConcept, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Concept is inactive, skipping");
			return;
		}

		//Have we already processed this concept because it was a dependency of another concept?
		if (allModifiedConcepts.contains(thisConcept)) {
			report(thisConcept, Severity.LOW, ReportActionType.INFO, "Concept specified for transfer but already processed as a dependency (or is duplicate).");
			return;
		}
		try {
			if (!switchModule(thisConcept, componentsToProcess)) {
				addSummaryInformation("Specified but no movement: " + thisConcept, null);
				incrementSummaryInformation("Concepts no movement required");
			} else if (thisConcept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED) &&
					SnomedUtils.countAttributes(thisConcept, CharacteristicType.STATED_RELATIONSHIP) == 0) {
				//Check we're not ending up with a Fully Defined concept with only ISAs
				report(thisConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Concept FD with only ISAs ", thisConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP));
			}
			if (doAdditionalProcessing) {
				doAdditionalProcessing(thisConcept, componentsToProcess);
			}
		} catch (IllegalStateException e) {
			throw new TermServerScriptException("Exception while processing: " + thisConcept, e);
		} catch (TermServerScriptException e) {
			report(thisConcept, Severity.CRITICAL, ReportActionType.API_ERROR, "Exception while processing: " + e.getMessage() + " : " + SnomedUtils.getStackTrace(e));
		}
	}

	protected void doAdditionalProcessing(Concept c, List<Component> componentsToProcess) throws TermServerScriptException {
		//Override this method in subclasses to do additional processing
	}

	private boolean switchModule(Concept c, List<Component> componentsToProcess) throws TermServerScriptException {
		boolean conceptAlreadyTransferred = false;

		if (c.getConceptId().equals("1119523003")) {
			LOGGER.debug("Here: {}", c);
		}
		
		//Have we already attempted to move this concept?  Don't try again
		if (noMoveRequired.contains(c)) {
			return false;
		}
		
		if (c.getModuleId() ==  null) {
			report(c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept does not specify a module!  Unable to switch.");
			return false;
		}
		//It's possible that this concept has already been transferred by an earlier run if it was identified as a dependency, so 
		//we have to check every concept.
		Concept conceptOnTS = loadConcept(c);
		
		//Switch the module of this concept, then all active descriptions and relationships
		//As long as the current module is not equal to the target module, we'll switch it
		//And even then we might do it, if it's missing from the target server (eg NEBCSR)
		if (conceptOnTS.equals(NULL_CONCEPT)) {
			switchModuleOfConceptNotOnTS(c, componentsToProcess);
		} else {
			conceptAlreadyTransferred = true;
			if (switchModuleOfConceptAlreadyOnTS(c, componentsToProcess, conceptOnTS)) {
				return false;
			}
		}
		
		boolean subComponentsMoved = switchModuleOfSubComponents(c, componentsToProcess, conceptOnTS);
		
		if (conceptAlreadyTransferred && !subComponentsMoved) {
			return false;
		}
		
		allModifiedConcepts.add(c);
		
		if (conceptAlreadyTransferred) {
			incrementSummaryInformation("Existing concept, additional components moved.");
		}
		
		return true;
	}

	private boolean switchModuleOfSubComponents(Concept c, List<Component> componentsToProcess, Concept conceptOnTS) throws TermServerScriptException {
		boolean subComponentsMoved = false;
		if (containsReplacementFSNs) {
			replaceFsnInTransit(c);
			subComponentsMoved = true;
		} else {
			subComponentsMoved = moveDescriptions(c, conceptOnTS);
		}

		subComponentsMoved |= switchModuleOfRelationships(c, conceptOnTS, componentsToProcess);

		//Policy is not to moved inferred modelling
		//Unless we also want the inferred parents
		if (includeInferredParents) {
			for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, IS_A, ActiveState.ACTIVE)) {
				if ((!r.getModuleId().equals(targetModuleId) && !r.getModuleId().equals(SCTID_MODEL_MODULE))) {
					moveRelationshipTarget(r, componentsToProcess);
					subComponentsMoved = true;
				}
			}
		}

		for (AxiomEntry a : c.getAxiomEntries()) {
			//We have an issue with concepts in the examined space already
			//having the target module but needing to move anyway.  So
			//attempt move and see what we're missing.
			if (moveAxiomToTargetModule(c, a, conceptOnTS, componentsToProcess)) {
				subComponentsMoved = true;
			}
		}
		return subComponentsMoved;
	}

	private boolean switchModuleOfRelationships(Concept c, Concept conceptOnTS, List<Component> componentsToProcess) throws TermServerScriptException {
		boolean subComponentsMoved = false;
		boolean relationshipMoved = false;
		boolean relationshipAlreadyMoved = false;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			//If this is an axiom based relationship, then we'll catch that below with an axiom move
			//Actually, if any of them are axiom based, then we don't need to check the others.
			if (r.fromAxiom()) {
				break;
			}

			//Rel may already be in the target module, but be missing from the target server
			if (!conceptOnTS.getRelationships(r).isEmpty()) {
				relationshipAlreadyMoved = true;
			} else {
				if (r.isActiveSafely() && !r.fromAxiom()) {
					LOGGER.info("Unexpected active stated relationship not from axiom: {}", r);
				}
				if (moveRelationshipToTargetModule(r, conceptOnTS, componentsToProcess)) {
					subComponentsMoved = true;
					relationshipMoved = true;
				}
			}
		}

		//Did we move some of the modeling but not all of it?  Warn about that if so
		if (relationshipMoved && relationshipAlreadyMoved) {
			report(c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Partial move on the modeling.  Exported axiom may be incomplete.");
		}
		return subComponentsMoved;
	}

	/**
	 * @return true if we've been asked to switch a concept that IS aleady on the target server, but wasn't in our original list of concepts to transfer
	 * @throws TermServerScriptException
	 */
	private boolean switchModuleOfConceptAlreadyOnTS(Concept c, List<Component> componentsToProcess, Concept conceptOnTS) throws TermServerScriptException {
		//Changing the moduleId won't mark concept dirty unless it really does change
		//Don't move any concepts already in the model module
		if (componentsToProcess.contains(c)) {
			if (!c.getModuleId().equals(SCTID_MODEL_MODULE)) {
				c.setModuleId(targetModuleId);
				if (c.getModuleId().equals(targetModuleId)) {
					report(c, Severity.HIGH, ReportActionType.NO_CHANGE, "Specified concept already in target module: " + c.getModuleId() + " checking for additional modeling in source module.");
				} else {
					String msg = "Odd Situation. Concept " + c + " already exists at destinaction in module " + conceptOnTS.getModuleId() + " and also in local content in module " + c.getModuleId();
					LOGGER.warn(msg);
					report(c, Severity.HIGH, ReportActionType.INFO, msg + ".  Looking for additional modelling anyway.");
				}
			}
		} else {
			//If this _isn't_ a concept that was originally listed for transfer and it does exist on the target server, then
			//we won't look any closer at it.
			return true;
		}
		return false;
	}

	private void switchModuleOfConceptNotOnTS(Concept c, List<Component> componentsToProcess) throws TermServerScriptException {
		//NEBCSR is a bit loose with its modules.  Allow CORE to be used without complaining
		if (!sourceModuleIds.contains(c.getModuleId()) && !c.getModuleId().equals(SCTID_CORE_MODULE)) {
			report(c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Specified concept in unexpected module, switching anyway", c.getModuleId());
		}
		//Was this concept originally specified, or picked up as a dependency?
		String parents = parentsToString(c);

		if (componentsToProcess.contains(c)) {
			report(c, Severity.LOW, ReportActionType.MODULE_CHANGE_MADE, "Specified concept, module set to " + targetModuleId, c.getDefinitionStatus().toString(), parents);
		} else {
			report(c, Severity.MEDIUM, ReportActionType.CONCEPT_CHANGE_MADE, "Dependency concept, module set to " + targetModuleId, c.getDefinitionStatus().toString(), parents);
		}
		c.setModuleId(targetModuleId);
		//mark it as dirty explicitly, just incase it already had this module!
		c.setDirty();
		incrementSummaryInformation("Concepts moved");

		allModifiedConcepts.add(c);

		//If we have no stated modelling (either stated relationships, or those extracted from an axiom,
		//create an Axiom Entry from the inferred rels.
		if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE).size() == 0) {
			convertInferredRelsToAxiomEntry(c);
		}
	}

	private void replaceFsnInTransit(Concept c) throws TermServerScriptException {
		List<Description> replacementTerms = new ArrayList<>();
		String fsnTern = replacementFSNs.get(c.getId());
		Description fsn = Description.withDefaults(fsnTern, DescriptionType.FSN, Acceptability.PREFERRED);
		replacementTerms.add(fsn);

		String ptTerm = SnomedUtilsBase.deconstructFSN(fsnTern)[0];
		Description pt = Description.withDefaults(ptTerm, DescriptionType.SYNONYM, Acceptability.PREFERRED);
		replacementTerms.add(pt);
		
		c.setDescriptions(replacementTerms);
	}

	private boolean moveDescriptions(Concept c, Concept conceptOnTS) throws TermServerScriptException {
		boolean subComponentsMoved = false;
		for (Description d : c.getDescriptions(ActiveState.BOTH)) {
			//We're going to skip any non-English Descriptions
			if (!d.getLang().equals("en")) {
				continue;
			}
			boolean thisDescMoved = false;
			//Move descriptions if concept doesn't exist in target location, or module id is not target
			//OR if the concept in the target location doesn't know about this description id.
			//OR if the description has been inactivated in the new location
			if (conceptOnTS.equals(NULL_CONCEPT)
					|| (!d.getModuleId().equals(targetModuleId) && !d.getModuleId().equals(SCTID_MODEL_MODULE))
					|| conceptOnTS.getDescription(d.getId()) == null
					|| SnomedUtils.hasLangRefsetDifference(d.getId(), c, conceptOnTS)
					|| SnomedUtils.hasDescActiveStateDifference(d.getId(), c, conceptOnTS)) {
				//However, don't move inactive descriptions if they don't already exist at the target location
				if (!d.isActiveSafely() && conceptOnTS.getDescription(d.getId()) == null) {
					LOGGER.info("Skipping inactive description, not existing in target location: {}", d);
					continue;
				}

				if (moveDescriptionToTargetModule(d, conceptOnTS)) {
					subComponentsMoved = true;
					thisDescMoved = true;
				}
			} else if (conceptOnTS != null && !conceptOnTS.equals(NULL_CONCEPT)){
				//Is this description already in the target module but not held on the target server?
				if (conceptOnTS.getDescription(d.getId()) == null) {
					if (moveDescriptionToTargetModule(d, conceptOnTS)) {
						subComponentsMoved = true;
						thisDescMoved = true;
					}
				}
			}

			if (thisDescMoved && !conceptOnTS.equals(NULL_CONCEPT)) {
				//Do we need to demote existing content to make way for the new?
				if (d.getType().equals(DescriptionType.FSN)) {
					String existingFsnId = conceptOnTS.getFSNDescription().getId();
					Description loadedFSN = c.getDescription(existingFsnId);
					if (loadedFSN != null && !existingFsnId.equals(loadedFSN.getId())) {
						report(c, Severity.MEDIUM, ReportActionType.DESCRIPTION_INACTIVATED, "Existing FSN on TS inactivated to make way for imported content.", loadedFSN);
						setDescriptionAndLangRefModule(loadedFSN);
						loadedFSN.setActive(false, true); //Force dirty flag
						subComponentsMoved = true;
					}
				} else if (d.isPreferred() && d.getType().equals(DescriptionType.SYNONYM)) {
					String existingPtId = conceptOnTS.getPreferredSynonym(US_ENG_LANG_REFSET).getId();
					Description loadedPT = c.getDescription(existingPtId);
					if (loadedPT != null && !existingPtId.equals(loadedPT.getId())) {
						report(c, Severity.MEDIUM, ReportActionType.DESCRIPTION_INACTIVATED, "Existing PT on TS demoted to make way for imported content.", loadedPT);
						setDescriptionAndLangRefModule(loadedPT);
						loadedPT.setActive(true); //Will only mark dirty if not already active
						//Only demote to US acceptable if currently preferred in US
						if (loadedPT.isPreferred(US_ENG_LANG_REFSET)) {
							loadedPT.setAcceptability(US_ENG_LANG_REFSET, Acceptability.ACCEPTABLE);
						}

						//Only demote to GB acceptable if currently preferred in GB
						if (loadedPT.isPreferred(GB_ENG_LANG_REFSET)) {
							loadedPT.setAcceptability(GB_ENG_LANG_REFSET, Acceptability.ACCEPTABLE);
						}
						//The local copy may already be acceptable, so mark as dirty to force this state to TS
						loadedPT.setDirty(ENGLISH_DIALECTS);
						subComponentsMoved = true;
					}
				}
			}
		}
		validatePreferredTermsInLanguageRefsets(c);
		return subComponentsMoved;
	}

	private void validatePreferredTermsInLanguageRefsets(Concept c) throws TermServerScriptException {
		// Ensure that the concept has a preferred term in both en-gb and en-us.
		if (validateLangResetEntryCount(c, false, DescriptionType.SYNONYM) &&
				validateLangResetEntryCount(c, false, DescriptionType.FSN)) {
			return;
		}

		// Check what happens behind setAcceptability....that may be more for working with the browser representation of the concept.
		// It may or may not also work through the RF2 rows of LangRefsetEntries.     It's not the Description that is being modified
		// (although, in fact, it will already be dirty because we changed its module), it's the Language Reference Set entries.

		// If it does NOT, then loop through preferred terms in knownMapToCoreLangRefsets and,
		// where en-gb/en-us langrefset entries exist for the same description, upgrade them from acceptable to preferred.
		for (String localAuthoritativeRefset : knownMapToCoreLangRefsets) {
			for (Description description : c.getDescriptions(ActiveState.ACTIVE)) {
				if (description.getType() != DescriptionType.SYNONYM || !description.isPreferred(localAuthoritativeRefset)) {
					continue;
				}

				description.setAcceptability(GB_ENG_LANG_REFSET, Acceptability.PREFERRED);
				description.setAcceptability(US_ENG_LANG_REFSET, Acceptability.PREFERRED);
				description.setDirty();
				report(c, Severity.MEDIUM, ReportActionType.LANG_REFSET_CREATED, "US + GB Acceptability brought into line with : "+localAuthoritativeRefset+" reference set");
			}
		}

		// Re-run the check a 2nd time and output a HIGH warning if it has not been possible to determine the preferred terms.
		validateLangResetEntryCount(c, true, DescriptionType.SYNONYM);
		validateLangResetEntryCount(c, true, DescriptionType.FSN);
	}

	// Both the FSN and one synonym should be marked as preferred in both
	// the en-gb and en-us language reference sets.
	private boolean validateLangResetEntryCount(Concept c, boolean logWarning, DescriptionType type) throws TermServerScriptException {
		int foundGB = 0;
		int foundUS = 0;
		boolean isValid = true;

		for (Description description : c.getDescriptions(ActiveState.ACTIVE)) {
			if (description.getType() == type) {
				if (description.isPreferred(GB_ENG_LANG_REFSET)) {
					foundGB++;
				}

				if (description.isPreferred(US_ENG_LANG_REFSET)) {
					foundUS++;
				}
			}
		}

		if (foundUS != 1 || foundGB != 1) {
			isValid = false;
			if (logWarning) {
				String msg = "Concept has unexpected langrefset count for type " + type + " : US = " + foundUS + ", GB = " + foundGB;
				report(c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg, SnomedUtils.getDescriptionsToString(c));
			}
		}
		return isValid;
	}

	private void setDescriptionAndLangRefModule(Description d) {
		d.setModuleId(targetModuleId);
		d.setDirty();
		for (LangRefsetEntry l : d.getLangRefsetEntries()) {
			l.setModuleId(targetModuleId);
			l.setDirty();
		}
	}

	private void convertInferredRelsToAxiomEntry(Concept c) throws TermServerScriptException {
		try {
			AxiomRepresentation axiom = new AxiomRepresentation();
			axiom.setLeftHandSideNamedConcept(Long.parseLong(c.getConceptId()));
			axiom.setPrimitive(c.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE));
			Map<Integer, List<org.snomed.otf.owltoolkit.domain.Relationship>> relationshipMap = new HashMap<>();
			boolean includeIsA = true;
			for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP, includeIsA)) {
				relationshipMap.put(g.getGroupId(), g.getToolKitRelationships());
			}
			axiom.setRightHandSideRelationships(relationshipMap);
			String axiomStr = axiomService.convertRelationshipsToAxiom(axiom);
			AxiomEntry axiomEntry = AxiomEntry.withDefaults(c, axiomStr);
			axiomEntry.setModuleId(targetModuleId);
			axiomEntry.setDirty();
			c.getAxiomEntries().add(axiomEntry);
		} catch (ConversionException e) {
			throw new TermServerScriptException(e);
		}
	}

	private String parentsToString(Concept c) {
		StringBuffer parentsStr = new StringBuffer();
		boolean firstParent = true;
		for (Concept p : c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
			if (!firstParent) {
				parentsStr.append(", ");
			} else {
				firstParent = false;
			}
			parentsStr.append(p.toString());
		}
		return parentsStr.toString();
	}
	
	private boolean moveAxiomToTargetModule(Concept c, AxiomEntry a, Concept conceptOnTS, List<Component> componentsToProcess) throws TermServerScriptException {
		boolean axiomRelationshipMoved = false;
		try {
			//Don't trust the local module, we might need to copy the data over anyway
			//if (a.getModuleId().equals(targetModuleId)) {
			//	return false;
			//}
			
			//If we already have the concept on the Terminology Server, perhaps we already have the axiom too,
			//despite what the local file claims
			if (!conceptOnTS.equals(NULL_CONCEPT)) {
				Axiom axiomOnTS = conceptOnTS.getClassAxiom(a.getId());
				//Has this axiom been modified compared to what's on the TS?
				if (axiomOnTS != null && (axiomOnTS.getEffectiveTime() == null 
					|| (a.getEffectiveTime() != null && a.getEffectiveTime().equals(axiomOnTS.getEffectiveTime())))) {
					//Only need to say something if we originally thought we were supposed to move this concept
					if (componentsToProcess.contains(c)) {
						report(conceptOnTS, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Axiom already on server with same (or no) effective time", a.getId());
					}
					return false;
				}
			}
			
			if (!a.getModuleId().equals(SCTID_MODEL_MODULE)) {
				a.setModuleId(targetModuleId);
			}
			
			AxiomRepresentation axiomRepresentation = axiomService.convertAxiomToRelationships(a.getOwlExpression());
			if (axiomRepresentation != null) {
				Map<Concept, Concept> replacementsMadeMap = new HashMap<>();
				for (Relationship r : AxiomUtils.getRHSRelationships(c, axiomRepresentation)) {
					r.setAxiomEntry(a);
					if (moveRelationshipToTargetModule(r, conceptOnTS, componentsToProcess)) {
						axiomRelationshipMoved = true;
						a.setDirty();
					}
				}
				//Did we have to modify the axiom during transfer?  Alter the OWL string if so
				if (a.isDirty()) {
					//The OWL Service is tricky to set up because we'd need to tell it about which attribute types
					//should be role grouped, so we'll do a dirty search and replace instead.
					//String owlStr = gl.getAxiomService().convertRelationshipsToAxiom(axiomRepresentation);
					for (Map.Entry<Concept, Concept> entry : replacementsMadeMap.entrySet()) {
						a.setOwlExpression(a.getOwlExpression().replaceAll(":"+entry.getKey().getId(), ":"+entry.getValue().getId()));
					}
				}
			} else {
				LOGGER.warn ("No Axiom Representation found for {} : {}", c, a.getOwlExpression());
			}
		} catch (ConversionException e) {
			throw new TermServerScriptException("Failed to convert axiom for " + c , e);
		}
		
		//Check if there are axioms inactivated locally that need to be inactivated at the target
		for (AxiomEntry entry : c.getAxiomEntries()) {
			if (!entry.isActiveSafely()) {
				if (!conceptOnTS.equals(NULL_CONCEPT)) {
					Axiom axiomOnTS = conceptOnTS.getClassAxiom(entry.getId());
					if (axiomOnTS != null && axiomOnTS.isActiveSafely()) {
						entry.setDirty();
					}
				}
			}
		}
		
		return axiomRelationshipMoved;
	}

	private boolean moveRelationshipToTargetModule(Relationship r, Concept conceptOnTS, List<Component> componentsToProcess) throws TermServerScriptException {
		//If we already have the concept on the Terminology Server, perhaps we already have the relationship too,
		//Despite what the local file claims
		if (!conceptOnTS.equals(NULL_CONCEPT)) {
			//Because axiom relationships don't have IDs we need to search for type/value
			//Relationship relOnTS = conceptOnTS.getRelationship(r.getId());
			if (conceptOnTS.getRelationships(r).size() > 0) {
				report(conceptOnTS, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Relationship already on target server", r);
				return false;
			}
		} 
		//Switch the relationship.   Also switch both the type and the destination - will return if not needed
		r.setModuleId(targetModuleId);
		//And mark the relationship as dirty just in case we're moving content that's already in the target module.  
		r.setDirty();
		
		if (includeDependencies && !allModifiedConcepts.contains(r.getType())) {
			if (switchModule(r.getType(), componentsToProcess)) {
				//Is this an unexpected dependency
				if (!componentsToProcess.contains(r.getType())) {
					incrementSummaryInformation("Unexpected dependencies included");
					addSummaryInformation("Unexpected type dependency: " + r.getType(), "");
				}
				//We've moved this concept now, list it so as not to try again
				allModifiedConcepts.add(r.getType());
			} else {
				//No need to try to switch this concept again
				noMoveRequired.add(r.getType());
			}
		}
		
		
		
		//Note that switching the target will also recursively work up the hierarchy as parents are also switched
		//up the hierarchy until a concept owned by the core module is encountered.
		moveRelationshipTarget(r, componentsToProcess);
		
		//If we didn't need to transfer the concept, then do report the movement of it's sub components.
		if (!conceptOnTS.equals(NULL_CONCEPT)) {
			report(r.getSource(), Severity.MEDIUM, ReportActionType.MODULE_CHANGE_MADE, r, r.getId());
		}
		return true;
	}

	private void moveRelationshipTarget(Relationship r, List<Component> componentsToProcess/*, Map<Concept, Concept> replacementsMadeMap*/) throws TermServerScriptException {
		Concept target = r.getTarget();
		boolean isIsA = r.getType().equals(IS_A);
		String charTypeStr = r.getCharacteristicType().equals(CharacteristicType.STATED_RELATIONSHIP)?"Stated":"Inferred";
		//If we don't have a target, we'll assume that's a concrete value.  No need to check that.
		if (target != null && includeDependencies && !allModifiedConcepts.contains(target)) {
			//Don't worry about the target if it's on our to-do list anyway.
			if (!componentsToProcess.contains(target)) {
				//If our dependency is in the core module, then check - live - if it is active
				//because if not, we can't take this relationship.  Check we haven't just moved it there.
				if (target.getModuleId().equals(targetModuleId) && ! allModifiedConcepts.contains(target)) {
					Concept loadedTarget = loadConcept(target);
					//If this target is inactive, find an alternative target and create a replacement relationship
					//TODO in the stated form, we'll need to re-write the axiom if we see this!
					if (!loadedTarget.isActiveSafely()) {
						String reason = loadedTarget.getInactivationIndicator().toString();
						Concept replacement = knownReplacements.get(loadedTarget);
						if (replacement == null) {
							replacement = getReplacement(PRIMARY_REPORT, r.getSource(), loadedTarget, isIsA);
							//We will probably not have loaded this concept via RF2 if the target has been replaced, so load from secondary source
							replacement = loadConcept(replacement);
						}
						
						if (replacement == null && isIsA) {
							report(r.getSource(), Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Unable to find safe replacement for parent: " + loadedTarget + " doing without.");
						} else {
							String msg = "Target of " + r + " is inactive in MAIN due to " + reason;
							msg += ". Replacing with " + replacement;
							report(r.getSource(), Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
							target = replacement;
							Relationship newRel = new Relationship(r.getSource(),r.getType(), replacement, r.getGroupId());
							//Ensure it gets allocated to the same Axiom
							newRel.setAxiomEntry(r.getAxiomEntry());
							newRel.setModuleId(targetModuleId);
							newRel.setDirty();
							//Don't need IDs for axiom based relationships
							if (!r.fromAxiom()) {
								newRel.setRelationshipId(relIdGenerator.getSCTID());
							}
							r.getSource().addRelationship(newRel);
						}
						r.getSource().removeRelationship(r, r.fromAxiom());
					}
				}
				//Again will recursively switch all dependencies as we're switching a concept here
				if (switchModule(target, componentsToProcess)) {
					//Is this an unexpected dependency
					if (!componentsToProcess.contains(target)) {
						incrementSummaryInformation("Unexpected dependencies included");
						addSummaryInformation("Unexpected target dependency: " + target, "");
						report(r.getSource(), Severity.HIGH, ReportActionType.INFO, "Unexpected dependency required in " + charTypeStr + " Modeling", target);
					}
				} else {
					//No need to try to switch this concept again
					noMoveRequired.add(target);
				}
			}
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		//Do we have an FSN override to deal with?
		if (containsReplacementFSNs && lineItems.length == 3) {
			replacementFSNs.put(lineItems[0], lineItems[2]);
		}
		return super.loadLine(lineItems);
	}

	 private Concept loadConcept(Concept c) throws TermServerScriptException {
		//Do we already have this concept?
		Concept loadedConcept = loadedConcepts.get(c.getConceptId());
		if (loadedConcept == null) {
			loadedConcept = loadConcept(secondaryConnection, c, secondaryCheckPath);
			//If the concept was not found, that may be OK
			if (loadedConcept == null || loadedConcept.getConceptId() == null) {
				loadedConcept = NULL_CONCEPT;
			}
			loadedConcepts.put(loadedConcept.getConceptId(), loadedConcept);
		}
		return loadedConcept;
	}

	private boolean moveDescriptionToTargetModule(Description d, Concept conceptOnTS) throws TermServerScriptException {
		Description descriptionOnTS;
		Concept c = gl.getConcept(d.getConceptId());
		
		//If we already have the concept on the Terminology Server, perhaps we already have the description too,
		//Despite what the local file claims
		boolean doShiftDescription = true;
		if (!conceptOnTS.equals(NULL_CONCEPT)) {
			descriptionOnTS = findMatchingDescription(d, conceptOnTS);
			if (descriptionOnTS != null 
					&& d.isActiveSafely() == descriptionOnTS.isActiveSafely()
					&& d.getTerm().equals(descriptionOnTS.getTerm())) {
				//Is it just down to a difference in langrefset entries?
				if (SnomedUtils.hasLangRefsetDifference(d.getId(), c, conceptOnTS)) {
					doShiftDescription = false;
				} else {
					report(conceptOnTS, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Description already on server, identical even down to LangRefset", d);
					return false;
				}
			}
		}
		
		//If the data we have loaded shows US/GB variance then we don't want to clone&modify the US acceptability
		boolean usGbVariance = SnomedUtils.hasUsGbVariance(gl.getConcept(d.getConceptId()));
		
		if (doShiftDescription) {
			//First swap the module id, then add in the GB refset 
			d.setModuleId(targetModuleId);
			//Set dirty explicitly in case the source content is already in the expected module
			d.setDirty();
		}
		
		moveLangRefsetEntriesToTargetModule(d, usGbVariance, doShiftDescription);
		
		//If we didn't need to transfer the concept, then do report the movement of it's sub components.
		if (!conceptOnTS.equals(NULL_CONCEPT)) {
			if (doShiftDescription) {
				report(c, Severity.MEDIUM, ReportActionType.MODULE_CHANGE_MADE, d, d.getId());
			} else {
				for (LangRefsetEntry l : d.getLangRefsetEntries()) {
					if (l.isDirty()) {
						report(c, Severity.MEDIUM, ReportActionType.MODULE_CHANGE_MADE, l, d);
					}
				}
			}
		}
		return true;
	}

	private void moveLangRefsetEntriesToTargetModule(Description d, boolean usGbVariance, boolean doShiftDescription) throws TermServerScriptException {
		//AU for example doesn't give language refset entries for FSNs
		if (d.isActiveSafely() && d.getLangRefsetEntries(ActiveState.ACTIVE, targetLangRefsetIds).isEmpty()) {
			createTargetLangRefsetEntries(d);
		} else {
			moveExistingLangRefsetEntries(d, usGbVariance, doShiftDescription);
		}

		//If Desc has been made inactive, take over the inactivation indicators
		if(!d.isActiveSafely()) {
			for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries()) {
				if (StringUtils.isEmpty(i.getEffectiveTime())) {
					i.setModuleId(targetModuleId);
					i.setDirty(); //Just in case we're missing this component rather than shifting module
				}
			}
		}
	}

	private void moveExistingLangRefsetEntries(Description d, boolean usGbVariance, boolean doShiftDescription) throws TermServerScriptException {
		boolean hasUSLangRefset = false;
		for (LangRefsetEntry usEntry : d.getLangRefsetEntries(ActiveState.ACTIVE, US_ENG_LANG_REFSET)) {
			hasUSLangRefset = true;
			//Only move if there's a difference
			//Note we cannot get LangRefsetEntries from TS because browser format only uses AcceptabilityMap
			if (doShiftDescription || StringUtils.isEmpty(usEntry.getEffectiveTime())) {
				moveExistingUSLangRefsetEntry(d, usEntry, usGbVariance);
			}
		}

		for (LangRefsetEntry gbEntry : d.getLangRefsetEntries(ActiveState.ACTIVE, GB_ENG_LANG_REFSET)) {
			//if (StringUtils.isEmpty(gbEntry.getEffectiveTime())) {
			gbEntry.setModuleId(targetModuleId);
			gbEntry.setDirty(); //Just in case we're missing this component rather than shifting module
			//}
			//Do we need to copy the GB Langref as the US one?  Not if we detected us/gb variance originally
			if (!usGbVariance && !hasUSLangRefset) {
				// This ensures existing langrefset entries are re-used if present, or a new one is created.
				d.setAcceptability(US_ENG_LANG_REFSET, SnomedUtils.translateAcceptability(gbEntry.getAcceptabilityId()));
			}
		}
	}

	private void moveExistingUSLangRefsetEntry(Description d, LangRefsetEntry usEntry, boolean usGbVariance) throws TermServerScriptException {
		usEntry.setModuleId(targetModuleId);
		usEntry.setDirty();
		if (!usGbVariance && d.getLangRefsetEntries(ActiveState.ACTIVE, GB_ENG_LANG_REFSET).isEmpty()) {
			//Might be an inactive GB refset entry that we can reuse
			//In which case, bring it into line with the US value
			List<LangRefsetEntry> gbInactiveLangRefs = d.getLangRefsetEntries(ActiveState.INACTIVE, GB_ENG_LANG_REFSET);
			if (!gbInactiveLangRefs.isEmpty()) {
				LOGGER.debug("Reactivating GB langrefset entry {}", d.getDescriptionId());
				LangRefsetEntry gbEntry = gbInactiveLangRefs.get(0);
				gbEntry.setActive(true);
				gbEntry.setEffectiveTime(null);
				gbEntry.setModuleId(targetModuleId);
				gbEntry.setAcceptabilityId(usEntry.getAcceptabilityId());
				gbEntry.setDirty();
				d.addLangRefsetEntry(gbEntry);
			} else {
				//Otherwise, clone the US entry to use as GB
				LOGGER.debug("Adding gb entry cloned from US {}", d.getDescriptionId());
				d.setAcceptability(GB_ENG_LANG_REFSET, SnomedUtils.translateAcceptability(usEntry.getAcceptabilityId()));
			}
		}
	}

	private void createTargetLangRefsetEntries(Description d) throws TermServerScriptException {
		//The international edition however, does PREF for FSNs
		String acceptability = SCTID_PREFERRED_TERM;
		if (d.getType().equals(DescriptionType.SYNONYM) && !d.isPreferred()) {
			acceptability = SCTID_ACCEPTABLE_TERM;
		}

		for (String refsetId : targetLangRefsetIds) {
			LangRefsetEntry entry = LangRefsetEntry.withDefaults(d, refsetId, acceptability);
			entry.setModuleId(targetModuleId);
			entry.setDirty();
			d.addAcceptability(entry);
		}
	}

	private Description findMatchingDescription(Description d, Concept conceptOnTS) {
		Description match = conceptOnTS.getDescription(d.getId());
		if (match != null) {
			return match;
		}
		
		for (Description m : conceptOnTS.getDescriptions()) {
			if (d.getTerm().contentEquals(m.getTerm()) ) {
				return m;
			}
		}
		return null;
	}

}
