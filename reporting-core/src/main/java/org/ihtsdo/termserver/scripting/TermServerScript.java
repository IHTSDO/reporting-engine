package org.ihtsdo.termserver.scripting;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.dao.ReportDataBroker;

import org.apache.commons.lang.time.DurationFormatUtils;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.authoringservices.AuthoringServicesClient;
import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.dao.ResourceDataLoader;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.domain.Branch;
import org.ihtsdo.termserver.scripting.domain.ConcreteValue;
import org.ihtsdo.termserver.scripting.domain.AssociationEntry;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate.Mode;
import org.ihtsdo.termserver.scripting.snapshot.ArchiveManager;
import org.ihtsdo.termserver.scripting.snapshot.SnapshotConfiguration;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.Script;
import org.snomed.otf.script.dao.ReportConfiguration;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.springframework.context.ApplicationContext;

import com.google.common.base.CharMatcher;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public abstract class TermServerScript extends Script implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(TermServerScript.class);
	/* ======================================================
	 * Public Constants
	 * ====================================================== */

	public static final String COMMAND_LINE_USAGE = "Usage: java <VM_ARGUMENTS> <TSScriptClass> " +
			"[-a author] " +
			"[-c <authenticatedCookie>] " +
			"[-d <Y/N>] " +
			"[-f <batch file Location>] " +
			"[-m <modules>] " +
			"[-n <taskSize>] " +
			"[-p <projectName>] " +
			"[-dp <dependency package(s) - comma separated>] " +
			"[-r2 <restart position>] " +
			"[-headless <env_number>] " +
			"[-task <taskKey>]" +
			"[-s <secondary server Url>]";

	public static final String AUTHOR = "Author";
	public static final String CONCEPTS_IN_FILE = "Concepts in file";
	public static final String CONCEPTS_PER_TASK = "Concepts per task";
	public static final String CONCEPTS_TO_PROCESS = "Concepts to process";
	public static final String CRITICAL_ISSUE = "CRITICAL ISSUE";
	public static final String DRY_RUN = "Dry Run";
	public static final String ECL = "ECL";
	public static final String EXCEPTION_ENCOUNTERED = "Exception encountered";
	public static final String EXPECTED_PROTOCOL = "https://";
	public static final String INCLUDE_ALL_LEGACY_ISSUES = "Include All Legacy Issues";
	public static final String INPUT_FILE = "InputFile";
	public static final String ISSUE_COUNT = "Issue count";
	public static final String ISSUES = "Issues";
	public static final String MAIN_SLASH = "MAIN/";
	public static final String MODULES = "Modules";
	public static final String NEW_CONCEPTS_ONLY = "New Concepts Only";
	public static final String RESTART_FROM_TASK = "Restart from task";
	public static final String RUN_HEADLESS = "Run Headless";
	public static final String SUB_HIERARCHY = "Subhierarchy";
	public static final String TEMPLATE = "Template";
	public static final String TEMPLATE2 = "Template 2";
	public static final String TEMPLATE_NAME = "TemplateName";
	public static final String UNPROMOTED_CHANGES_ONLY = "Unpromoted Changes Only";
	public static final String WHITE_LISTED_COUNT = "White Listed Count";

	/* ======================================================
	 * Private Static Fields
	 * ====================================================== */
	private static final String DUE_TO_STR = " due to ";
	private static final String DELETING = "Deleting {}";
	private static final String DRY_DELETING = "Dry run deleting {}";

	private static final List<String> integrityCheckIgnoreList = List.of(
			"21000241105", // |Common French language reference set|
			"763158003" // |Medicinal product (product)| Gets created as a constant, but does exist before 20180731
	);

	/* ======================================================
	 * Public Static Fields
	 * ====================================================== */
	public static String inputFileDelimiter = TSV_FIELD_DELIMITER;

	/* ======================================================
	 * Private Instance Fields
	 * ====================================================== */

	private List<String> dependencyArchives;
	private String reportName;
	private boolean loadingRelease = false;
	private boolean ignoreInputFileForReportName = false;
	private final List<String> finalWords = new ArrayList<>();

	/* ======================================================
	 * Core Runtime Components
	 * ====================================================== */
	protected GraphLoader gl = GraphLoader.getGraphLoader();
	protected TermServerClient tsClient;
	protected AuthoringServicesClient scaClient;
	protected ReportDataBroker reportDataBroker;
	protected String secondaryServerUrl;

	private Map<String, Map<String, Integer>> summaryCountsByCategory = new HashMap<>();
	//Secondary counts might be whitelisted items for same items as the main count
	private Map<String, Map<String, Integer>> secondaryCountsByCategory = new HashMap<>();
	protected boolean includeSecondaryCounts = false;

	/* ======================================================
	 * Job / Execution Context
	 * ====================================================== */
	protected JobRun jobRun;
	protected String url = getEnvironments()[0];
	protected int envIndex = NOT_SET;
	protected String authenticatedCookie;
	protected Integer headlessEnvironment = null;
	protected boolean runStandAlone = false;
	protected boolean offlineMode = false;
	protected boolean localClientsRequired = true;

	/* ======================================================
	 * Processing Configuration
	 * ====================================================== */
	protected boolean debug = true;
	protected boolean dryRun = true;
	protected boolean validateConceptOnUpdate = true;
	protected boolean reportAllDescriptions = false;
	protected boolean reportNullConcept = true;
	protected boolean expectNullConcepts = false;
	protected boolean expectStatedRelationshipInactivations = false;
	protected boolean reportChangesWithoutTask = true;
	protected boolean ignoreWhiteList = false;

	protected int maxFailures = 5;
	protected int restartPosition = NOT_SET;
	protected int processingLimit = NOT_SET;
	protected int tabForFinalWords = PRIMARY_REPORT;
	protected int summaryTabIdx = NOT_SET;

	protected boolean inputFileHasHeaderRow = false;
	protected boolean allowDirectoryInputFile = false;

	/* ======================================================
	 * Concept Selection / Filtering
	 * ====================================================== */
	protected boolean stateComponentType = true;
	protected boolean scriptRequiresSnomedData = true;
	protected boolean allowMissingExpectedModules = false;

	protected String subHierarchyStr;
	protected String subsetECL;
	protected String overrideEclBranch = null;

	protected Concept subHierarchy;
	protected List<Concept> excludeHierarchies = new ArrayList<>();
	protected List<String> moduleFilter;

	/* ======================================================
	 * Input / Batch Processing
	 * ====================================================== */
	protected List<File> inputFiles = new ArrayList<>(Collections.nCopies(10, (File) null));

	/* ======================================================
	 * Reporting
	 * ====================================================== */
	protected String headers = "Concept SCTID,";
	protected String additionalReportColumns = "ActionDetail, AdditionalDetail, ";
	protected String secondaryReportColumns = "ActionDetail, ";

	/* ======================================================
	 * Tracking / State Collections
	 * ====================================================== */
	protected Set<String> whiteListedConceptIds = new HashSet<>();
	protected Set<String> archiveEclWarningGiven = new HashSet<>();

	/* ======================================================
	 * Misc
	 * ====================================================== */
	public Scanner STDIN = new Scanner(System.in);
	protected String projectName;
	protected SnapshotConfiguration snapshotConfiguration = new SnapshotConfiguration();

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

	protected void setDryRun(boolean b) {
		dryRun = b;
	}

	protected void setReportAllDescriptions(boolean b) {
		reportAllDescriptions = b;
	}

	public String detectReleaseBranch(String projectKey) {
		String releaseBranch = projectKey.replace(MAIN_SLASH, "").replace("-", "");
		return StringUtils.isNumeric(releaseBranch) ? releaseBranch : null;
	}

	public String getScriptName() {
		return this.getClass().getSimpleName();
	}
	
	public String getAuthenticatedCookie() {
		return authenticatedCookie;
	}
	
	public void setAuthenticatedCookie(String authenticatedCookie) {
		this.authenticatedCookie = authenticatedCookie;
	}


	private static final String ENVIRONMENTS_FILE = "resources/environments.txt";

	private static String[] envKeys;
	private static String[] environments;
	private static int envProd;

	//Environments are not known publicly, so are loaded on demand from a local, gitignored file
	//rather than being hard-coded here. See ENVIRONMENTS_FILE.
	private static synchronized void loadEnvironmentsIfRequired() {
		if (environments != null) {
			return;
		}
		File environmentsFile = new File(ENVIRONMENTS_FILE);
		List<String> urls = new ArrayList<>();
		List<String> keys = new ArrayList<>();
		try {
			for (String line : Files.readLines(environmentsFile, StandardCharsets.UTF_8)) {
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				String[] parts = line.split("\\s+");
				urls.add(parts[0]);
				keys.add(parts[1]);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read " + environmentsFile.getPath(), e);
		}
		environments = urls.toArray(new String[0]);
		envKeys = keys.toArray(new String[0]);
		envProd = environments.length - 1;
	}

	protected static String[] getEnvironments() {
		loadEnvironmentsIfRequired();
		return environments;
	}

	protected static String[] getEnvKeys() {
		loadEnvironmentsIfRequired();
		return envKeys;
	}

	protected static int getEnvProd() {
		loadEnvironmentsIfRequired();
		return envProd;
	}

	protected void init(String[] args) throws TermServerScriptException {
		
		if (args.length < 2) {
			println("Usage: java <TSScriptClass> [-a author] [-n <taskSize>] [-r <restart position>] [-c <authenticatedCookie>] [-d <Y/N>] [-p <projectName>] [-f <batch file Location>] [-dp <dependency file(s) - comma separate>] [--config <configuration string>]");
			println(" d - dry run");
			System.exit(-1);
		}
		boolean unknownParameterExpected = false;
		for (int x=0; x< args.length; x++) {
			String thisArg = args[x];
			if (thisArg.equals("-p")) {
				projectName = args[x+1];
			} else if (thisArg.equals("-c")) {
				authenticatedCookie = args[x+1];
			} else if (thisArg.equals("-d")) {
				dryRun = args[x+1].equalsIgnoreCase("Y");
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
				if (!getInputFile(fileIdx).canRead()) {
					if (!getInputFile(fileIdx).getName().toLowerCase().contains("dummy")) {
						throw new TermServerScriptException("Unable to read input file: " + thisFile);
					}
				} else {
					LOGGER.info("Reading data (fileIdx {}) from {}", fileIdx, thisFile.getAbsolutePath());
				}
			} else if (thisArg.equals("-r")) {
				restartPosition = Integer.parseInt(args[x+1]);
			} else if (thisArg.equals("-dp")) {
				String dependencyArchiveStr = args[x+1];
				dependencyArchives = List.of(dependencyArchiveStr.split(","));
			} else if (thisArg.equals("-task") || thisArg.equals("--task")) {
				taskKey = args[x+1];
			} else if (thisArg.equals("-s") || thisArg.equals("--server")) {
				secondaryServerUrl = args[x+1];
			} else {
				//Some parameters are defined in base classes like deltaGenerator so we can ignore those
				if (List.of("-iC", "-iD", "-iP").contains(thisArg)) {
					unknownParameterExpected = true;
				} else if (unknownParameterExpected) {
					unknownParameterExpected = false;
				} else {
					LOGGER.warn("Ignoring unknown argument: {}", thisArg);
				}
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
		
		if (localClientsRequired) {
			initialiseSnomedServiceClients();
		}

		//Recover the full project path from authoring services, if not already fully specified
		if (project == null) {
			recoverProjectFromProjectName(projectName);
		} else {
			LOGGER.warn("Project already set as {}", project);
		}
		
		if (taskKey != null) {
			project.setBranchPath(project.getBranchPath() + "/" + taskKey);
		}
		
		if (!loadingRelease) {
			LOGGER.info("Full path for project {} determined to be: {}", project.getKey(), project.getBranchPath());
			//If we're loading a CodeSystem eg MAIN/SNOMEDCT-SE then we will have to recover the metadata from the branch instead
			if (project.getMetadata() == null) {
				Branch branch = tsClient.getBranch(project.getBranchPath());
				project.setMetadata(branch.getMetadata());
			}
		}
		
		// Configure the type(s) and locations(s) for processing report output.
		initialiseReportConfiguration(jobRun);
	}

	protected void initialiseSnomedServiceClients() throws TermServerScriptException {
		scaClient = new AuthoringServicesClient(url, authenticatedCookie);
		tsClient = createTSClient(this.url, authenticatedCookie);
	}

	public void recoverProjectFromProjectName(String projectName) throws TermServerScriptException {
		project = new Project();
		if (projectName.startsWith("MAIN")) {
			project.setBranchPath(projectName);
			if (projectName.equals("MAIN")) {
				project.setKey(projectName);
			} else {
				project.setKey(projectName.substring(projectName.lastIndexOf("/")));
			}
		} else if (StringUtils.isNumeric(projectName) || projectName.endsWith(".zip")) {
			LOGGER.info("Script configured to use release: {}", projectName);
			loadingRelease = true;
			project.setKey(projectName);
		} else {
			if (runStandAlone) {
				LOGGER.info("Running stand alone. Guessing project path to be MAIN/{}", projectName);
				project.setBranchPath(MAIN_SLASH + projectName);
			} else {
				try {
					project = scaClient.getProject(projectName, true);
					LOGGER.info("Recovered project {} with branch path: {}", project.getKey(), project.getBranchPath());
				} catch (RestClientException e) {
					throw new TermServerScriptException("Unable to recover project: " + projectName,e);
				}
			}
			project.setKey(projectName);
		}
	}

	protected void checkSettingsWithUser(JobRun jobRun) throws TermServerScriptException {
		determineEnvironment(false);

		if (jobRun != null) {
			//Not sure historically why we have this in two places
			jobRun.setTerminologyServerUrl(url);
		}
	
		if (jobRun != null && !jobRun.getAuthToken().isEmpty()) {
			authenticatedCookie = jobRun.getAuthToken();
		} else if (authenticatedCookie == null || authenticatedCookie.trim().isEmpty()) {
			print("Please enter your authenticated cookie for connection to " + url + " : ");
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

	public void determineEnvironment(boolean needNewCookie) {
		if (headlessEnvironment != null) {
			envIndex = headlessEnvironment;
		} else {
			println("Select an environment ");
			for (int i=0; i < getEnvironments().length; i++) {
				println("  " + i + ": " + getEnvironments()[i]);
			}

			print("Choice [" + getEnvProd() +"]: ");
			String choice = STDIN.nextLine().trim();
			if (choice.isEmpty()) {
				envIndex = getEnvProd();
			}  else {
				envIndex = Integer.parseInt(choice);
			}
		}
		url = getEnvironments()[envIndex];
		setEnv(getEnvKeys()[envIndex]);

		if (needNewCookie) {
			print("New cookie required: ");
			setAuthenticatedCookie(STDIN.nextLine().trim());
		}
	}

	protected void init (JobRun jobRun) throws TermServerScriptException {
		ApplicationContext context = getApplicationContext();
		if (context != null) {
			ResourceDataLoader resourceDataLoader = context.getBean(ResourceDataLoader.class);
			LOGGER.debug("ResourceDataLoader {} initialisation complete", resourceDataLoader.getInitalisationConfirmation());
		}
		this.url = jobRun.getTerminologyServerUrl();
		setEnv(getEnv(url));
		this.jobRun = jobRun;
		EclCache.reset();
		authenticatedCookie = jobRun.getAuthToken();
		recoverCommonParametersFromJobRun();
		init();
		recoverProjectDetails();
		LOGGER.info("Init Complete. Project Key determined: {} on {}", project.getKey(), project.getBranchPath());
	}

	private void recoverProjectDetails() throws TermServerScriptException {
		if (projectName.equals("MAIN") || projectName.startsWith(MAIN_SLASH)) {
			//MAIN is not a project.  Recover Main metadata from branch
			project.setMetadata(tsClient.getBranch(projectName).getMetadata());
		} else if (!StringUtils.isNumeric(projectName) && !projectName.endsWith(".zip")) {
			//Not if we're loading a release or extension
			try {
				int retry = 0;
				boolean ok = false;
				while (!ok && retry < 3) {
					boolean lastChance = retry == 2;
					ok = recoverProjectDetailsFromTS(lastChance);
					retry++;
				}
			} catch (InterruptedException | TermServerScriptException e) {
				throw new TermServerScriptException("Failed to recover project " + projectName, e);
			}
		}
	}

	private boolean recoverProjectDetailsFromTS(boolean lastChance) throws InterruptedException, TermServerScriptException {
		try {
			project = scaClient.getProject(projectName);
			//Are we in fact running against a task?
			if (jobRun != null && !StringUtils.isEmpty(jobRun.getTask())) {
				String taskBranchPath = project.getBranchPath() + "/" + jobRun.getTask();
				project.setBranchPath(taskBranchPath);
			} else if (taskKey != null) {
				String taskBranchPath = project.getBranchPath() + "/" + taskKey;
				project.setBranchPath(taskBranchPath);
			}
			return true;
		} catch (Exception e) {
			//No need to retry if we get a 403.  //No need to sleep if this was our last chance
			String exceptionMsg = ExceptionUtils.getExceptionCause("Unable to recover project", e) + " Retrying after short nap.";
			if (!exceptionMsg.contains("403") && !lastChance) {
				System.err.println(exceptionMsg);
				Thread.sleep(1000 * 10);
			} else {
				throw new TermServerScriptException("Failed to recover project " + projectName, e);
			}
		}
		return false;
	}

	private void recoverCommonParametersFromJobRun() throws TermServerScriptException {
		if (jobRun != null && !StringUtils.isEmpty(jobRun.getProject())) {
			projectName = jobRun.getProject();
		} else if ((jobRun == null && projectName == null) ||
				(jobRun != null && StringUtils.isEmpty(jobRun.getProject()))) {
			LOGGER.warn("No project specified, running against MAIN");
			projectName = "MAIN";
		}

		if (authenticatedCookie == null || authenticatedCookie.trim().isEmpty()) {
			throw new TermServerScriptException("Unable to proceed without an authenticated token/cookie");
		}

		if (jobRun == null) {
			return;
		}

		if (!StringUtils.isEmpty(jobRun.getParamValue(SUB_HIERARCHY))) {
			subHierarchy = gl.getConcept(jobRun.getParamValue(SUB_HIERARCHY));
		}

		if (!StringUtils.isEmpty(jobRun.getParamValue(DRY_RUN))) {
			dryRun = jobRun.getParamBoolean(DRY_RUN);
		}

		if (!StringUtils.isEmpty(jobRun.getParamValue(ECL))) {
			subsetECL =jobRun.getParamValue(ECL);
		}

		String modulesStr = jobRun.getParamValue(MODULES);
		if (!StringUtils.isEmpty(modulesStr)) {
			LOGGER.info("Filtering output to modules: {}", modulesStr);
			moduleFilter = Stream.of(modulesStr.split(",", -1))
					.map(String::trim)
					.toList();
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

	public void postInit(String googleFolder, String[] tabNames, String[] columnHeadings, boolean csvOutput) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId(googleFolder);
		postInit(tabNames, columnHeadings, csvOutput);
	}

	//Need to ensure that we don't end up calling a derivative version of this method and get into a loop
	//Descendant classes should override the version that does not take the csvOutput
	@Override
	public final void postInit(String[] tabNames, String[] columnHeadings, boolean csvOutput) throws TermServerScriptException {
		if (jobRun != null && jobRun.getParamValue(SUB_HIERARCHY) != null) {
			subHierarchy = gl.getConcept(jobRun.getMandatoryParamValue(SUB_HIERARCHY));
			//RP-4 And post that back in, so the FSN is always populated
			jobRun.setParameter(SUB_HIERARCHY, subHierarchy.toString());
		}

		int integrityIssuesTabId = NOT_SET;
		if (!gl.getIntegrityWarnings().isEmpty()) {
			integrityIssuesTabId = columnHeadings.length;
			if (tabNames == null) {
				tabNames = new String[columnHeadings.length];
				for (int i = 0; i < columnHeadings.length; i++) {
					tabNames[i] = "Tab " + (i + 1);
				}
			}
			tabNames = Arrays.copyOf(tabNames, tabNames.length + 1);
			tabNames[integrityIssuesTabId] = "Snapshot Integrity Issues";
			columnHeadings = Arrays.copyOf(columnHeadings, columnHeadings.length + 1);
			columnHeadings[integrityIssuesTabId] = "SCTID, Issue, Refset, RefsetMember";
		}

		super.postInit(tabNames, columnHeadings, csvOutput);

		if (integrityIssuesTabId != NOT_SET) {
			reportIntegrityWarnings(integrityIssuesTabId);
		}
	}

	private void reportIntegrityWarnings(int tabId) throws TermServerScriptException {
		for (List<Object> warning : gl.getIntegrityWarnings()) {
			report(tabId, warning.toArray());
		}
	}
	
	public void instantiate(JobRun jobRun, ApplicationContext appContext) {
		try {
			LOGGER.debug("Instantiating {} to process request for {}", this.getClass().getName(), jobRun.getJobName());
			LOGGER.debug("Application context has {}been supplied{}", (appContext == null?"not " : ""), (appContext == null?", hopefully running on a developer's local machine!" : "."));
			this.appContext = appContext;
			this.jobRun = jobRun;
			if (jobRun.getDependencyPackage() != null) {
				this.dependencyArchives = List.of(jobRun.getDependencyPackage());
			}

			// GraphLoader gl is a singleton, reset its state at the beginning of each report's execution to avoid settings corruption
			gl.reset();

			//Job Runs generally self determine
			preInit();
			
			//Are we running locally?
			if (appContext == null) {
				checkSettingsWithUser(jobRun);
			}

			init(jobRun);

			if (scriptRequiresSnomedData) {
				//Are we already specified as using an archive?
				if (project.getKey().endsWith(".zip")) {
					getSnapshotConfiguration().setSource(project.getKey());
				} else {
					getSnapshotConfiguration().setSource(project.getBranchPath());
					getSnapshotConfiguration().setKey(project.getKey());
				}
				loadProjectSnapshot();
			}

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
			doFinalTidyUp();
		}
	}

	private void doFinalTidyUp() {
		try {
			if (!finalWords.isEmpty()) {
				report(tabForFinalWords, "");
				report(tabForFinalWords, "", "***********************************");
				report(tabForFinalWords, "");
				for (String finalMsg : finalWords) {
					report(tabForFinalWords, finalMsg);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Exception while writing final words", e);
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
				Thread.sleep(5 * 1000L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				LOGGER.error(EXCEPTION_ENCOUNTERED,e);
			}
		}

		try {
			flushFiles(true);
		} catch (TermServerScriptException e) {
			//We tried
		}
	}

	protected void preInit() throws TermServerScriptException {
		//Override this method in concrete class to set flags that affect checkSettingsWithUser
		//like selfDetermining = true;
	}

	public void runJob () throws TermServerScriptException {
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
				case "-dp":
					jobRun.setDependencyPackage(parameter);
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
				case "-l":
					//These parameters will get picked up by batch fix processing
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
				case "--config":
					jobRun.setAdditionalConfig(parameter);
					break;
				case "-headless":
					jobRun.setParameter(RUN_HEADLESS,Integer.valueOf(parameter));
					break;
				default:
					LOGGER.error(COMMAND_LINE_USAGE);
					throw new IllegalArgumentException("Unknown parameter: " + args[argNumber] + " " + parameter);
			}
			argNumber += 2;
		}

		return jobRun;
	}
	
	protected TermServerClient createTSClient(String url, String authenticatedCookie) throws TermServerScriptException {
		if (!authenticatedCookie.contains("ihtsdo=")) {
			throw new IllegalArgumentException("Malformed cookie detected.  Expected <env>-ihtsdo=<token> instead received: " + authenticatedCookie);
		}
		String contextPath = "snowstorm/snomed-ct";
		return new TermServerClient(url + contextPath, authenticatedCookie, getUserAgent());
	}

	protected void loadProjectSnapshot() throws TermServerScriptException {
		//Watch that this call duplicates what happens in instantiate.  Can we pull these together?
		if (project.getKey().endsWith(".zip")) {
			getSnapshotConfiguration().setSource(project.getKey());
		} else {
			getSnapshotConfiguration().setSource(project.getBranchPath());
			getSnapshotConfiguration().setKey(project.getKey());
		}

		getArchiveManager().loadSnapshot(this, getSnapshotConfiguration());

		if (getSnapshotConfiguration().isRunIntegrityChecks()) {
			LOGGER.info("Running snapshot integrity checks");
			runIntegrityChecks(true);
		}
		//Reset the report name to null here as it will have been set by the Snapshot Generator
		setReportName(null);
	}

	protected Concept loadConcept(String sctid, String branchPath) throws TermServerScriptException {
		if (dryRun && getTaskKey() == null) {
			//In a dry run situation, the task branch is not created so use the Project instead
			//But we'll clone it, so the object isn't confused with any local changes

			//That said, if we've specified an _existing_ task, then we do want to use that.  So check for a taskKey
			
			//If we're already working at project level, don't modify branchPath
			//Note that for MS we expect two slashes eginstantiate( MAIN/SNOMEDCT-SE/SE
			if (branchPath.contains("SNOMEDCT-") && CharMatcher.is('/').countIn(branchPath) == 2) {
				//debug ("MS Project detected as branch path: " + branchPath);
			} else if (branchPath.indexOf("/") != branchPath.lastIndexOf("/")) {
				branchPath = branchPath.substring(0, branchPath.lastIndexOf("/"));
			}
			if (runStandAlone) {
				LOGGER.debug("Loading: {} from local store", gl.getConcept(sctid));
				return gl.getConcept(sctid).cloneWithIds();
			}
		}
		return loadConcept(tsClient, sctid, branchPath);
	}
	
	protected Concept loadConcept(Concept concept, String branchPath) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept.getConceptId(), branchPath);
		//Detect any attempt to load a deleted concept
		if (loadedConcept == null || StringUtils.isEmpty(loadedConcept.getConceptId())) {
			return null;
		}
		loadedConcept.setConceptType(concept.getConceptType());
		if (!dryRun) {
			//The loaded concept has some idea of the preferred term.  We'll have that now
			concept.setPreferredSynonym(loadedConcept.getPreferredSynonym());
		}
		//In any event, copy any issues over from the cached concept to the loaded one
		loadedConcept.setIssues(concept.getIssueList());
		return loadedConcept;
	}
	
	protected Concept loadConcept(TermServerClient client, Concept concept, String branchPath) throws TermServerScriptException {
			return loadConcept(client, concept.getConceptId(), branchPath);
	}
	
	protected Concept loadConcept(TermServerClient client, String sctId, String branchPath) throws TermServerScriptException {
		Concept concept =  gl.getConcept(sctId);
		try {
			LOGGER.debug("Loading: {} from TS branch {}", concept, branchPath);
			Concept loadedConcept = client.getConcept(sctId, branchPath);
			loadedConcept.setLoaded(true);
			convertAxiomsToRelationships(loadedConcept, loadedConcept.getClassAxioms());
			convertAxiomsToRelationships(loadedConcept, loadedConcept.getAdditionalAxioms());
			return loadedConcept;
		} catch (Exception e) {
			if (e.getMessage() != null && e.getMessage().contains("[404] Not Found") 
					|| e.getMessage().contains("404 Not Found")
					|| e.getMessage().contains("NOT_FOUND")) {
				LOGGER.debug("Unable to find {} on branch {}", concept, branchPath);
				return null;
			}
			String msg =  e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			throw new TermServerScriptException("Failed to recover " + concept + " from TS branch " + branchPath + ", due to: " + msg,e);
		}
	}
	
	protected RefsetMember loadRefsetMember(String uuid) {
		return loadRefsetMember(uuid, project.getBranchPath());
	}
	
	protected RefsetMember loadRefsetMember(String uuid, String branch) {
		LOGGER.debug("Loading refset member {} from {}", uuid, branch);
		return tsClient.getRefsetMember(uuid, branch);
	}
	
	protected LangRefsetEntry loadLangRefsetMember(String uuid, String branch) {
		LOGGER.debug("Loading langrefset member {} from {}", uuid, branch);
		return tsClient.getLangRefsetMember(uuid, branch);
	}
	
	protected RefsetMember loadPreviousRefsetMember(String uuid) {
		if (project.getPreviousBranchPath() == null) {
			String previousBranchPath = getArchiveManager().getPreviousBranch();
			project.setPreviousBranchPath(previousBranchPath);
		}
		return loadRefsetMember(uuid, project.getPreviousBranchPath());
	}
	
	protected RefsetMember updateRefsetMember(RefsetMember rm) throws TermServerScriptException {
		String debugTemplate = (dryRun?"Dry run update of":"Updating") + " refset member {}";
		LOGGER.debug(debugTemplate, rm.getId());
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
				
				LOGGER.debug("Updating state of {}{} on branch {}", c, (info == null?"":info), t.getBranchPath());
				return tsClient.updateConcept(c, t.getBranchPath());
			} catch (ValidationFailure e) {
				throw e;
			} catch (Exception e) {
				String excpStr =  e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
				String msg = "Failed to update " + c + " in TS due to " + excpStr;
				LOGGER.info("{} JSON = {}", msg, gson.toJson(c));
				throw new TermServerScriptException(msg,e); 
			}
		} 
	}
	
	private void validateConcept(Task t, Concept c) throws TermServerScriptException {
		//We need to populate new components with UUIDs for validation
		Concept uuidClone = c.cloneWithUUIDs();
		LOGGER.debug("Validating {}", c);
		
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
			LOGGER.debug("Validation clear: {}", c);
		} else {
			Set<String> warningsReported = new HashSet<>();
			LOGGER.debug("Validation issues: {}", validations.length);
			for (DroolsResponse response : validations) {
				if (response.getSeverity().equals(DroolsResponse.Severity.ERROR)) {
					throw new ValidationFailure(t,  c, "Drools error: " + response.getMessage() + " on " + response.getComponentId());
				} else if (response.getSeverity().equals(DroolsResponse.Severity.WARNING)) {
					//Only report a particular warning text once
					if (!warningsReported.contains(response.getMessage())) {
						report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Drools warning: " + response.getMessage());
						warningsReported.add(response.getMessage());
					}
				} else {
					throw new IllegalStateException("Unexpected drools response: " + response);
				}
			}
		}
	}

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
					LOGGER.warn("{} retrying...", msg);
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
		if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.BOTH).isEmpty()) {
			return;
		}
		
		//In the case of an inactive concept, we'll inactivate any axioms
		if (c.isActiveSafely()) {
			convertActiveConcept(c, mergeExistingAxioms, leaveStatedRelationships);
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

	private void convertActiveConcept(Concept c, boolean mergeExistingAxioms, boolean leaveStatedRelationships) {
		for (Axiom a : c.getClassAxioms()) {
			a.clearRelationships();
		}
		
		//Do we have an existing axiom to use by default?
		Axiom a = c.getFirstActiveClassAxiom();
		a.setModuleId(c.getModuleId());
		
		//If we're working with local concepts, remove any Axiom Entries and pinch their UUID
		if (a.getId() == null && !c.getAxiomEntries().isEmpty()) {
			for (AxiomEntry ae : c.getAxiomEntries()) {
				if (ae.isActiveSafely()) {
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
			convertStatedRelationship(c, a, rel, mergeExistingAxioms, leaveStatedRelationships);
		}
		
		removeEmptyAxioms(c);
	}

	private void removeEmptyAxioms(Concept c) {
		for (Axiom thisAxiom : new ArrayList<>(c.getClassAxioms())) {
			if (thisAxiom.getRelationships().isEmpty()) {
				//Has this axiom been released?  Remove if not and if it's empty
				if (StringUtils.isEmpty(thisAxiom.getId())) {
					c.getClassAxioms().remove(thisAxiom);
				} else {
					throw new IllegalStateException ("Axiom left with no relationships in " + c + ": " + thisAxiom);
				}
			}
		}
		
	}

	private void convertStatedRelationship(Concept c, Axiom a, Relationship rel, boolean mergeExistingAxioms, boolean leaveStatedRelationships) {
		if (handleInactiveRelationship(c, a, rel, leaveStatedRelationships)) {
			return;
		}

		Axiom thisAxiom  = a; 
		if (!mergeExistingAxioms) {
			thisAxiom = rel.getAxiom() == null ? a : rel.getAxiom();
		}
		
		//The definition status of the axiom needs to match that of the concept
		thisAxiom.setDefinitionStatus(c.getDefinitionStatus());
		
		//Don't add an inactive relationship to an active axiom
		if (!thisAxiom.isActive().equals(rel.isActive())) {
			if (!rel.isActiveSafely()) {
				LOGGER.warn("Skipping axiomification of {} due to active axiom", rel);
			} else {
				throw new IllegalStateException ("Active stated conflict between " + rel + " and " + thisAxiom);
			}
		}
		thisAxiom.getRelationships().add(rel);
		if (!rel.fromAxiom() && !rel.isActiveSafely()) {
			//Historically inactive stated relationship, leave it be
		} else if (!leaveStatedRelationships) {
			c.removeRelationship(rel, true);  //Safe to remove it even if published - will exist in axiom
		}
	}

	private boolean handleInactiveRelationship(Concept c, Axiom a, Relationship rel, boolean leaveStatedRelationships) {
		//Ignore inactive rels, unless they come from an inactive axiom, in which case leave them there
		if (!rel.isActiveSafely()) {
			//...unless it came from an axiom in which case it's no longer required
			//and causes confusion for a validation check due to having no effective time
			if (rel.getAxiom() != null) {
				if (!rel.getAxiom().isActiveSafely()) {
					rel.getAxiom().getRelationships().add(rel);
				}

				if (leaveStatedRelationships) {
					rel.setAxiom(a);
				} else {
					c.removeRelationship(rel, true); //Safe to remove it even if published - will exist in axiom
				}
			}
			return true;
		}
		return false;
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

	protected int deleteConcept(Task t, Concept c) throws TermServerScriptException {
		try {
			LOGGER.debug((dryRun ?DRY_DELETING:DELETING), c);
			if (!dryRun) {
				tsClient.deleteConcept(c.getConceptId(), t.getBranchPath());
			}
			return CHANGE_MADE;
		} catch (Exception e) {
			report(t, c, Severity.MEDIUM, ReportActionType.API_ERROR, "Failed to delete concept due to " + e.getMessage());
			return NO_CHANGES_MADE;
		}
	}

	protected int deleteRelationship(Task t, Relationship r) throws TermServerScriptException {
		try {
			LOGGER.debug((dryRun ?DRY_DELETING:DELETING), r);
			if (!dryRun) {
				tsClient.deleteRelationship(r.getRelationshipId(), t.getBranchPath());
			}
			report(t, r.getSource(), Severity.LOW, ReportActionType.RELATIONSHIP_DELETED, r);
			return CHANGE_MADE;
		} catch (Exception e) {
			report(t, r.getSource(), Severity.MEDIUM, ReportActionType.API_ERROR, "Failed to delete relationship " + r.getId() + DUE_TO_STR + e.getMessage());
			return NO_CHANGES_MADE;
		}
	}

	protected int deleteDescription(Task t, Description d) throws TermServerScriptException {
		try {
			LOGGER.debug((dryRun ?DRY_DELETING:DELETING), d);
			if (!dryRun) {
				tsClient.deleteDescription(d.getId(), t.getBranchPath());
			}
			return CHANGE_MADE;
		} catch (Exception e) {
			report(t, d, Severity.MEDIUM, ReportActionType.API_ERROR, "Failed to delete concept due to " + e.getMessage());
			return NO_CHANGES_MADE;
		}
	}
	
	protected int removeRefsetMember(Task t, Concept c, RefsetMember r, String info) throws TermServerScriptException {
		if (r.isReleasedSafely()) {
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
			LOGGER.debug((dryRun ?DRY_DELETING:DELETING), uuid);
			if (!dryRun) {
				tsClient.deleteRefsetMember(uuid, t.getBranchPath(), force); 
			}
			return CHANGE_MADE;
		} catch (Exception e) {
			report(t, null, Severity.MEDIUM, ReportActionType.API_ERROR, "Failed to delete refset member " + uuid + DUE_TO_STR + e.getMessage());
			return NO_CHANGES_MADE;
		}
	}
	
	protected int updateRefsetMember(Task t, RefsetMember r, String info) throws TermServerScriptException {
		String msg = (dryRun? "Dry run u":"U") + "pdating state of {} {}";
		LOGGER.debug(msg, r, info);
		if (!dryRun) {
			tsClient.updateRefsetMember(t.getBranchPath(), r, false); //Don't force delete
		}
		return CHANGE_MADE;
	}

	public EclCache getDefaultEclCache() {
		String branch = overrideEclBranch == null ? project.getBranchPath() : overrideEclBranch;
		return EclCache.getCache(branch, tsClient, gl, quiet, CharacteristicType.INFERRED_RELATIONSHIP);
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
		// Find reference set members using GET request
		return tsClient.findRefsetMembers(project.getBranchPath(), refCompIds, refsetFilter);
	}

	public Collection<RefsetMember> searchMembers(List<String> referencedComponentIds, String refsetFilter) throws TermServerScriptException {
		// Find reference set members using bulk POST request
		return tsClient.searchMembers(project.getBranchPath(), referencedComponentIds, refsetFilter);
	}
	
	public int getConceptsCount(String ecl) {
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
			(info == null ? "" : " in " + info) + ". Skipping.", e);
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
				LOGGER.warn("Not using {} to recover ECL. Using {} instead.", branch, historicECLBranch);
				archiveEclWarningGiven.add(branch);
			}
			branch = historicECLBranch;
		}
		
		EclCache cache = EclCache.getCache(branch, tsClient, gl, quiet, charType);
		return cache.findConcepts(ecl, useLocalStoreIfSimple);
	}

	protected List<Component> processFile() throws TermServerScriptException {
		if (getInputFile() == null) {
			throw new TermServerScriptException("Unable to process file as no file specified!  Check -f parameter has been supplied, or alternatively, an ECL selection.");
		}
		return processFile(getInputFile());
	}
	
	protected List<Component> processFile(File file) throws TermServerScriptException {
		Set<Component> allComponents= new LinkedHashSet<>();
		LOGGER.debug("Loading input file {}", file.getAbsolutePath());
		try {
			List<String> lines = Files.readLines(file, StandardCharsets.UTF_8);
			lines = StringUtils.removeBlankLines(lines);
			
			//Are we restarting the file from some line number
			int startPos = (restartPosition == NOT_SET)?0:restartPosition - 1;
			List<Component> components;
			for (int lineNum = startPos; lineNum < lines.size(); lineNum++) {
				if (lineNum == 0  && inputFileHasHeaderRow) {
					continue; //skip header row  
				}
				String lineStr = lines.get(lineNum);
				String[] lineItems;

				if (Objects.equals(inputFileDelimiter, CSV_FIELD_DELIMITER)) {
					//File format Concept Type, SCTID, FSN with string fields quoted.  Strip quotes also.
					lineItems = splitCarefully(lineStr);
				} else {
					lineItems = lineStr.replace("\"", "").split(inputFileDelimiter);
				}
				if (lineItems.length >= 1) {
					try{
						components = loadLine(lineItems);

						if (components != null && !components.isEmpty()) {
							allComponents.addAll(components);
						} else {
							if (!expectNullConcepts) {
								LOGGER.debug("Skipped line {}: '{}', malformed or not required?", lineNum, lineStr);
							}
						}
					} catch (Exception e) {
						throw new TermServerScriptException("Failed to load line " + lineNum + ": '" + lineStr + "' due to ",e);
					}
				} else {
					LOGGER.debug("Skipping blank line {}", lineNum);
				}
			}
			addSummaryInformation(CONCEPTS_IN_FILE, allComponents);

		} catch (FileNotFoundException e) {
			throw new TermServerScriptException("Unable to open input file " + file.getAbsolutePath(), e);
		} catch (IOException e) {
			throw new TermServerScriptException("Error while reading input file " + file.getAbsolutePath(), e);
		}
		return new ArrayList<>(allComponents);
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
		Date endTime = new Date();
		finaliseSummaryText(endTime);
		//If we're running in dry run mode, we won't have written any reports
		//Allow report to have some final word before completing.  Override if required
		recordFinalWords();
		outputSummaryText("Finished at: " + endTime);
		if (getReportManager() != null) {
			outputSummaryText("Processing Report URL: " + getReportManager().getUrl());
		}
		LOGGER.info(BREAK);
		flushFiles(false);
	}

	private void finaliseSummaryText(Date endTime) {
		List<String> reportLast = new ArrayList<>(Arrays.asList(ISSUE_COUNT, "Report lines written"));
		List<String> criticalIssues = new ArrayList<>();
		outputAllSummaryText(reportLast, criticalIssues);

		if (summaryDetails.containsKey("Tasks created") && summaryDetails.containsKey(CONCEPTS_TO_PROCESS) ) {
			if (summaryDetails.get(CONCEPTS_TO_PROCESS) instanceof Collection) {
				double c = ((Collection<?>)summaryDetails.get(CONCEPTS_TO_PROCESS)).size();
				double t = ((Integer)summaryDetails.get("Tasks created"));
				double avg = Math.round((c/t) * 10) / 10.0;
				outputSummaryText("Concepts per task: " + avg);
			}
		}

		outputCriticalIssues(criticalIssues);
		outputFinalWords(reportLast);

		if (startTime != null) {
			long diff = endTime.getTime() - startTime.getTime();
			outputSummaryText("Completed processing in " + DurationFormatUtils.formatDuration(diff, "HH:mm:ss"));
			outputSummaryText("Started at: " + startTime);
		}
	}

	private void outputAllSummaryText(List<String> reportLast, List<String> criticalIssues) {
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
			outputSummaryText(key + (display.isEmpty()?"":": ") + display);
		}
	}

	private void outputCriticalIssues(List<String> criticalIssues) {
		if (!criticalIssues.isEmpty()) {
			outputSummaryText("\nCritical Issues Encountered (" + criticalIssues.size() + ")\n========================");
			for (String thisCriticalIssue : criticalIssues) {
				outputSummaryText(thisCriticalIssue);
			}
			outputSummaryText("Total Critical Issues Encountered: " + criticalIssues.size());
		}
	}

	private void outputFinalWords(List<String> reportLast) {
		if (summaryTabIdx != NOT_SET) {
			outputSummaryText("");
			outputSummaryText("");
		}

		for (String key : reportLast) {
			if (summaryDetails.containsKey(key)) {
				String display = summaryDetails.get(key).toString();
				outputSummaryText(key + (display.isEmpty()?"":": ") + display);
			}
		}
	}

	protected void recordFinalWords() throws TermServerScriptException {
		//Override in base class if required
	}

	private synchronized void outputSummaryText(String msg) {
		LOGGER.info(msg);
		if (getReportManager() != null) {
			if (summaryTabIdx != NOT_SET) {
				try {
					//Split the colon into its own column (unless it's a time stamp!)
					if (msg.contains(":") 
							&& !msg.contains("http")
							&& !msg.contains("at: ")
							&& !msg.contains("\"")
							&& !msg.contains("Completed processing in")) {
						msg = QUOTE + msg.replace(": ", QUOTE_COMMA_QUOTE).replace(":", QUOTE_COMMA_QUOTE) + QUOTE;
					}
					writeToReportFile(summaryTabIdx, msg);
				} catch (Exception e) {
					LOGGER.error("Failed to write summary info to summary tab {} ", msg, e);
				}
			}
		} else {
			LOGGER.info("Unable to report due to missing report manager: {} ", msg);
		}
	}
	
	public void writeToRF2File(String fileName, Object[] columns) throws TermServerScriptException {
		if (StringUtils.isEmpty(fileName) || fileName.startsWith("null")) {
			throw new TermServerScriptException("Request to write to RF2 file with no / invalid filename specified: '" + fileName + "'");
		}
		getRF2Manager().writeToRF2File(fileName, columns);
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
							Concept simpleECLRoot = gl.getConcept(subsetECL.replaceAll("[<^]", "").trim());
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
	
	public void report(Task t, Component c, ValidationFailure v) throws TermServerScriptException {
		report(t, c, v.severity, v.reportActionType, v.getMessage());
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
		if (reportAllDescriptions && component instanceof Concept concept) {
			name = SnomedUtils.getDescriptionsToString(concept);
		}
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

	public boolean report(Concept c, Object...details) throws TermServerScriptException {
		return report(PRIMARY_REPORT, c, details);
	}
	
	public boolean report(int reportIdx, Concept c, Object...details) throws TermServerScriptException {
		if (quiet || isWhiteListed(c, details)) {
			return false;
		}

		String[] conceptFields = new String[3];
		if (reportNullConcept || c != null) {
			calculateConceptFields(c, conceptFields);
		}
		report(reportIdx, conceptFields, details);
		return true;
	}

	private void calculateConceptFields(Concept c, String[] conceptFields) {
		conceptFields[0] = c == null?"": QUOTE + c.getConceptId() + QUOTE;
		conceptFields[1] = c == null?"":c.getFsn();
		if (reportAllDescriptions) {
			conceptFields[1] = SnomedUtils.getDescriptionsToString(c);
		}

		if (c != null && !StringUtils.isEmpty(c.getFsn())) {
			conceptFields[2] = SnomedUtils.deconstructFSN(c.getFsn())[1];
			if (conceptFields[2] == null) {
				conceptFields[2] = " ";
			}
		} else {
			conceptFields[2] = "";
		}
	}

	private boolean isWhiteListed(Concept c, Object[] details) {
		//Have we whiteListed this concept?
		if (!ignoreWhiteList && c != null && whiteListedConceptIds.contains(c.getId())) {
			String detailsStr = writeToString(details);
			LOGGER.warn("Ignoring whiteListed concept: {} : {}", c, detailsStr);
			incrementSummaryInformation(WHITE_LISTED_COUNT);
			return true;
		}
		return false;
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
		LOGGER.info("Quiet mode set to {}", quiet);
		this.quiet = quiet;
	}

	public GraphLoader getGraphLoader() {
		return gl;
	}

	public TermServerClient getTSClient() {
		return tsClient;
	}

	public ArchiveManager getArchiveManager() {
		return ArchiveManager.create();
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
		if (!file.getName().toLowerCase().contains("dummy") &&
				(!file.canRead() || (!file.isFile() && !allowDirectoryInputFile))) {
			throw new TermServerScriptException("Unable to read specified file: " + file);
		}
		inputFiles.set(idx, file);
	}

	public void setExclusions(List<Concept> exclusions) {
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

	public boolean isOffline() {
		return this.offlineMode;
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

		List<String> inScopeModules = getInScopeModules(includeExpectedExtensionModules);
		if (!inScopeModules.isEmpty()) {
			return inScopeModules.contains(c.getModuleId());
		} else if (moduleFilter != null) {
			return moduleFilter.contains(c.getModuleId());
		}
		return true;
	}

	protected List<String> getInScopeModules(boolean includeExpectedExtensionModules) {
		if (project.getMetadata() == null) {
			return List.of();
		}

		var metadata = project.getMetadata();
		var defaultModuleId = metadata.getDefaultModuleId();

		if (defaultModuleId == null) {
			return List.of();
		}

		var expectedModules = metadata.getExpectedExtensionModules();
		if (expectedModules == null) {
			if (!allowMissingExpectedModules) {
				throw new IllegalArgumentException(
						"Extension does not have expectedExtensionModules metadata populated. Cannot continue."
				);
			}
			return List.of(defaultModuleId);
		}

		return includeExpectedExtensionModules
				? expectedModules
				: List.of(defaultModuleId);
	}

	protected Set<String> getInScopeNamespaces() {
		return getInScopeModules(true).stream()
				.map(SnomedUtilsBase::getNamespace)
				.filter(Objects::nonNull)
				.collect(Collectors.toUnmodifiableSet());
	}
	
	protected boolean isMS() {
		//Do we have a default module id ie for a managed service project?
		if (project.getMetadata() != null && project.getMetadata().getDefaultModuleId() != null) {
			return !project.getMetadata().getDefaultModuleId().equals(SCTID_CORE_MODULE);
		}
		return false;
	}
	
	public static void run(Class<? extends JobClass> jobClazz, String[] args, Map<String, String> parameters) throws TermServerScriptException {
		quietenDownLogging();
		JobRun jobRun = createJobRunFromArgs(jobClazz.getSimpleName(), args);
		if (parameters != null) {
			for (Map.Entry<String, String> entry : parameters.entrySet()) {
				jobRun.setParameter(entry.getKey(), entry.getValue());
			}
		}
		JobClass job;
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
		job.instantiate(jobRun, null);
	}

	private static void quietenDownLogging() {
		makeQuiet("org.semanticweb.owlapi.util.SAXParsers");
		makeQuiet("ch.qos.logback.classic.util.ContextInitializer");
		makeQuiet("ch.qos.logback.classic.util.DefaultJoranConfigurator");
	}

	private static void makeQuiet(String loggerName) {
		((ch.qos.logback.classic.Logger)LoggerFactory.getLogger(loggerName)).setLevel(ch.qos.logback.classic.Level.ERROR);
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

	public List<String> getDependencyArchives() {
		return dependencyArchives;
	}
	
	protected void setDependencyArchives(List<String> dependencyArchives) {
		this.dependencyArchives = dependencyArchives;
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
	@Override
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

	public Concept getReplacementSafely(List<String> notes, Concept inactiveConcept, boolean isIsA) {
		try {
			return getReplacement(notes, inactiveConcept, isIsA);
		} catch (TermServerScriptException e) {
			notes.add(e.getMessage());
			LOGGER.error("Failed to find a replacement for {}", inactiveConcept, e);
		}
		return null;
	}

	protected Concept getReplacement(List<String> notes, Concept inactiveConcept, boolean isIsA) throws TermServerScriptException {
		Set<String> assocs = new HashSet<>(inactiveConcept.getAssociationTargets().getReplacedBy());
		assocs.addAll(inactiveConcept.getAssociationTargets().getAlternatives());
		assocs.addAll(inactiveConcept.getAssociationTargets().getPossEquivTo());
		assocs.addAll(inactiveConcept.getAssociationTargets().getPartEquivTo());
		assocs.addAll(inactiveConcept.getAssociationTargets().getSameAs());
		if (assocs.isEmpty()) {
			if (isIsA) {
				//We'll try and carry on without this parent.
				return null;
			}
			throw new TermServerScriptException("Unable to find replacement for " + inactiveConcept + DUE_TO_STR + assocs.size() + " associations");
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
		Concept replacement = getReplacement(notes, inactiveConcept, isIsA);
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
		restateInferredRelationships(c, false);
	}

	public void restateInferredRelationships(Concept c, boolean includeISA) throws TermServerScriptException {
		//by default, the exclusions will be empty
		restateInferredRelationships(c, includeISA, Collections.emptyList());
	}

	public void restateInferredRelationships(Concept c, boolean includeISA, List<Concept> typeExclusions) throws TermServerScriptException {
		//Work through all inferred groups and collect any that aren't also stated, to state
		List<RelationshipGroup> toBeStated = new ArrayList<>();
		Collection<RelationshipGroup> inferredGroups = c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP, includeISA);
		Collection<RelationshipGroup> statedGroups = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, includeISA);

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
		stateRelationshipGroups(c, toBeStated, typeExclusions);
	}

	private int stateRelationshipGroups(Concept c, List<RelationshipGroup> toBeStated, List<Concept> typeExclusions) throws TermServerScriptException {
		int changesMade = 0;
		for (RelationshipGroup g : toBeStated) {
			//Group 0 must remain group 0.  Otherwise, find an available group number
			int freeGroup = g.getGroupId()==0?0:SnomedUtils.getFirstFreeGroup(c);
			changesMade += stateRelationshipGroup(c, g, freeGroup, typeExclusions);
		}
		return changesMade;
	}

	private int stateRelationshipGroup(Concept c, RelationshipGroup g, int freeGroup, List<Concept> typeExclusions) throws TermServerScriptException {
		int changesMade = 0;
		AxiomEntry axiom = null;
		//Does c already have an axiom we can merge these relationships into?
		if (!c.getAxiomEntries(ActiveState.ACTIVE, false).isEmpty()) {
			axiom = c.getAxiomEntries(ActiveState.ACTIVE, false).iterator().next();
		}

		for (Relationship r : g.getRelationships()) {
			if (!typeExclusions.contains(r.getType())) {
				Relationship newRel = r.clone(null);
				newRel.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
				newRel.setGroupId(freeGroup);
				changesMade += replaceRelationship((Task) null, c, newRel.getType(), newRel.getTarget(), newRel.getConcreteValue(), newRel.getGroupId(), RelationshipTemplate.Mode.PERMISSIVE, false, axiom);
			}
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
		return replaceRelationship(t, c, type, value, concreteValue, groupId, mode, reportAlreadyExisting, null);
	}

	protected int replaceRelationship(Task t, Concept c, Concept type, Concept value, ConcreteValue concreteValue, int groupId, RelationshipTemplate.Mode mode, boolean reportAlreadyExisting, AxiomEntry assignToAxiom) throws TermServerScriptException {
		int changesMade = 0;
		if (checkForNoViableRelationshipReplacement(t, c, type, value, concreteValue, groupId, reportAlreadyExisting)) {
			return NO_CHANGES_MADE;
		}

		if (checkForRelationshipExistsInactive(t, c, type, value, concreteValue, groupId)) {
			return CHANGE_MADE;
		}

		if (modePreventsReplacement(t, c, type, value, groupId, mode)) {
			return NO_CHANGES_MADE;
		}

		//Add the new relationship
		if (groupId == SELFGROUPED) {
			groupId = SnomedUtils.getFirstFreeGroup(c);
		}

		if (t != null || reportChangesWithoutTask) {
			Relationship newRel = new Relationship(c, type, value, groupId);
			//Copying relationships from elsewhere indicates they have not been released in their current condition
			newRel.setReleased(false);
			newRel.setDirty();
			newRel.setAxiomEntry(assignToAxiom);

			report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, newRel);
			c.addRelationship(newRel);
		}
		changesMade++;
		return changesMade;
	}

	private boolean modePreventsReplacement(Task t, Concept c, Concept type, Concept value,
			int groupId, Mode mode) throws TermServerScriptException {
		Set<Relationship> rels;
		//Or do we need to create and add?
		//Is this type (or type/value) unique for the concept
		//or (new feature) do we want to replace any attributes of the same type if they exist
		if (mode == RelationshipTemplate.Mode.UNIQUE_TYPE_ACROSS_ALL_GROUPS ||
				mode == RelationshipTemplate.Mode.REPLACE_TYPE_IN_THIS_GROUP) {
			rels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
					type,
					ActiveState.ACTIVE);
			if (!rels.isEmpty()) {
				if (mode == RelationshipTemplate.Mode.UNIQUE_TYPE_ACROSS_ALL_GROUPS) {
					report(t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, type + " attribute type already exists: " + rels.iterator().next());
					return true;
				} else {
					prepareToReplaceTypeInThisGroup(t, c, type, groupId);
				}
			}
		} else if (mode == RelationshipTemplate.Mode.UNIQUE_TYPE_VALUE_ACROSS_ALL_GROUPS) {
			RelationshipTemplate rt = new RelationshipTemplate(type,value);
			rels = c.getRelationships(rt, ActiveState.ACTIVE);
			if (!rels.isEmpty()) {
				report(t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Attribute type/value already exists: " + rels.iterator().next());
				return true;
			}
		} else if (mode == RelationshipTemplate.Mode.UNIQUE_TYPE_IN_THIS_GROUP) {
			RelationshipTemplate rt = new RelationshipTemplate(type,value);
			RelationshipGroup g = c.getRelationshipGroup(CharacteristicType.STATED_RELATIONSHIP, groupId);
			rels = g.getRelationshipsWithType(rt.getType());
			if (!rels.isEmpty()) {
				report(t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Attribute type already exists in specified group: " + rels.iterator().next());
				return true;
			}
		}
		return false;
	}

	private void prepareToReplaceTypeInThisGroup(Task t, Concept c, Concept type, int groupId) throws TermServerScriptException {
		//Removing existing relationships of the same type, but only in this group
		Set<Relationship> rels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
				type,
				groupId);
		for (Relationship remove : rels) {
			removeRelationship(t, c, remove);
		}
	}

	private boolean checkForRelationshipExistsInactive(Task t, Concept c, Concept type, Concept value,
			ConcreteValue concreteValue, int groupId) throws TermServerScriptException {
		//Do we have it inactive?
		Set<Relationship> rels = findExistingRelationships(c, type, value, concreteValue, groupId, ActiveState.INACTIVE);
		if (!rels.isEmpty()) {
			Relationship rel = rels.iterator().next();
			report(t, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_REACTIVATED, rel);
			rel.setActive(true);
			return true;
		}
		return false;
	}

	private boolean checkForNoViableRelationshipReplacement(Task t, Concept c, Concept type, Concept value,
			ConcreteValue concreteValue, int groupId, boolean reportAlreadyExisting) throws TermServerScriptException {
		if (type == null || (value == null && concreteValue == null)) {
			if (value == null && concreteValue == null) {
				String msg = "Unable to add relationship of type " + type + " due to lack of a value concept / concrete value";
				report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, msg);
			} else if (type == null) {
				String msg = "Unable to add relationship with value " + value + " due to lack of a type concept";
				report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, msg);
			}
			return true;
		}
		//Do we already have this relationship active in the target group (or at all if self grouped)?
		Set<Relationship> rels = findExistingRelationships(c, type, value, concreteValue, groupId, ActiveState.ACTIVE);
		if (rels.size() > 1) {
			report(t, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Found two active relationships for " + type + " -> " + value);
			return true;
		} else if (rels.size() == 1) {
			if (reportAlreadyExisting) {
				report(t, c, Severity.LOW, ReportActionType.NO_CHANGE, "Active relationship already exists ", rels.iterator().next());
			}
			return true;
		}
		return false;
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
			if (removedGroups.contains(originalGroup) || originalGroup.isEmpty()) {
				continue;
			}
			for (RelationshipGroup potentialRedundancy : originalGroups) {
				//Don't compare self, removed or empty groups
				if (originalGroup.getGroupId() == potentialRedundancy.getGroupId() ||
						potentialRedundancy.isEmpty() ||
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
						report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Group of larger size appears redundant - check!");
						groupToRemove = originalGroup;
					} else {
						LOGGER.warn("DEBUG HERE, Redundancy in {}", c);
					}

					if (groupToRemove != null && !groupToRemove.isEmpty()) {
						removedGroups.add(groupToRemove);
						report(t, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_GROUP_REMOVED, "Redundant relationship group removed:", groupToRemove);
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
				report(t, c, Severity.LOW, ReportActionType.INFO, "Post redundancy removal group", g);
			}
		}
		return changesMade;
	}

	protected void shuffleDown(Task t, Concept c) throws TermServerScriptException {
		List<RelationshipGroup> newGroups = new ArrayList<>();
		for (RelationshipGroup group : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			//Have we missed out the ungrouped group? fill in if so
			if (group.isGrouped() && newGroups.isEmpty()) {
				newGroups.add(new RelationshipGroup(UNGROUPED));
			}
			//Since we're working with the true concept relationships here, this will have
			//the effect of changing the groupId in all affected relationships
			if (group.getGroupId() != newGroups.size()) {
				report(t, c, Severity.MEDIUM, ReportActionType.INFO, "Shuffling stated group " + group.getGroupId() + " to " + newGroups.size());
				group.setGroupId(newGroups.size());
				group.setDirty();
				//If we have relationships without SCTIDs here, see if we can pinch them from inactive relationships
				int reuseCount = 0;
				for (Relationship moved : new ArrayList<>(group.getRelationships())) {
					if (StringUtils.isEmpty(moved.getId())) {
						Set<Relationship> existingInactives = c.getRelationships(moved, ActiveState.INACTIVE);
						if (!existingInactives.isEmpty()) {
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

	public boolean isDryRun() {
		return dryRun;
	}

	public void initialiseSummary (String item) {
		initialiseSummaryCount(ISSUES, item);
	}

	public void initialiseSummaryCount(String category, String item) {
		summaryCountsByCategory
				.computeIfAbsent(category, k -> new HashMap<>())
				.putIfAbsent(item, 0);
	}

	public void initialiseSecondarySummaryCount(String category, String item) {
		secondaryCountsByCategory
				.computeIfAbsent(category, k -> new HashMap<>())
				.putIfAbsent(item, 0);
	}

	public void incrementSummaryCount(String summaryItem) {
		incrementSummaryCount(ISSUES, summaryItem, 1);
	}

	public void incrementSummaryCount(String category, String summaryItem) {
		incrementSummaryCount(category, summaryItem, 1);
	}

	public void incrementSecondaryCount(String category, String summaryItem) {
		incrementSecondaryCount(category, summaryItem, 1);
	}

	public void incrementSummaryCount(String category, String summaryItem, int increment) {
		//Increment the count for this summary item, in the appropriate category
		Map<String, Integer> summaryCounts = summaryCountsByCategory.computeIfAbsent(category, k -> new HashMap<>());
		summaryCounts.merge(summaryItem, increment, Integer::sum);
	}

	public void incrementSecondaryCount(String category, String summaryItem, int increment) {
		//Increment the count for this summary item, in the appropriate category
		Map<String, Integer> secondaryCounts = secondaryCountsByCategory.computeIfAbsent(category, k -> new HashMap<>());
		secondaryCounts.merge(summaryItem, increment, Integer::sum);
	}

	protected void setIgnoreInputFileForReportName(boolean b) {
		ignoreInputFileForReportName = b;
	}

	public void copyScriptState(TermServerScript clone) {
		this.setReportManager(clone.getReportManager());
		this.project = clone.getProject();
		this.tsClient = clone.getTSClient();
		this.scaClient = clone.getAuthoringServicesClient();
		this.dryRun = clone.isDryRun();
		this.authenticatedCookie =  clone.getAuthenticatedCookie();
		this.setReportName(clone.getReportName());
	}

	public enum SUMMARY_SORT_ORDER {
		ALPHABETICAL,
		COUNT
	}

	protected void reportSummaryCounts(int summaryTabIdx) throws TermServerScriptException {
		reportSummaryCounts(summaryTabIdx, SUMMARY_SORT_ORDER.ALPHABETICAL);
	}

	protected void reportSummaryCounts(int summaryTabIdx, SUMMARY_SORT_ORDER sortOrder) throws TermServerScriptException {
		report(summaryTabIdx, "");

		// Work through each category (sorted alphabetically)
		summaryCountsByCategory.keySet().stream()
				.sorted()
				.forEach(cat -> {
					reportSafely(summaryTabIdx, cat);

					Map<String, Integer> summaryCounts = summaryCountsByCategory.get(cat);
					Stream<Map.Entry<String, Integer>> stream = summaryCounts.entrySet().stream();
					if (sortOrder == SUMMARY_SORT_ORDER.COUNT) {
						// Sort by count descending, then alphabetically for stability
						stream = stream.sorted(
								Comparator.comparing(Map.Entry<String, Integer>::getValue)
										.reversed()
										.thenComparing(Map.Entry::getKey)
						);
					} else {
						// Default alphabetical by key
						stream = stream.sorted(
								Comparator.comparing(Map.Entry::getKey)
						);
					}

					Map<String, Integer> secondaryCounts = secondaryCountsByCategory.get(cat);
					stream.forEach(entry -> {
						Integer secondaryCount = (includeSecondaryCounts && secondaryCounts != null)
								? secondaryCounts.get(entry.getKey())
								: null;
						if (secondaryCount != null && secondaryCount > 0) {
							reportSafely(summaryTabIdx, "", entry.getKey(), entry.getValue(), secondaryCount);
						} else {
							reportSafely(summaryTabIdx, "", entry.getKey(), entry.getValue());
						}
					});
				});
	}

	public String getUserAgent() throws TermServerScriptException {
		String userAgent = getConfigurationItem("reporting-worker.http.user-agent");
		return userAgent == null ? "" : userAgent;
	}

	protected String getTemplateServiceUrl() throws TermServerScriptException {
		String templateServiceName = getConfigurationItem("templateService.name");
		if (templateServiceName == null) {
			throw new IllegalStateException("Missing template service name.  Check consul or application-local.properties if running locally.");
		}
		//We'll assume that the template service lives on the same server as the TS
		return getServerUrl() + templateServiceName;
	}

	protected String getConfigurationItem(String key) throws TermServerScriptException {
		if (appContext != null) {
			return appContext.getEnvironment().getProperty(key);
		}
		Properties props = new Properties();
		try (InputStream is = openLocalPropertiesStream()) {
			if (is != null) {
				props.load(is);
			}
		} catch (IOException e) {
			LOGGER.warn("Could not load application-local.properties for key '{}'", key, e);
		}
		String value = props.getProperty(key);
		if (value == null) {
			throw new TermServerScriptException("Configuration item '" + key + "' not found in application-local.properties");
		}
		return value;
	}

	private InputStream openLocalPropertiesStream() throws FileNotFoundException {
		File localProps = new File("application-local.properties");
		if (localProps.exists()) {
			return new FileInputStream(localProps);
		}

		LOGGER.warn("No application-local.properties found in working directory ({}), falling back to classpath", localProps.getAbsolutePath());
		return getClass().getClassLoader().getResourceAsStream("application-local.properties");
	}

	protected Map<String, Map<String, Integer>> getSummaryCountsByCategoryMap() {
		return summaryCountsByCategory;
	}

	protected void setSummaryCountsByCategoryMap(Map<String, Map<String, Integer>> summaryCountsByCategory) {
		this.summaryCountsByCategory = summaryCountsByCategory;
	}

	public SnapshotConfiguration getSnapshotConfiguration() {
		return snapshotConfiguration;
	}

	public String getSecondaryServerUrl() {
		return secondaryServerUrl;
	}

	protected void runIntegrityChecks(boolean fsnOnly) throws TermServerScriptException {
		StringBuilder integrityFailureMessage = new StringBuilder();

		//Ensure that every active parent other than root has at least one parent in both views
		LOGGER.info("Ensuring all concepts have parents and depth if required.");

		//We need a separate copy of all concepts because we might modify it in passing if we encounter a phantom concept
		for (Concept c : new ArrayList<>(gl.getAllConcepts())) {
			if (integrityCheckIgnoreList.contains(c.getId()) || isPhantomConcept(c, integrityFailureMessage)) {
				continue;
			}

			if (c.isActiveSafely() && !c.equals(ROOT_CONCEPT)) {
				checkActiveConceptIntegrity(c, integrityFailureMessage);
			} else if (!c.isActiveSafely()) {
				checkInactiveConceptIntegrity(c, integrityFailureMessage);
			}

			checkConceptDepth(c, integrityFailureMessage);
		}

		if (!integrityFailureMessage.isEmpty()) {
			throw new UnrecoverableTermServerScriptException(integrityFailureMessage.toString());
		}

		if (!fsnOnly) {
			checkFirst100Descriptions();
		}

		LOGGER.info("Integrity check passed.  All concepts have at least one stated and one inferred active parent");
	}

	private void checkFirst100Descriptions() throws TermServerScriptException {
		//Check that we've got some descriptions to be sure we've not been given
		//a malformed, or classification style archive.
		LOGGER.debug("Checking first 100 concepts for integrity");
		List<Description> first100Descriptions = gl.getAllConcepts()
				.stream()
				.limit(100)
				.flatMap(c -> c.getDescriptions().stream())
				.toList();
		if (first100Descriptions.size() < 100) {
			throw new TermServerScriptException("Failed to find sufficient number of descriptions - classification archive used? Deleting snapshot, please retry.");
		}
	}

	private void checkActiveConceptIntegrity(Concept c, StringBuilder integrityFailureMessage) {
		checkParentalIntegrity(c, RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, integrityFailureMessage);
		if (snapshotConfiguration.isExpectStatedParents()) {
			checkParentalIntegrity(c, RF2Constants.CharacteristicType.STATED_RELATIONSHIP, integrityFailureMessage);
		}
	}

	private void checkInactiveConceptIntegrity(Concept c, StringBuilder integrityFailureMessage) {
		if (!c.getParents(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP).isEmpty()) {
			integrityFailureMessage.append(c).append(" is inactive but has inferred parents.");
		}

		if (!c.getChildren(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP).isEmpty()) {
			integrityFailureMessage.append(c).append(" is inactive but has inferred children.");
		}
	}

	private void checkConceptDepth(Concept c, StringBuilder integrityFailureMessage) throws TermServerScriptException {
		if (snapshotConfiguration.isPopulateHierarchyDepth() && c.isActiveSafely() && c.getDepth() == NOT_SET) {
			if (!integrityFailureMessage.isEmpty()) {
				integrityFailureMessage.append(",\n");
			}
			integrityFailureMessage.append(c).append(" failed to populate depth");
			String ancestorStr = c.getAncestors(NOT_SET).stream().map(Concept::toString).collect(Collectors.joining(","));
			LOGGER.warn("{} ancestors are : {}", c, ancestorStr);
		}
	}

	private void checkParentalIntegrity(Concept c, RF2Constants.CharacteristicType charType, StringBuilder integrityFailureMessage) {
		Set<Concept> parents = c.getParents(charType);
		checkParentsExist(c, parents, charType, integrityFailureMessage);
		checkParentsNotPhantomOrInactive(c, parents, charType, integrityFailureMessage);
		int parentRelCount = checkParentsRelationships(c, parents, charType, integrityFailureMessage);
		checkParentsRelationshipsCount(c, parents, parentRelCount, charType, integrityFailureMessage);
	}

	private void checkParentsExist(Concept c, Set<Concept> parents, RF2Constants.CharacteristicType charType, StringBuilder integrityFailureMessage) {
		if (parents.isEmpty()) {
			if (!integrityFailureMessage.isEmpty()) {
				integrityFailureMessage.append(",\n");
			}
			integrityFailureMessage.append(c).append(" has no ").append(charType).append(" parents.");
		}
	}

	private void checkParentsNotPhantomOrInactive(Concept c, Set<Concept> parents, RF2Constants.CharacteristicType charType, StringBuilder integrityFailureMessage) {
		for (Concept parent : parents) {
			isPhantomConcept(parent, integrityFailureMessage);
			if (!parent.isActiveSafely()) {
				if (!integrityFailureMessage.isEmpty()) {
					integrityFailureMessage.append(",\n");
				}
				integrityFailureMessage.append(c).append(" has inactive ").append(charType).append(" parent: ").append(parent);
			}
		}
	}

	private int checkParentsRelationships(Concept c, Set<Concept> parents, RF2Constants.CharacteristicType charType, StringBuilder integrityFailureMessage) {
		//Check that we've captured those parents correctly
		//Looping through existing objects rather than calling getRelationships so we're
		//not creating new collections.   getRelationships does all the looping anyway, so no cheaper.
		int parentRelCount = 0;
		for (Relationship r : c.getRelationships()) {
			if (r.isActiveSafely() && r.getCharacteristicType().equals(charType) && r.getType().equals(IS_A)) {
				parentRelCount++;
				if (!parents.contains(r.getTarget())) {
					if (!integrityFailureMessage.isEmpty()) {
						integrityFailureMessage.append(",\n");
					}
					integrityFailureMessage.append(c).append(" has internal ").append(charType).append(" inconsistency between parents and parental relationship for parent ").append(r.getTarget());
				}
			}
		}
		return parentRelCount;
	}

	private void checkParentsRelationshipsCount(Concept c, Set<Concept> parents, int parentRelCount, RF2Constants.CharacteristicType charType, StringBuilder integrityFailureMessage) {
		if (parentRelCount != parents.size())	{
			//Trying for minimal memory allocations here, so only check for duplicate targets between
			//axioms if we detect a problem
			Set<Concept> parentsFromRels = SnomedUtils.getTargets(c, new Concept[]{IS_A}, charType);
			if (parentsFromRels.size() != parents.size()) {
				if (!integrityFailureMessage.isEmpty()) {
					integrityFailureMessage.append(",\n");
				}
				integrityFailureMessage.append(c).append(" has internal ").append(charType).append(" inconsistency between parents (").append(parents.size()).append(") and parental relationship count (").append(parentsFromRels.size()).append(").");
			}
		}
	}

	private boolean isPhantomConcept(Concept c, StringBuilder integrityFailureMessage) {
		if (c.getActive() != null) {
			return false;
		}

		//Now SOMETHING had a reference to this concept, so let's try and work out what and
		//report that, rather than talk about a concept that doesn't exist

		//If this reference has come from some 'other' refset, then we can just record and later report
		//that without having the whole report bomb out
		if (phantomConceptReferencedByOtherRefsetOnly(c)) {
			return true;
		}

		String msg = determineSourceOfPhantomConcept(c);
		if (getDependencyArchives() != null) {
			msg += ". Check dependency is appropriate - " + getDependencyArchives();
		}
		//Now if we've imported all reference sets and we've got a phantom concept that's coming from an
		//inactive reference set member, then we're just going to report that as a "final word" rather than
		//bomb out the entire report
		if (snapshotConfiguration.isLoadOtherReferenceSets() && msg.contains("*RM")) {
			LOGGER.warn("Recording final words rather than throwing exception: {}", msg);
			addFinalWords(msg);
			//And we're going to remove this concept so that we don't trip over it again
			gl.removeConcept(c);
		} else {
			if (!integrityFailureMessage.isEmpty()) {
				integrityFailureMessage.append(",\n");
			}
			integrityFailureMessage.append(msg);
		}
		return true;
	}

	private boolean phantomConceptReferencedByOtherRefsetOnly(Concept c) {
		Set<RefsetMember> otherRefsetMembers = c.getOtherRefsetMembers();
		Collection<Component> components = SnomedUtils.getAllComponents(c);
		components.removeAll(otherRefsetMembers);
		components.removeIf(Concept.class::isInstance);
		if (otherRefsetMembers.isEmpty() || !components.isEmpty()) {
			return false;
		}

		//record the refset members that reference this concept for later reporting
		for (RefsetMember rm : otherRefsetMembers) {
			gl.addIntegrityWarning(List.of(
					c.getId(),
					"does not appear in this extension, but is referenced by refset member in refset",
					gl.getConceptSafely(rm.getRefsetId()),
					rm));
		}
		//Remove this concept from the graph so we don't attempt to sort it, or cache to disk
		//Actually, block caching to disk, else these issues will disappear
		gl.removeConcept(c);

		return true;
	}

	private String determineSourceOfPhantomConcept(Concept c) {
		//Which components referenced this concept?
		Collection<Component> components = SnomedUtils.getAllComponents(c);
		if (components.isEmpty()) {
			return "Integrity concern: concept" + c.getId() + " does not appear in concept file and is not referenced by any components.  Could have come in via WhiteListing?";
		}
		//Reduce the count by 1 because the concept itself gets counted, and that's a phantom.
		int refCount = components.size()-1;

		//If the concept is not referenced by any of its own components, then we'll see what other concepts reference it.
		if (refCount == 0) {
			//Find Inferred Relationship References
			List<Relationship> inferredReferences = getInferredReferences(c);
			if (!inferredReferences.isEmpty()) {
				return "Integrity concern: concept " + c.getId() + " does not appear in concept file.  It is, however, referenced by " + inferredReferences.size() + " inferred relationship(s), eg: " + inferredReferences.iterator().next().toLongString();
			}
		}
		return "Integrity concern: concept " + c.getId() + " does not appear in concept file.  It is, however, referenced by " + refCount + " component(s), eg: " + getFirstNonConceptComponent(components);
	}

	private List<Relationship> getInferredReferences(Concept phantomConcept) {
		List<Relationship> inferredReferences = new ArrayList<>();
		for (Concept c : gl.getAllConcepts()) {
			if (phantomConcept.equals(c)) {
				continue;
			}
			for (Relationship r : c.getRelationships(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP, RF2Constants.ActiveState.BOTH)) {
				if (!r.isConcrete() && r.getTarget().equals(phantomConcept) || r.getType().equals(phantomConcept)) {
					inferredReferences.add(r);
				}
			}
		}
		return inferredReferences;
	}

	private String getFirstNonConceptComponent(Collection<Component> components) {
		for (Component c : components) {
			if (!(c instanceof Concept)) {
				return c.toString();
			}
		}
		return "No non-concept components found";
	}
}
