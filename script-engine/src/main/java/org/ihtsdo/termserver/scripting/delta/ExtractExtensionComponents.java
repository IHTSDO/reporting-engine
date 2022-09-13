package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
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

/**
 * Class form a delta of specified concepts from some edition and 
 * promote those (along with attribute values and necessary ancestors)
 * into the core module.
 * TODO Load in the MRCM and properly populate the Never Group attributes
 */
public class ExtractExtensionComponents extends DeltaGenerator {
	
	private List<Component> allIdentifiedConcepts;
	private Set<Component> allModifiedConcepts = new HashSet<>();
	private List<Component> noMoveRequired = new ArrayList<>();
	private boolean includeDependencies = true;
	
	private Map<String, Concept> loadedConcepts = new HashMap<>();
	TermServerClient secondaryConnection;
	String targetModuleId = SCTID_CORE_MODULE;
	private static String secondaryCheckPath = "MAIN";
	private AxiomRelationshipConversionService axiomService = new AxiomRelationshipConversionService (new HashSet<Long>());
	
	private Integer conceptsPerArchive = 10;
	Queue<List<Component>> archiveBatches = null;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ExtractExtensionComponents delta = new ExtractExtensionComponents();
		try {
			delta.runStandAlone = false;
			delta.getArchiveManager().setPopulateReleasedFlag(true);
			//delta.moduleId = "1145237009"; //NEBCSR
			//delta.moduleId = "911754081000004104"; //Nebraska Lexicon Pathology Synoptic module
			//delta.moduleId = "731000124108";  //US Module
			//delta.moduleId = "32506021000036107"; //AU Module
			delta.moduleId = "11000181102"; //Estonia
			delta.getArchiveManager().setRunIntegrityChecks(false);
			delta.init(args);
			SnapshotGenerator.setSkipSave(true);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			//We won't include the project export in our timings
			delta.additionalReportColumns = "FSN, SemTag, Severity, ChangeType, Detail, Additional Detail, , ";
			delta.postInit();
			delta.startTimer();
			delta.preProcessFile();
			delta.processFile();
			if (delta.archiveBatches == null) {
				delta.outputModifiedComponents(true);
				delta.flushFiles(false, true); //Need to flush files before zipping
				if (dryRun) {
					info("Skipping archive creation due to dry run.");
				} else {
					SnomedUtils.createArchive(new File(delta.outputDirName));
				}
			}
		} finally {
			delta.finish();
		}
	}

	protected void init (String[] args) throws TermServerScriptException {
		super.init(args);
		info ("Select an environment for live secondary checking ");
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
	
	
	private void preProcessFile() throws TermServerScriptException {
		archiveBatches = new ArrayDeque<>();
		List<Component> componentsOfInterest = super.processFile();
		//Work through these and group parents with children.
		//However if a parent has more children than we have concepts in a task, 
		//then we have a problem.
		Map<Concept, Set<Concept>> parentChildMap = new HashMap<>();
		Set<Concept> allocatedConcepts = new HashSet<>();
		
		//Concepts that have no ancestors to group in with can be placed in any set
		Queue<Concept> footlooseConcepts = new ArrayDeque<>();
		Set<Concept> requiresFirstPassLoad = new HashSet<>();
		
		for (Component component : componentsOfInterest) {
			boolean alsoImportingAncestors = false;
			boolean alsoImportingDescendants = false;
			
			Concept c = (Concept)component;
			
			if (!c.isReleased()) {
				throw new IllegalStateException(c + " has not been released");
			}
			
			Set<Concept> ancestorsToImport = gl.getAncestorsCache().getAncestors(c, true);  //Need a mutable set
			//If no ancestors in common with what we're loading, then concept is free
			ancestorsToImport.retainAll(componentsOfInterest);
			if (ancestorsToImport.size() > 0) {
				alsoImportingAncestors = true;
				//We'll stop here and allow that ancestor to pick up its descendants when its turn comes
				continue;
			} 
			
			//Does concept have descendants that we want to group with it?
			//We need the parents to be imported before any children, so rework any set as one that maintains the order input
			Set<Concept> descendantsToImport = gl.getDescendantsCache().getDescendents(c, true);
			descendantsToImport.retainAll(componentsOfInterest);
			if (descendantsToImport.size() > 0) {
				alsoImportingDescendants = true;
				info(c + " has " + descendantsToImport.size() + " descendants to import");
				if (descendantsToImport.size() >= conceptsPerArchive) {
					//We'll need to load this concept in it's own import and promote to the project 
					//so it's available as a parent for subsequent loads
					requiresFirstPassLoad.add(c);
					continue;
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
		
		//Now work through the list and see if we can fit any together
		Set<Concept> batchesAvailableToMerge = new HashSet<>(parentChildMap.keySet());
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
					info ("Adding " + bestMerge + "(" + (parentChildMap.get(bestMerge).size() + 1) + ") to batch with parent " + batchParent);
					parentChildMap.remove(bestMerge);
				}
			} while (bestMerge != null);
		}
		
		//Now allocate the footloose concepts to fill up existing or new batches
		info("Allocating " + footlooseConcepts.size() + " loose concepts to " + parentChildMap.size() + " existing batches");
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
			throw new TermServerScriptException("Expected " + mapCount + " + " +  footlooseConcepts.size() + " to equals expected " + componentsOfInterest.size() + " concepts.");
		}
		
		//Add these batches into our queue, as we log the size of each one
		info("Batches Formed:");
		int batchNo = 0;
		for (Concept batchParent : new HashSet<>(parentChildMap.keySet())) {
			List<Component> thisBatch = new ArrayList<>();
			thisBatch.add(batchParent);
			thisBatch.addAll(parentChildMap.get(batchParent));
			info(++batchNo + ": " + batchParent + " - " +  thisBatch.size());
			archiveBatches.add(thisBatch);
		}
		
		//Any concepts left footloose can now be added into additional batches
		List<Component> thisBatch = new ArrayList<>();
		while (!footlooseConcepts.isEmpty()) {
			thisBatch.add(footlooseConcepts.remove());
			if (thisBatch.size() == conceptsPerArchive) {
				archiveBatches.add(thisBatch);
				info(++batchNo + ": No Hierarchy - " + thisBatch.size());
				thisBatch = new ArrayList<>();
			}
		}
		
		if (thisBatch.size() > 0) {
			archiveBatches.add(thisBatch);
			info(++batchNo + ": No Hierarchy - " + thisBatch.size());
		}
		
		long totalConceptsInBatches = archiveBatches.stream()
				.flatMap(l -> l.stream())
				.count();
		info("Total concepts pre-processed into batches: " + totalConceptsInBatches);
		
		if ((int)totalConceptsInBatches != componentsOfInterest.size()) {
			throw new TermServerScriptException("Expected to allocated " + componentsOfInterest.size() + " concepts.");
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
				info ("Processing archive batch " + batchNum++);
				process(archiveBatches.remove());
				outputModifiedComponents(true);
				flushFiles(false, true); //Need to flush files before zipping
				SnomedUtils.createArchive(new File(outputDirName));
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
		addSummaryInformation("Concepts specified", componentsToProcess.size());
		initialiseSummaryInformation("Unexpected dependencies included");
		info ("Extracting specified concepts");
		for (Component thisComponent : componentsToProcess) {
			Concept thisConcept = (Concept)thisComponent;

			//If we don't have a module id for this identified concept, then it doesn't properly exist in this release
			if (thisConcept.getModuleId() == null) {
				report(thisConcept, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Concept specified for extract not found in input Snapshot");
				continue;
			}
			
			if (!thisConcept.isActive()) {
				report(thisConcept, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Concept is inactive, skipping");
				continue;
			}
			
			//Have we already processed this concept because it was a dependency of another concept?
			if (allModifiedConcepts.contains(thisConcept)) {
				report(thisConcept, Severity.LOW, ReportActionType.INFO, "Concept specified for transfer but already processed as a dependency (or is duplicate).");
				continue;
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
			} catch (TermServerScriptException e) {
				report(thisConcept, Severity.CRITICAL, ReportActionType.API_ERROR, "Exception while processing: " + e.getMessage() + " : " + SnomedUtils.getStackTrace(e));
			}
		}
		return componentsToProcess;
		
	}

	private boolean switchModule(Concept c, List<Component> componentsToProcess) throws TermServerScriptException {
		boolean conceptAlreadyTransferred = false;
		
		/*if (c.getConceptId().equals("10200004")) {
			debug("Here: " + c);
		}*/
		
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
			if (!c.getModuleId().equals(moduleId)) {
				report(c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Specified concept in unexpected module, switching anyway", c.getModuleId());
			}
			//Was this concept originally specified, or picked up as a dependency?
			String parents = parentsToString(c);
			
			if (!conceptOnTS.equals(NULL_CONCEPT)) {
				conceptAlreadyTransferred = true;
				report(c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Concept has already been moved to " + targetModuleId + " (possibly as a dependency).   Checking descriptions and relationships", c.getDefinitionStatus().toString(), parents);
			} else {
				if (componentsToProcess.contains(c)) {
					report(c, Severity.LOW, ReportActionType.MODULE_CHANGE_MADE, "Specified concept, module set to " + targetModuleId, c.getDefinitionStatus().toString(), parents);
				} else {
					report(c, Severity.MEDIUM, ReportActionType.CONCEPT_CHANGE_MADE, "Dependency concept, module set to " + targetModuleId, c.getDefinitionStatus().toString(), parents);
				}
				c.setModuleId(targetModuleId);
				//mark it as dirty explicitly, just incase it already had this module!
				c.setDirty();
				incrementSummaryInformation("Concepts moved");
			}
			allModifiedConcepts.add(c);
			
			//If we have no stated modelling (either stated relationships, or those extracted from an axiom, 
			//create an Axiom Entry from the inferred rels.
			if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE).size() == 0) {
				convertInferredRelsToAxiomEntry(c);
			}
		} else {
			conceptAlreadyTransferred = true;
			//Changing the moduleId won't mark concept dirty unless it really does change
			//Don't move any concepts already in the model module
			if (!c.getModuleId().equals(SCTID_MODEL_MODULE)) {
				c.setModuleId(targetModuleId);
				if (componentsToProcess.contains(c)) {
					if (c.getModuleId().equals(targetModuleId)) {
						report(c, Severity.HIGH, ReportActionType.NO_CHANGE, "Specified concept already in target module: " + c.getModuleId() + " checking for additional modeling in source module.");
					} else {
						String msg = "Odd Situation. Concept " + c + " already exists at destinaction in module " + conceptOnTS.getModuleId() + " and also in local content in module " + c.getModuleId();
						warn(msg);
						report(c, Severity.HIGH, ReportActionType.INFO, msg + ".  Looking for additional modelling anyway.");
					}
				}
			}
		}
		
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
				if (moveDescriptionToTargetModule(d, conceptOnTS)) {
					if (true);
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
					if (loadedFSN != null) {
						report(c, Severity.MEDIUM, ReportActionType.DESCRIPTION_INACTIVATED, "Existing FSN on TS inactivated to make way for imported content.", loadedFSN);
						setDescriptionAndLangRefModule(loadedFSN);
						loadedFSN.setActive(false, true); //Force dirty flag
						subComponentsMoved = true;
					}
				} else if (d.isPreferred() && d.getType().equals(DescriptionType.SYNONYM)) {
					String existingPtId = conceptOnTS.getPreferredSynonym(US_ENG_LANG_REFSET).getId();
					Description loadedPT = c.getDescription(existingPtId);
					if (loadedPT != null) {
						report(c, Severity.MEDIUM, ReportActionType.DESCRIPTION_INACTIVATED, "Existing PT on TS demoted to make way for imported content.", loadedPT);
						setDescriptionAndLangRefModule(loadedPT);
						loadedPT.setActive(true); //Will only mark dirty if not already active
						//Only demote to US acceptable if currently preferred in US
						if (loadedPT.isPreferred(US_ENG_LANG_REFSET)) {
							loadedPT.setAcceptablity(US_ENG_LANG_REFSET, Acceptability.ACCEPTABLE);
						}
						
						//Only demote to GB acceptable if currently preferred in GB
						if (loadedPT.isPreferred(GB_ENG_LANG_REFSET)) {
							loadedPT.setAcceptablity(GB_ENG_LANG_REFSET, Acceptability.ACCEPTABLE);
						}
						//The local copy may already be acceptable, so mark as dirty to force this state to TS
						loadedPT.setDirty(ENGLISH_DIALECTS);
						subComponentsMoved = true;
					}
				}

			}
		}
		
		boolean relationshipMoved = false;
		boolean relationshipAlreadyMoved = false;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			//If this is an axiom based relationship, then we'll catch that below with an axiom move
			if (r.fromAxiom()) {
				continue;
			}
			
			//Rel may already be in the target module, but be missing from the target server
			if (conceptOnTS.getRelationships(r).size() > 0) {
				relationshipAlreadyMoved = true;
			} else {
				if (r.isActive() && !r.fromAxiom()) {
					info ("Unexpected active stated relationship not from axiom: "+ r);
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
		
		/* Policy is not to moved inferred modeling
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if ((!r.getModuleId().equals(targetModuleId) && !r.getModuleId().equals(SCTID_MODEL_MODULE)) {
				moveRelationshipToTargetModule(r);
				subComponentsMoved = true;
			}
		}*/
		
		for (AxiomEntry a : c.getAxiomEntries()) {
			//We have an issue with concepts in the examined space already
			//having the target module but needing to move anyway.  So
			//attempt move and see what we're missing.
			if (moveAxiomToTargetModule(c, a, conceptOnTS, componentsToProcess)) {
				subComponentsMoved = true;
			}
		}
		
		if (conceptAlreadyTransferred && !subComponentsMoved) {
			return false;
		}
		
		allModifiedConcepts.add(c);
		
		if (conceptAlreadyTransferred && subComponentsMoved) {
			incrementSummaryInformation("Existing concept, additional components moved.");
		}
		
		return true;
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
				for (Relationship r : AxiomUtils.getRHSRelationships(c, axiomRepresentation)) {
					r.setAxiomEntry(a);
					//This is only needed to include dependencies. 
					//The relationship itself is not attached to the concept
					if (moveRelationshipToTargetModule(r, conceptOnTS, componentsToProcess)) {
						axiomRelationshipMoved = true;
						a.setDirty();
					}
				}
			} else {
				warn ("No Axiom Representation found for : " + c + " : " + a.getOwlExpression());
			}
		} catch (ConversionException e) {
			throw new TermServerScriptException("Failed to convert axiom for " + c , e);
		}
		
		//Check if there are axioms inactivated locally that need to be inactivated at the target
		for (AxiomEntry entry : c.getAxiomEntries()) {
			if (!entry.isActive()) {
				if (!conceptOnTS.equals(NULL_CONCEPT)) {
					Axiom axiomOnTS = conceptOnTS.getClassAxiom(entry.getId());
					if (axiomOnTS != null && axiomOnTS.isActive()) {
						entry.setDirty();
					}
				}
			}
		}
		
		return axiomRelationshipMoved;
	}

	private boolean moveRelationshipToTargetModule(Relationship r, Concept conceptOnTS, List<Component>componentsToProcess) throws TermServerScriptException {
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
				if (!allIdentifiedConcepts.contains(r.getType())) {
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
		Concept target = r.getTarget();
		if (includeDependencies && !allModifiedConcepts.contains(target)) {
			//Don't worry about the target if it's on our to-do list anyway.
			if (!componentsToProcess.contains(target)) {
				//If our dependency is in the core module, then check - live - if it is active
				//because if not, we can't take this relationship.  Check we haven't just moved it there.
				if (target.getModuleId().equals(targetModuleId) && ! allModifiedConcepts.contains(target)) {
					Concept loadedTarget = loadConcept(target);
					//If this target is inactive, find an alternative target and create a replacement relationship
					//TODO in the stated form, we'll need to re-write the axiom if we see this!
					if (!loadedTarget.isActive()) {
						String reason = loadedTarget.getInactivationIndicator().toString();
						Concept replacement = getReplacement(loadedTarget);
						String msg = "Target of " + r + " is inactive in MAIN due to " + reason;
						msg += ". Replacing with " + replacement;
						report(r.getSource(), Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, msg);
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
						r.getSource().removeRelationship(r, r.fromAxiom());
						r.getSource().addRelationship(newRel);
					}
				}
				//Again will recursively switch all dependencies as we're switching a concept here
				if (switchModule(target, componentsToProcess)) {
					//Is this an unexpected dependency
					if (!componentsToProcess.contains(target)) {
						incrementSummaryInformation("Unexpected dependencies included");
						addSummaryInformation("Unexpected target dependency: " + target, "");
						report(r.getSource(), Severity.HIGH, ReportActionType.INFO, "Unexpected dependency required in Stated Modeling", target);
					}
				} else {
					//No need to try to switch this concept again
					noMoveRequired.add(target);
				}
			}
		}
		
		//If we didn't need to transfer the concept, then do report the movement of it's sub components.
		if (!conceptOnTS.equals(NULL_CONCEPT)) {
			report(r.getSource(), Severity.MEDIUM, ReportActionType.MODULE_CHANGE_MADE, r, r.getId());
		}
		return true;
	}

	private Concept getReplacement(Concept inactiveConcept) throws TermServerScriptException {
		/*List<HistoricalAssociation> assocs =  inactiveConcept.getHistorialAssociations(ActiveState.ACTIVE);
		if (assocs.size() == 1) {
			HistoricalAssociation assoc = assocs.iterator().next();
			Concept assocType = gl.getConcept(assoc.getRefsetId());
			Concept assocTarget = gl.getConcept(assoc.getTargetComponentId());
			println (inactiveConcept + " is " + assocType.getPreferredSynonym() + " " + assocTarget);
			return assocTarget;
		} else {
			throw new TermServerScriptException("Unable to find replacement for " + inactiveConcept + " due to " + assocs.size() + " associations");
		}*/
		Set<String> assocs = inactiveConcept.getAssociationTargets().getReplacedBy();
		if (assocs.size() != 1) {
			throw new TermServerScriptException("Unable to find replacement for " + inactiveConcept + " due to " + assocs.size() + " associations");
		} else {
			//We will probably not have loaded this concept via RF2 if the target has been replaced, so load from secondary source
			return loadConcept(gl.getConcept(assocs.iterator().next()));
		}
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
		Description descriptionOnTS = null;
		Concept c = gl.getConcept(d.getConceptId());
		
		//If we already have the concept on the Terminology Server, perhaps we already have the description too,
		//Despite what the local file claims
		boolean doShiftDescription = true;
		if (!conceptOnTS.equals(NULL_CONCEPT)) {
			descriptionOnTS = findMatchingDescription(d, conceptOnTS);
			if (descriptionOnTS != null 
					&& d.isActive() == descriptionOnTS.isActive()
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
		
		//AU for example doesn't give language refset entries for FSNs
		if (d.isActive() && d.getLangRefsetEntries(ActiveState.ACTIVE, targetLangRefsetIds).size() == 0) {
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
		} else {
			for (LangRefsetEntry usEntry : d.getLangRefsetEntries(ActiveState.BOTH, US_ENG_LANG_REFSET)) {
				//Only move if there's a difference
				//Note we cannot get LangRefsetEntries from TS because browser format only uses AcceptabilityMap
				if (doShiftDescription || StringUtils.isEmpty(usEntry.getEffectiveTime())) {
					usEntry.setModuleId(targetModuleId);
					usEntry.setDirty();
					if (!usGbVariance && d.getLangRefsetEntries(ActiveState.BOTH, GB_ENG_LANG_REFSET).size() ==0) {
						LangRefsetEntry gbEntry = usEntry.clone(d.getDescriptionId(), false);
						gbEntry.setRefsetId(GB_ENG_LANG_REFSET);
						d.addAcceptability(gbEntry);
					}
				}
			}
			
			for (LangRefsetEntry gbEntry : d.getLangRefsetEntries(ActiveState.BOTH, GB_ENG_LANG_REFSET)) {
				if (StringUtils.isEmpty(gbEntry.getEffectiveTime())) {
					gbEntry.setModuleId(targetModuleId);
					gbEntry.setDirty(); //Just in case we're missing this component rather than shifting module
				}
			}
		}
		
		//If Desc has been made inactive, take over the inactivation indicators
		if(!d.isActive()) {
			for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries()) {
				if (StringUtils.isEmpty(i.getEffectiveTime())) {
					i.setModuleId(targetModuleId);
					i.setDirty(); //Just in case we're missing this component rather than shifting module
				}
			}
		}
		
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
