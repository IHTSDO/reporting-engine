package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.ihtsdo.termserver.scripting.AxiomUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClient;

import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.owltoolkit.conversion.AxiomRelationshipConversionService;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;

/**
 * Class form a delta of specified concepts from some edition and 
 * promote those (along with attribute values and necessary ancestors)
 * into the core module.
 * 
 */
public class ExtractExtensionComponents extends DeltaGenerator {
	
	private List<Component> allIdentifiedConcepts;
	private List<Component> allModifiedConcepts = new ArrayList<>();
	private Map<String, Concept> loadedConcepts = new HashMap<>();
	TermServerClient secondaryConnection;
	String targetModuleId = SCTID_CORE_MODULE;
	private static String secondaryCheckPath = "MAIN";
	//private static String secondaryCheckPath = "MAIN/2019-07-31";
	private AxiomRelationshipConversionService axiomService = new AxiomRelationshipConversionService (null);
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ExtractExtensionComponents delta = new ExtractExtensionComponents();
		try {
			delta.runStandAlone = true;
			delta.moduleId = "731000124108";  //US Module
			//delta.moduleId = "32506021000036107"; //AU Module
			delta.init(args);
			delta.getArchiveManager().loadEditionArchive = true;
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			//We won't incude the project export in our timings
			delta.additionalReportColumns = "ACTION_DETAIL, DEF_STATUS, PARENT";
			delta.postInit();
			delta.startTimer();
			delta.processFile();
			delta.outputModifiedComponents();
			delta.flushFiles(false, true); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}
	
