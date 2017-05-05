package org.ihtsdo.termserver.scripting.fixes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONArray;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;

/**
 * Reads in a file containing a list of concept SCTIDs and processes them in batches
 * in tasks created on the specified project
 */
public abstract class BatchFix extends TermServerScript implements RF2Constants {
	
	protected int taskSize = 6;
	protected int wiggleRoom = 2;
	protected String targetAuthor;
	String[] emailDetails;
	protected boolean selfDetermining = false; //Set to true if the batch fix calculates its own data to process
	protected boolean populateEditPanel = true;

	protected BatchFix (BatchFix clone) {
		if (clone != null) {
			this.inputFile = clone.inputFile;
			this.reportFile = clone.reportFile;
			this.project = clone.project;
			this.tsClient = clone.tsClient;
			this.scaClient = clone.scaClient;
		}
	}

	protected List<Concept> processFile() throws TermServerScriptException {
		List<Concept> allConcepts = super.processFile();
		Batch batch = formIntoBatch(inputFile.getName(), allConcepts, projectPath);
		batchProcess(batch);
		if (emailDetails != null) {
			String msg = "Batch Scripting has completed successfully." + getSummaryText();
			sendEmail(msg, reportFile);
		}
		println ("Processing complete.  See results: " + reportFile.getAbsolutePath());
		return allConcepts;
	}
	
	abstract protected Batch formIntoBatch (String fileName, List<Concept> allConcepts, String branchPath) throws TermServerScriptException;
	
	abstract protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure;

