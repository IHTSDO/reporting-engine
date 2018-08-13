package org.ihtsdo.termserver.scripting;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.time.DurationFormatUtils;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.dao.ReportManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.template.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import us.monoid.json.JSONObject;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;

public abstract class TermServerScript implements RF2Constants {
	
	protected static boolean debug = true;
	protected static boolean dryRun = true;
	protected boolean quiet = false; 
	protected static int dryRunCounter = 0;
	protected String env;
	protected String url = environments[0];
	protected boolean useAuthenticatedCookie = true;
	protected boolean stateComponentType = true;
	protected SnowOwlClient tsClient;
	protected AuthoringServicesClient scaClient;
	private ArchiveManager archiveManager; 
	protected String authenticatedCookie;
	protected Resty resty = new Resty();
	protected Project project;
	public static final int maxFailures = 5;
	protected int restartPosition = NOT_SET;
	protected int processingLimit = NOT_SET;
	private Date startTime;
	private Map<String, Object> summaryDetails = new TreeMap<String, Object>();
	private String summaryText = "";
	protected boolean inputFileHasHeaderRow = false;
	protected boolean runStandAlone = true; //Set to true to avoid loading concepts from Termserver.  Should be used with Dry Run only.
	protected File inputFile;
	protected File inputFile2;

	protected GraphLoader gl = GraphLoader.getGraphLoader();
	protected String additionalReportColumns = "ActionDetail";
	protected String secondaryReportColumns = "ActionDetail";
	protected String tertiaryReportColumns = "ActionDetail";
	protected boolean expectNullConcepts = false; //Set to true to avoid warning about rows in input file that result in no concept to modify
	public Scanner STDIN = new Scanner(System.in);
	
	public static String CONCEPTS_IN_FILE = "Concepts in file";
	public static String CONCEPTS_TO_PROCESS = "Concepts to process";
	public static String REPORTED_NOT_PROCESSED = "Reported not processed";
	public static String CRITICAL_ISSUE = "CRITICAL ISSUE";
	public static String inputFileDelimiter = TSV_FIELD_DELIMITER;
	protected String tsRoot = "MAIN/"; //"MAIN/2016-01-31/SNOMEDCT-DK/";
	private ReportManager reportManager;
	

	protected static DescendentsCache descendantsCache = DescendentsCache.getDescendentsCache();
	protected static AncestorsCache ancestorsCache = AncestorsCache.getAncestorsCache();
	
	public static Gson gson;
	static {
		GsonBuilder gsonBuilder = new GsonBuilder();
		//gsonBuilder.registerTypeAdapter(Concept.class, new ConceptDeserializer());
		gsonBuilder.registerTypeAdapter(Relationship.class, new RelationshipSerializer());
		gsonBuilder.setPrettyPrinting();
		gsonBuilder.excludeFieldsWithoutExposeAnnotation();
		gson = gsonBuilder.create();
	}
	
	public enum ReportActionType { API_ERROR, DEBUG_INFO, INFO, UNEXPECTED_CONDITION,
									 CONCEPT_CHANGE_MADE, CONCEPT_ADDED, CONCEPT_INACTIVATED, CONCEPT_DELETED,
									 DESCRIPTION_CHANGE_MADE, DESCRIPTION_ACCEPTABILIY_CHANGED, DESCRIPTION_ADDED, DESCRIPTION_INACTIVATED, DESCRIPTION_DELETED,
									 CASE_SIGNIFICANCE_CHANGE_MADE,
									 RELATIONSHIP_ADDED, RELATIONSHIP_REPLACED, RELATIONSHIP_INACTIVATED, RELATIONSHIP_DELETED, RELATIONSHIP_MODIFIED, 
									 RELATIONSHIP_GROUP_ADDED,
									 NO_CHANGE, VALIDATION_ERROR, VALIDATION_CHECK, SKIPPING,
									 REFSET_MEMBER_REMOVED, UNKNOWN, RELATIONSHIP_REACTIVATED, ASSOCIATION_ADDED};
									 
	public enum Severity { NONE, LOW, MEDIUM, HIGH, CRITICAL }; 

