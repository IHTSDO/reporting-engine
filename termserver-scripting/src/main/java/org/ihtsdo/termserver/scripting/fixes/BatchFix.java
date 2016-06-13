package org.ihtsdo.termserver.scripting.fixes;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipSerializer;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONException;
import us.monoid.web.JSONResource;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Reads in a file containing a list of concept SCTIDs and processes them in batches
 * in tasks created on the specified project
 */
public abstract class BatchFix extends TermServerFix implements RF2Constants {
	
	protected int batchSize = 5;
	protected int restartPosition = NOT_SET;
	File batchFixFile;
	File reportFile;
	protected String targetAuthor;
	
	protected static Gson gson;
	static {
		GsonBuilder gsonBuilder = new GsonBuilder();
		//gsonBuilder.registerTypeAdapter(Concept.class, new ConceptDeserializer());
		gsonBuilder.registerTypeAdapter(Relationship.class, new RelationshipSerializer());
		gsonBuilder.setPrettyPrinting();
		gsonBuilder.excludeFieldsWithoutExposeAnnotation();
		gson = gsonBuilder.create();
	}
	
	protected static GraphLoader graph = GraphLoader.getGraphLoader();

	public enum REPORT_ACTION_TYPE { API_ERROR, CONCEPT_CHANGE_MADE, INFO, UNEXPECTED_CONDITION,
									 RELATIONSHIP_ADDED, RELATIONSHIP_REMOVED, DESCRIPTION_CHANGE_MADE, 
									 NO_CHANGE, VALIDATION_ERROR};
									 
	public enum SEVERITY { NONE, LOW, MEDIUM, HIGH, CRITICAL }; 

	protected BatchFix (BatchFix clone) {
		if (clone != null) {
			this.batchFixFile = clone.batchFixFile;
			this.reportFile = clone.reportFile;
			this.project = clone.project;
			this.tsClient = clone.tsClient;
			this.scaClient = clone.scaClient;
		}
	}

	protected void processFile() throws TermServerFixException {
		try {
			List<String> lines = Files.readLines(batchFixFile, Charsets.UTF_8);
			List<Concept> allConcepts = new ArrayList<Concept>();
			
			//Are we restarting the file from some line number
			int startPos = (restartPosition == NOT_SET)?0:restartPosition - 1;
			for (int lineNum = startPos; lineNum < lines.size(); lineNum++) {
				if (lineNum == 0) {
					//continue; //skip header row  //Current file format has no header
				}
				
				//File format Concept Type, SCTID, FSN with string fields quoted.  Strip quotes also.
				String[] lineItems = lines.get(lineNum).replace("\"", "").split(TSV_FIELD_DELIMITER);
				if (lineItems.length > 1) {
					Concept c = graph.getConcept(lineItems[1]);
					c.setConceptType(lineItems[0]);
					allConcepts.add(c);
				} else {
					debug ("Skipping blank line " + lineNum);
				}
			}
			String projectPath = "MAIN/" + project;
			List<Batch> batches = formIntoBatches(batchFixFile.getName(), allConcepts, projectPath);
			batchProcess(batches);
		} catch (FileNotFoundException e) {
			throw new TermServerFixException("Unable to open batch file " + batchFixFile.getAbsolutePath(), e);
		} catch (IOException e) {
			throw new TermServerFixException("Error while reading batch file " + batchFixFile.getAbsolutePath(), e);
		}
		println ("Processing complete.  See results: " + reportFile.getAbsolutePath());
	}
	
	abstract List<Batch> formIntoBatches (String fileName, List<Concept> allConcepts, String branchPath) throws TermServerFixException;
	
	abstract int doFix(Task task, Concept concept) throws TermServerFixException;

