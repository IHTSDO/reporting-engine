package org.ihtsdo.termserver.scripting.fixes;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads in a file containing a list of concept SCTIDs and processes them in batches
 * in tasks created on the specified project
 */
public class BatchFix extends TermServerFix {
	
	private int batchSize = 5;
	File batchFixFile;
	String projectName;
	TermServerFix fixProcessor;
	
	public static void main(String[] args) throws TermServerFixException {
		BatchFix batchFix = new BatchFix();
		batchFix.init(args);
		batchFix.processFile();
	}

	private void processFile() throws TermServerFixException {
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
				}
				if (lineNumber%batchSize == 0) {
					batchProcess(thisBatch, taskDescription + lineNumber, taskSummary);
					thisBatch.clear();
					taskDescription = fileShortName + " " + (lineNumber + 1) + " - ";
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
			String taskPath = scaClient.createTask(project, description, summary);
			//Process each line
			
			//Prefill the Edit Panel
			
			//Run the classifier and report the results
			
			//Reassign the task to the intended author
		} catch (Exception e) {
			throw new TermServerFixException("Failed to process batch " + description, e);
		}
		
	}


	@Override
	public void doFix(String conceptId, String branchPath)
			throws TermServerFixException {
		throw new TermServerFixException("Unexpected Call");
		
	}
	
	private void init (String[] args) throws TermServerFixException {
		if (args.length < 2) {
			print("Usage: java BatchFix [-b batchSize] [-p Project Name] [-f Fix Name] [batch file Location]");
			print("Current Fixes: Drug"); 
			System.exit(-1);
		}
		boolean isBatchSize = false;
		boolean isProjectName = false;
		boolean isFixName = false;
	
		for (String thisArg : args) {
			if (thisArg.equals("-b")) {
				isBatchSize = true;
			} else if (thisArg.equals("-p")) {
				isProjectName = true;
			} else if (thisArg.equals("-f")) {
				isFixName = true;
			}else if (isBatchSize) {
				batchSize = Integer.parseInt(thisArg);
				isBatchSize = false;
			} else if (isProjectName) {
				projectName = thisArg;
				isProjectName = false;
			}else if (isFixName) {
				if (thisArg.toLowerCase().equals("drug")) {
					fixProcessor = new DrugProductFix();
				} else {
					throw new TermServerFixException("Unknown fix: " + thisArg);
				}
				isFixName = false;
			} else {
				File possibleFile = new File(thisArg);
				if (possibleFile.exists() && !possibleFile.isDirectory() && possibleFile.canRead()) {
					batchFixFile = possibleFile;
				}
			}
		}
	}
}
