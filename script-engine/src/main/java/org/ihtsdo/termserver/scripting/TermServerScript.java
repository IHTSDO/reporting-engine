package org.ihtsdo.termserver.scripting;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.dao.ReportDataBroker;

import org.apache.commons.lang.time.DurationFormatUtils;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.authoringservices.AuthoringServicesClient;
import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.domain.Branch;
import org.ihtsdo.termserver.scripting.domain.ConcreteValue;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.Script;
import org.snomed.otf.script.dao.ReportConfiguration;
import org.springframework.context.ApplicationContext;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public abstract class TermServerScript extends Script implements ScriptConstants {
	private static final Logger LOGGER = LoggerFactory.getLogger(TermServerScript.class);
	public static final String COMMAND_LINE_USAGE = "Usage: java <VM_ARGUMENTS> <TSScriptClass> " +
			"[-a author] " +
			"[-c <authenticatedCookie>] " +
			"[-d <Y/N>] " +
			"[-f <batch file Location>] " +
			"[-m <modules>] " +
			"[-n <taskSize>] " +
			"[-p <projectName>] " +
			"[-r2 <restart position>] " +
			"[-headless <env_number>] " +
			"[-task <taskKey>]";

	protected static boolean debug = true;
	protected static boolean dryRun = true;
	protected static Integer headlessEnvironment = null;
	protected boolean validateConceptOnUpdate = true;
	protected boolean offlineMode = false;
	protected static int dryRunCounter = 0;
	protected String env;
	protected String url = environments[0];
	protected boolean stateComponentType = true;
	protected JobRun jobRun;
	protected boolean localClientsRequired = true;
	protected TermServerClient tsClient;
	protected AuthoringServicesClient scaClient;
	protected String authenticatedCookie;
	protected boolean ignoreInputFileForReportName = false;
	protected int maxFailures = 5;
	protected int restartPosition = NOT_SET;
	protected int processingLimit = NOT_SET;
	protected boolean inputFileHasHeaderRow = false;
	protected boolean runStandAlone = false; //Set to true to avoid loading concepts from Termserver.  Should be used with Dry Run only.
	protected List<File> inputFiles = new ArrayList<File>(Collections.nCopies(10, (File) null));
	private String dependencyArchive;
	protected String projectName;
	private String reportName;
	protected int summaryTabIdx = NOT_SET;
	protected boolean reportNullConcept = true;
	protected boolean expectStatedRelationshipInactivations = false;
	protected String subHierarchyStr;
	protected String subsetECL;
	protected String overrideEclBranch = null;
	protected Concept subHierarchy;
	protected String[] excludeHierarchies;
	protected boolean ignoreWhiteList = false;
	protected boolean allowMissingExpectedModules = false;
	protected boolean allowDirectoryInputFile = false;
	protected int tabForFinalWords = PRIMARY_REPORT;
	
	protected Set<String> whiteListedConceptIds = new HashSet<>();
	protected Set<String> archiveEclWarningGiven = new HashSet<>();
	private List<String> finalWords = new ArrayList<>();

	protected GraphLoader gl = GraphLoader.getGraphLoader();
	protected String headers = "Concept SCTID,";
	protected String additionalReportColumns = "ActionDetail, AdditionalDetail, ";
	protected String secondaryReportColumns = "ActionDetail, ";
	protected String tertiaryReportColumns = "ActionDetail, ";
	protected boolean expectNullConcepts = false; //Set to true to avoid warning about rows in input file that result in no concept to modify
	public Scanner STDIN = new Scanner(System.in);
	
	public static String CONCEPTS_IN_FILE = "Concepts in file";
	public static String CONCEPTS_TO_PROCESS = "Concepts to process";
	public static String REPORTED_NOT_PROCESSED = "Reported not processed";
	public static String ISSUE_COUNT = "Issue count";
	public static String CRITICAL_ISSUE = "CRITICAL ISSUE";
	public static String WHITE_LISTED_COUNT = "White Listed Count";
	public static String inputFileDelimiter = TSV_FIELD_DELIMITER;
	protected String tsRoot = "MAIN/"; //"MAIN/2016-01-31/SNOMEDCT-DK/";
	public static final String EXPECTED_PROTOCOL = "https://";
	
	
	protected static final String AUTHOR = "Author";
	public static final String Reviewer = "Reviewer";
	public static final String CONCEPTS_PER_TASK = "Concepts per task";
	public static final String RESTART_FROM_TASK = "Restart from task";
	
	public static final String FILE = "File";
	protected static final String DRY_RUN = "Dry Run";
	protected static final String INPUT_FILE = "InputFile";
	protected static final String NEW_CONCEPTS_ONLY = "New Concepts Only";
	protected static final String SUB_HIERARCHY = "Subhierarchy";
	protected static final String ATTRIBUTE_TYPE = "Attribute Type";
	protected static final String HIERARCHIES = "Hierarchies";
	protected static final String INCLUDE_ALL_LEGACY_ISSUES = "Include All Legacy Issues";
	protected static final String ECL = "ECL";
	protected static final String MODULES = "Modules";
	protected static final String TEMPLATE = "Template";
	protected static final String TEMPLATE2 = "Template 2";
	protected static final String TEMPLATE_NAME = "TemplateName";
	protected static final String SERVER_URL = "ServerUrl";

	protected ReportDataBroker reportDataBroker;

	public static Gson gson;
	static {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter(Relationship.class, new RelationshipSerializer());
		gsonBuilder.setPrettyPrinting();
		gsonBuilder.excludeFieldsWithoutExposeAnnotation();
		gson = gsonBuilder.create();
	}
	
	public Concept[] selfGroupedAttributes = new Concept[] { FINDING_SITE, CAUSE_AGENT, ASSOC_MORPH };
	
	private boolean asyncSnapshotCacheInProgress = false;

	public String detectReleaseBranch() {
		return getArchiveManager(true).detectReleaseBranch(project.getKey());
	}

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
	
	protected static String[] envKeys = new String[] {"local","dev","uat","prod","dev","dev","uat", "uat", "prod", "prod"};

	protected static String[] environments = new String[] {	"http://localhost:8080/",
															"https://dev-authoring.ihtsdotools.org/",
															"https://uat-authoring.ihtsdotools.org/",
															"https://prod-authoring.ihtsdotools.org/",
															"https://dev-bb18-ms-authoring.ihtsdotools.org/",
															"https://dev-snowstorm.ihtsdotools.org/",
															"https://uat-ms-authoring.ihtsdotools.org/",
															"https://uat-snowstorm.ihtsdotools.org/",
															"https://prod-ms-authoring.ihtsdotools.org/",
															"https://prod-snowstorm.ihtsdotools.org/"
	};
	
	protected void init(String[] args) throws TermServerScriptException {
		
		if (args.length < 2) {
			println("Usage: java <TSScriptClass> [-a author] [-n <taskSize>] [-r <restart position>] [-c <authenticatedCookie>] [-d <Y/N>] [-p <projectName>] [-f <batch file Location>] [-dp <dependency file>]");
			println(" d - dry run");
			System.exit(-1);
		}
	
		for (int x=0; x< args.length; x++) {
			String thisArg = args[x];
			if (thisArg.equals("-p")) {
				projectName = args[x+1];
			} else if (thisArg.equals("-c")) {
				authenticatedCookie = args[x+1];
			} else if (thisArg.equals("-d")) {
				dryRun = args[x+1].toUpperCase().equals("Y");
				if (!dryRun) {
					this.runStandAlone = false;
				}
			} else if (thisArg.startsWith("-f")) {
				int fileIdx = 0;
				if (thisArg.length() > 2) {
					//If we don't have a number, skip this argument
					if (!StringUtils.isNumeric(thisArg.substring(2))) {
						continue;
					}
					fileIdx = Integer.parseInt(thisArg.substring(2));
				}
				File thisFile = new File(args[x+1]);
				setInputFile(fileIdx, thisFile);
				if (!getInputFile().canRead()) {
					throw new TermServerScriptException ("Unable to read input file: " + thisFile);
				}
				LOGGER.info("Reading data (fileIdx " + fileIdx + ") from " + thisFile.getAbsolutePath());
			} else if (thisArg.equals("-r")) {
				restartPosition = Integer.parseInt(args[x+1]);
			} else if (thisArg.equals("-dp")) {
				dependencyArchive = args[x+1];
			} else if (thisArg.equals("-task") || thisArg.equals("--task")) {
				taskKey = args[x+1];
			}
		}
		
		if (headlessEnvironment == null) {
			checkSettingsWithUser(null);
		}
		
		init();
	}
	
	private void init() throws TermServerScriptException {
		if (restartPosition == 0) {
			LOGGER.info("Restart position given as 0 but line numbering starts from 1.  Starting at line 1.");
			restartPosition = 1;
		}
		
		//TODO Make calls through client objects rather than resty direct and remove this member
		//TODO May then be able to remove otf-common entirely and just use resource-manager
		if (localClientsRequired == true) {
			scaClient = new AuthoringServicesClient(url, authenticatedCookie);
			tsClient = createTSClient(this.url, authenticatedCookie);
		}
		boolean loadingRelease = false;
		//Recover the full project path from authoring services, if not already fully specified
		if (project == null) {
			project = new Project();
			if (projectName.startsWith("MAIN")) {
				project.setBranchPath(projectName);
				if (projectName.equals("MAIN")) {
					project.setKey(projectName);
				} else {
					project.setKey(projectName.substring(projectName.lastIndexOf("/")));
				}
			} else if (StringUtils.isNumeric(projectName) || projectName.endsWith(".zip")) {
				LOGGER.info("Loading release: " + projectName); 
				loadingRelease = true;
				project.setKey(projectName);
			} else {
				if (runStandAlone) {
					LOGGER.info("Running stand alone. Guessing project path to be MAIN/" + projectName);
					project.setBranchPath("MAIN/" + projectName);
				} else {
					try {
						project = scaClient.getProject(projectName);
						LOGGER.info("Recovered project " + project.getKey() + " with branch path: " + project.getBranchPath());
					} catch (RestClientException e) {
						throw new TermServerScriptException("Unable to recover project: " + projectName,e);
					}
				}
				project.setKey(projectName);
			}
		} else {
			LOGGER.warn("Project already set as " + project);
		}
		
		if (taskKey != null) {
			project.setBranchPath(project.getBranchPath() + "/" + taskKey);
		}
		
		if (!loadingRelease) {
			LOGGER.info("Full path for project " + project.getKey() + " determined to be: " + project.getBranchPath());
			//If we're loading a CodeSystem eg MAIN/SNOMEDCT-SE then we will have to recover the metadata from the branch instead
			if (project.getMetadata() == null) {
				Branch branch = tsClient.getBranch(project.getBranchPath());
				project.setMetadata(branch.getMetadata());
			}
		}
		
		// Configure the type(s) and locations(s) for processing report output.
		initialiseReportConfiguration(jobRun);
	}

	protected void checkSettingsWithUser(JobRun jobRun) throws TermServerScriptException {
		int envChoice = NOT_SET;
		if (headlessEnvironment != null) {
			envChoice = headlessEnvironment;
		} else {
			println("Select an environment ");
			for (int i=0; i < environments.length; i++) {
				println("  " + i + ": " + environments[i]);
			}
			
			print("Choice: ");
			String choice = STDIN.nextLine().trim();
			envChoice = Integer.parseInt(choice);
		}
		url = environments[envChoice];
		env = envKeys[envChoice];
		
		if (jobRun != null) {
			//Not sure historically why we have this in two places
			jobRun.setTerminologyServerUrl(url);
			jobRun.setParameter(SERVER_URL, url);
		}
	
		if (jobRun != null && !jobRun.getAuthToken().isEmpty()) {
			authenticatedCookie = jobRun.getAuthToken();
		} else if (authenticatedCookie == null || authenticatedCookie.trim().isEmpty()) {
			print ("Please enter your authenticated cookie for connection to " + url + " : ");
			authenticatedCookie = STDIN.nextLine().trim();
		}
		
		if (jobRun != null && !StringUtils.isEmpty(jobRun.getProject())) {
			projectName = jobRun.getProject();
		}
		
		if (headlessEnvironment == null) {
			print("Specify Project " + (projectName==null?": ":"[" + projectName + "]: "));
			String response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				projectName = response;
				if (jobRun != null) {
					jobRun.setProject(response);
				}
			}
		
			if (restartPosition != NOT_SET) {
				print("Restarting from position [" +restartPosition + "]: ");
				response = STDIN.nextLine().trim();
				if (!response.isEmpty()) {
					restartPosition = Integer.parseInt(response);
				}
			}
		}
		
	}

	protected void init (JobRun jobRun) throws TermServerScriptException {
		this.url = jobRun.getTerminologyServerUrl();
		this.env = getEnv(url);
		this.jobRun = jobRun;
		EclCache.reset();
		authenticatedCookie = jobRun.getAuthToken();
		
		if (jobRun != null && !StringUtils.isEmpty(jobRun.getProject())) {
			projectName = jobRun.getProject();
		} else if ((jobRun == null && projectName == null) || 
				(jobRun != null && StringUtils.isEmpty(jobRun.getProject()))) {
			LOGGER.warn("No project specified, running against MAIN");
			projectName = "MAIN";
		}
		
		if (!StringUtils.isEmpty(jobRun.getParamValue(SUB_HIERARCHY))) {
			subHierarchy = gl.getConcept(jobRun.getParamValue(SUB_HIERARCHY));
		}
		
		if (!StringUtils.isEmpty(jobRun.getParamValue(ECL))) {
			subsetECL =jobRun.getParamValue(ECL);
		}
		
		String inputFileName = jobRun.getParamValue(INPUT_FILE);
		if (!StringUtils.isEmpty(inputFileName)) {
			setInputFile(0,new File(inputFileName));
		}
		
		if (jobRun.getWhiteList() != null) {
			whiteListedConceptIds = jobRun.getWhiteList().stream()
					.map( w -> SnomedUtils.makeMachineReadable(w.getSctId()))
					.collect(Collectors.toSet());
		}
		
		if (authenticatedCookie == null || authenticatedCookie.trim().isEmpty()) {
			throw new TermServerScriptException("Unable to proceed without an authenticated token/cookie");
		}
		
		init();
		
		if (projectName.equals("MAIN") || projectName.startsWith("MAIN/")) {
			//MAIN is not a project.  Recover Main metadata from branch
			project.setMetadata(tsClient.getBranch(projectName).getMetadata());
		} else if (!StringUtils.isNumeric(projectName) && !projectName.endsWith(".zip")) {
			//Not if we're loading a release or extension
			try {
				int retry = 0;
				boolean ok = false;
				while (!ok && retry < 3) {
					try {
						project = scaClient.getProject(projectName);
						ok = true;
						
						//Are we in fact running against a task?
						if (jobRun != null && !StringUtils.isEmpty(jobRun.getTask())) {
							String taskBranchPath = project.getBranchPath() + "/" + jobRun.getTask();
							project.setBranchPath(taskBranchPath);
						} else if (taskKey != null) {
							String taskBranchPath = project.getBranchPath() + "/" + taskKey;
							project.setBranchPath(taskBranchPath);
						}
					} catch (Exception e) {
						//No need to retry if we get a 403
						String exceptionMsg = ExceptionUtils.getExceptionCause("Unable to recover project", e) + " Retrying after short nap.";
						if (!exceptionMsg.contains("403") && ++retry < 3) {
							System.err.println(exceptionMsg);
							Thread.sleep(1000 * 10);
						} else {
							throw new TermServerScriptException("Failed to recover project " + projectName, e);
						}
					}
				}
				
			} catch (InterruptedException e) {
				throw new TermServerScriptException("Failed to recover project " + projectName, e);
			}
		}
		
		LOGGER.info("Init Complete. Project Key determined: " + project.getKey() + " on " + project.getBranchPath());
	}
	
	private String getEnv(String terminologyServerUrl) throws TermServerScriptException {
		if (!terminologyServerUrl.startsWith(EXPECTED_PROTOCOL)) {
			throw new TermServerScriptException("Termserver URL should start with " + EXPECTED_PROTOCOL);
		}
		String url = terminologyServerUrl.substring(EXPECTED_PROTOCOL.length());
		//What's the first part of the address?
		String machineName = url.split("\\.")[0];
		//Find the last dash, to pick out the environment
		int lastDash = machineName.lastIndexOf("-");
		if (lastDash == NOT_SET) {
			return "prod";
		}
		return machineName.substring(0, lastDash);
	}

	public void postInit() throws TermServerScriptException {
		postInit(null, new String[] {headers + additionalReportColumns}, false);
	}
	
	public void postInit(boolean csvOutput) throws TermServerScriptException {
		postInit(null, new String[] {headers + additionalReportColumns}, csvOutput);
	}
	
	public void postInit(String[] tabNames, String[] columnHeadings, boolean csvOutput) throws TermServerScriptException {
		if (jobRun != null && jobRun.getParamValue(SUB_HIERARCHY) != null) {
			subHierarchy = gl.getConcept(jobRun.getMandatoryParamValue(SUB_HIERARCHY));
			//RP-4 And post that back in, so the FSN is always populated
			jobRun.setParameter(SUB_HIERARCHY, subHierarchy.toString());
		}
		super.postInit(tabNames, columnHeadings, csvOutput);

		if (gl.getIntegrityWarnings().size() > 0) {
			report(PRIMARY_REPORT, "***********  Snapshot Integrity Warnings  ***********");
			for (String warning : gl.getIntegrityWarnings()) {
				report(PRIMARY_REPORT, warning);
			}
			report(PRIMARY_REPORT, "******************************************************");
			report(PRIMARY_REPORT, "");
		}
	}
	
	public void instantiate(JobRun jobRun, ApplicationContext appContext) {
		try {
			LOGGER.debug("Instantiating " + this.getClass().getName() + " to process request for " + jobRun.getJobName());
			LOGGER.debug("Application context has " + (appContext == null?"not " : "") + "been supplied");
			this.appContext = appContext;
			this.jobRun = jobRun;

			//Job Runs generally self determine
			preInit();
			
			//Are we running locally?
			if (appContext == null) {
				checkSettingsWithUser(jobRun);
			}
			init(jobRun);
			loadProjectSnapshot(false);  //Load all descriptions
			postInit();
			runJob();
			flushFilesWithWait(false);
			finish();
			jobRun.setStatus(JobStatus.Complete);
		} catch (Exception e) {
			String msg = "Failed to complete " + jobRun.getJobName() + ExceptionUtils.getExceptionCause("", e);
			jobRun.setStatus(JobStatus.Failed);
			jobRun.setDebugInfo(msg);
			LOGGER.error(msg, e);
		} finally {
			try {
				if (finalWords.size() > 0) {
					report(tabForFinalWords, "");
					report(tabForFinalWords, "", "***********************************");
					report(tabForFinalWords, "");
					for (String finalMsg : finalWords) {
						report(tabForFinalWords, finalMsg);
					}
				}
				flushFiles(true);
			} catch (Exception e) {
				LOGGER.warn("Exception while writing final words and flushing: " + e.getMessage());
			}

			try {
				if (!suppressOutput) {
					if (getReportManager() != null) {
						jobRun.setResultUrl(getReportManager().getUrl());
					}
					Object issueCountObj = summaryDetails.get(ISSUE_COUNT);
					int issueCount = 0;
					if (issueCountObj != null && StringUtils.isNumeric(issueCountObj.toString())) {
						issueCount = Integer.parseInt(issueCountObj.toString());
					}
					jobRun.setIssuesReported(issueCount);
				}
			} catch (Exception e2) {
				LOGGER.error("Failed to set result URL in final block", e2);
			}
			
			//Are we still writing our snapshot to disk?  Don't move on to anything else while we are
			while (asyncSnapshotCacheInProgress) {
				LOGGER.warn("Snapshot cache still being written to disk.  Waiting for completion. Recheck in 5s.");
				try {
					Thread.sleep(5 * 1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	protected void preInit() throws TermServerScriptException {
		//Override this method in concrete class to set flags that affect checkSettingsWithUser
		//like selfDetermining = true;
	}

	protected void runJob () throws TermServerScriptException {
		throw new TermServerScriptException("Override this method in concrete class");
	}

	protected static JobRun createJobRunFromArgs(String jobName, String[] args) {
		if (args.length < 2) {
			LOGGER.info(COMMAND_LINE_USAGE);
			System.exit(-1);
		}

		JobRun jobRun = JobRun.create(jobName, null);

		int argNumber = 0;
		while (argNumber < args.length - 1) {
			String parameter = args[argNumber + 1];

			switch (args[argNumber]) {
				case "-p":
					jobRun.setProject(parameter);
					break;
				case "-c":
					jobRun.setAuthToken(parameter);
					break;
				case "-d":
					jobRun.setParameter(DRY_RUN, parameter);
					break;
				case "-f":
					jobRun.setParameter(INPUT_FILE, parameter);
					break;
				case "-a":
					jobRun.setParameter(AUTHOR, parameter);
					jobRun.setUser(parameter);
					break;
				case "-n":
					jobRun.setParameter(CONCEPTS_PER_TASK, parameter);
					break;
				case "-m":
					jobRun.setParameter(MODULES, parameter);
					break;
				case "-r2":
					jobRun.setParameter(RESTART_FROM_TASK, parameter);
					break;
				case "-task":
				case "--task":
					jobRun.setTask(parameter);
					break;
				case "-headless":
					headlessEnvironment = Integer.valueOf(parameter);
					break;
				default:
					LOGGER.error(COMMAND_LINE_USAGE);
					throw new IllegalArgumentException("Unknown parameter: " + parameter);
			}

			argNumber += 2;
		}

		return jobRun;
	}
	
	protected TermServerClient createTSClient(String url, String authenticatedCookie) {
		if (!authenticatedCookie.contains("ihtsdo=")) {
			throw new IllegalArgumentException("Malformed cookie detected.  Expected <env>-ihtsdo=<token> instead received: " + authenticatedCookie);
		}
		String contextPath = "snowstorm/snomed-ct";
		return new TermServerClient(url + contextPath, authenticatedCookie);
	}

	//Default implementation - load all descriptions
	protected void loadProjectSnapshot() throws TermServerScriptException {
		loadProjectSnapshot(false);
	}
	
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException {
		getArchiveManager(true).loadSnapshot(fsnOnly);
		//Reset the report name to null here as it will have been set by the Snapshot Generator
		setReportName(null);
	}
	
	protected void loadArchive(TermServerScript parentProcess, File archive, boolean fsnOnly, String fileType, Boolean isReleased) throws TermServerScriptException  {
		parentProcess.getArchiveManager(true).loadArchive(archive, fsnOnly, fileType, isReleased);
	}
	
	protected Concept loadConcept(String sctid, String branchPath) throws TermServerScriptException {
		if (dryRun) {
			//In a dry run situation, the task branch is not created so use the Project instead
			//But we'll clone it, so the object isn't confused with any local changes
			
			//If we're already working at project level, don't modify branchPath
			//Note that for MS we expect two slashes eg MAIN/SNOMEDCT-SE/SE
			if (branchPath.contains("SNOMEDCT-") && CharMatcher.is('/').countIn(branchPath) == 2) {
				//debug ("MS Project detected as branch path: " + branchPath);
			} else if (branchPath.indexOf("/") != branchPath.lastIndexOf("/")) {
				branchPath = branchPath.substring(0, branchPath.lastIndexOf("/"));
			}
			if (runStandAlone) {
				LOGGER.debug("Loading: " + gl.getConcept(sctid) + " from local store");
				return gl.getConcept(sctid).cloneWithIds();
			}
		}
		Concept loadedConcept = loadConcept (tsClient, sctid, branchPath);
		return loadedConcept;
	}
	
	protected Description loadDescription(String sctId, String branchPath) throws TermServerScriptException {
		if (dryRun) {
			//In a dry run situation, the task branch is not created so use the Project instead
			//But we'll clone it, so the object isn't confused with any local changes
			
			//If we're already working at project level, don't modify branchPath
			//Note that for MS we expect two slashes eg MAIN/SNOMEDCT-SE/SE
			if (branchPath.contains("SNOMEDCT-") && CharMatcher.is('/').countIn(branchPath) == 2) {
				//debug ("MS Project detected as branch path: " + branchPath);
			} else if (branchPath.indexOf("/") != branchPath.lastIndexOf("/")) {
				branchPath = branchPath.substring(0, branchPath.lastIndexOf("/"));
			}
			if (runStandAlone) {
				LOGGER.debug("Loading: " + gl.getDescription(sctId) + " from local store");
				return gl.getDescription(sctId).clone(null, true);
			}
		}
		try {
			return tsClient.getDescription(sctId, branchPath);
		} catch (Exception e) {
			if (e.getMessage() != null && e.getMessage().contains("[404] Not Found") 
				|| e.getMessage().contains("404 Not Found")
				|| e.getMessage().contains("NOT_FOUND")) {
				LOGGER.debug("Unable to find description " + sctId + " on branch " + branchPath);
				return null;
			}
			e.printStackTrace();
			String msg =  e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			throw new TermServerScriptException("Failed to recover description " + sctId + " from TS branch " + branchPath + ", due to: " + msg,e);
		}
	}
	
	protected Concept loadConcept(Concept concept, String branchPath) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept.getConceptId(), branchPath);
		//Detect attempt to load a deleted concept
		if (loadedConcept == null || StringUtils.isEmpty(loadedConcept.getConceptId())) {
			return null;
		}
		loadedConcept.setConceptType(concept.getConceptType());
		if (!dryRun) {
			//The loaded concept has some idea of the preferred term.  We'll have that now
			concept.setPreferredSynonym(loadedConcept.getPreferredSynonym());
		}
		//In any event, copy any issues over from the cached concept to the loaded one
		loadedConcept.setIssue(concept.getIssues());
		return loadedConcept;
	}
	
	protected Concept loadConcept(TermServerClient client, Concept concept, String branchPath) throws TermServerScriptException {
			return loadConcept(client, concept.getConceptId(), branchPath);
	}
	
	protected Concept loadConcept(TermServerClient client, String sctId, String branchPath) throws TermServerScriptException {
		Concept concept =  gl.getConcept(sctId);
		try {
			LOGGER.debug("Loading: " + concept + " from TS branch " + branchPath);
			Concept loadedConcept = client.getConcept(sctId, branchPath);
			loadedConcept.setLoaded(true);
			convertAxiomsToRelationships(loadedConcept, loadedConcept.getClassAxioms());
			convertAxiomsToRelationships(loadedConcept, loadedConcept.getAdditionalAxioms());
			return loadedConcept;
		} catch (Exception e) {
			if (e.getMessage() != null && e.getMessage().contains("[404] Not Found") 
					|| e.getMessage().contains("404 Not Found")
					|| e.getMessage().contains("NOT_FOUND")) {
				LOGGER.debug("Unable to find " + concept + " on branch " + branchPath);
				return null;
			}
			e.printStackTrace();
			String msg =  e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			throw new TermServerScriptException("Failed to recover " + concept + " from TS branch " + branchPath + ", due to: " + msg,e);
		}
	}
	
	protected RefsetMember loadRefsetMember(String uuid) throws TermServerScriptException {
		return loadRefsetMember(uuid, project.getBranchPath());
	}
	
	protected RefsetMember loadRefsetMember(String uuid, String branch) throws TermServerScriptException {
		LOGGER.debug("Loading refset member " + uuid + " from " + branch);
		return tsClient.getRefsetMember(uuid, branch);
	}
	
	protected LangRefsetEntry loadLangRefsetMember(String uuid, String branch) throws TermServerScriptException {
		LOGGER.debug("Loading refset member " + uuid + " from " + branch);
		return tsClient.getLangRefsetMember(uuid, branch);
	}
	
	protected RefsetMember loadPreviousRefsetMember(String uuid) throws TermServerScriptException {
		if (project.getPreviousBranchPath() == null) {
			String previousBranchPath = getArchiveManager(true).getPreviousBranch(project);
			project.setPreviousBranchPath(previousBranchPath);
		}
		LOGGER.debug("Loading refset member " + uuid + " from " + project.getPreviousBranchPath());
		return tsClient.getRefsetMember(uuid, project.getPreviousBranchPath());
	}
	
	protected RefsetMember updateRefsetMember(RefsetMember rm) throws TermServerScriptException {
		LOGGER.debug((dryRun?"Dry run update of":"Updating") + " refset member " + rm.getId());
		if (dryRun) {
			return rm;
		} else {
			return tsClient.updateRefsetMember(rm, project.getBranchPath());
		}
	}
	
	private void convertAxiomsToRelationships(Concept c, List<Axiom> axioms) throws TermServerScriptException {
		try {
			if (axioms != null) {
				for (Axiom axiom : axioms) {
					for (Relationship r : axiom.getRelationships()) {
						r.setEffectiveTime(axiom.getEffectiveTime());
						r.setActive(axiom.isActive());
						r.setAxiom(axiom);
						r.setSource(gl.getConcept(c.getConceptId()));
						if (!r.isConcrete()) {
							r.setTarget(gl.getConcept(r.getTarget().getConceptId()));
						}
						c.addRelationship(r);
						r.setReleased(axiom.getReleased());
					}
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException ("Failed to convert axioms to relationships in " + c, e);
		}
	}

	protected Concept updateConcept(Task t, Concept c, String info) throws TermServerScriptException {
		if (dryRun) {
			return c;
		} else {
			try {
				convertStatedRelationshipsToAxioms(c, false);
				if (validateConceptOnUpdate) {
					validateConcept(t, c);
				}
				
				LOGGER.debug("Updating state of " + c + (info == null?"":info));
				return tsClient.updateConcept(c, t.getBranchPath());
			} catch (ValidationFailure e) {
				throw e;
			} catch (Exception e) {
				String excpStr =  e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
				String msg = "Failed to update " + c + " in TS due to " + excpStr;
				LOGGER.error(msg + " JSON = " + gson.toJson(c), e);
				throw new TermServerScriptException(msg,e); 
			}
		} 
	}
	
	private void validateConcept(Task t, Concept c) throws TermServerScriptException {
		//We need to populate new components with UUIDs for validation
		Concept uuidClone = c.cloneWithUUIDs();
		LOGGER.debug("Validating " + c);
		
		//We should not be modifying any stated relationships
		if (!expectStatedRelationshipInactivations) {
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.BOTH)) {
				if (StringUtils.isEmpty(r.getEffectiveTime())) {
					throw new IllegalStateException("Stated Relationship update attempt (during validation): " + r);
				}
			}
		}
		
		DroolsResponse[] validations = tsClient.validateConcept(uuidClone, t.getBranchPath());
		if (validations.length == 0) {
			LOGGER.debug("Validation clear: " + c);
		} else {
			Set<String> warningsReported = new HashSet<>();
			LOGGER.debug("Validation issues: " + validations.length);
			for (DroolsResponse response : validations) {
				if (response.getSeverity().equals(DroolsResponse.Severity.ERROR)) {
					throw new ValidationFailure(t,  c, "Drools error: " + response.getMessage());
				} else if (response.getSeverity().equals(DroolsResponse.Severity.WARNING)) {
					//Only report a particular warning text once
					if (!warningsReported.contains(response.getMessage())) {
						report (t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Drools warning: " + response.getMessage());
						warningsReported.add(response.getMessage());
					}
				} else {
					throw new IllegalStateException("Unexpected drools response: " + response);
				}
			}
		}
	}

	/*private void ensureSaveEffective(Concept before, Concept after) throws TermServerScriptException {
		//Check we've got the same number of active / inactive descriptions / relationships
		int activeDescBefore = before.getDescriptions(ActiveState.ACTIVE).size();
		int activeDescAfter = after.getDescriptions(ActiveState.ACTIVE).size();
		
		int inactiveDescBefore = before.getDescriptions(ActiveState.INACTIVE).size();
		int inactiveDescAfter = after.getDescriptions(ActiveState.INACTIVE).size();

		int activeStdRelBefore = before.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE).size();
		int activeStdRelAfter = after.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE).size();
	
		int inactiveStdRelBefore = before.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.INACTIVE).size();
		int inactiveStdRelAfter = after.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.INACTIVE).size();
	
		int activeInfRelBefore = before.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE).size();
		int activeInfRelAfter = after.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE).size();
	
		int inactiveInfRelBefore = before.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.INACTIVE).size();
		int inactiveInfRelAfter = after.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.INACTIVE).size();
	
		if (	activeDescBefore != activeDescAfter ||
				inactiveDescBefore != inactiveDescAfter ||
				activeStdRelBefore != activeStdRelAfter ||
				inactiveStdRelBefore != inactiveStdRelAfter ||
				activeInfRelBefore != activeInfRelAfter ||
				inactiveInfRelBefore != inactiveInfRelAfter) {
			throw new TermServerScriptException("Concept has not fully saved to TS, although no error was reported");
		}
	}*/
	
	protected Concept createConcept(Task t, Concept c, String info) throws TermServerScriptException {
		return createConcept(t, c,  info, true); //validate by default
	}

	protected Concept createConcept(Task t, Concept c, String info, boolean validate) throws TermServerScriptException {
		if (c.getFsn() == null || c.getFsn().isEmpty()) {
			throw new ValidationFailure(c, "Cannot create concept with no FSN");
		}
		int attempt = 0;
		while (true) {
			try {
				//Copy across the concept type to the returned object - it isn't known to the TS
				ConceptType conceptType = c.getConceptType();
				Concept createdConcept = attemptConceptCreation(t, c, info, validate);
				createdConcept.setConceptType(conceptType);
				//Populate the new SCTID into our original object, so the task can properly update the task description
				c.setId(createdConcept.getId());
				return createdConcept;
			} catch (Exception e) {
				attempt++;
				String msg = "Failed to create " + c + " in TS due to " + getMessage(e);
				if (attempt <= 2) {
					incrementSummaryInformation("Concepts creation exceptions");
					LOGGER.warn(msg + " retrying...");
					try {
						Thread.sleep(5 * 1000);
					} catch(InterruptedException ie) {}
				} else {
					throw new TermServerScriptException(msg ,e);
				}
			}
		}
	}
	
	private Concept attemptConceptCreation(Task t, Concept c, String info, boolean validate) throws Exception {
		LOGGER.debug((dryRun ?"Dry run creating ":"Creating ") + (c.getConceptType() != null ?c.getConceptType() + " ":"") + c + info);
		convertStatedRelationshipsToAxioms(c, false);
		if (!dryRun) {
			if (validate) {
				validateConcept(t, c);
			}
			c = tsClient.createConcept(c, t.getBranchPath());
		} else {
			c = c.clone("NEW_SCTID");
		}
		incrementSummaryInformation("Concepts created");
		return c;
	}

	protected void convertStatedRelationshipsToAxioms(Concept c, boolean mergeExistingAxioms) {
		convertStatedRelationshipsToAxioms(c, mergeExistingAxioms, false);
	}

	protected void convertStatedRelationshipsToAxioms(Concept c, boolean mergeExistingAxioms, boolean leaveStatedRelationships) {
		//We might have already done this if an error condition has occurred.
		//Skip if there are not stated relationships
		if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.BOTH).size() == 0) {
			return;
		}
		
		/*if (c.getConceptId().equals("2301000004107")) {
			debug("Here");
		}*/
		
		//In the case of an inactive concept, we'll inactivate any axioms
		if (c.isActive()) {
			for (Axiom a : c.getClassAxioms()) {
				a.clearRelationships();
			}
			
			//Do we have an existing axiom to use by default?
			Axiom a = c.getFirstActiveClassAxiom();
			a.setModuleId(c.getModuleId());
			
			//If we're working with local concepts, remove any Axiom Entries and pinch their UUID
			if (a.getId() == null && c.getAxiomEntries().size() > 0) {
				for (AxiomEntry ae : c.getAxiomEntries()) {
					if (ae.isActive()) {
						a.setAxiomId(ae.getId());
					}
				}
			}
			
			if (mergeExistingAxioms) {
				c.getAxiomEntries().clear();
			}
	
			//We'll remove the stated relationships as they get converted to the axiom
			//Unless we want to keep it so we can easily form the expression
			Set<Relationship> rels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.BOTH);
			for (Relationship rel : rels) {
				//Ignore inactive rels, unless they come from an inactive axiom, in which case leave them there
				if (!rel.isActive()) {
					//...unless it came from an axiom in which case it's no longer required
					//and causes confusion for a validation check due to having no effective time
					if (rel.getAxiom() != null) {
						if (!rel.getAxiom().isActive()) {
							rel.getAxiom().getRelationships().add(rel);
						}

						if (leaveStatedRelationships) {
							rel.setAxiom(a);
						} else {
							c.removeRelationship(rel, true); //Safe to remove it even if published - will exist in axiom
						}
					}
					continue;
				}

				Axiom thisAxiom  = a; 
				if (!mergeExistingAxioms) {
					thisAxiom = rel.getAxiom() == null ? a : rel.getAxiom();
				}
				
				//The definition status of the axiom needs to match that of the concept
				thisAxiom.setDefinitionStatus(c.getDefinitionStatus());
				
				//Don't add an inactive relationship to an active axiom
				if (thisAxiom.isActive() != rel.isActive()) {
					if (!rel.isActive()) {
						LOGGER.warn("Skipping axiomification of " + rel + " due to active axiom");
					} else {
						throw new IllegalStateException ("Active stated conflict between " + rel + " and " + thisAxiom);
					}
				}
				thisAxiom.getRelationships().add(rel);
				if (!rel.fromAxiom() && !rel.isActive()) {
					//Historically inactive stated relationship, leave it be
				} else if (!leaveStatedRelationships) {
					c.removeRelationship(rel, true);  //Safe to remove it even if published - will exist in axiom
				}
			}
			
			for (Axiom thisAxiom : new ArrayList<>(c.getClassAxioms())) {
				if (thisAxiom.getRelationships().size() == 0) {
					//Has this axiom been released?  Remove if not and if it's empty
					if (StringUtils.isEmpty(thisAxiom.getId())) {
						c.getClassAxioms().remove(thisAxiom);
					} else {
						throw new IllegalStateException ("Axiom left with no relationships in " + c + ": " + thisAxiom);
					}
				}
			}
		} else {
			//Inactive concept, inactivate any axioms
			for (Axiom thisAxiom : c.getClassAxioms()) {
				thisAxiom.setActive(false);
			}
			//And remove relationships that have come from an axiom
			c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.BOTH)
				.stream()
				.forEach(r -> c.removeRelationship(r, true));   //Safe to remove these if published.
		}
	}
	

	protected void selfGroupAttributes(Task t, Concept c) {
		RelationshipGroup ungrouped = c.getRelationshipGroup(CharacteristicType.STATED_RELATIONSHIP, UNGROUPED);
		if (ungrouped != null) {
			for (Relationship r : ungrouped.getRelationships()) {
				for (Concept selfGrouped : selfGroupedAttributes) {
					if (r.getType().equals(selfGrouped)) {
						r.setGroupId(SnomedUtils.getFirstFreeGroup(c));
					}
				}
			}
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
	
	protected int deleteConcept(Task t, Concept c) throws TermServerScriptException {
		try {
			LOGGER.debug((dryRun ?"Dry run deleting ":"Deleting ") + c );
			if (!dryRun) {
				tsClient.deleteConcept(c.getConceptId(), t.getBranchPath());
			}
			return CHANGE_MADE;
		} catch (Exception e) {
			report (t, c, Severity.MEDIUM, ReportActionType.API_ERROR, "Failed to delete concept due to " + e.getMessage());
			return NO_CHANGES_MADE;
		}
	}

	protected int deleteDescription(Task t, Description d) throws TermServerScriptException {
		try {
			LOGGER.debug((dryRun ?"Dry run deleting ":"Deleting ") + d );
			if (!dryRun) {
				tsClient.deleteDescription(d.getId(), t.getBranchPath());
			}
			return CHANGE_MADE;
		} catch (Exception e) {
			report (t, d, Severity.MEDIUM, ReportActionType.API_ERROR, "Failed to delete concept due to " + e.getMessage());
			return NO_CHANGES_MADE;
		}
	}
	
	protected int removeRefsetMember(Task t, Concept c, RefsetMember r, String info) throws TermServerScriptException {
		if (r.isReleased()) {
			r.setActive(false);
			report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_INACTIVATED, r, info);
			if (!dryRun) {
				tsClient.updateRefsetMember(t.getBranchPath(), r, false); //Don't force delete
			}
		} else {
			report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_DELETED, r, info);
			deleteRefsetMember(t, r.getId());
		}
		return CHANGE_MADE;
	}
	
	protected int deleteRefsetMember(Task t, String uuid) throws TermServerScriptException {
		return deleteRefsetMember(t, uuid, false); //Don't force! (by default anyway)
	}
	
	protected int deleteRefsetMember(Task t, String uuid, boolean force) throws TermServerScriptException {
		try {
			LOGGER.debug((dryRun ?"Dry run deleting ":"Deleting ") + uuid );
			if (!dryRun) {
				tsClient.deleteRefsetMember(uuid, t.getBranchPath(), force); 
			}
			return CHANGE_MADE;
		} catch (Exception e) {
			report (t, null, Severity.MEDIUM, ReportActionType.API_ERROR, "Failed to delete refset member " + uuid + " due to " + e.getMessage());
			return NO_CHANGES_MADE;
		}
	}
	
	protected int updateRefsetMember(Task t, RefsetMember r, String info) throws TermServerScriptException {
		LOGGER.debug((dryRun? "Dry run ":"") + "Updating state of " + r + info);
		if (!dryRun) {
			tsClient.updateRefsetMember(t.getBranchPath(), r, false); //Don't force delete
		}
		return CHANGE_MADE;
	}
	
	public Collection<Concept> findConcepts(String ecl) throws TermServerScriptException {
		String branch = overrideEclBranch == null ? project.getBranchPath() : overrideEclBranch;
		return findConcepts(branch, ecl, false, true, CharacteristicType.INFERRED_RELATIONSHIP);
	}

	public Collection<Concept> findConceptsWithoutEffectiveTime(String ecl) throws TermServerScriptException {
		Collection<Concept> concepts = findConcepts(ecl);
		concepts.removeIf(Concept::hasEffectiveTime);

		return concepts;
	}
	
	public Collection<RefsetMember> findRefsetMembers(List<Concept> refCompIds, String refsetFilter) throws TermServerScriptException {
		return tsClient.findRefsetMembers(project.getBranchPath(), refCompIds, refsetFilter);
	}
	
	public int getConceptsCount(String ecl) throws TermServerScriptException {
		return tsClient.getConceptsCount(ecl, project.getBranchPath());
	}
	
	public Collection<Concept> findConceptsSafely(String ecl) {
		return findConceptsSafely(ecl, null);
	}
		
	
	public Collection<Concept> findConceptsSafely(String ecl, String info) {
		try {
			return findConcepts(ecl, true, true);
		} catch (Exception e) {
			LOGGER.error("Exception while recovering " + ecl + 
			info == null ? "" : " in " + info +
			". Skipping.", e);
		}
		return new HashSet<>();
	}
	
	public Collection<Concept> findConcepts(String ecl, boolean quiet, CharacteristicType charType) throws TermServerScriptException {
		return findConcepts(project.getBranchPath(), ecl, quiet, true, charType);
	}
	
	public Collection<Concept> findConcepts(String ecl, boolean quiet) throws TermServerScriptException {
		return findConcepts(ecl, quiet, true);
	}
	
	public Collection<Concept> findConcepts(String branch, String ecl) throws TermServerScriptException {
		return findConcepts(branch, ecl, true, false, CharacteristicType.INFERRED_RELATIONSHIP);  //Don't use local store when some other branch specified
	}
	
	public Collection<Concept> findConcepts(String ecl, boolean quiet, boolean useLocalStoreIfSimple) throws TermServerScriptException {
		return findConcepts(project.getBranchPath(), ecl, quiet, useLocalStoreIfSimple, CharacteristicType.INFERRED_RELATIONSHIP);
	}
	
	public Collection<Concept> findConcepts(String branch, String ecl, boolean quiet, boolean useLocalStoreIfSimple, CharacteristicType charType) throws TermServerScriptException {
		
		//If we're working from a zip file, then use MAIN instead
		//unless the ECL is simple, in which case we can use that directly from memory
		if (!EclCache.isSimple(ecl) && (branch == null || branch.endsWith(".zip"))) {
			String historicECLBranch = "MAIN";
			if (branch != null) {
				//TODO Better regex to work out the correct branch for historic ECL
				if (branch.contains("20200731")) {
					historicECLBranch = "MAIN/2020-07-31";
				} else if (branch.contains("2021-01-31") || branch.contains("20210131")) {
					historicECLBranch = "MAIN/2021-01-31";
				}
			}
			
			if (!archiveEclWarningGiven.contains(branch)) {
				LOGGER.warn("Not using " + branch + " to recover ECL.  Using " + historicECLBranch + " instead.");
				archiveEclWarningGiven.add(branch);
			}
			branch = historicECLBranch;
		}
		
		EclCache cache = EclCache.getCache(branch, tsClient, gson, gl, quiet, charType);
		boolean wasCached = cache.isCached(ecl);
		Collection<Concept> concepts = cache.findConcepts(branch, ecl, useLocalStoreIfSimple); 

		//If this is the first time we've seen these results, check for duplicates
		if (!wasCached) {
			LOGGER.debug(concepts.size() + " concepts recovered.  Checking for duplicates...");
			//Failure in the pagination can cause duplicates.  Check for this
			Set<Concept> uniqConcepts = new HashSet<>(concepts);
			if (uniqConcepts.size() != concepts.size()) {
				LOGGER.warn("Duplicates detected " + concepts.size() + " vs " + uniqConcepts.size() + " - identifying...");
				//Work out what the actual duplication is
				for (Concept c : uniqConcepts) {
					concepts.remove(c);
				}
				for (Concept c : concepts) {
					LOGGER.warn("Duplicate concept received from ECL: " + c);
				}
				throw new TermServerScriptException(concepts.size() + " duplicate concepts returned from ecl: " + ecl + " eg " + concepts.iterator().next());
			}
			LOGGER.debug("No duplicates detected.");
		}
		return concepts; 
	}
	
	/*protected Set<Concept> findConceptsByCriteria(String eclType, String criteria, String branch, boolean useLocalCopies) throws TermServerScriptException {
		Set<Concept> allConcepts = new HashSet<>();
		boolean allRecovered = false;
		String searchAfter = null;
		int totalRecovered = 0;
		while (!allRecovered) {
			try {
					ConceptCollection collection = tsClient.getConceptsMatchingCriteria(eclType, criteria, branch, searchAfter, PAGING_LIMIT);
					totalRecovered += collection.getItems().size();
					if (searchAfter == null) {
						//First time round, report how many we're receiving.
						TermServerScript.debug ("Recovering " + collection.getTotal() + " concepts on " + branch + " matching criteria: '" + criteria +"'");
					}
					
					if (useLocalCopies) {
						//Recover our locally held copy of these concepts so that we have the full hierarchy populated
						List<Concept> localCopies = collection.getItems().stream()
								.map(c -> gl.getConceptSafely(c.getId()))
								.collect(Collectors.toList());
						allConcepts.addAll(localCopies);
					} else {
						allConcepts.addAll(collection.getItems());
					}
					
					//If we've counted more concepts than we currently have, then some duplicates have been lost in the 
					//add to the set
					if (totalRecovered > allConcepts.size()) {
						TermServerScript.warn ("Duplicates detected");
					}
					
					//Did we get all the concepts that there are?
					if (totalRecovered < collection.getTotal()) {
						searchAfter = collection.getSearchAfter();
						if (searchAfter == null) {
							throw new TermServerScriptException("More concepts to recover, but TS did not populate the searchAfter field");
						}
					} else {
						allRecovered = true;
					}
			} catch (Exception e) {
				throw new TermServerScriptException("Failed to recover concepts matching criteria '" + criteria + "' due to " + e.getMessage(),e);
			}
		}
		return allConcepts;
	}
*/
	protected List<Component> processFile() throws TermServerScriptException {
		if (getInputFile() == null) {
			throw new TermServerScriptException("Unable to process file as no file specified!  Check -f parameter has been supplied, or alternatively, an ECL selection.");
		}
		return processFile(getInputFile());
	}
	
	protected List<Component> processFile(File file) throws TermServerScriptException {
		Set<Component> allComponents= new LinkedHashSet<>();
		LOGGER.debug("Loading input file " + file.getAbsolutePath());
		try {
			List<String> lines = Files.readLines(file, Charsets.UTF_8);
			lines = StringUtils.removeBlankLines(lines);
			
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

						if (components != null && components.size() > 0) {
							allComponents.addAll(components);
						} else {
							if (!expectNullConcepts) {
								LOGGER.debug("Skipped line " + lineNum + ": '" + lines.get(lineNum) + "', malformed or not required?");
							}
						}
					} catch (Exception e) {
						throw new TermServerScriptException("Failed to load line " + lineNum + ": '" + lines.get(lineNum) + "' due to ",e);
					}
				} else {
					LOGGER.debug("Skipping blank line " + lineNum);
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
	
	protected void reportSafely (int reportIdx, Concept c, Object... details) {
		try {
			report(reportIdx, c, details);
		} catch (TermServerScriptException e) {
			throw new IllegalStateException("Failed to write to report", e);
		}
	}

	public void finish() throws TermServerScriptException {
		LOGGER.info(BREAK);
		List<String> reportLast = new ArrayList<String>(Arrays.asList("Issue count", "Report lines written"));
		
		List<String> criticalIssues = new ArrayList<String>();
		for (Map.Entry<String, Object> summaryDetail : summaryDetails.entrySet()) {
			String key = summaryDetail.getKey();
			if (reportLast.contains(key)) {
				continue;
			}
			Object value = summaryDetail.getValue();
			String display = "";
			if (value != null) {
				if (value instanceof Collection) {
					display += ((Collection<?>)value).size();
				} else if (key.startsWith(CRITICAL_ISSUE)) {
					criticalIssues.add(key + ": " + value.toString());
					continue;
				} else {
					display = value.toString();
				}
			}
			recordSummaryText (key + (display.isEmpty()?"":": ") + display);
		}
		if (summaryDetails.containsKey("Tasks created") && summaryDetails.containsKey(CONCEPTS_TO_PROCESS) ) {
			if (summaryDetails.get(CONCEPTS_TO_PROCESS) != null &&  summaryDetails.get(CONCEPTS_TO_PROCESS) instanceof Collection) {
				double c = (double)((Collection<?>)summaryDetails.get(CONCEPTS_TO_PROCESS)).size();
				double t = (double)((Integer)summaryDetails.get("Tasks created")).intValue();
				double avg = Math.round((c/t) * 10) / 10.0;
				recordSummaryText("Concepts per task: " + avg);
			}
		}
		
		if (criticalIssues.size() > 0) {
			recordSummaryText("\nCritical Issues Encountered (" + criticalIssues.size() + ")\n========================");
			for (String thisCriticalIssue : criticalIssues) {
				recordSummaryText(thisCriticalIssue);
			}
			recordSummaryText("Total Critical Issues Encountered: " + criticalIssues.size());
		}
		
		if (summaryTabIdx != NOT_SET) {
			recordSummaryText("");
			recordSummaryText("");
		}
		
		for (String key : reportLast) {
			if (summaryDetails.containsKey(key)) {
				String display = summaryDetails.get(key).toString();
				recordSummaryText(key + (display.isEmpty()?"":": ") + display);
			}
		}
		
		Date endTime = new Date();
		if (startTime != null) {
			long diff = endTime.getTime() - startTime.getTime();
			recordSummaryText("Completed processing in " + DurationFormatUtils.formatDuration(diff, "HH:mm:ss"));
			recordSummaryText("Started at: " + startTime);
		}
		//Allow report to have some final word before completing.  Override if required
		recordFinalWords();

		recordSummaryText("Finished at: " + endTime);
		if (getReportManager() != null) {
			recordSummaryText("Processing Report URL: " + getReportManager().getUrl());
		}
		LOGGER.info(BREAK);
		
		flushFiles(false);
	}

	protected void recordFinalWords() throws TermServerScriptException {
		//Override in base class if required
	}

	private synchronized void recordSummaryText(String msg) {
		LOGGER.info(msg);
		if (getReportManager() != null) {
			if (summaryTabIdx != NOT_SET) {
				try {
					//Split the colon into it's own column (unless it's a time stamp!)
					if (msg.contains(":") 
							&& !msg.contains("http")
							&& !msg.contains("at: ")
							&& !msg.contains("\"")
							&& !msg.contains("Completed processing in")) {
						msg = QUOTE + msg.replaceAll(": ", QUOTE_COMMA_QUOTE).replaceAll(":", QUOTE_COMMA_QUOTE) + QUOTE;
					}
					writeToReportFile(summaryTabIdx, msg);
				} catch (Exception e) {
					LOGGER.error("Failed to write summary info: " + msg, e);
				}
			}
		} else {
			LOGGER.info("Unable to report: " + msg);
		}
	}
	
	protected void writeToRF2File(String fileName, Object[] columns) throws TermServerScriptException {
		if (StringUtils.isEmpty(fileName)) {
			throw new TermServerScriptException("Request to write to RF2 file with no filename specified");
		}
		//if (dryRun) {
		//	debug("Dry run. Live run would write to " + fileName + " : " + columns);
		//} else {
			getRF2Manager().writeToRF2File(fileName, columns);
		//}
	}
	
	protected void writeToRF2File(String fileName, String line) throws TermServerScriptException {
		if (StringUtils.isEmpty(fileName)) {
			throw new TermServerScriptException("Request to write to RF2 file with no filename specified");
		}
		getRF2Manager().writeToRF2File(fileName, line);
	}
	
	public String getReportName() {
		if (reportName == null) {
			String fileName = "";
			if (!ignoreInputFileForReportName && hasInputFile(0)) {
				fileName = SnomedUtils.deconstructFilename(getInputFile())[1];
			}
			String spacer = " ";
			reportName = getScriptName() + (fileName.isEmpty()?"" : spacer + fileName);
			try {
				if (subHierarchy == null && subHierarchyStr != null && !subHierarchyStr.contains(ROOT_CONCEPT.getConceptId())) {
					subHierarchy = gl.getConcept(subHierarchyStr);
				}
				
				if (subHierarchy != null && !subHierarchy.equals(ROOT_CONCEPT)) {
					reportName += spacer + subHierarchy.toStringPref();
				}
				
				if (subHierarchy == null && subHierarchyStr == null && subsetECL != null) {
					//Take the first focus concept
					int cutPoint = subsetECL.indexOf(":");
					if (cutPoint > NOT_SET) {
						int potentialCut = subsetECL.indexOf("MINUS");
						if (potentialCut > NOT_SET && potentialCut < cutPoint) {
							cutPoint = potentialCut;
						}
						reportName += spacer + subsetECL.subSequence(0, cutPoint);
					} else {
						if (subsetECL.equals("*")) {
							reportName += spacer + "All_Concepts";
						} else {
							Concept simpleECLRoot = gl.getConcept(subsetECL.replaceAll("<", "").trim());
							if (simpleECLRoot.getDescriptions().size() > 0) {
								reportName += spacer + simpleECLRoot.toStringPref();
							} else {
								reportName += spacer + SnomedUtils.deconstructFSN(simpleECLRoot.getFsn())[0];
							}
						}
					}
				}
				
				if (jobRun != null && !StringUtils.isEmpty(jobRun.getTask())) {
					reportName += "_" + jobRun.getTask();
				} else if (project != null && !StringUtils.isEmpty(project.getKey())) {
					reportName += "_" + project.getKey();
				}
				
			} catch (Exception e) {
				LOGGER.error("Recoverable hiccup while setting report name",e);
			}
		}
		
		if (reportName.contains("null")) {
			LOGGER.warn("Report name contains 'null' did you specify to load FSNs only?");
		}
		
		return reportName;
	}

	protected String getPrettyHistoricalAssociation (Concept c) throws TermServerScriptException {
		String prettyString = "No association specified.";
		if (c.getAssociationEntries(ActiveState.ACTIVE).size() > 0) {
			prettyString = " ";
			for (AssociationEntry assoc : c.getAssociationEntries(ActiveState.ACTIVE)) {
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
		d.setCaseSignificance(StringUtils.calculateCaseSignificance(term));
		d.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(acceptability, dialects));
		d.setConceptId(concept.getConceptId());
		concept.addDescription(d);
	}
	
	public void report (Task t, Component c, ValidationFailure v) throws TermServerScriptException {
		report(t, c, v.severity, v.reportActionType, v.getMessage());
	}

	public void reportLoud(Task task, Component component, Severity severity, ReportActionType actionType, Object... details) throws TermServerScriptException {
		setQuiet(false);
		report(task, component, severity, actionType, details);
		setQuiet(true);
	}
	
	public void report(Task task, Component component, Severity severity, ReportActionType actionType, Object... details) throws TermServerScriptException {
		if (quiet) {
			return;
		}

		//If we're using BatchFix methods in a non-BatchFix script, we'll use a more usual report method.
		if (task == null && !(this instanceof BatchFix) && component != null && component instanceof Concept) {
			report(PRIMARY_REPORT, (Concept)component, severity, actionType, details);
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
							if (detail != null && !detail.toString().isEmpty()) {
								value += ", " + detail;
							}
						}
					}
				}
				addSummaryInformation(key, value);
				LOGGER.info( key + " : " + value);
			}
		}
		String key = (task == null? "" :  task.getKey());
		String desc = (task == null? "" :  task.getSummary());
		String name = (component == null ? "" : component.getReportedName());
		String type = (component == null ? "" : component.getReportedType());
		String id = (component == null ? "" : component.getId());
		StringBuffer sb = new StringBuffer();

		sb.append(key + COMMA + desc + COMMA + id + COMMA_QUOTE)
				.append(name + QUOTE_COMMA);
		if (stateComponentType) {
			sb.append(type + COMMA );
		}

		sb.append( severity + COMMA + actionType );
		for (Object detail : details) {
			if (detail == null) {
				detail = "";
			}
			
			if (detail instanceof Object[]) {
				Object[] arr = (Object[]) detail;
				for (Object obj : arr) {
					sb.append(COMMA_QUOTE + (obj==null?"":obj) + QUOTE);
				}
			} else {
				sb.append(COMMA_QUOTE + detail + QUOTE);
			}
		}
		writeToReportFile(sb.toString());
		incrementSummaryInformation("Report lines written");
	}

	protected boolean report (Concept c, Object...details) throws TermServerScriptException {
		return report(PRIMARY_REPORT, c, details);
	}
	
	public boolean report (int reportIdx, Concept c, Object...details) throws TermServerScriptException {
		if (quiet) {
			return false;
		}

		//Have we whiteListed this concept?
		if (!ignoreWhiteList && c != null && whiteListedConceptIds.contains(c.getId())) {
			String detailsStr = writeToString(details);
			LOGGER.warn("Ignoring whiteListed concept: " + c + " :  " + detailsStr);
			incrementSummaryInformation(WHITE_LISTED_COUNT);
			return false;
		} else {
			String[] conceptFields = new String[3];
			if (reportNullConcept || c != null) {
				conceptFields[0] = c == null?"": QUOTE + c.getConceptId() + QUOTE;
				conceptFields[1] = c == null?"":c.getFsn();
				
				if (c != null && !StringUtils.isEmpty(c.getFsn())) {
					conceptFields[2] = SnomedUtils.deconstructFSN(c.getFsn())[1];
					if (conceptFields[2] == null) {
						conceptFields[2] = " ";
					}
				} else {
					conceptFields[2] = "";
				}
			}
			report(reportIdx, conceptFields, details);
		}
		return true;
	}
	
	protected void countIssue(Concept c) {
		countIssue(c, 1);
	}
	
	protected void countIssue(Concept c, int increment) {
		if (c==null || !whiteListedConceptIds.contains(c.getId())) {
			incrementSummaryInformation(ISSUE_COUNT, increment);
		}
	}

	protected List<Concept> determineProximalPrimitiveParents(Concept c) throws TermServerScriptException {
		//Filter for only the primitive ancestors
		//Sort to work with the lowest level concepts first for efficiency
		List<Concept> primitiveAncestors = gl.getAncestorsCache().getAncestors(c).stream()
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
	
	public void setEnv(String env) {
		this.env = env;
	}

	public GraphLoader getGraphLoader() {
		return gl;
	}

	public TermServerClient getTSClient() {
		return tsClient;
	}

	public ArchiveManager getArchiveManager() {
		return ArchiveManager.getArchiveManager(this, appContext);
	}
	
	public ArchiveManager getArchiveManager(boolean forceReuse) {
		return ArchiveManager.getArchiveManager(this, appContext, forceReuse);
	}
	
	public boolean hasInputFile() {
		return hasInputFile(0);
	}
	
	public boolean hasInputFile(int n) {
		return inputFiles.get(n) != null;
	}

	public File getInputFile() {
		if (getInputFile(0) == null) {
			throw new IllegalArgumentException("No file specified for process.  Check the -f command line argument");
		}
		return getInputFile(0);
	}
	
	public File getInputFile(int idx) {
		return inputFiles.get(idx);
	}

	public File getInputFileOrThrow(int idx) {
		File file = getInputFile(idx);
		if (file == null) {
			throw new IllegalArgumentException("No input file specified with index: " + idx);
		}
		return file;
	}

	public void setInputFile(int idx, File file) throws TermServerScriptException {
		//Allow Dummy file for basic sequential integer SCTID Generators.
		if (!file.getName().equals("Dummy") && 
				(!file.canRead() || (!file.isFile() && !allowDirectoryInputFile))) {
			throw new TermServerScriptException("Unable to read specified file: " + file);
		}
		inputFiles.set(idx, file);
	}

	public void setExclusions(String[] exclusions) throws TermServerScriptException {
		this.excludeHierarchies = exclusions;
	}
	
	public Integer countAttributes(Concept c, CharacteristicType cType) {
		int attributeCount = 0;
		for (Relationship r : c.getRelationships(cType, ActiveState.ACTIVE)) {
			if (!r.getType().equals(IS_A)) {
				attributeCount++;
			}
		}
		return attributeCount;
	}

	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null;
	}

	public JobRun getJobRun() {
		return jobRun;
	}
	
	public void setReportName(String reportName) {
		this.reportName = reportName;
	}
	
	public void offlineMode(boolean offline) {
		if (offline) {
			getArchiveManager().setAllowStaleData(true);
		}
		this.offlineMode = offline;
	}
	
	public boolean isOffline() {
		return this.offlineMode;
	}

	public static void runHeadless(Integer envNum) {
		headlessEnvironment = envNum;
	}

	protected boolean inScope(Component c) {
		return inScope(c, true);
	}

	protected boolean inScope(Component c, boolean includeExpectedExtensionModules) {
		//RP-349 Allow MS customers to run reports against MAIN.
		//In this case all concepts are "in scope" to allow MS customers to see
		//what changes to international concepts might affect them
		if (project.getKey().equals("MAIN")) {
			return true;
		}
		//Do we have a default module id ie for a managed service project?
		if (project.getMetadata() != null && project.getMetadata().getDefaultModuleId() != null) {
			//We really need to be sure that expectedExtensionModules has been populated, 
			//because CH and NO will have content in multiple modules
			if (project.getMetadata().getExpectedExtensionModules() == null) {
				if (allowMissingExpectedModules) {
					return c.getModuleId().equals(project.getMetadata().getDefaultModuleId());
				} else {
					throw new IllegalArgumentException("Extension does not have expectedExtensionModules metadata populated.  Cannot continue.");
				}
			}
			if (includeExpectedExtensionModules) {
				return project.getMetadata().getExpectedExtensionModules().contains(c.getModuleId());
			} else {
				return c.getModuleId().equals(project.getMetadata().getDefaultModuleId());
			}
		}
		return true;
	}
	
	protected boolean isMS() {
		//Do we have a default module id ie for a managed service project?
		if (project.getMetadata() != null && project.getMetadata().getDefaultModuleId() != null) {
			return !project.getMetadata().getDefaultModuleId().equals(SCTID_CORE_MODULE);
		}
		return false;
	}
	
	public static void run(Class<? extends JobClass> jobClazz, String[] args, Map<String, String> parameters) throws TermServerScriptException {
		JobRun jobRun = createJobRunFromArgs(jobClazz.getSimpleName(), args);
		if (parameters != null) {
			for (Map.Entry<String, String> entry : parameters.entrySet()) {
				jobRun.setParameter(entry.getKey(), entry.getValue());
			}
		}
		JobClass job = null;
		try {
			job = jobClazz.getDeclaredConstructor((Class<?>[])null).newInstance((Object[])null);
		} catch ( InstantiationException |
				IllegalArgumentException |
				SecurityException |
				NoSuchMethodException |
				InvocationTargetException |
				IllegalAccessException e) {
			throw new TermServerScriptException("Unable to instantiate " + jobClazz.getSimpleName(), e);
		} 
		job.instantiate(jobRun, (ApplicationContext)null);
	}
	
	public static void run(Class<? extends JobClass> jobClazz, Map<String, Object> parameters, String[] args) throws TermServerScriptException {
		JobRun jobRun = createJobRunFromArgs(jobClazz.getSimpleName(), args);
		if (parameters != null) {
			for (Map.Entry<String, Object> entry : parameters.entrySet()) {
				jobRun.setParameter(entry.getKey(), entry.getValue());
			}
		}
		JobClass job;
		try {
			job = jobClazz.getDeclaredConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
			throw new TermServerScriptException("Unable to instantiate " + jobClazz.getSimpleName(), e);
		}
		job.instantiate(jobRun, null);
	}

	protected String getDependencyArchive() {
		return dependencyArchive;
	}
	
	protected void setDependencyArchive(String dependencyArchive) {
		this.dependencyArchive = dependencyArchive;
		if (dependencyArchive != null) {
			getArchiveManager(true).setLoadDependencyPlusExtensionArchive(true);
		}
	}

	public ReportDataBroker getReportDataUploader() throws TermServerScriptException {
		if (reportDataBroker == null) {
			if (appContext == null) {
				LOGGER.info("No ReportDataUploader loader configured, creating one locally...");
				reportDataBroker = ReportDataBroker.create();
			} else {
				reportDataBroker = appContext.getBean(ReportDataBroker.class);
			}
		}
		//Share our gson - they're expensive
		reportDataBroker.setGson(gson);
		return reportDataBroker;
	}

	// This is used for reports that might want to return a complex name
	// i.e say two released so r1-r2 (so we have projects/branches and now a complex name)
	// It is only used Summary Component as we are not dealing with just a simple name (different releases)
	public String getReportComplexName() {
		// default is nothing.
		return "";
	}

	public ReportConfiguration getReportConfiguration() {
		return reportConfiguration;
	}

	public AuthoringServicesClient getAuthoringServicesClient() {
		return scaClient;
	}

	public String getServerUrl() {
		return url;
	}
	
	public void setServerUrl(String url) {
		this.url = url;
	}

	synchronized public void asyncSnapshotCacheInProgress(boolean asyncSnapshotCacheInProgress) {
		this.asyncSnapshotCacheInProgress  = asyncSnapshotCacheInProgress;
	}
	
	public List<Component> getConceptsInReview() throws TermServerScriptException {
		LOGGER.info("Recovering list of review concepts from " + project.getBranchPath());
		Review review = tsClient.getReview(project.getBranchPath());
		if (true);
		return review.getChangedConcepts().stream()
				.map(s -> gl.getConceptSafely(s.toString()))
				.collect(Collectors.toList());
	}
	
	public Concept getReplacementSafely(int reportTabIdx, Object context, Concept inactiveConcept, boolean isIsA) {
		try {
			return getReplacement(reportTabIdx, context, inactiveConcept, isIsA);
		} catch (TermServerScriptException e) {
			LOGGER.error("Failed to find a replacement for {}", inactiveConcept, e);
		}
		return null;
	}

	public Concept getReplacementSafely(List<String> notes, Object context, Concept inactiveConcept, boolean isIsA) {
		try {
			return getReplacement(notes, context, inactiveConcept, isIsA);
		} catch (TermServerScriptException e) {
			notes.add(e.getMessage());
			LOGGER.error("Failed to find a replacement for {}", inactiveConcept, e);
		}
		return null;
	}

	protected Concept getReplacement(List<String> notes, Object context, Concept inactiveConcept, boolean isIsA) throws TermServerScriptException {
		Set<String> assocs = new HashSet<>(inactiveConcept.getAssociationTargets().getReplacedBy());
		assocs.addAll(inactiveConcept.getAssociationTargets().getAlternatives());
		assocs.addAll(inactiveConcept.getAssociationTargets().getPossEquivTo());
		assocs.addAll(inactiveConcept.getAssociationTargets().getPartEquivTo());
		assocs.addAll(inactiveConcept.getAssociationTargets().getSameAs());
		if (assocs.size() == 0) {
			if (isIsA) {
				//We'll try and carry on without this parent.
				return null;
			}
			throw new TermServerScriptException("Unable to find replacement for " + inactiveConcept + " due to " + assocs.size() + " associations");
		} else {
			if(assocs.size() > 1){
				String assocStr = inactiveConcept.getAssociationTargets().toString(gl);
				notes.add("Multiple HistAssocs available for "  + inactiveConcept + ". Replacement chosen at random.  Please specify to hardcode choice");
				notes.add(assocStr);
			}
			return  gl.getConcept(assocs.iterator().next());
		}
	}
	
	protected Concept getReplacement(int reportTabIdx, Object context, Concept inactiveConcept, boolean isIsA) throws TermServerScriptException {
		List<String> notes = new ArrayList<>();
		Concept replacement = getReplacement(notes, context, inactiveConcept, isIsA);
		for (String note : notes) {
			if (context instanceof Concept) {
				report((Concept)context, Severity.HIGH, ReportActionType.VALIDATION_CHECK, note);
			} else {
				report(reportTabIdx, "", context, note);
			}
		}
		return replacement;
	}

	public boolean getAsyncSnapshotCacheInProgress() {
		return asyncSnapshotCacheInProgress;
	}

	public void addFinalWords(String msg) {
		finalWords.add(msg);
	}

	public void restateInferredRelationships(Concept c) throws TermServerScriptException {
		//Work through all inferred groups and collect any that aren't also stated, to state
		List<RelationshipGroup> toBeStated = new ArrayList<>();
		Collection<RelationshipGroup> inferredGroups = c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP);
		Collection<RelationshipGroup> statedGroups = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP);

		nextInferredGroup:
		for (RelationshipGroup inferredGroup : inferredGroups) {
			boolean matchFound = false;
			for (RelationshipGroup statedGroup : statedGroups) {
				if (inferredGroup.equals(statedGroup)) {
					matchFound = true;
					continue nextInferredGroup;
				}
			}
			if (!matchFound) {
				toBeStated.add(inferredGroup);
			}
		}
		stateRelationshipGroups(c, toBeStated);
	}

	private int stateRelationshipGroups(Concept c, List<RelationshipGroup> toBeStated) throws TermServerScriptException {
		int changesMade = 0;
		for (RelationshipGroup g : toBeStated) {
			//Group 0 must remain group 0.  Otherwise find an available group number
			int freeGroup = g.getGroupId()==0?0:SnomedUtils.getFirstFreeGroup(c);
			changesMade += stateRelationshipGroup(c, g, freeGroup);
		}
		return changesMade;
	}

	private int stateRelationshipGroup(Concept c, RelationshipGroup g, int freeGroup) throws TermServerScriptException {
		int changesMade = 0;
		AxiomEntry axiom = null;
		//Does c already have an axiom we can merge these relationships into?
		if (c.getAxiomEntries(ActiveState.ACTIVE, false).size() > 0) {
			axiom = c.getAxiomEntries(ActiveState.ACTIVE, false).iterator().next();
		}

		for (Relationship r : g.getRelationships()) {
			Relationship newRel = r.clone(null);
			newRel.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
			newRel.setGroupId(freeGroup);
			newRel.setAxiomEntry(axiom);
			changesMade += replaceRelationship((Task)null, c, newRel.getType(), newRel.getTarget(), newRel.getConcreteValue(), newRel.getGroupId(), RelationshipTemplate.Mode.PERMISSIVE, false);
		}
		return changesMade;
	}

	protected int replaceRelationship(Task t, Concept c, Concept type, Concept value, int groupId, RelationshipTemplate.Mode mode) throws TermServerScriptException {
		return replaceRelationship(t, c, type, value, (ConcreteValue)null, groupId, mode);
	}

	protected int replaceRelationship(Task t, Concept c, Concept type, Concept value, ConcreteValue concreteValue, int groupId, RelationshipTemplate.Mode mode) throws TermServerScriptException {
		return replaceRelationship(t, c, type, value, concreteValue, groupId, mode, true);
	}

	protected int replaceRelationship(Task t, Concept c, Concept type, Concept value, ConcreteValue concreteValue, int groupId, RelationshipTemplate.Mode mode, boolean reportAlreadyExisting) throws TermServerScriptException {
		int changesMade = 0;
		if (type == null || (value == null && concreteValue == null)) {
			if (value == null && concreteValue == null) {
				String msg = "Unable to add relationship of type " + type + " due to lack of a value concept / concrete value";
				report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, msg);
			} else if (type == null) {
				String msg = "Unable to add relationship with value " + value + " due to lack of a type concept";
				report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, msg);
			}
			return NO_CHANGES_MADE;
		}
		//Do we already have this relationship active in the target group (or at all if self grouped)?
		Set<Relationship> rels = findExistingRelationships(c, type, value, concreteValue, groupId, ActiveState.ACTIVE);
		if (rels.size() > 1) {
			report(t, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Found two active relationships for " + type + " -> " + value);
			return NO_CHANGES_MADE;
		} else if (rels.size() == 1) {
			if (reportAlreadyExisting) {
				report(t, c, Severity.LOW, ReportActionType.NO_CHANGE, "Active relationship already exists ", rels.iterator().next());
			}
			return NO_CHANGES_MADE;
		}

		//Do we have it inactive?
		rels = findExistingRelationships(c, type, value, concreteValue, groupId, ActiveState.INACTIVE);
		if (rels.size() >= 1) {
			Relationship rel = rels.iterator().next();
			report (t, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_REACTIVATED, rel);
			rel.setActive(true);
			return CHANGE_MADE;
		}

		//Or do we need to create and add?
		//Is this type (or type/value) unique for the concept
		//or (new feature) do we want to replace any attributes of the same type if they exist
		if (mode == RelationshipTemplate.Mode.UNIQUE_TYPE_ACROSS_ALL_GROUPS ||
				mode == RelationshipTemplate.Mode.REPLACE_TYPE_IN_THIS_GROUP) {
			rels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
					type,
					ActiveState.ACTIVE);
			if (rels.size() > 0) {
				if (mode == RelationshipTemplate.Mode.UNIQUE_TYPE_ACROSS_ALL_GROUPS) {
					report (t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, type + " attribute type already exists: " + rels.iterator().next());
					return changesMade;
				} else {
					//Removing existing relationships of the same type, but only in this group
					rels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
							type,
							groupId);
					for (Relationship remove : rels) {
						changesMade += removeRelationship(t, c, remove);
					}
				}
			}
		} else if (mode == RelationshipTemplate.Mode.UNIQUE_TYPE_VALUE_ACROSS_ALL_GROUPS) {
			RelationshipTemplate rt = new RelationshipTemplate(type,value);
			rels = c.getRelationships(rt, ActiveState.ACTIVE);
			if (rels.size() > 0) {
				report(t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Attribute type/value already exists: " + rels.iterator().next());
				return changesMade;
			}
		} else if (mode == RelationshipTemplate.Mode.UNIQUE_TYPE_IN_THIS_GROUP) {
			RelationshipTemplate rt = new RelationshipTemplate(type,value);
			RelationshipGroup g = c.getRelationshipGroup(CharacteristicType.STATED_RELATIONSHIP, groupId);
			rels = g.getRelationshipsWithType(rt.getType());
			if (rels.size() > 0) {
				report(t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Attribute type already exists in specified group: " + rels.iterator().next());
				return changesMade;
			}
		}

		//Add the new relationship
		if (groupId == SELFGROUPED) {
			groupId = SnomedUtils.getFirstFreeGroup(c);
		}
		Relationship newRel = new Relationship (c, type, value, groupId);
		//Copying relationships from elsewhere indicates they have not been released in their current condition
		newRel.setReleased(false);
		newRel.setDirty();
		report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, newRel);
		c.addRelationship(newRel);
		changesMade++;
		return changesMade;
	}

	private Set<Relationship> findExistingRelationships(Concept c, Concept type, Concept value, ConcreteValue concreteValue, int groupId, ActiveState activeState) {
		Set<Relationship> rels;
		if (concreteValue == null) {
			if (groupId == SELFGROUPED) {
				rels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
						type,
						value,
						activeState);
			} else {
				rels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
						type,
						value,
						groupId,
						activeState);
			}
		} else {
			if (groupId == SELFGROUPED) {
				rels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
						type,
						concreteValue,
						activeState);
			} else {
				rels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
						type,
						concreteValue,
						groupId,
						activeState);
			}
		}
		return rels;
	}

	protected int removeRelationship(Task t, Concept c, Relationship r) throws TermServerScriptException {
		return removeRelationship(t, c, r, "");
	}

	protected int removeRelationshipGroup(Task t, Concept c, RelationshipGroup g) throws TermServerScriptException {
		int changesMade = 0;
		for (Relationship r : g.getRelationships()) {
			changesMade += removeRelationship(t, c, r, "");
		}
		return changesMade;
	}

	protected int removeRelationship(Task t, Concept c, Relationship r, String reasonPrefix) throws TermServerScriptException {
		//Are we inactivating or deleting this relationship?
		ReportActionType action = ReportActionType.UNKNOWN;

		if (!r.fromAxiom() && r.isReleased() == null) {
			throw new TermServerScriptException("Attempted to remove Relationship " + r + " with no released status");
		}
		//Since stated relationsips aren't really inactivated, if this rel has come from an
		//axiom, we can just say it's deleted
		if (r.fromAxiom() || !r.isReleased()) {
			r.setActive(false);
			c.removeRelationship(r);
			action = ReportActionType.RELATIONSHIP_DELETED;
		} else {
			c.inactivateRelationship(r);
			action = ReportActionType.RELATIONSHIP_INACTIVATED;
		}
		report(t, c, Severity.LOW, action, reasonPrefix + r);
		return CHANGE_MADE;
	}

	protected int removeRedundandGroups(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		List<RelationshipGroup> originalGroups = new ArrayList<>(c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP));
		Set<RelationshipGroup> removedGroups = new HashSet<>();

		for (RelationshipGroup originalGroup : originalGroups) {
			if (removedGroups.contains(originalGroup) || originalGroup.size() == 0) {
				continue;
			}
			for (RelationshipGroup potentialRedundancy : originalGroups) {
				//Don't compare self, removed or empty groups
				if (originalGroup.getGroupId() == potentialRedundancy.getGroupId() ||
						potentialRedundancy.size() == 0 ||
						removedGroups.contains(potentialRedundancy)) {
					continue;
				}
				boolean aCoversB = SnomedUtils.covers(originalGroup, potentialRedundancy, gl.getAncestorsCache());
				boolean bCoversA = SnomedUtils.covers(potentialRedundancy, originalGroup, gl.getAncestorsCache());
				RelationshipGroup groupToRemove = null;
				if (aCoversB || bCoversA) {
					//If they're the same, remove the potential - likely to be a higher group number
					if (aCoversB && bCoversA && potentialRedundancy.size() <= originalGroup.size()) {
						groupToRemove = potentialRedundancy;
					} else if (aCoversB && potentialRedundancy.size() <= originalGroup.size()) {
						groupToRemove = potentialRedundancy;
					} else if (bCoversA && potentialRedundancy.size() >= originalGroup.size()) {
						groupToRemove = originalGroup;
					} else if (bCoversA && potentialRedundancy.size() < originalGroup.size()) {
						report (t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Group of larger size appears redundant - check!");
						groupToRemove = originalGroup;
					} else {
						LOGGER.warn ("DEBUG HERE, Redundancy in " + c);
					}

					if (groupToRemove != null && groupToRemove.size() > 0) {
						removedGroups.add(groupToRemove);
						report (t, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_GROUP_REMOVED, "Redundant relationship group removed:", groupToRemove);
						for (Relationship r : groupToRemove.getRelationships()) {
							changesMade += removeRelationship(t, c, r);
						}
					}
				}
			}
		}
		if (changesMade > 0) {
			shuffleDown(t,c);
			for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
				report (t, c, Severity.LOW, ReportActionType.INFO, "Post redundancy removal group", g);
			}
		}
		return changesMade;
	}

	protected void shuffleDown(Task t, Concept c) throws TermServerScriptException {
		List<RelationshipGroup> newGroups = new ArrayList<>();
		for (RelationshipGroup group : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			//Have we missed out the ungrouped group? fill in if so
			if (group.isGrouped() && newGroups.size() == 0) {
				newGroups.add(new RelationshipGroup(UNGROUPED));
			}
			//Since we're working with the true concept relationships here, this will have
			//the effect of changing the groupId in all affected relationships
			if (group.getGroupId() != newGroups.size()) {
				report (t, c, Severity.MEDIUM, ReportActionType.INFO, "Shuffling stated group " + group.getGroupId() + " to " + newGroups.size());
				group.setGroupId(newGroups.size());
				group.setDirty();
				//If we have relationships without SCTIDs here, see if we can pinch them from inactive relationships
				int reuseCount = 0;
				for (Relationship moved : new ArrayList<>(group.getRelationships())) {
					if (StringUtils.isEmpty(moved.getId())) {
						Set<Relationship> existingInactives = c.getRelationships(moved, ActiveState.INACTIVE);
						if (existingInactives.size() > 0) {
							group.removeRelationship(moved);
							c.removeRelationship(moved, true);  //It's OK to force removal, the axiom will still exist.
							Relationship reuse = existingInactives.iterator().next();
							reuse.setActive(true);
							reuse.setDirty();
							group.addRelationship(reuse);
							c.addRelationship(reuse);
							reuseCount++;
						}
					}
				}

				if (reuseCount > 0) {
					report(t, c, Severity.MEDIUM, ReportActionType.INFO, "Reused " + reuseCount + " inactivated ids");
				}
			}
			newGroups.add(group);
		}
	}
}