	private void batchProcess(List<Batch> batches) throws TermServerFixException {
		int failureCount = 0;
		for (Batch batch : batches) {
			for (Task task : batch.getTasks()) {
				try {
					String branchPath;
					String taskKey;
					//Create a task for this batch of concepts
					if (!dryRun) {
						debug ("Creating jira task on project: " + project);
						taskKey = scaClient.createTask(project, task.getDescription(), task.getSummaryHTML());
						debug ("Creating task branch in terminology server: " + taskKey);
						branchPath = tsClient.createBranch("MAIN/" + project, taskKey);
					} else {
						taskKey = project + "-" + getNextDryRunNum();
						branchPath = "MAIN/" + project + "/" + taskKey;
					}
					
					debug ( (dryRun?"Dry Run " : "Created") + "task: " + branchPath);
					task.setTaskKey(taskKey);
					task.setBranchPath(branchPath);
					
					//Process each concept
					for (Concept concept : task.getConcepts()) {
						try {
							int changesMade = doFix(task, concept);
							if (changesMade == 0) {
								report(task, concept, SEVERITY.NONE, REPORT_ACTION_TYPE.NO_CHANGE, "");
							}
						} catch (TermServerFixException e) {
							report(task, concept, SEVERITY.CRITICAL, REPORT_ACTION_TYPE.API_ERROR, getMessage(e));
							if (++failureCount > maxFailures) {
								throw new TermServerFixException ("Failure count exceeded " + maxFailures);
							}
						}
					}
					
					if (!dryRun) {
						//Prefill the Edit Panel
						try {
							scaClient.setUIState(project, taskKey, task.toQuotedList());
						} catch (Exception e) {
							String msg = "Failed to preload edit-panel ui state: " + e.getMessage();
							warn (msg);
							report(task, null, SEVERITY.LOW, REPORT_ACTION_TYPE.API_ERROR, msg);
						}
						
						//Reassign the task to the intended author
						if (targetAuthor != null && !targetAuthor.isEmpty()) {
							scaClient.updateTask(project, taskKey, null, null, targetAuthor);
						}
					}
				} catch (Exception e) {
					throw new TermServerFixException("Failed to process batch " + task.getDescription() + " on task " + task.getTaskKey(), e);
				}
			}
		}
		
	}

	protected int ensureDefinitionStatus(Task t, Concept c, DEFINITION_STATUS targetDefStat) {
		int changesMade = 0;
		if (!c.getDefinitionStatus().equals(targetDefStat.toString())) {
			report (t, c, SEVERITY.MEDIUM, REPORT_ACTION_TYPE.CONCEPT_CHANGE_MADE, "Definition status changed to " + targetDefStat);
			c.setDefinitionStatus(targetDefStat.toString());
			changesMade++;
		}
		return changesMade;
	}
	
	protected void report(Task task, Concept concept, SEVERITY severity, REPORT_ACTION_TYPE actionType, String actionDetail) {
		String sctid = "";
		String fsn = "";
		if (concept != null) {
			sctid = concept.getConceptId();
			fsn = concept.getFsn();
		}
		String batchKey = (task == null? "" :  task.getTaskKey());
		String batchDesc = (task == null? "" :  task.getDescription());
		String line = batchKey + COMMA + batchDesc + COMMA + sctid + COMMA_QUOTE + fsn + QUOTE_COMMA + concept.getConceptType() + COMMA + severity + COMMA + actionType + COMMA + QUOTE + actionDetail + QUOTE;
		writeToFile (line);
	}
	
	private void writeToFile(String line) {
		try(FileWriter fw = new FileWriter(reportFile, true);
				BufferedWriter bw = new BufferedWriter(fw);
				PrintWriter out = new PrintWriter(bw))
		{
			out.println(line);
		} catch (Exception e) {
			print ("Unable to output report line: " + line + " due to " + e.getMessage());
		}
		
	}


