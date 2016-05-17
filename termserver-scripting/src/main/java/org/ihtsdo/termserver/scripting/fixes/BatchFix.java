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
	protected boolean dryRun = true;
	File batchFixFile;
	File reportFile;
	private static String COMMENT_CHAR = "--";
	private static String COMMA = ",";
	private static String QUOTE = "\"";
	
	protected  Gson gson;	

	public enum REPORT_ACTION_TYPE { ACTION_TYPE, API_ERROR, CONCEPT_CHANGE_MADE, INFO,
									 RELATIONSHIP_CHANGE_MADE, DESCRIPTION_CHANGE_MADE};

	protected void processFile() throws TermServerFixException {
		try {
			List<String> lines = Files.readLines(batchFixFile, Charsets.UTF_8);
			List<Concept> allConcepts = new ArrayList<Concept>();
			for (int lineNum = 0; lineNum < lines.size(); lineNum++) {
				//We might have a comment on the line to strip off 
				String thisConceptId = lines.get(lineNum).split(COMMENT_CHAR)[0].trim();
				allConcepts.add(new Concept(thisConceptId, lineNum));
			}
			String projectPath = "MAIN/" + project;
			List<Batch> batches = formIntoBatches(batchFixFile.getName(), allConcepts, projectPath);
			//System.exit(0);
			batchProcess(batches);
		} catch (FileNotFoundException e) {
			throw new TermServerFixException("Unable to open batch file " + batchFixFile.getAbsolutePath(), e);
		} catch (IOException e) {
			throw new TermServerFixException("Error while reading batch file " + batchFixFile.getAbsolutePath(), e);
		}
		println ("Processing complete.  See results: " + reportFile.getAbsolutePath());
	}
	
	abstract List<Batch> formIntoBatches (String fileName, List<Concept> allConcepts, String branchPath) throws TermServerFixException;
	
	abstract void doFix(Concept concept, String taskPath);

	private void batchProcess(List<Batch> batches) throws TermServerFixException {
		for (Batch thisBatch : batches) {
			try {
				//Create a task for this batch of concepts
				String taskKey = scaClient.createTask(project, thisBatch.getDescription(), thisBatch.getSummary());
				String taskPath = tsClient.createBranch("MAIN/" + project, taskKey);
				debug ("Created task: " + taskPath);
				
				//Process each concept
				for (Concept concept : thisBatch.getConcepts()) {
					doFix(concept, taskPath);
				}
				
				//Prefill the Edit Panel
				
				//Run the classifier and report the results
				
				//Reassign the task to the intended author
			} catch (Exception e) {
				throw new TermServerFixException("Failed to process batch " + thisBatch.getDescription(), e);
			}
		}
		
	}

	protected void ensureDefinitionStatus(Concept c, DEFINITION_STATUS targetDefStat) {
		if (!c.getDefinitionStatus().equals(targetDefStat.toString())) {
			report (c.getConceptId(), REPORT_ACTION_TYPE.CONCEPT_CHANGE_MADE, "Definition status changed to " + targetDefStat);
			c.setDefinitionStatus(targetDefStat.toString());
		}
	}
	
	protected void report(String conceptId, REPORT_ACTION_TYPE actionType, String actionDetail) {
		try(FileWriter fw = new FileWriter(reportFile, true);
				BufferedWriter bw = new BufferedWriter(fw);
				PrintWriter out = new PrintWriter(bw))
			{
				String line = conceptId + COMMA + actionType + COMMA + QUOTE + actionDetail + QUOTE;
				out.println(line);
			} catch (Exception e) {
				print ("Unable to output " + conceptId + ": " + actionDetail + " due to " + e.getMessage());
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
				dryRun = Boolean.parseBoolean(thisArg);
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
		report ("SCTID",REPORT_ACTION_TYPE.ACTION_TYPE,"ACTION_DETAIL");
		
		GsonBuilder gsonBuilder = new GsonBuilder();
		//gsonBuilder.registerTypeAdapter(Concept.class, new ConceptDeserializer());
		gson = gsonBuilder.create();
	}
	
	Concept loadConcept(Concept concept, String branchPath) {
		try {
			JSONResource response = tsClient.getConcept(concept.getConceptId(), branchPath);
			String json = response.toObject().toString();
			concept = gson.fromJson(json, Concept.class);
			concept.setLoaded(true);
		} catch (SnowOwlClientException | JSONException | IOException e) {
			report(concept.getConceptId(), REPORT_ACTION_TYPE.API_ERROR, "Failed to recover concept from termserver: " + e.getMessage());
		}
		return concept;
	}
	
	protected void ensureAcceptableParent(Concept c, Concept acceptableParent) {
		List<Relationship> statedParents = c.getRelationships(CHARACTERISTIC_TYPE.STATED_RELATIONSHIP, IS_A);
		boolean hasAcceptableParent = false;
		for (Relationship thisParent : statedParents) {
			if (thisParent.isActive() && !thisParent.getTarget().equals(acceptableParent)) {
				report(c.getConceptId(), REPORT_ACTION_TYPE.RELATIONSHIP_CHANGE_MADE, "Inactivated unwanted parent: " + thisParent);
				thisParent.setActive(false);
			} else {
				if (thisParent.getTarget().equals(acceptableParent)) {
					hasAcceptableParent = true;
				}
			}
		}
		
		if (!hasAcceptableParent) {
			c.addRelationship(IS_A, acceptableParent);
			report(c.getConceptId(), REPORT_ACTION_TYPE.RELATIONSHIP_CHANGE_MADE, "Added required parent: " + acceptableParent);
		}
	}
}