	protected void init (String[] args) throws TermServerScriptException {
		super.init(args);
		
		info ("Select an environment for secondary checking ");
		for (int i=0; i < environments.length; i++) {
			info ("  " + i + ": " + environments[i]);
		}
		
		print ("Choice: ");
		String choice = STDIN.nextLine().trim();
		int envChoice = Integer.parseInt(choice);
		String url = environments[envChoice];
		
		print ("Please enter your authenticated cookie for connection to " + url + " : ");
		String secondaryCookie = STDIN.nextLine().trim();
		secondaryConnection = createTSClient(url, secondaryCookie);
		
		print ("Specify source module id " + (moduleId==null?": ":"[" + moduleId + "]: "));
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			moduleId = response;
		}
	}

	protected List<Component> processFile() throws TermServerScriptException {
		allIdentifiedConcepts = super.processFile();
		addSummaryInformation("Concepts specified", allIdentifiedConcepts.size());
		info ("Extracting specified concepts");
		for (Component thisComponent : allIdentifiedConcepts) {
			Concept thisConcept = (Concept)thisComponent;
			if (!thisConcept.isActive()) {
				report (thisConcept, null, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is inactive, skipping");
				continue;
			}
			try {
				switchModule(thisConcept);
			} catch (TermServerScriptException e) {
				report (thisConcept, null, Severity.CRITICAL, ReportActionType.API_ERROR, "Exception while processing: " + e.getMessage() + " : " + SnomedUtils.getStackTrace(e));
			}
		}
		return allIdentifiedConcepts;
	}
	
	private void outputModifiedComponents() throws TermServerScriptException {
		info ("Outputting to RF2...");
		for (Concept thisConcept : gl.getAllConcepts()) {
/*			if (thisConcept.getConceptId().equals("820511000168109")) {
				debug ("Here");
			}*/
			try {
				outputRF2((Concept)thisConcept, false);  //Don't check desc/rels if concept not modified.
			} catch (TermServerScriptException e) {
				report ((Concept)thisConcept, null, Severity.CRITICAL, ReportActionType.API_ERROR, "Exception while processing: " + e.getMessage() + " : " + SnomedUtils.getStackTrace(e));
			}
		}
	}

	private void switchModule(Concept c) throws TermServerScriptException {
		
		//Switch the module of this concept, then all active descriptions and relationships
		if (c.getModuleId().equals(moduleId)) {
			//Was this concept originally specified, or picked up as a dependency?
			String parents = parentsToString(c);
			
			//It's possible that this concept has already been transferred by an earlier run if it was identified as a dependency, so 
			//we have to check every concept.
			Concept dependency = loadConcept(c);
			if (!dependency.equals(NULL_CONCEPT)) {
				report (c, null, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Concept has already been promoted to core (possibly as a dependency)", c.getDefinitionStatus().toString(), parents);
				return;
			}
			
			if (allIdentifiedConcepts.contains(c)) {
				report (c, null, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Specified concept, module set to core", c.getDefinitionStatus().toString(), parents);
			} else {
				report (c, null, Severity.MEDIUM, ReportActionType.CONCEPT_CHANGE_MADE, "Dependency concept, module set to core", c.getDefinitionStatus().toString(), parents);
			}
			c.setModuleId(targetModuleId);
			allModifiedConcepts.add(c);
			
			//If we have no stated modelling (either stated relationships, or those extracted from an axiom, 
			//create an Axiom Entry from the inferred rels.
			if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE).size() == 0) {
				convertInferredRelsToAxiomEntry(c);
			}
			
			incrementSummaryInformation("Concepts moved");
		} else {
			if (allIdentifiedConcepts.contains(c)) {
				if (c.getModuleId().equals(targetModuleId)) {
					report (c, null, Severity.HIGH, ReportActionType.NO_CHANGE, "Specified concept already in target module: " + c.getModuleId());
				} else {
					report (c, null, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Specified concept in unexpected module: " + c.getModuleId());
				}
			}
			return;
		}
		
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getModuleId().equals(moduleId)) {
				moveDescriptionToTargetModule(d);
			}
		}
		
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getModuleId().equals(moduleId)) {
				if (r.isActive() && !r.fromAxiom()) {
					info ("Unexpected active stated relationship: "+ r);
				}
				moveRelationshipToTargetModule(r);
			}
		}
		
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getModuleId().equals(moduleId)) {
				moveRelationshipToTargetModule(r);
			}
		}
		
		for (AxiomEntry a : c.getAxiomEntries()) {
			if (a.getModuleId().equals(moduleId)) {
				moveAxiomToTargetModule(c,a);
			}
		}
	}

	private void convertInferredRelsToAxiomEntry(Concept c) {
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
	
	private void moveAxiomToTargetModule(Concept c, AxiomEntry a) throws TermServerScriptException {
		try {
			a.setModuleId(targetModuleId);
			AxiomRepresentation axiomRepresentation = axiomService.convertAxiomToRelationships(a.getOwlExpression());
			for (Relationship r : AxiomUtils.getRHSRelationships(c, axiomRepresentation)) {
				//This is only needed to include dependencies. 
				//The relationship itself is not attached to the concept
				moveRelationshipToTargetModule(r);
			}
		} catch (ConversionException e) {
			throw new TermServerScriptException("Failed to convert axiom for " + c , e);
		}
	}

	private void moveRelationshipToTargetModule(Relationship r) throws TermServerScriptException {
		//Switch the relationship.   Also switch both the type and the destination - will return if not needed
		r.setModuleId(targetModuleId);
		switchModule(r.getType());
		//Note that switching the target will also recursively work up the hierarchy as parents are also switched
		//up the hierarchy until a concept owned by the core module is encountered.
		
		Concept target = r.getTarget();
		//Don't worry about the target if it's on our to-do list anyway.
		if (!allIdentifiedConcepts.contains(target)) {
			//If our dependency is in the core module, then check - live - if it is active
			//because if not, we can't take this relationship.  Check we haven't just moved it there.
			if (target.getModuleId().equals(targetModuleId) && ! allModifiedConcepts.contains(target)) {
				Concept loadedTarget = loadConcept(target);
				//If this target is inactive, find an alternative target and create a replacement relationship
				if (!loadedTarget.isActive()) {
					String reason = loadedTarget.getInactivationIndicator().toString();
					Concept replacement = getReplacement(loadedTarget);
					String msg = "Target of " + r + " is inactive in MAIN due to " + reason;
					msg += ". Replacing with " + replacement;
					report (r.getSource(), null, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, msg);
					target = replacement;
					Relationship newRel = new Relationship(r.getSource(),r.getType(), replacement, r.getGroupId());
					newRel.setDirty();
					newRel.setRelationshipId(relIdGenerator.getSCTID());
					r.getSource().removeRelationship(r);
					r.getSource().addRelationship(newRel);
				}
			}
			switchModule(target);
		}
	}

	private Concept getReplacement(Concept inactiveConcept) throws TermServerScriptException {
		/*List<HistoricalAssociation> assocs =  inactiveConcept.getHistorialAssociations(ActiveState.ACTIVE);
		if (assocs.size() == 1) {
			HistoricalAssociation assoc = assocs.get(0);
			Concept assocType = gl.getConcept(assoc.getRefsetId());
			Concept assocTarget = gl.getConcept(assoc.getTargetComponentId());
			println (inactiveConcept + " is " + assocType.getPreferredSynonym() + " " + assocTarget);
			return assocTarget;
		} else {
			throw new TermServerScriptException("Unable to find replacement for " + inactiveConcept + " due to " + assocs.size() + " associations");
		}*/
		List<String> assocs = inactiveConcept.getAssociationTargets().getReplacedBy();
		if (assocs.size() != 1) {
			throw new TermServerScriptException("Unable to find replacement for " + inactiveConcept + " due to " + assocs.size() + " associations");
		} else {
			//We will probably not have loaded this concept via RF2 if the target has been replaced, so load from seconardy source
			return loadConcept(gl.getConcept(assocs.get(0)));
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

	private void moveDescriptionToTargetModule(Description d) throws TermServerScriptException {
		//First swap the module id, then add in the GB refset 
		d.setModuleId(targetModuleId);
		
		//AU for example doesn't give language refset entries for FSNs
		if (d.getLangRefsetEntries(ActiveState.ACTIVE, targetLangRefsetIds).size() == 0) {
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
			for (LangRefsetEntry usEntry : d.getLangRefsetEntries(ActiveState.ACTIVE, US_ENG_LANG_REFSET)) {
				usEntry.setModuleId(targetModuleId);
				LangRefsetEntry gbEntry = usEntry.clone(d.getDescriptionId());
				gbEntry.setRefsetId(GB_ENG_LANG_REFSET);
				d.addAcceptability(gbEntry);
			}
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return Collections.singletonList(gl.getConcept(lineItems[0]));
	}
}