	protected void batchProcess(Batch batch) throws TermServerScriptException {
		int failureCount = 0;
		int tasksCreated = 0;
		int tasksSkipped = 0;
		boolean isFirst = true;
			for (Task task : batch.getTasks()) {
				try {
					String branchPath="";
					String taskKey="";
					//If we don't have any concepts in this task eg this is 100% ME file, then skip
					if (task.size() == 0) {
						println ("Skipping Task " + task.getDescription() + " - no concepts to process");
						continue;
					} else if (selfDetermining && restartPosition > 1 && (tasksSkipped + 1) < restartPosition) {
						//For self determining projects we'll restart based on a task count, rather than the line number in the input file
						tasksSkipped++;
						println ("Skipping Task " + task.getDescription() + " - restarting from task " + restartPosition);
						continue;
					} else if (task.size() > (taskSize + wiggleRoom)) {
						warn (task + " contains " + task.size() + " concepts");
					}
					
					//Create a task for this batch of concepts
					if (!dryRun) {
						if (!isFirst) {
							debug ("Letting TS catch up - " + taskThrottle + "s nap.");
							Thread.sleep(taskThrottle * 1000);
						} else {
							isFirst = false;
						}
						
						boolean taskCreated = false;
						int taskCreationAttempts = 0; 
						while (!taskCreated) {
							try{
								debug ("Creating jira task on project: " + project);
								taskKey = scaClient.createTask(project, task.getDescription(), task.getSummaryHTML());
								debug ("Creating task branch in terminology server: " + taskKey);
								branchPath = tsClient.createBranch(tsRoot + project, taskKey);
								taskCreated = true;
							} catch (Exception e) {
								taskCreationAttempts++;
								scaClient.deleteTask(project, taskKey, true);  //Don't worry if deletion fails
								if (taskCreationAttempts >= 3) {
									throw new TermServerScriptException("Maxed out failure attempts", e);
								}
								warn ("Branch creation failed (" + e.getMessage() + "), retrying...");
							}
						}
					} else {
						taskKey = project + "-" + getNextDryRunNum();
						branchPath = tsRoot + project + "/" + taskKey;
					}
					tasksCreated++;
					String xOfY =  (tasksCreated+tasksSkipped) + " of " + batch.getTasks().size();
					println ( (dryRun?"Dry Run " : "Created ") + "task (" + xOfY + "): " + branchPath);
					task.setTaskKey(taskKey);
					task.setBranchPath(branchPath);
					incrementSummaryInformation("Tasks created",1);
					int conceptInTask = 0;
					//Process each concept
					for (Concept concept : task.getConcepts()) {
						try {
							conceptInTask++;
							if (!dryRun && task.getConcepts().indexOf(concept) != 0) {
								Thread.sleep(conceptThrottle * 1000);
							}
							String info = " Task (" + xOfY + ") Concept (" + conceptInTask + " of " + task.getConcepts().size() + ")";
							int changesMade = doFix(task, concept, info);
							if (changesMade == 0) {
								report(task, concept, Severity.NONE, ReportActionType.NO_CHANGE, "");
							}
						} catch (ValidationFailure f) {
							report(task, concept, f.getSeverity(),f.getReportActionType(), f.getMessage());
						} catch (TermServerScriptException e) {
							report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, getMessage(e));
							if (++failureCount > maxFailures) {
								throw new TermServerScriptException ("Failure count exceeded " + maxFailures);
							}
						}
					}
					
					if (!dryRun) {
						//Prefill the Edit Panel
						try {
							if (populateEditPanel) {
								scaClient.setEditPanelUIState(project, taskKey, task.toQuotedList());
							}
							scaClient.setSavedListUIState(project, taskKey, convertToSavedListJson(task));
						} catch (Exception e) {
							String msg = "Failed to preload edit-panel ui state: " + e.getMessage();
							warn (msg);
							report(task, null, Severity.LOW, ReportActionType.API_ERROR, msg);
						}
						
						//Reassign the task to the intended author.  Set at task or processing level
						String taskAuthor = task.getAssignedAuthor();
						if (taskAuthor == null) {
							taskAuthor = targetAuthor;
						}
						if (taskAuthor != null && !taskAuthor.isEmpty()) {
							debug("Assigning " + taskKey + " to " + taskAuthor);
							scaClient.updateTask(project, taskKey, null, null, taskAuthor);
						}
						
						String taskReviewer = task.getReviewer();
						if (taskReviewer != null && !taskReviewer.isEmpty()) {
							debug("Assigning " + taskKey + " to reviewer " + taskReviewer);
							scaClient.putTaskIntoReview(project, taskKey, taskReviewer);
						}
					}
				} catch (Exception e) {
					throw new TermServerScriptException("Failed to process batch " + task.getDescription() + " on task " + task.getTaskKey(), e);
				}
				
				if (processingLimit > NOT_SET && tasksCreated >= processingLimit) {
					println ("Processing limit of " + processingLimit + " tasks reached.  Stopping");
					break;
				}
			}
	}

	protected int ensureDefinitionStatus(Task t, Concept c, DefinitionStatus targetDefStat) {
		int changesMade = 0;
		if (!c.getDefinitionStatus().equals(targetDefStat)) {
			report (t, c, Severity.MEDIUM, ReportActionType.CONCEPT_CHANGE_MADE, "Definition status changed to " + targetDefStat);
			c.setDefinitionStatus(targetDefStat);
			changesMade++;
		}
		return changesMade;
	}
	
	protected void report(Task task, Concept concept, Severity severity, ReportActionType actionType, String actionDetail) {
		String sctid = "";
		String fsn = "";
		String type = "";
		if (concept != null) {
			sctid = concept.getConceptId();
			fsn = concept.getFsn();
			if (concept.getConceptType() != null) {
				type = concept.getConceptType().toString();
			}
			
			if (severity.equals(Severity.CRITICAL)) {
				String key = CRITICAL_ISSUE + " encountered for " + sctid + " |" + fsn + "|" ;
				addSummaryInformation(key, actionDetail);
				println ( key + " : " + actionDetail);
			}
		}
		String batchKey = (task == null? "" :  task.getTaskKey());
		String batchDesc = (task == null? "" :  task.getDescription());
		String line = batchKey + COMMA + batchDesc + COMMA + sctid + COMMA_QUOTE + fsn + QUOTE_COMMA + type + COMMA + severity + COMMA + actionType + COMMA_QUOTE + actionDetail + QUOTE;
		writeToFile (line);
	}

	protected void init (String[] args) throws TermServerScriptException, IOException {
		if (args.length < 3) {
			println("Usage: java <FixClass> [-a author] [-n <taskSize>] [-r <restart position>] [-l <limit> ] [-t taskCreationDelay] -c <authenticatedCookie> [-d <Y/N>] [-p <projectName>] -f <batch file Location>");
			println(" d - dry run");
			System.exit(-1);
		}
		boolean isTaskSize = false;
		boolean isProjectName = false;
		boolean isAuthor = false;
		boolean isLimit = false;
	
		for (String thisArg : args) {
			if (thisArg.equals("-a")) {
				isAuthor = true;
			} else if (thisArg.equals("-n")) {
				isTaskSize = true;
			}else if (thisArg.equals("-l")) {
				isLimit = true;
			} else if (isAuthor) {
				targetAuthor = thisArg.toLowerCase();
				isAuthor = false;
			} else if (isTaskSize) {
				taskSize = Integer.parseInt(thisArg);
				isTaskSize = false;
			} else if (isLimit) {
				processingLimit = Integer.parseInt(thisArg);
				println ("Limiting number of tasks being created to " + processingLimit);
				isLimit = false;
			}else if (isProjectName) {
				project = thisArg;
				isProjectName = false;
			} 
		}
		
		super.init(args);
		
		if (!selfDetermining && inputFile == null) {
			throw new TermServerScriptException("No valid batch import file detected in command line arguments");
		}
		
		if (targetAuthor == null) {
			throw new TermServerScriptException("No target author detected in command line arguments");
		}
		
		if (!selfDetermining) {
			println("Reading file from line " + restartPosition + " - " + inputFile.getName());
		}
		
		print ("Number of concepts per task [" + taskSize + "]: ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			taskSize = Integer.parseInt(response);
		}
		println ("\nBatching " + taskSize + " concepts per task");
		initialiseReportFile("TASK_KEY, TASK_DESC, SCTID, FSN, CONCEPT_TYPE,SEVERITY,ACTION_TYPE,ACTION_DETAIL");
	}
	
	protected int ensureAcceptableParent(Task task, Concept c, Concept acceptableParent) {
		List<Relationship> statedParents = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);
		boolean hasAcceptableParent = false;
		int changesMade = 0;
		for (Relationship thisParent : statedParents) {
			if (!thisParent.getTarget().equals(acceptableParent)) {
				report(task, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_REMOVED, "Inactivated unwanted parent: " + thisParent.getTarget());
				thisParent.setActive(false);
				changesMade++;
			} else {
				hasAcceptableParent = true;
			}
		}
		
		if (!hasAcceptableParent) {
			c.addRelationship(IS_A, acceptableParent);
			changesMade++;
			report(task, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_ADDED, "Added required parent: " + acceptableParent);
		}
		return changesMade;
	}

	
	/**
	 Validate that that any attribute with that attribute type is a descendent of the target Value
	 * @param cardinality 
	 * @throws TermServerScriptException 
	 */
	protected int validateAttributeValues(Task task, Concept concept,
			Concept attributeType, Concept descendentsOfValue, Cardinality cardinality) throws TermServerScriptException {
		
		List<Relationship> attributes = concept.getRelationships(CharacteristicType.ALL, attributeType, ActiveState.ACTIVE);
		Set<Concept> descendents = ClosureCache.getClosureCache().getClosure(descendentsOfValue);
		for (Relationship thisAttribute : attributes) {
			Concept value = thisAttribute.getTarget();
			if (!descendents.contains(value)) {
				Severity severity = thisAttribute.isActive()?Severity.CRITICAL:Severity.LOW;
				String activeStr = thisAttribute.isActive()?"":"inactive ";
				String relType = thisAttribute.getCharacteristicType().equals(CharacteristicType.STATED_RELATIONSHIP)?"stated ":"inferred ";
				String msg = "Attribute has " + activeStr + relType + "target which is not a descendent of: " + descendentsOfValue;
				report (task, concept, severity, ReportActionType.VALIDATION_ERROR, msg);
			}
		}
		
		int changesMade = 0;
		
		//Now check cardinality on active stated relationships
		attributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
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
			report (task, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, msg);
		}
		return changesMade;
	}
	

	private int transferInferredRelationshipsToStated(Task task,
			Concept concept, Concept attributeType, Cardinality cardinality) {
		List<Relationship> replacements = concept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
		int changesMade = 0;
		if (replacements.size() == 0) {
			String msg = "Unable to find any inferred " + attributeType + " relationships to state.";
			report(task, concept, Severity.HIGH, ReportActionType.INFO, msg);
		} else if (cardinality.equals(Cardinality.EXACTLY_ONE) && replacements.size() > 1) {
			String msg = "Found " + replacements.size() + " " + attributeType + " relationships to state but wanted only one!";
			report(task, concept, Severity.HIGH, ReportActionType.INFO, msg);
		} else {
			//Clone the inferred relationships, make them stated and add to concept
			for (Relationship replacement : replacements) {
				Relationship statedClone = replacement.clone();
				statedClone.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
				concept.addRelationship(statedClone);
				String msg = "Restated inferred relationship: " + replacement;
				report(task, concept, Severity.MEDIUM, ReportActionType.RELATIONSHIP_ADDED, msg);
				changesMade++;
			}
		}
		return changesMade;
	}

	protected void validatePrefInFSN(Task task, Concept concept) throws TermServerScriptException {
		//Check that the FSN with the semantic tags stripped off is
		//equal to the preferred terms
		List<Description> preferredTerms = concept.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
		String trimmedFSN = SnomedUtils.deconstructFSN(concept.getFsn())[0];
		//Special handling for acetaminophen
		if (trimmedFSN.toLowerCase().contains(ACETAMINOPHEN) || trimmedFSN.toLowerCase().contains(PARACETAMOL)) {
			println ("Doing ACETAMINOPHEN processing for " + concept);
		} else {
			for (Description pref : preferredTerms) {
				if (!pref.getTerm().equals(trimmedFSN)) {
					String msg = concept + " has preferred term that does not match FSN: " + pref.getTerm();
					report (task, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, msg);
				}
			}
		}
	}
	
	protected void sendEmail(String content, File resultsFile)  {
		Properties props = new Properties();
		props.put("mail.transport.protocol", emailDetails[0]);
		props.put("mail.smtp.host", emailDetails[1]); // smtp.gmail.com?
		props.put("mail.smtp.port", emailDetails[2]);
		props.put("mail.smtp.auth", "true");
		Authenticator authenticator = new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
			return new PasswordAuthentication(emailDetails[3], emailDetails[4]);
			}
		};
		Session session = Session.getDefaultInstance(props, authenticator);
		try {
			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress("techsupport@ihtsdo.org", "IHTSDO Email"));
			msg.addRecipient(Message.RecipientType.TO, new InternetAddress("user@example.com"));
			msg.setSubject("Terminology Server Batch script complete.");
			
			Multipart mp = new MimeMultipart();
			MimeBodyPart htmlPart = new MimeBodyPart();
			htmlPart.setContent(content, "text/html");
			mp.addBodyPart(htmlPart);

			MimeBodyPart attachment = new MimeBodyPart();
			InputStream attachmentDataStream = new FileInputStream(resultsFile);
			attachment.setFileName(resultsFile.getName());
			attachment.setContent(attachmentDataStream, "application/csv");
			mp.addBodyPart(attachment);
			Transport.send(msg);
		} catch (MessagingException | FileNotFoundException | UnsupportedEncodingException e) {
			println ("Failed to send email " + e.getMessage());
		}
	}

	private JSONObject convertToSavedListJson(Task task) throws JSONException {
		JSONObject savedList = new JSONObject();
		JSONArray items = new JSONArray();
		savedList.put("items", items);
		for (Concept thisConcept : task.getConcepts()) {
			items.put(convertToSavedListJson(thisConcept));
		}
		return savedList;
	}

	private JSONObject convertToSavedListJson(Concept concept) throws JSONException {
		JSONObject jsonObj = new JSONObject();
		jsonObj.put("term", concept.getPreferredSynonym());
		jsonObj.put("active", concept.isActive()?"true":"false");
		JSONObject conceptObj = new JSONObject();
		jsonObj.put("concept", conceptObj);
		conceptObj.put("active", concept.isActive()?"true":"false");
		conceptObj.put("conceptId", concept.getConceptId());
		conceptObj.put("fsn", concept.getFsn());
		conceptObj.put("moduleId", concept.getModuleId());
		conceptObj.put("defintionStatus", concept.getDefinitionStatus());
		return jsonObj;
	}
}
