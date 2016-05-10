package org.ihtsdo.termserver.scripting.fixes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Reads in a file containing a list of concept SCTIDs and processes them in batches
 * in tasks created on the specified project
 */
public abstract class BatchFix extends TermServerFix {
	
	private int batchSize = 5;
	protected boolean dryRun = true;
	File batchFixFile;
	File reportFile;
	private static String COMMENT_CHAR = "--";
	private static String COMMA = ",";
	private static String QUOTE = "\"";
	
	public enum REPORT_ACTION_TYPE { ACTION_TYPE, API_ERROR, CONCEPT_CHANGE_MADE, INFO,
									 RELATIONSHIP_CHANGE_MADE, DESCRIPTION_CHANGE_MADE};

	protected void processFile() throws TermServerFixException {
		try (BufferedReader br = new BufferedReader(new FileReader(batchFixFile))) {
			String fileShortName = batchFixFile.getName();
			String taskDescription = fileShortName + " 1 - ";
			String taskSummary = "";
			String line;
			int lineNumber = 0;
			List<String> thisBatch = new ArrayList<String>();
			while ((line = br.readLine()) != null) {
				//Skip header line
				if (lineNumber > 0) {
					thisBatch.add(line);
					if (lineNumber%batchSize == 0) {
						batchProcess(thisBatch, taskDescription + lineNumber, taskSummary);
						thisBatch.clear();
						taskDescription = fileShortName + " " + (lineNumber + 1) + " - ";
					}
				}
				lineNumber++;
			}
			//Any left overs?
			if (thisBatch.size() >0) {
				batchProcess(thisBatch, taskDescription + lineNumber, taskSummary);
			}
		} catch (FileNotFoundException e) {
			throw new TermServerFixException("Unable to open batch file " + batchFixFile.getAbsolutePath(), e);
		} catch (IOException e) {
			throw new TermServerFixException("Error while reading batch file " + batchFixFile.getAbsolutePath(), e);
		}
	}

	private void batchProcess(List<String> thisBatch, String description, String summary) throws TermServerFixException {
		try {
			//Create a task for this 
			String taskKey = scaClient.createTask(project, description, summary);
			String taskPath = tsClient.createBranch("MAIN/" + project, taskKey);
			debug ("Created task: " + taskPath);
			
			//Process each line
			for (String thisLine : thisBatch) {
				//We might have a comment on the line to strip off 
				String thisConceptId = thisLine.split(COMMENT_CHAR)[0].trim();
				doFix(thisConceptId, taskPath);
			}
			
			//Prefill the Edit Panel
			
			//Run the classifier and report the results
			
			//Reassign the task to the intended author
		} catch (Exception e) {
			throw new TermServerFixException("Failed to process batch " + description, e);
		}
		
	}
	
	
	protected void report(String conceptId, REPORT_ACTION_TYPE actionType, String actionDetail) {
		try(FileWriter fw = new FileWriter(reportFile, true);
				BufferedWriter bw = new BufferedWriter(fw);
				PrintWriter out = new PrintWriter(bw))
			{
				String line = conceptId + COMMA + actionType + COMMA + QUOTE + actionDetail + QUOTE;
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
	}
}
