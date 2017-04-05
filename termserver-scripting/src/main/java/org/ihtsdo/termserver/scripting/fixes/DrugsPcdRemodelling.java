package org.ihtsdo.termserver.scripting.fixes;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ConceptChange;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import us.monoid.json.JSONObject;

/*
Fix identifies otherwise identical lower case and upper case terms and inactivates
the lower case term
 */
public class DrugsPcdRemodelling extends BatchFix implements RF2Constants{
	
	String[] author_reviewer = new String[] {targetAuthor};
	
	public static String SCTID_HAS_ACTIVE_INGREDIENT = "127489000";
	
	enum Mode { VMPF, VCD }
	
	Mode mode = null;
	
	Map <String, List<Relationship>> remodelledAttributes;
	
	protected DrugsPcdRemodelling(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		DrugsPcdRemodelling fix = new DrugsPcdRemodelling(null);
		try {
			fix.init(args);
			fix.inputFileDelimiter = TAB;
			fix.inputFileHasHeaderRow = true;
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.determineProcessingMode(args);
			//We won't include the project export in our timings
			fix.startTimer();
			println ("Processing started.  See results: " + fix.reportFile.getAbsolutePath());
			fix.processFile();
			println ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
		} finally {
			fix.finish();
		}
	}
	
	private void determineProcessingMode(String[] args) throws TermServerScriptException {
		String fileName = null;
		//Work out what file we're trying to load
		for (int i=0; i < args.length; i++) {
			if (args[i].equalsIgnoreCase("-z")) {
				fileName = args[i+1];
			}
		}
		
		if (fileName == null) {
			mode = Mode.VMPF;
			println ("Processing PCDF - Description updates only.");
		} else {
			mode = Mode.VCD;
			println ("Processing PCD_Prep - Description and relationship updates.");
			loadRelationshipFile(fileName);
		}
	}