	public String getScriptName() {
		return this.getClass().getSimpleName();
	}
	
	public String getAuthenticatedCookie() {
		return authenticatedCookie;
	}
	
	public static int getNextDryRunNum() {
		return ++dryRunCounter;
	}

	public void setAuthenticatedCookie(String authenticatedCookie) {
		this.authenticatedCookie = authenticatedCookie;
	}
	
	protected static String[] envKeys = new String[] {"local","dev","uat","flat-uat","prod","dev","uat","prod"};

	protected static String[] environments = new String[] {	"http://localhost:8080/",
															"https://dev-authoring.ihtsdotools.org/",
															"https://uat-authoring.ihtsdotools.org/",
															"https://uat-flat-termserver.ihtsdotools.org/",
															"https://prod-authoring.ihtsdotools.org/",
															"https://dev-ms-authoring.ihtsdotools.org/",
															"https://uat-ms-authoring.ihtsdotools.org/",
															"https://prod-ms-authoring.ihtsdotools.org/",
	};
	
	public static void info (String msg) {
		System.out.println (msg);
	}
	
	public static void debug (Object obj) {
		System.out.println (obj==null?"NULL":obj.toString());
	}
	
	public static void warn (Object obj) {
		System.out.println ("*** " + (obj==null?"NULL":obj.toString()));
	}
	
	public static void print (Object msg) {
		System.out.print (msg.toString());
	}
	
	public static String getMessage (Exception e) {
		String msg = e.getMessage();
		Throwable cause = e.getCause();
		if (cause != null) {
			msg += " caused by " + cause.getMessage();
		}
		return msg;
	}
	
	protected void init(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		
		if (args.length < 3) {
			info("Usage: java <TSScriptClass> [-a author] [-n <taskSize>] [-r <restart position>] [-c <authenticatedCookie>] [-d <Y/N>] [-p <projectName>] -f <batch file Location>");
			info(" d - dry run");
			System.exit(-1);
		}
		boolean isProjectName = false;
		boolean isCookie = false;
		boolean isDryRun = false;
		boolean isRestart = false;
		boolean isInputFile = false;
		boolean isInputFile2 = false;
		String projectName = "unknown";
	
		for (String thisArg : args) {
			if (thisArg.equals("-p")) {
				isProjectName = true;
			} else if (thisArg.equals("-c")) {
				isCookie = true;
			} else if (thisArg.equals("-d")) {
				isDryRun = true;
			} else if (thisArg.equals("-f")) {
				isInputFile = true;
			} else if (thisArg.equals("-f2")) {
				isInputFile2 = true;
			} else if (thisArg.equals("-r")) {
				isRestart = true;
			} else if (isProjectName) {
				projectName = thisArg;
				isProjectName = false;
			} else if (isDryRun) {
				dryRun = thisArg.toUpperCase().equals("Y");
				isDryRun = false;
			} else if (isRestart) {
				restartPosition = Integer.parseInt(thisArg);
				isRestart = false;
			} else if (isInputFile) {
				inputFile = new File(thisArg);
				if (!inputFile.canRead()) {
					throw new TermServerScriptException ("Unable to read input file " + thisArg);
				}
				info ("Reading data from " + inputFile.getAbsolutePath());
				isInputFile = false;
			} else if (isInputFile2) {
				inputFile2 = new File(thisArg);
				if (!inputFile2.canRead()) {
					throw new TermServerScriptException ("Unable to read input file 2 " + thisArg);
				}
				isInputFile2 = false;
			}else if (isCookie) {
				authenticatedCookie = thisArg;
				isCookie = false;
			} 
		}
		
		info ("Select an environment ");
		for (int i=0; i < environments.length; i++) {
			info ("  " + i + ": " + environments[i]);
		}
		
		print ("Choice: ");
		String choice = STDIN.nextLine().trim();
		int envChoice = Integer.parseInt(choice);
		url = environments[envChoice];
		env = envKeys[envChoice];
	
		if (authenticatedCookie == null || authenticatedCookie.trim().isEmpty()) {
			print ("Please enter your authenticated cookie for connection to " + url + " : ");
			authenticatedCookie = STDIN.nextLine().trim();
		}
		//TODO Make calls through client objects rather than resty direct and remove this member 
		resty.withHeader("Cookie", authenticatedCookie);  
		scaClient = new AuthoringServicesClient(url, authenticatedCookie);
		initialiseSnowOwlClient();
		
		print ("Specify Project " + (projectName==null?": ":"[" + projectName + "]: "));
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			projectName = response;
		}
		
