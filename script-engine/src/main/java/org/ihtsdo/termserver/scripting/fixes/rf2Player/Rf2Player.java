package org.ihtsdo.termserver.scripting.fixes.rf2Player;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
 * Reads from an RF2 Archive and uses the changes indicated to drive the 
 * TermServer browser API, with tasks grouped by criteria specified by 
 * the concrete class (or just randomly if run directly)
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Rf2Player extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(Rf2Player.class);

	protected Map<String, ConceptChange> changingConcepts = new HashMap<String, ConceptChange>();
	protected Map<String, Description> changingDescriptions = new HashMap<String, Description>();
	protected Set<Concept> deltaModifications = new HashSet<Concept>();
	protected List<String[]> langRefsetStorage = new ArrayList<String[]>();
	protected boolean allowRecentChanges = false;
	protected String processName = "BE Problematic";
	
	public static void main (String[] args) throws FileNotFoundException, TermServerScriptException {
		Rf2Player player = new Rf2Player(null);
		player.playRf2Archive(args);
	}
	
	protected Rf2Player(Rf2Player clone) {
		super(clone);
	}
	
	public String getScriptName() {
		return processName;
	}
	
	protected void playRf2Archive(String[] args) throws TermServerScriptException, FileNotFoundException {
		try {
			selfDetermining = true;
			//putTaskIntoReview = true;
			init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			loadProjectSnapshot(false);
			startTimer();
			LOGGER.info("Processing delta");
			processDelta();
			LOGGER.info("Grouping changes into tasks");
			processFile();
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to play Rf2Archive", e);
		} finally {
			finish();
		}
	}
	
	private void processDelta() throws TermServerScriptException, FileNotFoundException, IOException {
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(getInputFile()));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path p = Paths.get(ze.getName());
						String fileName = p.getFileName().toString();
						ComponentType rf2File = Rf2File.getComponentType(fileName, FileType.DELTA);
						if (rf2File != null) {
							processRf2Delta(zis, rf2File, fileName);
						} else {
							LOGGER.info("Skipping unrecognised file: " + fileName);
						}
					}
					ze = zis.getNextEntry();
				}
			} finally {
				zis.closeEntry();
				zis.close();
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to expand archive " + getInputFile().getName(), e);
		}
		
		for (Concept changingConcept : changingConcepts.values()) {
			//If we didn't get an fsn from the delta, we should have it from the snapshot load
			//This will not be processed, it's just so we know where it's coming from.
			if (changingConcept.getFsn() == null) {
				changingConcept.setFsn(gl.getConcept(changingConcept.getConceptId()).getFsn());
			}
		}
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return new ArrayList<Component> (changingConcepts.values());
	}

	private void processRf2Delta(InputStream is, ComponentType rf2File, String fileName) throws IOException, TermServerScriptException {
		//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
		LOGGER.info("Processing " + fileName);
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String line;
		boolean isHeader = true;
		try {
			while ((line = br.readLine()) != null) {
				if (!isHeader) {
					String[] lineItems = line.split(FIELD_DELIMITER);
					//Ensure that the effectiveTime is set to null for an import delta
					lineItems[IDX_EFFECTIVETIME] = "";
					switch (rf2File) {
						//TODO We're processing the description file prior to the stated relationship file because of 
						//alphabetic order, but if this changes, we'll need to store the file and process it last to ensure
						//that all the descriptions are available for reporting.
						case CONCEPT : processConcept(lineItems, rf2File, fileName);
							break;
						case DESCRIPTION : processDescription(lineItems);
							break; 
						case STATED_RELATIONSHIP : processRelationship(lineItems, true);
							break;
						case LANGREFSET : processLangrefset(lineItems);
							break;
						case INFERRED_RELATIONSHIP : processRelationship(lineItems, false);
							break;
						case ATTRIBUTE_VALUE : processInactivation(lineItems);
						default:
					}
				} else {
					isHeader = false;
				}
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to process " + fileName, e);
		}
	}

	private void processConcept(String[] lineItems, ComponentType rf2File, String fileName) throws TermServerScriptException {
		String id = lineItems[IDX_ID];
		ConceptChange changingConcept;
		if (changingConcepts.containsKey(id)) {
			changingConcept = changingConcepts.get(id);
			Concept.fillFromRf2(changingConcept, lineItems);
		} else {
			changingConcept = Concept.fromRf2Delta(lineItems);
		}
		//This concept is in fact changing, not just a holder for rels and desc
		changingConcept.setModified();
		changingConcepts.put(changingConcept.getConceptId(), changingConcept);
	}

	private void processRelationship(String[] lineItems, boolean isStated) throws NumberFormatException, TermServerScriptException {
		String sourceId = lineItems[REL_IDX_SOURCEID];
		//Do we know about this concept yet?
		ConceptChange changingConcept = changingConcepts.get(sourceId);
		if (changingConcept == null) {
			changingConcept = new ConceptChange(sourceId);
			changingConcepts.put(sourceId, changingConcept);
		}
		CharacteristicType cType = isStated ? CharacteristicType.STATED_RELATIONSHIP : CharacteristicType.INFERRED_RELATIONSHIP;
		gl.addRelationshipToConcept(cType, lineItems, null);
	}

	private void processDescription(String[] lineItems) throws TermServerScriptException {
		String conceptId = lineItems[DES_IDX_CONCEPTID];
		String descId = lineItems[IDX_ID];
		Description changingDescription = changingDescriptions.get(descId);
		if (changingDescription == null) {
			changingDescription = new Description();
			changingDescriptions.put(descId, changingDescription);
		}
		
		ConceptChange changingConcept = changingConcepts.get(conceptId);
		if (changingConcept == null) {
			changingConcept = new ConceptChange(conceptId);
			changingConcepts.put(conceptId, changingConcept);
		}
		
		Description.fillFromRf2(changingDescription, lineItems);
		changingConcept.addDescription(changingDescription);
	}
	
	private void processLangrefset(String[] lineItems) throws TermServerScriptException {
		String descId = lineItems[LANG_IDX_REFCOMPID];
		Description changingDescription = changingDescriptions.get(descId);
		if (changingDescription == null) {
			changingDescription = new Description();
			changingDescriptions.put(descId, changingDescription);
		}
		
		LangRefsetEntry lang = LangRefsetEntry.fromRf2(lineItems);
		changingDescription.addAcceptability(lang);
	}
	

	private void processInactivation(String[] lineItems) {
		//TODO Check if we've got a concept or description inactivation
		//for the moment, we know that they're all description based
		String descId = lineItems[REF_IDX_REFCOMPID];
		Description changingDescription = changingDescriptions.get(descId);
		if (changingDescription == null) {
			changingDescription = new Description();
			changingDescriptions.put(descId, changingDescription);
		}
		
		InactivationIndicator inact = SnomedUtils.translateInactivationIndicator(lineItems[REF_IDX_FIRST_ADDITIONAL]);
		changingDescription.setInactivationIndicator(inact);
	}
		
	/*private String relationshipToString(String typeId, String destId) throws TermServerScriptException {
		Concept destination = gl.getConcept(destId, false, false);  //Don't create, don't validate
		Concept type = gl.getConcept(typeId, false, false);
		String typeStr = SnomedUtils.deconstructFSN(type.getFsn())[0];
		
		if (destination == null) {
			throw new TermServerScriptException ("No knowledge of concept " + destId + " used in relationship");
		}
		String destStr = SnomedUtils.deconstructFSN(destination.getFsn())[0];
		return typeStr + " -> " + destId + "|"  + destStr + "|";
	}*/
	

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = null;
		try{
			loadedConcept = loadConcept(concept, t.getBranchPath());
			if (hasUnpublishedRelationships(loadedConcept, CharacteristicType.STATED_RELATIONSHIP)) {
				report(t, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Recent stated relationship edits detected on this concept");
				if (!allowRecentChanges) {
					return 0;
				}
			}
				
			ConceptChange conceptChanges = (ConceptChange)concept; 
			if (conceptChanges.isModified()) {
				loadedConcept.setActive(conceptChanges.isActive());
				loadedConcept.setEffectiveTime(null);
				loadedConcept.setDefinitionStatus(conceptChanges.getDefinitionStatus());
				report(t, loadedConcept, Severity.MEDIUM, ReportActionType.CONCEPT_CHANGE_MADE, "Definition status set to " + conceptChanges.getDefinitionStatus());
			}
			fixDescriptions(t, loadedConcept, conceptChanges.getDescriptions());
			fixRelationships(t, loadedConcept, conceptChanges.getRelationships());
		} catch (Exception e) {
			//See if we can get that 2nd level exception's reason which says what the problem actually was
			String additionalInfo = "";
			if (e.getCause() != null && e.getCause().getCause() != null) {
				additionalInfo = " - " + e.getCause().getCause().getMessage().replaceAll(COMMA, " ").replaceAll(QUOTE, "'");
			} 
			report(t, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to make changes to concept " + concept.toString() + ": " + e.getClass().getSimpleName()  + " - " + additionalInfo);
			LOGGER.error("Exception encountered",e);
			return 0;
		}
		
		updateConcept(t, loadedConcept, info);
		return CHANGE_MADE;
	}

	private boolean hasUnpublishedRelationships(Concept loadedConcept, CharacteristicType cType) {
		for (Relationship r : loadedConcept.getRelationships(cType, ActiveState.ACTIVE)) {
			if (r.getEffectiveTime() == null || r.getEffectiveTime().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private void fixDescriptions(Task task, Concept loadedConcept, List<Description> descriptions) throws TermServerScriptException {
		for (Description d : descriptions) {
			//Are we adding or deleting a description?
			if (d.isActive()) {
				//TODO Check description doesn't already exist
				//Remove the sctid so that the TS notices it has a new description
				d.setDescriptionId(null);
				d.setEffectiveTime(null);
				loadedConcept.addDescription(d);
				report(task, loadedConcept, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, d.toString());
			} else {
				Description loadedDescription = loadedConcept.getDescription(d.getDescriptionId());
				InactivationIndicator i = d.getInactivationIndicator();
				if (d.getInactivationIndicator() == null) {
					report(task, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Description inactivation indicator not specifed.  Using Non-conformance");
					i = InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY;
				}
				loadedDescription.setInactivationIndicator(i);
				loadedDescription.setActive(false);
				report(task, loadedConcept, Severity.LOW, ReportActionType.DESCRIPTION_INACTIVATED, loadedDescription.toString());
			}
		}
	}
	

	private void fixRelationships(Task task, Concept loadedConcept, Set<Relationship> relationships) throws TermServerScriptException {
		for (Relationship r : relationships) {
			//Are we adding or deleting a Relationship?
			if (r.isActive()) {
				//TODO Check relationship doesn't already exist
				//Remove the sctid so that the TS notices it has a new relationship
				r.setRelationshipId(null);
				r.setEffectiveTime(null);
				loadedConcept.addRelationship(r);
				report(task, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, r.toString());
			} else {
				Relationship loadedRelationship = loadedConcept.getRelationship(r.getRelationshipId());
				if (loadedRelationship == null) {
					report(task, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Relationship " + r.getRelationshipId() + " did not exist in TS to inactivate");
				} else {
					loadedRelationship.setActive(false);
					report(task, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_INACTIVATED, loadedRelationship.toString());
				}
			}
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}
}


