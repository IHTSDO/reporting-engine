package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Class form a delta of specified concepts from some edition and 
 * promote those (along with attribute values and necessary ancestors)
 * into the core module.
 */
public class ExtractExtensionComponents extends DeltaGenerator {
	
	private List<Component> allIdentifiedConcepts;
	private List<Component> allModifiedConcepts = new ArrayList<>();
	private Map<String, Concept> loadedConcepts = new HashMap<>();
	SnowOwlClient secondaryConnection;
	private static Concept NULL_CONCEPT = new Concept("-1");
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		ExtractExtensionComponents delta = new ExtractExtensionComponents();
		try {
			delta.runStandAlone = true;
			delta.moduleId = "731000124108";  //US Module
			delta.additionalReportColumns = "ACTION_DETAIL, DEF_STATUS, PARENT";
			
			delta.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			//We won't incude the project export in our timings
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
		secondaryConnection = new SnowOwlClient(url + "snowowl/snomed-ct/v2", secondaryCookie);
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
		for (Component thisConcept : gl.getAllConcepts()) {
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
			
			//It's possible that this concept has already been transferred by an earlier run if it was identified as a dependecy, so 
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
			c.setModuleId(SCTID_CORE_MODULE);
			allModifiedConcepts.add(c);
			incrementSummaryInformation("Concepts moved");
		} else {
			if (allIdentifiedConcepts.contains(c)) {
				report (c, null, Severity.HIGH, ReportActionType.CONCEPT_CHANGE_MADE, "Specified concept in unexpected module: " + c.getModuleId());
			}
			return;
		}
		
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getModuleId().equals(moduleId)) {
				moveDescriptionToCore(d);
			}
		}
		
		List<Relationship> activeRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
		for (Relationship r : activeRels) {
			moveRelationshipToCore(r);
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

	private void moveRelationshipToCore(Relationship r) throws TermServerScriptException {
		//Switch the relationship.   Also switch both the type and the destination - will return if not needed
		r.setModuleId(SCTID_CORE_MODULE);
		switchModule(r.getType());
		//Note that switching the target will also recursively work up the hierarchy as parents are also switched
		//up the hierarchy until a concept owned by the core module is encountered.
		
		//Don't worry about the target if it's on our to-do list anyway.
		Concept target = r.getTarget();
		if (!allIdentifiedConcepts.contains(target)) {
			//If our dependecy is in the core module, then check - live - if it is active
			//because if not, we can't take this relationship.  Check we haven't just moved it there.
			if (target.getModuleId().equals(SCTID_CORE_MODULE) && ! allModifiedConcepts.contains(target)) {
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
			loadedConcept = loadConcept(secondaryConnection, c, "MAIN");
			//If the concept was not found, that may be OK
			if (loadedConcept == null) {
				loadedConcept = NULL_CONCEPT;
			}
			loadedConcepts.put(loadedConcept.getConceptId(), loadedConcept);
		}
		return loadedConcept;
	}

	private void moveDescriptionToCore(Description d) throws TermServerScriptException {
		//First swap the module id, then add in the GB refset 
		d.setModuleId(SCTID_CORE_MODULE);
		for (LangRefsetEntry usEntry : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
			usEntry.setModuleId(SCTID_CORE_MODULE);
			if (!usEntry.getRefsetId().equals(US_ENG_LANG_REFSET)) {
				throw new TermServerScriptException("Unexpected language refset entry for " + d + ": " + usEntry.getRefsetId());
			}
			LangRefsetEntry gbEntry = usEntry.clone(d.getDescriptionId());
			gbEntry.setRefsetId(GB_ENG_LANG_REFSET);
			d.addAcceptability(gbEntry);
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return Collections.singletonList(gl.getConcept(lineItems[0]));
	}
}
