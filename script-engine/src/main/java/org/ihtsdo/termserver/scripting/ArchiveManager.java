package org.ihtsdo.termserver.scripting;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.*;

import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClient.*;
import org.ihtsdo.termserver.scripting.dao.ArchiveDataLoader;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.snapshot.SnapshotGenerator;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ArchiveManager implements ScriptConstants {
	
	static ArchiveManager singleton;
	
	protected Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private ArchiveDataLoader archiveDataLoader;
	
	protected String dataStoreRoot = "";
	protected GraphLoader gl;
	protected TermServerScript ts;
	protected ApplicationContext appContext;
	private boolean allowStaleData = false;
	private boolean loadDependencyPlusExtensionArchive = false;
	private boolean loadEditionArchive = false;
	private boolean populateHierarchyDepth = true;  //Term contains X needs this
	private boolean populateReleasedFlag = false;
	private boolean populatePreviousTransativeClosure = false;
	private boolean expectStatedParents = true;  //UK Edition doesn't provide these, so don't look for them.
	private boolean releasedFlagPopulated = false;
	private boolean runIntegrityChecks = true;
	private final List<String> integrityCheckIgnoreList = List.of(
			"21000241105", // |Common French language reference set|
			"763158003" // |Medicinal product (product)| Gets created as a constant, but does exist before 20180731
	);
	
	private Project currentlyHeldInMemory;
	ZoneId utcZoneID= ZoneId.of("Etc/UTC");
	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	
	public static ArchiveManager getArchiveManager(TermServerScript ts, ApplicationContext appContext) {
		return getArchiveManager(ts, appContext, false);
	}
	
	public static ArchiveManager getArchiveManager(TermServerScript ts, ApplicationContext appContext, boolean forceReuse) {
		if (singleton == null) {
			singleton = new ArchiveManager();
			singleton.appContext = appContext;
		}
		
		if (singleton.ts == null || !singleton.ts.getClass().getSimpleName().equals(ts.getClass().getSimpleName())) {
			TermServerScript.info("Archive manager under first or new ownership: " + ts.getClass().getSimpleName() + ".  Resetting load flags");
			singleton.gl = ts.getGraphLoader();
		} else {
			TermServerScript.info("Archive manager being reused in: " + ts.getClass().getSimpleName()); 
		}
		
		if (!forceReuse) {
			//Don't assume that just because we're being reused, we're loading the same files
			singleton.loadEditionArchive = false;
			singleton.loadDependencyPlusExtensionArchive = false;
			singleton.populatePreviousTransativeClosure = false;
			singleton.populateReleasedFlag = false;
		}
		singleton.ts = ts;
		return singleton;
	}
	
	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		singleton = this;
	}
	
	private ArchiveManager () {
		//Only access via singleton above
	}
	
	protected void info(String msg) {
		TermServerScript.info(msg);
	}
	
	protected void debug(String msg) {
		TermServerScript.debug(msg);
	}
	
	public static void print (Object msg) {
		System.out.print (msg.toString());
	}
	
	protected Branch loadBranch(Project project) throws TermServerScriptException {
		String branchPath = project.getBranchPath();
		String server = "uknown";
		try {
			debug ("Checking TS branch metadata: " + branchPath);
			server = ts.getTSClient().getUrl();
			Branch branch = ts.getTSClient().getBranch(branchPath);
			//If metadata is empty, or missing previous release, recover parent
			//But timestamp will remain a property of the branch
			//But not if we're already on MAIN and it's STILL missing!
			while (branch.getMetadata() == null || branch.getMetadata().getPreviousRelease() == null) {
				if (branchPath.equals("MAIN")) {
					throw new TermServerScriptException("Metadata data missing in MAIN");
				}
				branchPath = getParentBranch(branchPath);
				Branch parent = loadBranch(new Project().withBranchPath(branchPath));
				branch.setMetadata(parent.getMetadata());
			}
			return branch;
		} catch (Exception e) {
			if (e.getMessage().contains("[404] Not Found")) {
				debug ("Unable to find branch " + branchPath);
				return null;
			}
			throw new TermServerScriptException("Failed to recover " + project + " from TS (" + server + ") due to " + e.getMessage(),e);
		}
	}
	
	private String getParentBranch(String branchPath) {
		//Do we have a path?
		int lastSlash = branchPath.lastIndexOf("/");
		if (lastSlash == NOT_FOUND) {
			if (branchPath.equals("MAIN")) {
				return branchPath;
			} else {
				throw new IllegalStateException ("Root level branch is not MAIN: " + branchPath);
			}
		}
		return branchPath.substring(0, lastSlash);
	}

	public String getPreviousPreviousBranch(Project project) throws TermServerScriptException {
		Branch branch = loadBranch(project);
		String previousRelease = branch.getMetadata().getPreviousRelease();
		String codeSystem = extractCodeSystemFromBranch(branch);
		try {
			List<CodeSystemVersion> codeSystems = ts.getTSClient().getCodeSystemVersions(codeSystem);
			//Filter out anything that's not a release date, then sort descending
			List<CodeSystemVersion> releases = codeSystems.stream()
			.sorted(Comparator.comparing(CodeSystemVersion::getEffectiveDate).reversed())
			.collect(Collectors.toList());
			
			if (releases.size() < 2) {
				throw new TermServerScriptException("Less than 2 previous releases detected");
			}
			if (!releases.get(0).getEffectiveDate().toString().equals(previousRelease)) {
				TermServerScript.warn("Check here - unexpected previous release: " +  releases.get(0).getEffectiveDate() + " expected " + previousRelease);
			}
			return releases.get(1).getBranchPath();
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to recover child branches due to " + e.getMessage(),e);
		}
	}
	
	public String getPreviousBranch(Project project) throws TermServerScriptException {
		Branch branch = loadBranch(project);
		String previousRelease = branch.getMetadata().getPreviousRelease();
		String codeSystem = extractCodeSystemFromBranch(branch);
		try {
			List<CodeSystemVersion> codeSystems = ts.getTSClient().getCodeSystemVersions(codeSystem);
			//Filter out anything that's not a release date, then sort descending
			List<CodeSystemVersion> releases = codeSystems.stream()
			.sorted(Comparator.comparing(CodeSystemVersion::getEffectiveDate).reversed())
			.collect(Collectors.toList());
			
			if (releases.size() < 1) {
				throw new TermServerScriptException("Less than 1 previous releases detected");
			}
			if (!releases.get(0).getEffectiveDate().toString().equals(previousRelease)) {
				throw new TermServerScriptException("Check here - unexpected previous release: " +  releases.get(0).getEffectiveDate() + " expected " + previousRelease);
			}
			logger.info("Detected previous branch: {}", releases.get(0).getBranchPath());
			return releases.get(0).getBranchPath();
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to recover previous branch due to " + e.getMessage(),e);
		}
	}

	private String extractCodeSystemFromBranch(Branch branch) {
		String codeSystem = "SNOMEDCT";
		if (branch.getPath().contains(codeSystem)) {
			String[] branchParts = branch.getPath().split("/");
			if (branchParts[1].startsWith(codeSystem)) {
				codeSystem = branchParts[1];
			}
		}
		return codeSystem;
	}

	public void loadSnapshot(boolean fsnOnly) throws TermServerScriptException {
		try {
			if (loadDependencyPlusExtensionArchive) {
				if (StringUtils.isEmpty(ts.getDependencyArchive())) {
					throw new TermServerScriptException("Told to load dependency + extension but no dependency package specified");
				} else {
					TermServerScript.info("Loading dependency plus extension archives");
					gl.reset();
					File dependency = new File ("releases/" + ts.getDependencyArchive());
					if (dependency.exists()) {
						loadArchive(dependency, fsnOnly, "Snapshot", true);
					} else {
						//Can we find it in S3?
						String cwd = new File("").getAbsolutePath();
						TermServerScript.info(ts.getDependencyArchive() + " not found locally in " + cwd + ", attempting to download from S3.");
						getArchiveDataLoader().download(dependency);
						if (dependency.exists()) {
							loadArchive(dependency, fsnOnly, "Snapshot", true);
						} else {
							throw new TermServerScriptException("Dependency Package " + dependency.getAbsolutePath() + " does not exist and was not recovered from S3.");
						}
					}
					//Now lets not pretend we're holding anything in memory at this point, because we still have to load in
					//the extension before we have that.
					currentlyHeldInMemory = null;
				}
			}
			
			//If the project specifies its a .zip file, that's another way to know we're loading an edition
			String fileExt = ".zip";
			if (ts.getProject().getKey().endsWith(fileExt)) {
				info("Project key ('" + ts.getProject().getKey() + "') identified as zip archive, loading Edition Archive");
				loadEditionArchive = true;
			}
			
			//Look for an expanded directory by preference
			File snapshot = getSnapshotPath();
			if (!snapshot.exists() && !snapshot.getName().endsWith(fileExt)) {
				//Otherwise, do we have a zip file to play with?
				snapshot = new File (snapshot.getPath() + fileExt);
			}
			
			if (!snapshot.exists()) {
				//If it doesn't exist as a zip file locally either, we can try downloading it from S3
				try {
					String cwd = new File("").getAbsolutePath();
					TermServerScript.info(snapshot + " not found locally in " + cwd + ", attempting to download from S3.");
					getArchiveDataLoader().download(snapshot);
				} catch (TermServerScriptException e) {
					info("Could not find " + snapshot.getName() + " in S3.");
				}
			}
			
			boolean originalStateDataFlag = allowStaleData;
			//If we're loading a particular release, it will be stale
			if (loadEditionArchive || StringUtils.isNumeric(ts.getProject().getKey())) {
				loadEditionArchive = true;
				allowStaleData = true;
				if (loadEditionArchive && !snapshot.exists()) {
					throw new TermServerScriptException ("Could not find " + snapshot + " to import");
				}
			}
			
			Branch branch = null;
			//Are we lacking data, or is our data out of date?  
			boolean isStale = false;
			if (snapshot.exists() && !allowStaleData) {
				branch = loadBranch(ts.getProject());
				isStale = checkIsStale(ts, branch, snapshot);
				if (isStale) {
					TermServerScript.warn(ts.getProject() + " snapshot held locally is stale.  Requesting delta to rebuild...");
				} else {
					TermServerScript.debug(ts.getProject() + " snapshot held locally is sufficiently recent");
				}
			}

			if (!snapshot.exists() || 
					(isStale && !allowStaleData) || 
					(populateReleasedFlag && !releasedFlagPopulated && !loadEditionArchive) ||
					(populatePreviousTransativeClosure && gl.getPreviousTC() == null)) {
				
				if (populateReleasedFlag && !releasedFlagPopulated && !loadEditionArchive) {
					info("Generating fresh snapshot because 'released' flag must be populated");
				} else if (populatePreviousTransativeClosure && gl.getPreviousTC() == null) {
					info("Generating fresh snapshot because previous transative closure must be populated");
				}
				generateSnapshot(ts.getProject());
				releasedFlagPopulated=true;
				//We don't need to load the snapshot if we've just generated it
			} else {
				//We might already have this project in memory
				if (currentlyHeldInMemory != null && currentlyHeldInMemory.equals(ts.getProject()) && 
						(populateReleasedFlag == false || (populateReleasedFlag && releasedFlagPopulated))) {
					info (ts.getProject() + " already held in memory, no need to reload.  Resetting any issues held against components...");
					gl.makeReady();
				} else {
					if (currentlyHeldInMemory != null) {
						//Make sure the Graph Loader is clean if we're loading a different project
						info(currentlyHeldInMemory.getKey() + " being wiped to make room for " + ts.getProject());
						gl.reset();
						System.gc();
						releasedFlagPopulated = false;
					}
					//Do we also need a fresh snapshot here so we can have the 'released' flag?
					//If we're loading an edition archive then that is - by definition all released.
					if (populateReleasedFlag && !releasedFlagPopulated && !loadEditionArchive) {
						info("Generating fresh snapshot (despite having a non-stale on disk) because 'released' flag must be populated");
						gl.reset();
						generateSnapshot (ts.getProject());
						releasedFlagPopulated=true;
					} else {
						info ("Loading snapshot archive contents into memory: " + snapshot);
						try {
							//This archive is 'current state' so we can't know what is released or not
							//Unless it's an edition archive
							releasedFlagPopulated = loadEditionArchive;
							//We only know if the components are released when loading an edition archive
							Boolean isReleased = loadEditionArchive ? true : null;
							loadArchive(snapshot, fsnOnly, "Snapshot", isReleased);
						} catch (UnrecoverableTermServerScriptException unrecoverable) {
							throw unrecoverable;
						} catch (Exception e) {
							TermServerScript.error ("Non-viable snapshot encountered (Exception: " + e.getMessage()  +").", e);
							if (!snapshot.getName().startsWith("releases/")) {
								TermServerScript.info ("Deleting " + snapshot);
								try {
									if (snapshot.isFile()) {
										snapshot.delete();
									} else if (snapshot.isDirectory()) {
										FileUtils.deleteDirectory(snapshot);
									} else {
										throw new TermServerScriptException (snapshot + " is neither file nor directory.");
									}
								} catch (Exception e2) {
									TermServerScript.warn("Failed to delete snapshot " + snapshot + " due to " + e2);
								}
							} else {
								TermServerScript.info ("Not deleting " + snapshot + " as it's a release.");
							}
							//We were trying to load the archive from disk.  If it's been created from a delta, we can try that again
							//Next time round the snapshot on disk won't be detected and we'll take a different code path
							if (!loadEditionArchive) {
								TermServerScript.warn("Attempting to regenerate...");
								loadSnapshot(fsnOnly);
							} 
						}
					}
				}
			}
			currentlyHeldInMemory = ts.getProject();
			allowStaleData = originalStateDataFlag;
		} catch (Exception e) {
			String msg = ExceptionUtils.getExceptionCause("Unable to load " + ts.getProject(), e);
			throw new TermServerScriptException (msg, e);
		}
		info ("Snapshot loading complete, checking integrity");
		checkIntegrity(fsnOnly);
	}
	
	private void checkIntegrity(boolean fsnOnly) throws TermServerScriptException {
		if (gl.getAllConcepts().size() < 300000) {
			throw new TermServerScriptException("Insufficient number of concepts loaded " + gl.getAllConcepts().size() + " - Snapshot archive damaged?");
		}
		
		if (isRunIntegrityChecks()) {
			//Ensure that every active parent other than root has at least one parent in both views
			debug("Ensuring all concepts have parents and depth if required.");
			StringBuffer integrityFailureMessage = new StringBuffer();
			for (Concept c : gl.getAllConcepts()) {
				/*if (c.getId().equals("15747361000119104")) {
					debug("here");
				}*/
				if (integrityCheckIgnoreList.contains(c.getId())) {
					continue;
				}
				if (c.isActive() && !c.equals(ROOT_CONCEPT)) {
					checkParentalIntegrity(c, CharacteristicType.INFERRED_RELATIONSHIP, integrityFailureMessage);
					if (expectStatedParents) {
						checkParentalIntegrity(c, CharacteristicType.STATED_RELATIONSHIP, integrityFailureMessage);
					}
				}
				
				if (populateHierarchyDepth && c.isActive() && c.getDepth() == NOT_SET) {
					if (integrityFailureMessage.length() > 0) {
						integrityFailureMessage.append(",\n");
					}
					integrityFailureMessage.append(c + " failed to populate depth");
					String ancestorStr = c.getAncestors(NOT_SET).stream().map(a -> a.toString()).collect(Collectors.joining(","));
					TermServerScript.warn(c + " ancestors are :" + ancestorStr );
				}
			}
			if (integrityFailureMessage.length() > 0) {
				throw new UnrecoverableTermServerScriptException(integrityFailureMessage.toString());
			}
			info("Integrity check passed.  All concepts have at least one stated and one inferred active parent");
		}
		
		if (!fsnOnly) {  
			//Check that we've got some descriptions to be sure we've not been given
			//a malformed, or classification style archive.
			debug("Checking first 100 concepts for integrity");
			List<Description> first100Descriptions = gl.getAllConcepts()
					.stream()
					.limit(100)
					.flatMap(c -> c.getDescriptions().stream())
					.collect(Collectors.toList());
			if (first100Descriptions.size() < 100) {
				throw new TermServerScriptException("Failed to find sufficient number of descriptions - classification archive used? Deleting snapshot, please retry.");
			}
			debug("Integrity check complete");
		}
	}

	private boolean checkIsStale(TermServerScript ts, Branch branch, File snapshot) throws IOException {
		Date branchHeadTime = new Date(branch.getHeadTimestamp());
		BasicFileAttributes attr = java.nio.file.Files.readAttributes(snapshot.toPath(), BasicFileAttributes.class);
		LocalDateTime snapshotCreation = LocalDateTime.ofInstant(Instant.ofEpochMilli(attr.creationTime().toMillis()), ZoneId.systemDefault());
		//What timezone is that in?
		TimeZone localZone = TimeZone.getDefault();
		ZonedDateTime snapshotCreationLocal = ZonedDateTime.of(snapshotCreation, localZone.toZoneId());
		ZonedDateTime snapshotCreationUTC = snapshotCreationLocal.withZoneSameInstant(utcZoneID);
		ZonedDateTime branchHeadUTC = ZonedDateTime.ofInstant(branchHeadTime.toInstant(), utcZoneID);
		TermServerScript.debug("Comparing branch time: " + branchHeadUTC + " to local " + snapshot.getName() + " snapshot time: " + snapshotCreationUTC);
		return branchHeadUTC.compareTo(snapshotCreationUTC) > 0;
	}

	private void generateSnapshot(Project project) throws TermServerScriptException, IOException {
		File snapshot = getSnapshotPath();
		//Delete the current snapshot if it exists - will be stale
		if (snapshot.isDirectory()) {
			FileUtils.deleteDirectory(snapshot);
		} else {
			java.nio.file.Files.deleteIfExists(snapshot.toPath());
		}
		
		ensureProjectMetadataPopulated(project);
	
		File previous = new File (dataStoreRoot + "releases/"  + project.getMetadata().getPreviousPackage());
		if (!previous.exists()) {
			getArchiveDataLoader().download(previous);
		}
		TermServerScript.info("Building snapshot release based on previous: " + previous);
		
		//In the case of managed service, we will also have a dependency package
		File dependency = null;
		if (project.getMetadata().getDependencyPackage() != null) {
			dependency = new File (dataStoreRoot + "releases/"  + project.getMetadata().getDependencyPackage());
			if (!dependency.exists()) {
				getArchiveDataLoader().download(dependency);
			}
			TermServerScript.info("Building Extension snapshot release also based on dependency: " + dependency);
		}
		
		//Now we need a recent delta to add to it
		File delta = generateDelta(project);
		SnapshotGenerator snapshotGenerator = new SnapshotGenerator();
		snapshotGenerator.setProject(ts.getProject());
		snapshotGenerator.leaveArchiveUncompressed();
		snapshotGenerator.setOutputDirName(snapshot.getPath());
		snapshotGenerator.generateSnapshot(ts, dependency, previous, delta, snapshot);
	}
	
	private ArchiveDataLoader getArchiveDataLoader() throws TermServerScriptException {
		if (archiveDataLoader == null) {
			if (appContext == null) {
				TermServerScript.info("No ArchiveData loader configured, creating one locally...");
				archiveDataLoader = ArchiveDataLoader.create();
			} else {
				archiveDataLoader = appContext.getBean(ArchiveDataLoader.class);
			}
		}
		return archiveDataLoader;
	}
	
	public File generateDelta(Project project) throws IOException, TermServerScriptException {
		return generateDelta(project, false);
	}

	public File generateDelta(Project project, boolean unpromotedChangesOnly) throws IOException, TermServerScriptException {
		File delta = File.createTempFile("delta_export-", ".zip");
		delta.deleteOnExit();
		ts.getTSClient().export(project.getBranchPath(), null, ExportType.UNPUBLISHED, ExtractType.DELTA, delta, unpromotedChangesOnly);
		return delta;
	}

	private void ensureProjectMetadataPopulated(Project project) throws TermServerScriptException {
		if (project.getMetadata() == null || project.getMetadata().getPreviousPackage() == null) {
			boolean metadataPopulated = false;
			String branchPath = project.getBranchPath();
			while (!metadataPopulated) {
				Branch branch = ts.getTSClient().getBranch(branchPath);
				project.setMetadata(branch.getMetadata());
				if (project.getMetadata() != null && project.getMetadata().getPreviousPackage() != null) {
					metadataPopulated = true;
				} else {
					int cutPoint = branchPath.lastIndexOf("/");
					if (cutPoint == NOT_FOUND) {
						throw new TermServerScriptException("Insufficient metadata found for project " + project.getKey() + " including ancestors to " + branchPath);
					} else {
						branchPath = branchPath.substring(0, cutPoint);
					}
				}
			}
		}
	}

	private File getSnapshotPath() {
		//If the project specifies its a .zip file, that's another way to know we're loading an edition
		String fileExt = ".zip";
		String projectTaskKey = ts.getProject().getKey();
		
		if (ts.getJobRun() != null && !StringUtils.isEmpty(ts.getJobRun().getTask())) {
			projectTaskKey += "_" + ts.getJobRun().getTask();
		} else if (ts.getTaskKey() != null) {
			projectTaskKey += "_" + ts.getTaskKey();
		}
		
		if (loadEditionArchive || 
				StringUtils.isNumeric(projectTaskKey) ||
				projectTaskKey.endsWith(fileExt)) {
			if (projectTaskKey.endsWith(fileExt)) {
				fileExt = "";
			}
			return new File (dataStoreRoot + "releases/" + projectTaskKey + fileExt);
		} else {
			//Do we have a release effective time as a project?  Or a branch release
			String releaseBranch = detectReleaseBranch(projectTaskKey);
			if (releaseBranch != null) {
				info ("Release branch determined to be numeric: " + releaseBranch);
				return new File (dataStoreRoot + "releases/" + releaseBranch + ".zip");
			} else  {
				return new File (dataStoreRoot + "snapshots/" + projectTaskKey + "_" + ts.getEnv());
			}
		}
	}

	public String detectReleaseBranch(String projectKey) {
		String releaseBranch = projectKey.replace("MAIN/", "").replace("-", "");
		return StringUtils.isNumeric(releaseBranch) ? releaseBranch : null;
	}

	protected void loadArchive(File archive, boolean fsnOnly, String fileType, Boolean isReleased) throws TermServerScriptException {
		try {
			boolean isDelta = (fileType.equals(DELTA));
			//Are we loading an expanded or compressed archive?
			if (archive.isDirectory()) {
				loadArchiveDirectory(archive, fsnOnly, fileType, isDelta, isReleased);
			} else if (archive.getPath().endsWith(".zip")) {
				TermServerScript.debug("Loading archive file: " + archive);
				loadArchiveZip(archive, fsnOnly, fileType, isDelta, isReleased);
			} else {
				throw new TermServerScriptException("Unrecognised archive : " + archive);
			}
			
			//Are we generating the transitive closure?
			if (fileType.equals(SNAPSHOT) && populatePreviousTransativeClosure) {
				gl.populatePreviousTransativeClosure();
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to extract project state from archive " + archive.getName(), e);
		}
	}

	private void checkParentalIntegrity(Concept c, CharacteristicType charType, StringBuffer sb) throws TermServerScriptException {
		Set<Concept> parents = c.getParents(charType);
		if (parents.size() == 0) {
			if (sb.length() > 0) {
				sb.append(",\n");
			}
			sb.append(c + " has no " + charType + " parents");
		}
		
		for (Concept parent : parents) {
			if (!parent.isActive()) {
				sb.append(c + " has inactive " + charType + " parent: " + parent);
			}
		}
		
		//Check that we've captured those parents correctly;
		//Looping through existing objects rather than calling getRelationships so we're 
		//not creating new collections.   getRelationships does all the looping anyway, so no cheaper.
		int parentRelCount = 0;
		for (Relationship r : c.getRelationships()) {
			if (r.isActive() && r.getCharacteristicType().equals(charType)
					&& r.getType().equals(IS_A)) {
				parentRelCount++;
				if (!parents.contains(r.getTarget())) {
					if (sb.length() > 0) {
						sb.append(",\n");
					}
					sb.append(c + " has internal " + charType + " inconsistency between parents and parental relationship for parent " + r.getTarget());
				}
			}
		}
		
		if (parentRelCount != parents.size()) {
			//Trying for minimal memory allocations here, so only check for duplicate targets between 
			//axioms if we detect a problem
			Set<Concept> parentsFromRels = SnomedUtils.getTargets(c, new Concept[] {IS_A}, charType);
			if (parentsFromRels.size() != parents.size()) {
				if (sb.length() > 0) {
					sb.append(",\n");
				}
				sb.append(c + " has internal " + charType + " inconsistency between parents and parental relationship count");
			}
		}
	}

	private void loadArchiveZip(File archive, boolean fsnOnly, String fileType, boolean isDelta, Boolean isReleased) throws IOException, TermServerScriptException {
		ZipInputStream zis = new ZipInputStream(new FileInputStream(archive));
		ZipEntry ze = zis.getNextEntry();
		try {
			while (ze != null) {
				if (!ze.isDirectory()) {
					Path path = Paths.get(ze.getName());
					loadFile(path, zis, fileType, isDelta, fsnOnly, isReleased);
				}
				ze = zis.getNextEntry();
			}
		}  finally {
			try{
				zis.closeEntry();
				zis.close();
			} catch (Exception e){} //Well, we tried.
		}
	}
	
	private void loadArchiveDirectory(File dir, boolean fsnOnly, String fileType, boolean isDelta, Boolean isReleased) throws IOException {
		try (Stream<Path> paths = Files.walk(dir.toPath())) {
			paths.filter(Files::isRegularFile)
			.forEach( path ->  {
				try {
					InputStream is = toInputStream(path);
					loadFile(path, is , fileType, isDelta, fsnOnly, isReleased);
					is.close();
				} catch (Exception e) {
					throw new RuntimeException ("Faied to load " + path + " due to " + e.getMessage(),e);
				}
			});
		} 
	}
	
	private InputStream toInputStream(Path path) {
		InputStream is;
		try {
			is = new BufferedInputStream(Files.newInputStream(path));
		} catch (IOException e) {
			throw new IllegalStateException("Unable to load " + path, e);
		}
		return is;
	}

	private void loadFile(Path path, InputStream is, String fileType, boolean isDelta, boolean fsnOnly, Boolean isReleased)  {
		try {
			String fileName = path.getFileName().toString();
			
			if (fileName.contains("._")) {
				//info("Skipping " + fileName);
				return;
			}
			
			if (fileName.contains(fileType)) {
				if (fileName.contains("sct2_Concept_" )) {
					info("Loading Concept " + fileType + " file.");
					gl.loadConceptFile(is, isReleased);
				} else if (fileName.contains("sct2_Relationship_" )) {
					info("Loading Relationship " + fileType + " file.");
					gl.loadRelationships(CharacteristicType.INFERRED_RELATIONSHIP, is, true, isDelta, isReleased);
					if (populateHierarchyDepth) {
						info("Calculating concept depth...");
						gl.populateHierarchyDepth(ROOT_CONCEPT, 0);
					}
				} else if (fileName.contains("sct2_StatedRelationship_" )) {
					info("Loading StatedRelationship " + fileType + " file.");
					gl.loadRelationships(CharacteristicType.STATED_RELATIONSHIP, is, true, isDelta, isReleased);
				} else if (fileName.contains("sct2_RelationshipConcrete" )) {
					info("Loading Concrete Relationship " + fileType + " file.");
					gl.loadRelationships(CharacteristicType.INFERRED_RELATIONSHIP, is, true, isDelta, isReleased);
				} else if (fileName.contains("sct2_sRefset_OWLExpression" ) ||
						   fileName.contains("sct2_sRefset_OWLAxiom" )) {
					info("Loading Axiom " + fileType + " refset file.");
					gl.loadAxioms(is, isDelta, isReleased);
				} else if (fileName.contains("sct2_Description_" )) {
					info("Loading Description " + fileType + " file.");
					int count = gl.loadDescriptionFile(is, fsnOnly, isReleased);
					info("Loaded " + count + " descriptions.");
				} else if (fileName.contains("sct2_TextDefinition_" )) {
					info("Loading Text Definition " + fileType + " file.");
					gl.loadDescriptionFile(is, fsnOnly, isReleased);
				} else if (fileName.contains("der2_cRefset_ConceptInactivationIndicatorReferenceSet" )) {
					info("Loading Concept Inactivation Indicator " + fileType + " file.");
					gl.loadInactivationIndicatorFile(is, isReleased);
				} else if (fileName.contains("der2_cRefset_DescriptionInactivationIndicatorReferenceSet" )) {
					info("Loading Description Inactivation Indicator " + fileType + " file.");
					gl.loadInactivationIndicatorFile(is, isReleased);
				} else if (fileName.contains("der2_cRefset_AttributeValue" )) {
					info("Loading Concept/Description Inactivation Indicators " + fileType + " file.");
					gl.loadInactivationIndicatorFile(is, isReleased);
				} else if (fileName.contains("Association" ) || fileName.contains("AssociationReferenceSet" )) {
					info("Loading Historical Association File: " + fileName);
					gl.loadHistoricalAssociationFile(is, isReleased);
				} else if (fileName.contains("MRCMDomain")) {
					info("Loading MRCM Domain File: " + fileName);
					gl.loadMRCMDomainFile(is, isReleased);
				} else if (fileName.contains("MRCMAttributeRange")) {
					info("Loading MRCM AttributeRange File: " + fileName);
					gl.loadMRCMAttributeRangeFile(is, isReleased);
				}
				//If we're loading all terms, load the language refset as well
				if (!fsnOnly && (fileName.contains("English" ) || fileName.contains("Language"))) {
					info("Loading " + fileType + " Language Reference Set File - " + fileName);
					gl.loadLanguageFile(is, isReleased);
				}
			}
		} catch (TermServerScriptException | IOException e) {
			throw new IllegalStateException("Unable to load " + path + " due to " + e.getMessage(), e);
		}
	}

	public boolean isAllowStaleData() {
		return allowStaleData;
	}

	public void setAllowStaleData(boolean allowStaleData) {
		this.allowStaleData = allowStaleData;
	}

	public boolean isLoadDependencyPlusExtensionArchive() {
		return loadDependencyPlusExtensionArchive;
	}

	public void setLoadDependencyPlusExtensionArchive(boolean loadDependencyPlusExtensionArchive) {
		this.loadDependencyPlusExtensionArchive = loadDependencyPlusExtensionArchive;
	}

	public boolean isLoadEditionArchive() {
		return loadEditionArchive;
	}

	public void setLoadEditionArchive(boolean loadEditionArchive) {
		this.loadEditionArchive = loadEditionArchive;
	}

	public boolean isPopulateHierarchyDepth() {
		return populateHierarchyDepth;
	}

	public void setPopulateHierarchyDepth(boolean populateHierarchyDepth) {
		this.populateHierarchyDepth = populateHierarchyDepth;
	}

	public boolean isPopulateReleasedFlag() {
		return populateReleasedFlag;
	}

	public void setPopulateReleasedFlag(boolean populateReleasedFlag) {
		this.populateReleasedFlag = populateReleasedFlag;
	}

	public boolean isPopulatePreviousTransativeClosure() {
		return populatePreviousTransativeClosure;
	}

	public void setPopulatePreviousTransativeClosure(boolean populatePreviousTransativeClosure) {
		this.populatePreviousTransativeClosure = populatePreviousTransativeClosure;
	}

	public boolean isReleasedFlagPopulated() {
		return releasedFlagPopulated;
	}

	public void setReleasedFlagPopulated(boolean releasedFlagPopulated) {
		this.releasedFlagPopulated = releasedFlagPopulated;
	}
	
	public void reset() {
		reset(true);
	}

	public void reset(boolean fullReset) {
		//Do we need to reset?
		if (this.gl.getAllConcepts().size() > 100) {
			this.gl.reset();
			this.currentlyHeldInMemory = null;
		}
		
		if (fullReset) {
			singleton.releasedFlagPopulated = false;
			singleton.loadEditionArchive = false;
			singleton.loadDependencyPlusExtensionArchive = false;
			singleton.populatePreviousTransativeClosure = false;
		}
	}
	
	public boolean isRunIntegrityChecks() {
		return runIntegrityChecks;
	}

	public void setRunIntegrityChecks(boolean runIntegrityChecks) {
		if (!runIntegrityChecks) {
			TermServerScript.warn("INTEGRITY CHECK DISABLED - ARE YOU SURE?");
		}
		this.runIntegrityChecks = runIntegrityChecks;
		this.gl.setRunIntegrityChecks(runIntegrityChecks);
	}

	public boolean isExpectStatedParents() {
		return expectStatedParents;
	}

	public void setExpectStatedParents(boolean expectStatedParents) {
		this.expectStatedParents = expectStatedParents;
	}
}