	private void loadRelationshipFile(String fileName) throws TermServerScriptException {
		try {
			remodelledAttributes = new HashMap<String, List<Relationship>>();
			File relationshipFile = new File(fileName);
			List<String> lines = Files.readLines(relationshipFile, Charsets.UTF_8);
			lines.remove(0); //Delete the header line
			println ("Loading relationships from " + fileName);

			for (String line : lines) {
				String[] columns = line.split(TAB);
				//Have we seen this concept before?
				String sourceSCTID = columns[1];
				//Concept source = graph.getConcept(sourceSCTID);
				//Concept product = graph.getConcept(PHARM_BIO_PRODUCT_SCTID);
				List<Relationship> mapEntry = remodelledAttributes.get(sourceSCTID);
				if (mapEntry == null) {
					mapEntry = new ArrayList<Relationship>();
					remodelledAttributes.put(sourceSCTID, mapEntry);
					// 23/3/17 Existing parents will remain, until grouper issue is resolved.
					//First relationship to add is the parent
					//Relationship parent = new Relationship(source, IS_A, product, 0);
					//mapEntry.add(parent);
					//TODO Add check if sufficient condition changes within a set of relationships
					//This field is missing in the current iteration
				}
				mapEntry.add(createRelationship(columns));
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to import national terms file " + fileName, e);
		}
	}

	private Relationship createRelationship(String[] columns) throws TermServerScriptException {
		
		Concept source = graph.getConcept(columns[1], false);
		int groupId = Integer.parseInt(columns[3]);
		Concept type = graph.getConcept(columns[4], false);
		Concept destination = graph.getConcept(columns[6], false);

		Relationship r = new Relationship(source, type, destination, groupId);
		r.setActive(true);
		r.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
		r.setModifier(Modifier.EXISTENTIAL);
		return r;
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept tsConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = 0;
		if (tsConcept.isActive() == false) {
			report(task, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is inactive.  No changes attempted");
		} else {
			changesMade = remodelConcept(task, concept, tsConcept);
			if (changesMade > 0) {
				try {
					String conceptSerialised = gson.toJson(tsConcept);
					debug ((dryRun?"Dry run updating":"Updating") + " state of " + tsConcept + info);
					if (!dryRun) {
						tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
					}
					report(task, concept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept successfully remodelled. " + changesMade + " changes made.");
				} catch (Exception e) {
					report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + e.getClass().getSimpleName()  + " - " + e.getMessage());
					e.printStackTrace();
				}
			}
		}
		return changesMade;
	}

	private int remodelConcept(Task task, Concept concept, Concept tsConcept) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		changesMade += remodelDescriptions(task, concept, tsConcept);
		if (mode == Mode.VCD) {
			changesMade += remodelAttributes(task, concept, tsConcept);
		}
		return changesMade;
	}

	private int remodelAttributes(Task task, Concept remodelledConcept, Concept tsConcept) throws ValidationFailure {
		//Inactivate all stated relationships unless they're one of the ones we want to add
		int changesMade = 0;
		if (remodelledConcept.getRelationships() == null || remodelledConcept.getRelationships().isEmpty()) {
			throw new ValidationFailure(tsConcept, Severity.LOW, ReportActionType.VALIDATION_ERROR, "No relationships found to remodel concept. Out of scope? Skipping.");
		} else {
			for (Relationship r : tsConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				//Leave existing parents as they are.  Definition status of concept will also not change.
				if (r.getType().equals(IS_A)) {
					continue;
				}
				
				if (r.getType().getConceptId().equals(SCTID_HAS_ACTIVE_INGREDIENT)) {
					//Check the ingredient FSN appears in whole in the product's fsn
					String ingredient = SnomedUtils.deconstructFSN(r.getTarget().getFsn())[0].toLowerCase();
					if (!remodelledConcept.getFsn().toLowerCase().contains(ingredient)) {
						report(task, tsConcept, Severity.MEDIUM, ReportActionType.VALIDATION_ERROR, "New FSN does not contain active ingredient: " + ingredient + ":" + remodelledConcept.getFsn());
					}
				}
				
				if (remodelledConcept.getRelationships().contains(r)) {
					remodelledConcept.getRelationships().remove(r);
					//TODO Check for existing inactive relationships and reactivate
				} else {
					r.setActive(false);
					changesMade++;
				}
			}
			//Now loop through whatever relationships we have left and add them to the ts concept
			for (Relationship r : remodelledConcept.getRelationships()) {
				if (r.getType().isActive() == false || r.getTarget().isActive() == false) {
					report(task, tsConcept, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Unable to add relationship, inactive type or destination: " + r);
				} else {
					tsConcept.addRelationship(r);
					changesMade++;
				}
			}
		}
		return changesMade;
	}

	private int remodelDescriptions(Task task, Concept remodelledConcept,
			Concept tsConcept) throws TermServerScriptException {
		ConceptChange change = (ConceptChange)remodelledConcept;
		int changesMade = 0;
		if (!change.getCurrentTerm().equals(tsConcept.getFsn())) {
			report(task, tsConcept, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "FSN did not meet change file expectations: " + change.getFsn());
		} else {
			//Firstly, inactivate and replace the FSN
			Description fsn = tsConcept.getDescriptions(Acceptability.PREFERRED, DescriptionType.FSN, ActiveState.ACTIVE).get(0);
			Description replacement = fsn.clone(null);
			replacement.setTerm(change.getFsn());
			replacement.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE.toString());
			replacement.setAcceptabilityMap(createAcceptabilityMap(Acceptability.PREFERRED, ENGLISH_DIALECTS));
			fsn.inactivateDescription(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			tsConcept.addDescription(replacement);
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
					report(task, tsConcept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Inactivated description being made active");
					d.setActive(true);
				}
				//TODO Check acceptability of existing term
				//And remove the description from our change set, so we don't add it again
				remodelledConcept.getDescriptions().remove(keeping);
			} else {
				//Reset the acceptability.   Apparently this isn't accepted by the TS.  Must inactivate instead.
				//d.setAcceptabilityMap(null);
				d.inactivateDescription(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
				changesMade++;
				if (isCaseSensitive(d)) {
					report (task, tsConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Inactivated description was case sensitive");
				}
			}
		}
		//Add back in any remaining descriptions from our change set
		for (Description newDesc : remodelledConcept.getDescriptions()) {
			tsConcept.addDescription(newDesc);
			changesMade++;
		}
		return changesMade;
	}

	private boolean isCaseSensitive(Description d) {
		String cs = d.getCaseSignificance();
		return (cs.equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE) || cs.equals(ONLY_INITIAL_CHAR_CASE_INSENSITIVE_SCTID));
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
		Batch batch = new Batch(getReportName());
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
		String sctid = items[1];
		ConceptChange concept = new ConceptChange(sctid);
		concept.setConceptType((mode==Mode.VCD)? ConceptType.PCD_PREP : ConceptType.PCDF);
		concept.setCurrentTerm(items[2]);
		concept.setFsn(items[3]);
		//If we have a column 6, then split the preferred terms into US and GB separately
		if (items.length > 6) {
			addSynonym(concept, items[4], Acceptability.PREFERRED, GB_DIALECT );
			addSynonym(concept, items[6], Acceptability.PREFERRED, US_DIALECT );
			addSynonym(concept, items[7], Acceptability.ACCEPTABLE, US_DIALECT);
		} else {
			addSynonym(concept, items[4], Acceptability.PREFERRED, ENGLISH_DIALECTS );
		}
		addSynonym(concept, items[5], Acceptability.ACCEPTABLE, ENGLISH_DIALECTS);

		if (mode == Mode.VCD) {
			concept.setRelationships(remodelledAttributes.get(sctid));
		}
		return concept;
	}

	private void addSynonym(ConceptChange concept, String term, Acceptability acceptability, String[] dialects) {
		if (term.isEmpty()) {
			return;
		}
		Description d = new Description();
		d.setTerm(term);
		d.setActive(true);
		d.setType(DescriptionType.SYNONYM);
		d.setLang(LANG_EN);
		//TODO May wish to check for captials at idx > 0 and adjust CS.
		d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE.toString());
		d.setAcceptabilityMap(createAcceptabilityMap(acceptability, dialects));
		d.setConceptId(concept.getConceptId());
		concept.addDescription(d);
	}

	private Map<String, Acceptability> createAcceptabilityMap(Acceptability acceptability, String[] dialects) {
		Map<String, Acceptability> aMap = new HashMap<String, Acceptability>();
		for (String dialect : dialects) {
			aMap.put(dialect, acceptability);
		}
		return aMap;
	}
}
