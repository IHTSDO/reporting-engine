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

import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipSerializer;

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
public abstract class BatchFix extends TermServerFix implements RF2Constants{
	
	protected int batchSize = 5;
	File batchFixFile;
	File reportFile;
	private static String COMMA = ",";
	private static String CSV_FIELD_DELIMITER = COMMA;
	private static String QUOTE = "\"";
	
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

	public enum REPORT_ACTION_TYPE { ACTION_TYPE, API_ERROR, CONCEPT_CHANGE_MADE, INFO,
									 RELATIONSHIP_ADDED, RELATIONSHIP_REMOVED, DESCRIPTION_CHANGE_MADE, 
									 NO_CHANGE, VALIDATION_ERROR};

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
			for (int lineNum = 0; lineNum < lines.size(); lineNum++) {
				if (lineNum == 0) {
					continue; //skip header row
				}
				//File format Concept Type, SCTID, FSN with string fields quoted.  Strip quotes also.
				String[] lineItems = lines.get(lineNum).replace("\"", "").split(CSV_FIELD_DELIMITER);
				Concept c = graph.getConcept(lineItems[1]);
				c.setConceptType(lineItems[0]);
				allConcepts.add(c);
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
	
	abstract int doFix(Batch batch, Concept concept) throws TermServerFixException;

	private void batchProcess(List<Batch> batches) throws TermServerFixException {
		int failureCount = 0;
		for (Batch batch : batches) {
			try {
				String branchPath;
				String taskKey;
				//Create a task for this batch of concepts
				if (!dryRun) {
					debug ("Creating task on project: " + project);
					taskKey = scaClient.createTask(project, batch.getDescription(), batch.getSummaryHTML());
					branchPath = tsClient.createBranch("MAIN/" + project, taskKey);
				} else {
					taskKey = project + "-" + getNextDryRunNum();
					branchPath = "MAIN/" + project + "/" + taskKey;
				}
				
				debug ( (dryRun?"Dry Run " : "Created") + "task: " + branchPath);
				batch.setTaskKey(taskKey);
				batch.setBranchPath(branchPath);
				
				//Process each concept
				for (Concept concept : batch.getConcepts()) {
					try {
						int changesMade = doFix(batch, concept);
						if (changesMade == 0) {
							report(batch, concept, REPORT_ACTION_TYPE.NO_CHANGE, "");
						}
					} catch (TermServerFixException e) {
						report(batch, concept, REPORT_ACTION_TYPE.API_ERROR, getMessage(e));
						if (++failureCount > maxFailures) {
							throw new TermServerFixException ("Failure count exceeded " + maxFailures);
						}
					}
				}
				
				//Prefill the Edit Panel
				try {
					scaClient.setUIState(project, taskKey, batch.toQuotedList());
				} catch (Exception e) {
					String msg = "Failed to preload edit-panel ui state: " + e.getMessage();
					warn (msg);
					report(batch, null, REPORT_ACTION_TYPE.NO_CHANGE, msg);
				}
				
				//Reassign the task to the intended author
			} catch (Exception e) {
				throw new TermServerFixException("Failed to process batch " + batch.getDescription(), e);
			}
		}
		
	}

	protected int ensureDefinitionStatus(Batch b, Concept c, DEFINITION_STATUS targetDefStat) {
		int changesMade = 0;
		if (!c.getDefinitionStatus().equals(targetDefStat.toString())) {
			report (b, c, REPORT_ACTION_TYPE.CONCEPT_CHANGE_MADE, "Definition status changed to " + targetDefStat);
			c.setDefinitionStatus(targetDefStat.toString());
			changesMade++;
		}
		return changesMade;
	}
	
	protected void report(Batch batch, Concept concept, REPORT_ACTION_TYPE actionType, String actionDetail) {
		String sctid = "";
		String fsn = "";
		if (concept != null) {
			sctid = concept.getConceptId();
			fsn = concept.getFsn();
		}
		String line = batch.toString() + COMMA + sctid + COMMA + fsn + COMMA + concept.getConceptType() + COMMA + actionType + COMMA + QUOTE + actionDetail + QUOTE;
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
			println("Usage: java <FixClass> [-b <batchSize>] [-c <authenticatedCookie>] [-d <Y/N>] [-p <projectName>] <batch file Location>");
			println(" d - dry run");
			System.exit(-1);
		}
		boolean isBatchSize = false;
		boolean isProjectName = false;
		boolean isCookie = false;
		boolean isDryRun = false;
	
		for (String thisArg : args) {
			if (thisArg.equals("-b")) {
				isBatchSize = true;
			} else if (thisArg.equals("-p")) {
				isProjectName = true;
			} else if (thisArg.equals("-c")) {
				isCookie = true;
			} else if (thisArg.equals("-d")) {
				isDryRun = true;
			} else if (isBatchSize) {
				batchSize = Integer.parseInt(thisArg);
				isBatchSize = false;
			} else if (isProjectName) {
				project = thisArg;
				isProjectName = false;
			} else if (isDryRun) {
				dryRun = thisArg.toUpperCase().equals("Y");
				isDryRun = false;
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
		init();
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = "batch_fix_" + df.format(new Date()) + ".csv";
		reportFile = new File(reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("TASK, SCTID, FSN, CONCEPT_TYPE," +REPORT_ACTION_TYPE.ACTION_TYPE + ",ACTION_DETAIL");
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
	
	protected int ensureAcceptableParent(Batch batch, Concept c, Concept acceptableParent) {
		List<Relationship> statedParents = c.getRelationships(CHARACTERISTIC_TYPE.STATED_RELATIONSHIP, IS_A, ACTIVE_STATE.ACTIVE);
		boolean hasAcceptableParent = false;
		int changesMade = 0;
		for (Relationship thisParent : statedParents) {
			if (!thisParent.getTarget().equals(acceptableParent)) {
				report(batch, c, REPORT_ACTION_TYPE.RELATIONSHIP_REMOVED, "Inactivated unwanted parent: " + thisParent.getTarget());
				thisParent.setActive(false);
				changesMade++;
			} else {
				hasAcceptableParent = true;
			}
		}
		
		if (!hasAcceptableParent) {
			c.addRelationship(IS_A, acceptableParent);
			changesMade++;
			report(batch, c, REPORT_ACTION_TYPE.RELATIONSHIP_ADDED, "Added required parent: " + acceptableParent);
		}
		return changesMade;
	}
}
