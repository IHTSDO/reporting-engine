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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ConceptChange;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.SnomedRf2File;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
 * Reads from an RF2 Archive and uses the changes indicated to drive the 
 * TermServer browser API, with tasks grouped by criteria specified by 
 * the concrete class
 */
public abstract class Rf2Player extends BatchFix {
	
	String[] author_reviewer = new String[] {targetAuthor};
	Set<Rf2File> filesProcessed = new HashSet<Rf2File>();
	Map<String, ConceptChange> changingConcepts = new HashMap<String, ConceptChange>();
	Map<String, Description> changingDescriptions = new HashMap<String, Description>();
	
	Set<Concept> deltaModifications = new HashSet<Concept>();
	List<String[]> langRefsetStorage = new ArrayList<String[]>();
	
	protected Rf2Player(Rf2Player clone) {
		super(clone);
	}
	
	protected void playRf2Archive(String[] args) throws TermServerScriptException {
		try {
			selfDetermining = true;
			init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			loadProjectSnapshot(false);
			startTimer();
			processDelta();
			Batch batch = formIntoBatch();
			//batchProcess(batch);
			println ("Processing complete.  See results: " + reportFile.getAbsolutePath());
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to play Rf2Archive", e);
		} finally {
			finish();
		}
	}
	
	private void processDelta() throws TermServerScriptException, FileNotFoundException, IOException, SnowOwlClientException {
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(inputFile));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path p = Paths.get(ze.getName());
						String fileName = p.getFileName().toString();
						Rf2File rf2File = SnomedRf2File.getRf2File(fileName, FileType.DELTA);
						filesProcessed.add(rf2File);
						if (rf2File != null) {
							processRf2Delta(zis, rf2File, fileName);
						} else {
							println ("Skipping unrecognised file: " + fileName);
						}
					}
					ze = zis.getNextEntry();
				}
			} finally {
				zis.closeEntry();
				zis.close();
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to expand archive " + inputFile.getName(), e);
		}
		
		for (Concept changingConcept : changingConcepts.values()) {
			//If we didn't get an fsn from the delta, we should have it from the snapshot load
			//This will not be processed, it's just so we know where it's coming from.
			if (changingConcept.getFsn() == null) {
				changingConcept.setFsn(gl.getConcept(changingConcept.getConceptId()).getFsn());
			}
		}
	}

	private void processRf2Delta(InputStream is, Rf2File rf2File, String fileName) throws IOException, TermServerScriptException, SnowOwlClientException {
		//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
		println ("Processing " + fileName);
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
						case RELATIONSHIP : processRelationship(lineItems, false);
							break;
						//case ATTRIBUTE_VALUE : processInactivation(lineItems);
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

	private void processConcept(String[] lineItems, Rf2File rf2File, String fileName) throws TermServerScriptException {
		String id = lineItems[IDX_ID];
		ConceptChange changingConcept;
		if (changingConcepts.containsKey(id)) {
			changingConcept = changingConcepts.get(id);
			Concept.fillFromRf2(changingConcept, lineItems);
		} else {
			changingConcept = Concept.fromRf2Delta(lineItems);
		}
		//This concept is in fact changing, not just a holder for rels and desc
		changingConcept.isModified();
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
		gl.addRelationshipToConcept(changingConcept, cType, lineItems);
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

	private String relationshipToString(String typeId, String destId) throws TermServerScriptException {
		Concept destination = gl.getConcept(destId, false, false);  //Don't create, don't validate
		Concept type = gl.getConcept(typeId, false, false);
		String typeStr = SnomedUtils.deconstructFSN(type.getFsn())[0];
		
		if (destination == null) {
			throw new TermServerScriptException ("No knowledge of concept " + destId + " used in relationship");
		}
		String destStr = SnomedUtils.deconstructFSN(destination.getFsn())[0];
		return typeStr + " -> " + destId + "|"  + destStr + "|";
	}
	

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		ConceptChange conceptChanges = (ConceptChange)concept; 
		if (conceptChanges.isModified()) {
			loadedConcept.setActive(conceptChanges.isActive());
			loadedConcept.setEffectiveTime(null);
			loadedConcept.setDefinitionStatus(conceptChanges.getDefinitionStatus());
		}
		fixDescriptions(task, loadedConcept, conceptChanges.getDescriptions());
		fixRelationships(task, loadedConcept, conceptChanges.getRelationships());
		return 1; 
	}

	private void fixDescriptions(Task task, Concept loadedConcept, List<Description> descriptions) {
		for (Description d : descriptions) {
			//Are we adding or deleting a description?
			if (d.isActive()) {
				//TODO Check description doesn't already exist
				loadedConcept.addDescription(d);
			} else {
				Description loadedDescription = loadedConcept.getDescription(d.getDescriptionId());
				InactivationIndicator i = d.getInactivationIndicator();
				if (d.getInactivationIndicator() == null) {
					report (task, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Description inactivation indicator not specifed.  Using Non-conformance");
					i = InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY;
				}
				loadedDescription.setInactivationIndicator(i);
				loadedDescription.setActive(false);
			}
		}
	}
	

	private void fixRelationships(Task task, Concept loadedConcept, List<Relationship> relationships) {
		for (Relationship r : relationships) {
			//Are we adding or deleting a Relationship?
			if (r.isActive()) {
				//TODO Check relationship doesn't already exist
				loadedConcept.addRelationship(r);
			} else {
				Relationship loadedRelationship = loadedConcept.getRelationship(r.getRelationshipId());
				loadedRelationship.setActive(false);
			}
		}
	}
}


