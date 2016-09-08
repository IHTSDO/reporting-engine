package org.ihtsdo.termserver.scripting.fixes;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExportType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExtractType;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipSerializer;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONArray;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;
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
	
	protected int taskSize = 6;
	protected int wiggleRoom = 2;
	File batchFixFile;
	protected String targetAuthor;
	String[] emailDetails;
	//String DELIMETER = TSV_FIELD_DELIMITER;
	String DELIMITER = CSV_FIELD_DELIMITER;
	protected String tsRoot = "MAIN/2016-01-31/SNOMEDCT-DK/";
	
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
	
	abstract Concept loadLine(String[] lineItems) throws TermServerFixException;

	protected void processFile() throws TermServerFixException {
		try {
			List<String> lines = Files.readLines(batchFixFile, Charsets.UTF_8);
			lines = SnomedUtils.removeBlankLines(lines);
			List<Concept> allConcepts = new ArrayList<Concept>();
			
			//Are we restarting the file from some line number
			int startPos = (restartPosition == NOT_SET)?0:restartPosition - 1;
			for (int lineNum = startPos; lineNum < lines.size(); lineNum++) {
				if (lineNum == 0) {
					//continue; //skip header row  //Current file format has no header
				}
				
				//File format Concept Type, SCTID, FSN with string fields quoted.  Strip quotes also.
				String[] lineItems = lines.get(lineNum).replace("\"", "").split(DELIMITER);
				if (lineItems.length > 1) {
					Concept c = loadLine(lineItems);
					allConcepts.add(c);
				} else {
					debug ("Skipping blank line " + lineNum);
				}
			}
			String projectPath = tsRoot + project;
			addSummaryInformation(CONCEPTS_IN_FILE, allConcepts);
			Batch batch = formIntoBatch(batchFixFile.getName(), allConcepts, projectPath);
			batchProcess(batch);
			if (emailDetails != null) {
				String msg = "Batch Scripting has completed successfully." + getSummaryText();
				sendEmail(msg, reportFile);
			}
		} catch (FileNotFoundException e) {
			throw new TermServerFixException("Unable to open batch file " + batchFixFile.getAbsolutePath(), e);
		} catch (IOException e) {
			throw new TermServerFixException("Error while reading batch file " + batchFixFile.getAbsolutePath(), e);
		}
		println ("Processing complete.  See results: " + reportFile.getAbsolutePath());
	}
	
	abstract Batch formIntoBatch (String fileName, List<Concept> allConcepts, String branchPath) throws TermServerFixException;
	
	abstract int doFix(Task task, Concept concept) throws TermServerFixException;

	private void batchProcess(Batch batch) throws TermServerFixException {
		int failureCount = 0;
		int tasksCreated = 0;
		boolean isFirst = true;
			for (Task task : batch.getTasks()) {
				try {
					String branchPath="";
					String taskKey="";
					//If we don't have any concepts in this task eg this is 100% ME file, then skip
					if (task.size() == 0) {
						println ("Skipping Task " + task.getDescription() + " - no concepts to process");
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
									throw new TermServerFixException("Maxed out failure attempts", e);
								}
								warn ("Branch creation failed (" + e.getMessage() + "), retrying...");
							}
						}
					} else {
						taskKey = project + "-" + getNextDryRunNum();
						branchPath = tsRoot + project + "/" + taskKey;
					}
					tasksCreated++;
					String xOfY =  tasksCreated + " of " + batch.getTasks().size();
					println ( (dryRun?"Dry Run " : "Created ") + "task (" + xOfY + "): " + branchPath);
					task.setTaskKey(taskKey);
					task.setBranchPath(branchPath);
					incrementSummaryInformation("Tasks created",1);
					
					//Process each concept
					for (Concept concept : task.getConcepts()) {
						try {
							if (!dryRun && task.getConcepts().indexOf(concept) != 0) {
								Thread.sleep(conceptThrottle * 1000);
							}
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
							scaClient.setEditPanelUIState(project, taskKey, task.toQuotedList());
							scaClient.setSavedListUIState(project, taskKey, convertToSavedListJson(task));
						} catch (Exception e) {
							String msg = "Failed to preload edit-panel ui state: " + e.getMessage();
							warn (msg);
							report(task, null, SEVERITY.LOW, REPORT_ACTION_TYPE.API_ERROR, msg);
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
					throw new TermServerFixException("Failed to process batch " + task.getDescription() + " on task " + task.getTaskKey(), e);
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
		String type = "";
		if (concept != null) {
			sctid = concept.getConceptId();
			fsn = concept.getFsn();
			if (concept.getConceptType() != null) {
				type = concept.getConceptType().toString();
			}
			
			if (severity.equals(SEVERITY.CRITICAL)) {
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

	protected void init (String[] args) throws TermServerFixException, IOException {
		if (args.length < 3) {
			println("Usage: java <FixClass> [-a author] [-n <taskSize>] [-r <restart lineNum>] [-t taskCreationDelay] [-c <authenticatedCookie>] [-d <Y/N>] [-p <projectName>] <batch file Location>");
			println(" d - dry run");
			System.exit(-1);
		}
		boolean isTaskSize = false;
		boolean isProjectName = false;
		boolean isAuthor = false;
		boolean isMailRecipient = false;
	
		for (String thisArg : args) {
			if (thisArg.equals("-a")) {
				isAuthor = true;
			} else if (thisArg.equals("-b")) {
				isTaskSize = true;
			} else if (isAuthor) {
				targetAuthor = thisArg.toLowerCase();
				isAuthor = false;
			} else if (isTaskSize) {
				taskSize = Integer.parseInt(thisArg);
				isTaskSize = false;
			} else if (isProjectName) {
				project = thisArg;
				isProjectName = false;
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
		
		println("Reading file from line " + restartPosition + " - " + batchFixFile.getName());
		
		super.init(args);
		
		print ("Number of concepts per task [" + taskSize + "]: ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			taskSize = Integer.parseInt(response);
		}
		println ("\nBatching " + taskSize + " concepts per task");
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = "results_" + SnomedUtils.deconstructFilename(batchFixFile)[1] + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
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
	 * Any description that has been updated (effectiveTime == null) and is now inactive
	 * should have its inactivation reason set to RETIRED aka "Reason not stated"
	 * @param loadedConcept
	 * @throws  
	 */
	//The browser endpoint can now handle inactivation reasons, so set when description is inactivated in concept.
/*	protected void updateDescriptionInactivationReason(Task t, Concept loadedConcept) {
		for (Description d : loadedConcept.getDescriptions()) {
			if (d.getEffectiveTime() == null && d.isActive() == false) {
				try {
					String descriptionSerialised = gson.toJson(d);
					JSONObject jsonObjDesc = new JSONObject(descriptionSerialised);
					//The following fields can't be updated in a description and so are not represented
					jsonObjDesc.remove("descriptionId");
					jsonObjDesc.remove("conceptId");
					jsonObjDesc.remove("type");
					jsonObjDesc.remove("lang");
					jsonObjDesc.remove("term");
					jsonObjDesc.put("inactivationIndicator", InactivationIndicator.RETIRED.toString());
					jsonObjDesc.put("commitComment", "Batch Script Update");
					//Description endpoint uses acceptability rather than acceptabilityMap
					if (jsonObjDesc.optJSONObject("acceptabilityMap") != null) {
						jsonObjDesc.remove("acceptabilityMap");
					}
					jsonObjDesc.put("acceptability", JSONObject.NULL);
					tsClient.updateDescription(d.getDescriptionId(), jsonObjDesc, t.getBranchPath());
				} catch (SnowOwlClientException | JSONException e) {
					String errStr = "Failed to set inactivation reason on description '" + d.getTerm() + "' : " + e.getMessage();
					report(t, loadedConcept, SEVERITY.CRITICAL, REPORT_ACTION_TYPE.API_ERROR, errStr);
				}
			}
		}
	}*/
	
	protected void loadProjectSnapshot() throws SnowOwlClientException, TermServerFixException {
		File snapShotArchive = new File (project + "_" + env + ".zip");
		//Do we already have a copy of the project locally?  If not, recover it.
		if (!snapShotArchive.exists()) {
			println ("Recovering current state of " + project + " from TS (" + env + ")");
			tsClient.export(tsRoot + project, null, ExportType.MIXED, ExtractType.SNAPSHOT, snapShotArchive);
		}
		GraphLoader gl = GraphLoader.getGraphLoader();
		println ("Loading archive contents into memory...");
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(snapShotArchive));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path p = Paths.get(ze.getName());
						String fileName = p.getFileName().toString();
						if (fileName.contains("sct2_Relationship_Snapshot")) {
							println("Loading Relationship File.");
							gl.loadRelationshipFile(CHARACTERISTIC_TYPE.INFERRED_RELATIONSHIP, zis);
						} else if (fileName.contains("sct2_Description_Snapshot")) {
							println("Loading Description File.");
							gl.loadDescriptionFile(zis);
						}
					}
					ze = zis.getNextEntry();
				}
			} finally {
				try{
					zis.closeEntry();
					zis.close();
				} catch (Exception e){} //Well, we tried.
			}
		} catch (IOException e) {
			throw new TermServerFixException("Failed to extract project state from archive " + snapShotArchive.getName(), e);
		}
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
		if (trimmedFSN.toLowerCase().contains(ACETAMINOPHEN) || trimmedFSN.toLowerCase().contains(PARACETAMOL)) {
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