		if (restartPosition != NOT_SET) {
			print ("Restarting from position [" +restartPosition + "]: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				restartPosition = Integer.parseInt(response);
			}
		}

		if (restartPosition == 0) {
			info ("Restart position given as 0 but line numbering starts from 1.  Starting at line 1.");
			restartPosition = 1;
		}
		
		//Recover the full project path from authoring services, if not already fully specified
		project = new Project();
		if (projectName.startsWith("MAIN")) {
			project.setBranchPath(projectName);
			if (projectName.equals("MAIN")) {
				project.setKey(projectName);
			} else {
				project.setKey(projectName.substring(projectName.lastIndexOf("/")));
			}
		} else {
			if (runStandAlone) {
				info ("Running stand alone. Guessing project path to be MAIN/" + projectName);
				project.setBranchPath("MAIN/" + projectName);
			} else {
				project = scaClient.getProject(projectName);
			}
			project.setKey(projectName);
		}
		info("Full path for projected determined to be: " + project.getBranchPath());
		setArchiveManager(new ArchiveManager(this));
		reportManager = ReportManager.create(env, getReportName());
	}
	
	protected void initialiseSnowOwlClient() {
		if (useAuthenticatedCookie) {
			tsClient = new SnowOwlClient(url + "snowowl/snomed-ct/v2", authenticatedCookie);
		} else {
			tsClient = new SnowOwlClient(url + "snowowl/snomed-ct/v2", "snowowl", "snowowl");
		}
	}
	
	protected void loadProjectSnapshot(boolean fsnOnly) throws SnowOwlClientException, TermServerScriptException, InterruptedException, IOException {
		getArchiveManager().loadProjectSnapshot(fsnOnly);
	}
	
	protected void loadArchive(File archive, boolean fsnOnly, String fileType) throws TermServerScriptException, SnowOwlClientException {
		getArchiveManager().loadArchive(archive, fsnOnly, fileType);
	}
	
	protected Concept loadConcept(String sctid, String branchPath) throws TermServerScriptException {
		if (dryRun) {
			//In a dry run situation, the task branch is not created so use the Project instead
			//But we'll clone it, so the object isn't confused with any local changes
			branchPath = branchPath.substring(0, branchPath.lastIndexOf("/"));
			if (runStandAlone) {
				debug ("Loading: " + gl.getConcept(sctid) + " from local store");
				return gl.getConcept(sctid).cloneWithIds();
			}
		}
		Concept loadedConcept = loadConcept (tsClient, sctid, branchPath);
		return loadedConcept;
	}
	
	protected Concept loadConcept(Concept concept, String branchPath) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept.getConceptId(), branchPath);
		loadedConcept.setConceptType(concept.getConceptType());
		if (!dryRun) {
			//The loaded concept has some idea of the preferred term.  We'll have that now
			concept.setPreferredSynonym(loadedConcept.getPreferredSynonym());
		}
		return loadedConcept;
	}
	
	protected Concept loadConcept(SnowOwlClient client, Concept concept, String branchPath) throws TermServerScriptException {
			return loadConcept(client, concept.getConceptId(), branchPath);
	}
	
	protected Concept loadConcept(SnowOwlClient client, String sctId, String branchPath) throws TermServerScriptException {
		Concept concept =  gl.getConcept(sctId);
		try {
			debug ("Loading: " + concept + " from TS branch " + branchPath);
			JSONResource response = client.getConcept(sctId, branchPath);
			String json = response.toObject().toString();
			Concept loadedConcept = gson.fromJson(json, Concept.class);
			loadedConcept.setLoaded(true);
			return loadedConcept;
		} catch (Exception e) {
			if (e.getMessage().contains("[404] Not Found")) {
				debug ("Unable to find " + concept + " on branch " + branchPath);
				return null;
			}
			throw new TermServerScriptException("Failed to recover " + concept + " from TS due to " + e.getMessage(),e);
		}
	}
	
	protected Concept updateConcept(Task t, Concept c, String info) throws TermServerScriptException {
		try {
			String conceptSerialised = gson.toJson(c);
			debug ((dryRun ?"Dry run updating ":"Updating ") + "state of " + c + info);
			if (!dryRun) {
				JSONResource response = tsClient.updateConcept(new JSONObject(conceptSerialised), t.getBranchPath());
				String json = response.toObject().toString();
				c = gson.fromJson(json, Concept.class);
			}
			return c;
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to update " + c + " in TS due to " + e.getMessage(),e);
		}
	}
	
	protected Concept createConcept(Task t, Concept c, String info) throws TermServerScriptException {
		if (c.getFsn() == null || c.getFsn().isEmpty()) {
			throw new ValidationFailure(c, "Cannot create concept with no FSN");
		}
		try {
			String conceptSerialised = gson.toJson(c);
			debug ((dryRun ?"Dry run creating ":"Creating ") + c + info);
			if (!dryRun) {
				JSONResource response = tsClient.createConcept(new JSONObject(conceptSerialised), t.getBranchPath());
				String json = response.toObject().toString();
				c = gson.fromJson(json, Concept.class);
			} else {
				c = c.clone("NEW_SCTID");
			}
			incrementSummaryInformation("Concepts created");
			return c;
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to create " + c + " in TS due to " + e.getMessage(),e);
		}
	}
	
	/**
	 * Creates a set of concepts based on a structure around the initial concept 
	 * ie parents as high as is required, siblings and children.
	 * @param initialConcept
	 * @return a list of the new concepts created.
	 */
	protected List<Concept> createConceptStructure(Concept initialConcept) {
		List<Concept> created = new ArrayList<>();
		return created;
	}
	
	protected void deleteConcept(Task t, Concept c) throws TermServerScriptException {
		try {
			debug ((dryRun ?"Dry run deleting ":"Deleting ") + c );
			if (!dryRun) {
				tsClient.deleteConcept(c.getConceptId(), t.getBranchPath());
			} 
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to delete " + c + " in TS due to " + e.getMessage(),e);
		}
	}
	
	protected List<Concept> findConcepts(String branch, String ecl) throws TermServerScriptException {
		EclCache cache = EclCache.getCache(branch, tsClient, gson);
		return cache.findConcepts(branch, ecl); 
	}

	protected List<Component> processFile() throws TermServerScriptException {
		return processFile(inputFile);
	}
	
	protected List<Component> processFile(File file) throws TermServerScriptException {
		Set<Component> allComponents= new LinkedHashSet<>();
		debug ("Loading input file " + file.getAbsolutePath());
		try {
			List<String> lines = Files.readLines(file, Charsets.UTF_8);
			lines = SnomedUtils.removeBlankLines(lines);
			
			//Are we restarting the file from some line number
			int startPos = (restartPosition == NOT_SET)?0:restartPosition - 1;
			List<Component> components;
			for (int lineNum = startPos; lineNum < lines.size(); lineNum++) {
				if (lineNum == 0  && inputFileHasHeaderRow) {
					continue; //skip header row  
				}
				String[] lineItems;
				if (inputFileDelimiter == CSV_FIELD_DELIMITER) {
					//File format Concept Type, SCTID, FSN with string fields quoted.  Strip quotes also.
					lineItems = splitCarefully(lines.get(lineNum));
				} else {
					lineItems = lines.get(lineNum).replace("\"", "").split(inputFileDelimiter);
				}
				if (lineItems.length >= 1) {
					try{
						components = loadLine(lineItems);
					} catch (Exception e) {
						throw new TermServerScriptException("Failed to load line " + lineNum,e);
					}
					if (components != null && components.size() > 0) {
						allComponents.addAll(components);
					} else {
						if (!expectNullConcepts) {
							debug ("Skipped line " + lineNum + ": " + lines.get(lineNum) + ", malformed or not required?");
						}
					}
				} else {
					debug ("Skipping blank line " + lineNum);
				}
			}
			addSummaryInformation(CONCEPTS_IN_FILE, allComponents);

		} catch (FileNotFoundException e) {
			throw new TermServerScriptException("Unable to open input file " + file.getAbsolutePath(), e);
		} catch (IOException e) {
			throw new TermServerScriptException("Error while reading input file " + file.getAbsolutePath(), e);
		}
		return new ArrayList<Component>(allComponents);
	}
	
	/*
	 * Splits a line, ensuring that any commas that are within a quoted string are not treated as delimiters
	 * https://stackoverflow.com/questions/1757065/java-splitting-a-comma-separated-string-but-ignoring-commas-in-quotes
	 */
	private String[] splitCarefully(String line) {
		String otherThanQuote = " [^\"] ";
		String quotedString = String.format(" \" %s* \" ", otherThanQuote);
		String regex = String.format("(?x) "+ // enable comments, ignore white spaces
			",                         "+ // match a comma
			"(?=                       "+ // start positive look ahead
			"  (?:                     "+ //   start non-capturing group 1
			"    %s*                   "+ //     match 'otherThanQuote' zero or more times
			"    %s                    "+ //     match 'quotedString'
			"  )*                      "+ //   end group 1 and repeat it zero or more times
			"  %s*                     "+ //   match 'otherThanQuote'
			"  $                       "+ // match the end of the string
			")                         ", // stop positive look ahead
			otherThanQuote, quotedString, otherThanQuote);

		//And now remove the quotes
		String[] items = line.split(regex, -1);
		for (int i=0; i<items.length; i++) {
			items[i] = items[i].replace("\"", "");
		}
		return items;
	}

	protected abstract List<Component> loadLine(String[] lineItems) throws TermServerScriptException;

	public Project getProject() {
		return project;
	}

	public void startTimer() {
		startTime = new Date();
	}
	
	public void addSummaryInformation(String item, Object detail) {
		summaryDetails.put(item, detail);
	}
	
	public void incrementSummaryInformation(String key) {
		if (!quiet) {
			incrementSummaryInformation(key, 1);
		}
	}
	
	public void initialiseSummaryInformation(String key) {
		summaryDetails.put (key, new Integer(0));
	}
	
	public void incrementSummaryInformation(String key, int incrementAmount) {
		if (!summaryDetails.containsKey(key)) {
			summaryDetails.put (key, new Integer(0));
		}
		int newValue = ((Integer)summaryDetails.get(key)).intValue() + incrementAmount;
		summaryDetails.put(key, newValue);
	}
	
	public void storeRemainder(String start, String remove1, String remove2, String storeAs) {
		Collection<?> differences = new ArrayList((Collection<?>)summaryDetails.get(start));
		Collection<?> removeList = (Collection<?>)summaryDetails.get(remove1);
		differences.removeAll(removeList);
		if (remove2 != null && !remove2.isEmpty()) {
			removeList = (Collection<?>)summaryDetails.get(remove2);
			differences.removeAll(removeList);
		}
		summaryDetails.put(storeAs, differences.toString());
	}
	
	public void flushFilesSoft() throws TermServerScriptException {
		getReportManager().flushFilesSoft();
	}
	
	public void flushFiles(boolean andClose) throws TermServerScriptException {
		getReportManager().flushFiles(andClose);
	}
	
	public void finish() throws FileNotFoundException, TermServerScriptException {
		info (BREAK);
		flushFiles(true);
		Date endTime = new Date();
		if (startTime != null) {
			long diff = endTime.getTime() - startTime.getTime();
			recordSummaryText ("Completed processing in " + DurationFormatUtils.formatDuration(diff, "HH:mm:ss"));
			recordSummaryText ("Started at: " + startTime);
		}
		recordSummaryText ("Finished at: " + endTime);
		List<String> criticalIssues = new ArrayList<String>();
		
		for (Map.Entry<String, Object> summaryDetail : summaryDetails.entrySet()) {
			String key = summaryDetail.getKey();
			Object value = summaryDetail.getValue();
			String display = "";
			if (value instanceof Collection) {
				display += ((Collection<?>)value).size();
			} else if (key.startsWith(CRITICAL_ISSUE)) {
				criticalIssues.add(key + ": " + value.toString());
				continue;
			} else {
				display = value.toString();
			}
			recordSummaryText (key + ": " + display);
		}
		if (summaryDetails.containsKey("Tasks created") && summaryDetails.containsKey(CONCEPTS_TO_PROCESS) ) {
			if (summaryDetails.get(CONCEPTS_TO_PROCESS) != null &&  summaryDetails.get(CONCEPTS_TO_PROCESS) instanceof Collection) {
				double c = (double)((Collection<?>)summaryDetails.get(CONCEPTS_TO_PROCESS)).size();
				double t = (double)((Integer)summaryDetails.get("Tasks created")).intValue();
				double avg = Math.round((c/t) * 10) / 10.0;
				recordSummaryText ("Concepts per task: " + avg);
			}
		}
		
		if (criticalIssues.size() > 0) {
			recordSummaryText ("\nCritical Issues Encountered\n========================");
			for (String thisCriticalIssue : criticalIssues) {
				recordSummaryText(thisCriticalIssue);
			}
		}
		
	}
	
	private synchronized void recordSummaryText(String msg) {
		info (msg);
		msg = msg.replace("\n", "\n</br>");
		summaryText += msg + "\n<br/>";
	}
	
	public String getSummaryText() {
		return summaryText;
	}
	
	protected void writeToRF2File(String fileName, Object[] columns) throws TermServerScriptException {
		getReportManager().writeToRF2File(fileName, columns);
	}
	
	protected void writeToReportFile(int reportIdx, String line) throws TermServerScriptException {
		getReportManager().writeToReportFile(reportIdx, line);
	}
	
	protected void writeToReportFile(String line) throws TermServerScriptException {
		writeToReportFile(0, line);
	}
	

	protected String getReportName() {
		String reportName = SnomedUtils.deconstructFilename(inputFile)[1];
		if (reportName.isEmpty()) {
			reportName = getScriptName();
		}
		return reportName;
	}

	protected String getPrettyHistoricalAssociation (Concept c) throws TermServerScriptException {
		String prettyString = "No association specified.";
		if (c.getHistorialAssociations(ActiveState.ACTIVE).size() > 0) {
			prettyString = " ";
			for (HistoricalAssociation assoc : c.getHistorialAssociations(ActiveState.ACTIVE)) {
				prettyString += SnomedUtils.deconstructFSN(gl.getConcept(assoc.getRefsetId()).getFsn())[0].replace(" association reference set", "");
				prettyString += " -> ";
				prettyString += gl.getConcept(assoc.getTargetComponentId());
			}
		}
		return prettyString;
	}

	public static List<Concept> asConcepts(Collection<Component> components) {
		List<Concept> concepts = new ArrayList<>();
		for (Component c : components) {
			concepts.add((Concept)c);
		}
		return concepts;
	}
	
	public static List<Component> asComponents(Collection<Concept> concepts) {
		List<Component> components = new ArrayList<>();
		for (Concept c : concepts) {
			components.add((Concept)c);
		}
		return components;
	}
	
	protected void addSynonym(Concept concept, String term, Acceptability acceptability, String[] dialects) {
		if (term.isEmpty()) {
			return;
		}
		Description d = new Description();
		d.setTerm(term);
		d.setActive(true);
		d.setType(DescriptionType.SYNONYM);
		d.setLang(LANG_EN);
		d.setCaseSignificance(SnomedUtils.calculateCaseSignificance(term));
		d.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(acceptability, dialects));
		d.setConceptId(concept.getConceptId());
		concept.addDescription(d);
	}
	
	public void report (Task t, Component c, ValidationFailure v) throws TermServerScriptException {
		report (t, c, v.severity, v.reportActionType, v.getMessage());
	}

	public void report(Task task, Component component, Severity severity, ReportActionType actionType, Object... details) throws TermServerScriptException {
		if (quiet) {
			return;
		}
		if (component != null) {
			if (severity.equals(Severity.CRITICAL)) {
				String key = CRITICAL_ISSUE + " encountered for " + component.toString();
				String value = "";
				boolean firstDetail = true;
				for (Object detail : details) {
					if (detail instanceof Object[]) {
						Object[] arr = (Object[]) detail;
						for (Object obj : arr) {
							value += obj + ", ";
						}
					} else {
						if (firstDetail)
							value += detail;
						else {
							firstDetail = false;
							value += ", " + detail;
						}
					}
				}
				addSummaryInformation(key, value);
				info ( key + " : " + value);
			}
		}
		String key = (task == null? "" :  task.getKey());
		String desc = (task == null? "" :  task.getSummary());
		String name = (component == null ? "" : component.getReportedName());
		String type = (component == null ? "" : component.getReportedType());
		String id = (component == null ? "" : component.getId());
		StringBuffer sb = new StringBuffer();
		
		sb.append(key + COMMA + desc + COMMA + id + COMMA_QUOTE)
		.append( name + QUOTE_COMMA);
		if (stateComponentType) {
			sb.append(type + COMMA );
		}
		sb.append( severity + COMMA + actionType );
		for (Object detail : details) {
			if (detail instanceof Object[]) {
				Object[] arr = (Object[]) detail;
				for (Object obj : arr) {
					sb.append(COMMA_QUOTE + obj + QUOTE);
				}
			} else {
				sb.append(COMMA_QUOTE + detail + QUOTE);
			}
		}
		writeToReportFile (sb.toString());
	}
	

	
	protected void report (Concept c, Description d, Object...details) throws TermServerScriptException {
		incrementSummaryInformation("Report lines written");
		StringBuffer sb = new StringBuffer();
		sb.append (QUOTE)
		.append(c==null?"":c.getConceptId())
		.append(QUOTE_COMMA_QUOTE);
		if (d != null) {
			sb.append(SnomedUtils.deconstructFSN(c.getFsn())[1])
			.append(QUOTE_COMMA_QUOTE);
		}
		sb.append(d==null?c.getFsn():d)
		.append(QUOTE);
		
		for (Object detail : details) {
			 sb.append(COMMA_QUOTE)
			 .append(detail)
			 .append(QUOTE);
		}
		writeToReportFile (sb.toString());
	}

	protected List<Concept> determineProximalPrimitiveParents(Concept c) throws TermServerScriptException {
		//Filter for only the primitive ancestors
		//Sort to work with the lowest level concepts first for efficiency
		List<Concept> primitiveAncestors = ancestorsCache.getAncestors(c).stream()
											.filter(ancestor -> ancestor.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE))
											.sorted((c1, c2) -> Integer.compare(c2.getDepth(), c1.getDepth()))
											.collect(Collectors.toList());
		
		//Now which of these primitive concepts do not subsume others?
		Set<Concept> subsumers = new HashSet<>();
		for (Concept thisAncestor : primitiveAncestors) {
			//Skip any that have already been identified as subsumers
			if (!subsumers.contains(thisAncestor)) {
				//Does thisAncestor's ancestors contain any of the other candidates?
				Set<Concept> subsumesThisAncestor = thisAncestor.getAncestors(NOT_SET);
				subsumesThisAncestor.retainAll(primitiveAncestors);
				subsumers.addAll(subsumesThisAncestor);
			}
		}
		//Now remove all subsumers from our list, to leave the most specific concepts
		primitiveAncestors.removeAll(subsumers);
		return primitiveAncestors;
	}
	
	public void setQuiet(boolean quiet) {
		this.quiet = quiet;
	}

	public String getEnv() {
		return env;
	}

	public GraphLoader getGraphLoader() {
		return gl;
	}

	public SnowOwlClient getTSClient() {
		return tsClient;
	}

	public ArchiveManager getArchiveManager() {
		return archiveManager;
	}

	public void setArchiveManager(ArchiveManager archiveManager) {
		this.archiveManager = archiveManager;
	}

	public File getInputFile() {
		return inputFile;
	}

	public ReportManager getReportManager() {
		return reportManager;
	}

	public void setReportManager(ReportManager reportManager) {
		this.reportManager = reportManager;
	}

}
