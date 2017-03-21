package org.ihtsdo.termserver.scripting.fixes;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ConceptChange;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import us.monoid.json.JSONObject;

/*
Fix identifies otherwise identical lower case and upper case terms and inactivates
the lower case term
 */
public class DrugsPcdRemodelling extends BatchFix implements RF2Constants{
	
	String[] author_reviewer = new String[] {targetAuthor};
	
	Map <String, List<Relationship>> remodelledAttributes;
	
	protected DrugsPcdRemodelling(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		DrugsPcdRemodelling fix = new DrugsPcdRemodelling(null);
		try {
			fix.useAuthenticatedCookie = true;
			fix.selfDetermining = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			//We won't incude the project export in our timings
			fix.startTimer();
			fix.processFile();
			println ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
		} finally {
			fix.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException, IOException {
		super.init(args);
		
		boolean fileLoaded = false;
		for (int i=0; i < args.length; i++) {
			if (args[i].equalsIgnoreCase("-z")) {
				loadRelationshipFile(args[i+1]);
				fileLoaded = true;
			}
		}
		
		if (!fileLoaded) {
			println ("Failed to find Ban/Usan file to load.  Specify path with 'z' command line parameter");
			System.exit(1);
		}
	}

	private void loadRelationshipFile(String fileName) throws TermServerScriptException {
		try {
			remodelledAttributes = new HashMap<String, List<Relationship>>();
			File relationshipFile = new File(fileName);
			List<String> lines = Files.readLines(relationshipFile, Charsets.UTF_8);
			println ("Loading relationships from " + fileName);

			for (String line : lines) {
				String[] columns = line.split(TAB);
				//Have we seen this concept before?
				String sourceSCTID = columns[0];
				Concept source = graph.getConcept(sourceSCTID);
				Concept product = graph.getConcept(PHARM_BIO_PRODUCT_SCTID);
				List<Relationship> mapEntry = remodelledAttributes.get(sourceSCTID);
				if (mapEntry == null) {
					mapEntry = new ArrayList<Relationship>();
					remodelledAttributes.put(sourceSCTID, mapEntry);
					//First relationship to add is the parent
					Relationship parent = new Relationship(source, IS_A, product, 0);
					mapEntry.add(parent);
				}
				mapEntry.add(createRelationship(columns));
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to import national terms file " + fileName, e);
		}
	}

	private Relationship createRelationship(String[] columns) throws TermServerScriptException {
		Concept source = graph.getConcept(columns[0]);
		Concept type = graph.getConcept(columns[3]);
		Concept destination = graph.getConcept(columns[5]);
		int groupId = Integer.parseInt(columns[7]);
		Relationship r = new Relationship(source, type, destination, groupId);
		r.setActive(true);
		r.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
		return r;
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept tsConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = remodelConcept(task, concept, tsConcept);
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(tsConcept);
				debug ("Updating state of " + tsConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, SEVERITY.CRITICAL, REPORT_ACTION_TYPE.API_ERROR, "Failed to save changed concept to TS: " + e.getMessage());
			}
		}
		return changesMade;
	}

	private int remodelConcept(Task task, Concept concept, Concept tsConcept) throws TermServerScriptException {
		int changesMade = remodelDescriptions(task, concept, tsConcept);
		changesMade += remodelAttributes(task, concept, tsConcept);
		return changesMade;
	}

	private int remodelAttributes(Task task, Concept remodelledConcept, Concept tsConcept) {
		//Inactivate all stated relationships unless they're one of the ones we want to add
		int changesMade = 0;
		if (remodelledConcept.getRelationships().isEmpty()) {
			report(task, tsConcept, SEVERITY.CRITICAL, REPORT_ACTION_TYPE.VALIDATION_ERROR, "No relationships found to remodel concept");
		} else {
			for (Relationship r : tsConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (remodelledConcept.getRelationships().contains(r)) {
						remodelledConcept.getRelationships().remove(r);
					} else {
						r.setActive(false);
						changesMade++;
					}
				}
				//Now loop through whatever relationships we have left and add them to the ts concept
				for (Relationship r : remodelledConcept.getRelationships()) {
					tsConcept.addRelationship(r);
					changesMade++;
				}
		}
		return changesMade;
	}

	private int remodelDescriptions(Task task, Concept remodelledConcept,
			Concept tsConcept) throws TermServerScriptException {
		ConceptChange change = (ConceptChange)remodelledConcept;
		int changesMade = 0;
		if (!change.getFsn().equals(tsConcept.getFsn())) {
			report(task, tsConcept, SEVERITY.CRITICAL, REPORT_ACTION_TYPE.VALIDATION_ERROR, "FSN did not meet change file expectations: " + change.getFsn());
		} else {
			//Firstly, inactivate and replace the FSN
			Description fsn = tsConcept.getDescriptions(Acceptability.PREFERRED, DescriptionType.FSN, ActiveState.ACTIVE).get(0);
			Description replacement = fsn.clone(null);
			replacement.setTerm(change.getFsn());
			fsn.setActive(false);
			changesMade++;
			
			//Now synonyms
			changesMade += remodelSynonyms(task, remodelledConcept, tsConcept);
		}
		return changesMade;
	}

	private int remodelSynonyms(Task task, Concept remodelledConcept,
			Concept tsConcept) {
		int changesMade = 0;
		//Now make all synonyms unacceptable, unless we're keeping them
		for (Description d : tsConcept.getDescriptions(ActiveState.ACTIVE)) {
			Description keeping = findDescription (remodelledConcept, d.getTerm());
			if (keeping!=null) {
				if (!d.isActive()) {
					report(task, tsConcept, SEVERITY.MEDIUM, REPORT_ACTION_TYPE.DESCRIPTION_CHANGE_MADE, "Inactivated description being made active");
					d.setActive(true);
				}
				//TODO Check acceptability of existing term
				//And remove the description from our change set, so we don't add it again
				remodelledConcept.getDescriptions().remove(keeping);
			} else {
				//Reset the acceptability
				d.setAcceptabilityMap(new HashMap<String, Acceptability>());
			}
		}
		//Add back in any remaining descriptions from our change set
		for (Description newDesc : remodelledConcept.getDescriptions()) {
			tsConcept.addDescription(newDesc);
			changesMade++;
		}
		return changesMade;
	}

	//Return the first description that equals the term
	private Description findDescription(Concept remodelledConcept, String term) {
		for (Description d : remodelledConcept.getDescriptions()) {
			if (d.getTerm().equals(term)) {
				return d;
			}
		}
		return null;
	}

	@Override
	protected Batch formIntoBatch (String fileName, List<Concept> allConcepts, String branchPath) throws TermServerScriptException {
		Batch batch = new Batch(getScriptName());
		Task task = batch.addNewTask();

		for (Concept thisConcept : allConcepts) {
			if (task.size() >= taskSize) {
				task = batch.addNewTask();
				setAuthorReviewer(task, author_reviewer);
			}
			task.add(thisConcept);
		}
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		addSummaryInformation(CONCEPTS_PROCESSED, allConcepts);
		return batch;
	}

	private void setAuthorReviewer(Task task, String[] author_reviewer) {
		task.setAssignedAuthor(author_reviewer[0]);
		if (author_reviewer.length > 1) {
			task.setReviewer(author_reviewer[1]);
		}
	}

	@Override
	protected Concept loadLine(String[] items) throws TermServerScriptException {
		String sctid = items[0];
		ConceptChange concept = new ConceptChange(sctid);
		concept.setCurrentTerm(items[1]);
		concept.setFsn(items[2]);
		//TODO Create descriptions with the appropriate acceptability
		concept.setRelationships(remodelledAttributes.get(sctid));
		return concept;
	}
}