	protected void init (String[] args) throws TermServerFixException, IOException {
		if (args.length < 3) {
			println("Usage: java <FixClass> [-a author] [-b <batchSize>] [-r <restart lineNum>] [-c <authenticatedCookie>] [-d <Y/N>] [-p <projectName>] <batch file Location>");
			println(" d - dry run");
			System.exit(-1);
		}
		boolean isBatchSize = false;
		boolean isProjectName = false;
		boolean isCookie = false;
		boolean isDryRun = false;
		boolean isAuthor = false;
		boolean isRestart = false;
	
		for (String thisArg : args) {
			if (thisArg.equals("-a")) {
				isAuthor = true;
			} else if (thisArg.equals("-b")) {
				isBatchSize = true;
			} else if (thisArg.equals("-p")) {
				isProjectName = true;
			} else if (thisArg.equals("-c")) {
				isCookie = true;
			} else if (thisArg.equals("-d")) {
				isDryRun = true;
			}  else if (thisArg.equals("-r")) {
				isRestart = true;
			} else if (isAuthor) {
				targetAuthor = thisArg.toLowerCase();
				isAuthor = false;
			} else if (isBatchSize) {
				batchSize = Integer.parseInt(thisArg);
				isBatchSize = false;
			} else if (isProjectName) {
				project = thisArg;
				isProjectName = false;
			} else if (isDryRun) {
				dryRun = thisArg.toUpperCase().equals("Y");
				isDryRun = false;
			} else if (isRestart) {
				restartPosition = Integer.parseInt(thisArg);
				isRestart = false;
			} else if (isCookie) {
				authenticatedCookie = thisArg;
				isCookie = false;
			} else {
				File possibleFile = new File(thisArg);
				if (possibleFile.exists() && !possibleFile.isDirectory() && possibleFile.canRead()) {
					batchFixFile = possibleFile;
				}
			}
		}
		if (batchFixFile == null) {
			throw new TermServerFixException("No valid batch import file detected in command line arguments");
		}
		
		if (targetAuthor == null) {
			throw new TermServerFixException("No target author detected in command line arguments");
		}
		init();
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = "batch_fix_" + df.format(new Date()) + ".csv";
		reportFile = new File(reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("TASK_KEY, TASK_DESC, SCTID, FSN, CONCEPT_TYPE,SEVERITY,ACTION_TYPE,ACTION_DETAIL");
	}
	
	Concept loadConcept(Concept concept, String branchPath) throws TermServerFixException {
		debug ("Loading: " + concept + " from TS.");
		try {
			//In a dry run situation, the task branch is not created so use the Project instead
			if (dryRun) {
				branchPath = branchPath.substring(0, branchPath.lastIndexOf("/"));
			}
			JSONResource response = tsClient.getConcept(concept.getConceptId(), branchPath);
			String json = response.toObject().toString();
			concept = gson.fromJson(json, Concept.class);
			concept.setLoaded(true);
		} catch (SnowOwlClientException | JSONException | IOException e) {
			throw new TermServerFixException("Failed to recover " + concept + " from TS",e);
		}
		return concept;
	}
	
	protected int ensureAcceptableParent(Task task, Concept c, Concept acceptableParent) {
		List<Relationship> statedParents = c.getRelationships(CHARACTERISTIC_TYPE.STATED_RELATIONSHIP, IS_A, ACTIVE_STATE.ACTIVE);
		boolean hasAcceptableParent = false;
		int changesMade = 0;
		for (Relationship thisParent : statedParents) {
			if (!thisParent.getTarget().equals(acceptableParent)) {
				report(task, c, SEVERITY.MEDIUM, REPORT_ACTION_TYPE.RELATIONSHIP_REMOVED, "Inactivated unwanted parent: " + thisParent.getTarget());
				thisParent.setActive(false);
				changesMade++;
			} else {
				hasAcceptableParent = true;
			}
		}
		
		if (!hasAcceptableParent) {
			c.addRelationship(IS_A, acceptableParent);
			changesMade++;
			report(task, c, SEVERITY.MEDIUM, REPORT_ACTION_TYPE.RELATIONSHIP_ADDED, "Added required parent: " + acceptableParent);
		}
		return changesMade;
	}
	
	/**
	 Validate that that any attribute with that attribute type is a descendent of the target Value
	 * @param cardinality 
	 * @throws TermServerFixException 
	 */
	protected int validateAttributeValues(Task task, Concept concept,
			Concept attributeType, Concept descendentsOfValue, CARDINALITY cardinality) throws TermServerFixException {
		
		List<Relationship> attributes = concept.getRelationships(CHARACTERISTIC_TYPE.ALL, attributeType, ACTIVE_STATE.ACTIVE);
		Set<Concept> descendents = ClosureCache.getClosureCache().getClosure(descendentsOfValue);
		for (Relationship thisAttribute : attributes) {
			Concept value = thisAttribute.getTarget();
			if (!descendents.contains(value)) {
				SEVERITY severity = thisAttribute.isActive()?SEVERITY.CRITICAL:SEVERITY.LOW;
				String activeStr = thisAttribute.isActive()?"":"inactive ";
				String relType = thisAttribute.getCharacteristicType().equals(CHARACTERISTIC_TYPE.STATED_RELATIONSHIP)?"stated ":"inferred ";
				String msg = "Attribute has " + activeStr + relType + "target which is not a descendent of: " + descendentsOfValue;
				report (task, concept, severity, REPORT_ACTION_TYPE.VALIDATION_ERROR, msg);
			}
		}
		
		int changesMade = 0;
		
		//Now check cardinality on active stated relationships
		attributes = concept.getRelationships(CHARACTERISTIC_TYPE.STATED_RELATIONSHIP, attributeType, ACTIVE_STATE.ACTIVE);
		String msg = null;
		switch (cardinality) {
			case EXACTLY_ONE : if (attributes.size() != 1) {
									msg = "Concept has " + attributes.size() + " active stated attributes of type " + attributeType + " expected exactly one.";
								}
								break;
			case AT_LEAST_ONE : if (attributes.size() < 1) {
									msg = "Concept has " + attributes.size() + " active stated attributes of type " + attributeType + " expected one or more.";
								}
								break;
		}
		
		//If we have no stated attributes of the expected type, attempt to pull from the inferred ones
		if (attributes.size() == 0) {
			changesMade = transferInferredRelationshipsToStated(task, concept, attributeType, cardinality);
		}
		
		//If we have an error message but have not made any changes to the concept, then report that error now
		if (msg != null && changesMade == 0) {
			report (task, concept, SEVERITY.HIGH, REPORT_ACTION_TYPE.VALIDATION_ERROR, msg);
		}
		return changesMade;
	}
	

	private int transferInferredRelationshipsToStated(Task task,
			Concept concept, Concept attributeType, CARDINALITY cardinality) {
		List<Relationship> replacements = concept.getRelationships(CHARACTERISTIC_TYPE.INFERRED_RELATIONSHIP, attributeType, ACTIVE_STATE.ACTIVE);
		int changesMade = 0;
		if (replacements.size() == 0) {
			String msg = "Unable to find any inferred " + attributeType + " relationships to state.";
			report(task, concept, SEVERITY.HIGH, REPORT_ACTION_TYPE.INFO, msg);
		} else if (cardinality.equals(CARDINALITY.EXACTLY_ONE) && replacements.size() > 1) {
			String msg = "Found " + replacements.size() + " " + attributeType + " relationships to state but wanted only one!";
			report(task, concept, SEVERITY.HIGH, REPORT_ACTION_TYPE.INFO, msg);
		} else {
			//Clone the inferred relationships, make them stated and add to concept
			for (Relationship replacement : replacements) {
				Relationship statedClone = replacement.clone();
				statedClone.setCharacteristicType(CHARACTERISTIC_TYPE.STATED_RELATIONSHIP);
				concept.addRelationship(statedClone);
				String msg = "Restated inferred relationship: " + replacement;
				report(task, concept, SEVERITY.MEDIUM, REPORT_ACTION_TYPE.RELATIONSHIP_ADDED, msg);
				changesMade++;
			}
		}
		return changesMade;
	}

	protected void validatePrefInFSN(Task task, Concept concept) throws TermServerFixException {
		//Check that the FSN with the semantic tags stripped off is
		//equal to the preferred terms
		List<Description> preferredTerms = concept.getDescriptions(ACCEPTABILITY.PREFERRED, DESCRIPTION_TYPE.SYNONYM, ACTIVE_STATE.ACTIVE);
		String trimmedFSN = SnomedUtils.deconstructFSN(concept.getFsn())[0];
		//Special handling for acetaminophen
		if (trimmedFSN.toLowerCase().contains(ACETAMINOPHEN)) {
			println ("Doing ACETAMINOPHEN processing for " + concept);
		} else {
			for (Description pref : preferredTerms) {
				if (!pref.getTerm().equals(trimmedFSN)) {
					String msg = concept + " has preferred term that does not match FSN: " + pref.getTerm();
					report (task, concept, SEVERITY.HIGH, REPORT_ACTION_TYPE.VALIDATION_ERROR, msg);
				}
			}
		}
	}
}
