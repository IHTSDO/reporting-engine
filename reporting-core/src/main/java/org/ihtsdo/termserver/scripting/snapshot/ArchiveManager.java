package org.ihtsdo.termserver.scripting.snapshot;

import java.io.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.UnrecoverableTermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClient.*;
import org.ihtsdo.termserver.scripting.dao.ArchiveDataLoader;
import org.ihtsdo.termserver.scripting.dao.BuildArchiveDataLoader;
import org.ihtsdo.termserver.scripting.dao.DataLoader;
import org.ihtsdo.termserver.scripting.dao.S3Manager;
import org.ihtsdo.termserver.scripting.domain.*;
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

	private static final String SNAPSHOT = "snapshot";
	
	static ArchiveManager singleton;
	private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveManager.class);

	@Autowired
	private ArchiveDataLoader archiveDataLoader;

	@Autowired
	private BuildArchiveDataLoader buildArchiveDataLoader;
	
	protected String dataStoreRoot = "";
	protected GraphLoader gl;
	protected TermServerScript ts;
	protected ApplicationContext appContext;
	private SnapshotConfiguration config = new SnapshotConfiguration();
	private final List<String> integrityCheckIgnoreList = List.of(
			"21000241105", // |Common French language reference set|
			"763158003" // |Medicinal product (product)| Gets created as a constant, but does exist before 20180731
	);
	
	private Project currentlyHeldInMemory;

	ZoneId utcZoneID= ZoneId.of("Etc/UTC");

	public static ArchiveManager getArchiveManager(TermServerScript ts, ApplicationContext appContext) {
		boolean isBrandNew = false;
		boolean underChangedOwnership = false;
		if (singleton == null) {
			singleton = new ArchiveManager();
			singleton.appContext = appContext;
			isBrandNew = true;
		}

		if (singleton.ts == null || !singleton.ts.getClass().getSimpleName().equals(ts.getClass().getSimpleName())) {
			String ownershipIndicator = singleton.ts == null ? "first" : "new";
			if (!isBrandNew) {
				ownershipIndicator = "changed";
				underChangedOwnership = true;
			}
			LOGGER.info("Archive manager under {} ownership due to change of report: {}", ownershipIndicator, ts.getClass().getSimpleName());
		} else if (singleton.currentlyHeldInMemory != null && !singleton.currentlyHeldInMemory.equals(ts.getProject())) {
			underChangedOwnership = true;
			LOGGER.info("Archive manager under changed ownership due to change of project. Previously {}, now {}", singleton.currentlyHeldInMemory, ts.getProject());
		} else {
			LOGGER.info("Archive manager being reused in: {}", ts.getClass().getSimpleName());
		}

		singleton.ts = ts;
		singleton.gl = ts.getGraphLoader();

		if (underChangedOwnership) {
			singleton.reset();
		} else if (!isBrandNew){
			LOGGER.info("Archive Manager load flags retained - reusing.");
		}

		return singleton;
	}
	
	@EventListener(ApplicationReadyEvent.class)
	public void init() {
		singleton = this;
	}
	
	private ArchiveManager () {
		//Only access via singleton above
	}
	
	public boolean isLoadOtherReferenceSets() {
		return config.isLoadOtherReferenceSets();
	}

	public void setLoadOtherReferenceSets(boolean loadOtherReferenceSets) {
		LOGGER.info("Setting loadOtherReferenceSets to {}", loadOtherReferenceSets);
		config.setLoadOtherReferenceSets(loadOtherReferenceSets);
	}

	protected Branch loadBranch(Project project) throws TermServerScriptException {
		String branchPath = project.getBranchPath();
		String server = "unknown";
		try {
			LOGGER.debug("Checking TS branch metadata: {}", branchPath);
			server = ts.getTSClient().getServerUrl();
			Branch branch = ts.getTSClient().getBranch(branchPath);
			if (branch == null) {
				throw new TermServerScriptException("Unable to find branch: '" + branchPath + "'");
			}
			//If metadata is empty, or missing previous release, recover the parent
			//But the timestamp will remain a property of the branch
			//But not if we're already on MAIN, and it's STILL missing!
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
				LOGGER.debug("Unable to find branch {}", branchPath);
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
			.toList();
			
			if (releases.size() < 2) {
				throw new TermServerScriptException("Less than 2 previous releases detected");
			}
			if (!releases.get(0).getEffectiveDate().toString().equals(previousRelease)) {
				LOGGER.warn("Check here - unexpected previous release: {} expected {} ", releases.get(0).getEffectiveDate(), previousRelease);
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
			.toList();
			
			if (releases.isEmpty()) {
				throw new TermServerScriptException("Less than 1 previous releases detected");
			}
			if (!releases.get(0).getEffectiveDate().toString().equals(previousRelease)) {
				throw new TermServerScriptException("Check here - unexpected previous release: " +  releases.get(0).getEffectiveDate() + " expected " + previousRelease);
			}
			LOGGER.info("Detected previous branch: {}", releases.get(0).getBranchPath());
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
		boolean writeSnapshotToCache = false;
		ArchiveImporter archiveImporter = new ArchiveImporter(gl, config);
		try {
			if (config.isLoadDependencyPlusExtensionArchive()) {
				if (StringUtils.isEmpty(ts.getDependencyArchive())) {
					throw new TermServerScriptException("Told to load dependency + extension but no dependency package specified");
				} else {
					LOGGER.info("Loading dependency plus extension archives");
					gl.reset();
					File dependency = new File("releases", ts.getDependencyArchive());
					if (dependency.exists()) {
						archiveImporter.loadArchive(dependency, fsnOnly, SNAPSHOT, true);
					} else {
						//Can we find it in S3?
						String cwd = new File("").getAbsolutePath();
						LOGGER.info("Dependency Archive {} not found locally in {}, attempting to download from S3.", ts.getDependencyArchive(), cwd);
						getArchiveDataLoader().download(dependency);
						if (dependency.exists()) {
							archiveImporter.loadArchive(dependency, fsnOnly, SNAPSHOT, true);
						} else {
							throw new TermServerScriptException("Dependency Package " + dependency.getAbsolutePath() + " does not exist and was not recovered from S3.");
						}
					}
					//Now let's not pretend we're holding anything in memory at this point, because we still have to load in
					//the extension before we have that.
					currentlyHeldInMemory = null;
				}
			}

			//If the project specifies it's a .zip file, that's another way to know we're loading an edition
			String fileExt = ".zip";
			if (ts.getProject().getKey().endsWith(fileExt)) {
				LOGGER.info("Project key ('{}') identified as zip archive, loading Edition Archive", ts.getProject().getKey());
				config.setLoadEditionArchive(true);
			}

			//Look for an expanded directory by preference
			File snapshot = getSnapshotPath();
			LOGGER.info("Snapshot path {}", snapshot.getPath());
			if (!snapshot.exists() && !snapshot.getName().endsWith(fileExt)) {
				//Otherwise, do we have a zip file to play with?
				snapshot = new File (snapshot.getPath() + fileExt);
			}
			
			if (!snapshot.exists()) {
				downloadSnapshot(snapshot);
			}
			
			boolean originalStateDataFlag = config.isAllowStaleData();
			//If we're loading a particular release, it will be stale
			if (config.isLoadEditionArchive() || StringUtils.isNumeric(ts.getProject().getKey())) {
				config.setLoadEditionArchive(true);
				config.setAllowStaleData(true);
				if (config.isLoadEditionArchive() && !snapshot.exists()) {
					throw new TermServerScriptException ("Could not find " + snapshot + " to import");
				}
			}
			
			Branch branch = null;
			//Are we lacking data, or is our data out of date?  
			boolean isStale = false;
			if (snapshot.exists() && !config.isAllowStaleData()) {
				branch = loadBranch(ts.getProject());
				isStale = checkIsStale(branch, snapshot);
				if (isStale) {
					LOGGER.warn("{} snapshot held locally is stale.  Requesting delta to rebuild...", ts.getProject());
				} else {
					LOGGER.debug("{} snapshot held locally is sufficiently recent", ts.getProject());
				}
			}

			if (!snapshot.exists() ||
					(isStale && !config.isAllowStaleData()) ||
					((config.isEnsureSnapshotPlusDeltaLoad() || config.isPopulateReleaseFlag()) && !config.isLoadEditionArchive()) ||
					(config.isPopulatePreviousTransitiveClosure() && gl.getPreviousTC() == null)) {
				
				if ((config.isEnsureSnapshotPlusDeltaLoad() || config.isPopulateReleaseFlag()) && !config.isLoadEditionArchive()) {
					LOGGER.info("Generating fresh snapshot because 'ensureSnapshotPlusDeltaLoad' or 'populateReleaseFlag' is set, and not loading from edition archive");
				} else if (config.isPopulatePreviousTransitiveClosure() && gl.getPreviousTC() == null) {
					LOGGER.info("Generating fresh snapshot because previous transitive closure must be populated");
				}
				generateSnapshot(ts.getProject(), archiveImporter);
				setPopulateReleaseFlag(true);
				writeSnapshotToCache = true;
				//We don't need to load the snapshot if we've just generated it
			} else {
				//We might already have this project in memory
				if (currentlyHeldInMemory != null && currentlyHeldInMemory.equals(ts.getProject()) && 
						(!config.isEnsureSnapshotPlusDeltaLoad() || (config.isEnsureSnapshotPlusDeltaLoad() && config.isPopulateReleaseFlag()))) {
					LOGGER.info("{} already held in memory, no need to reload.  Resetting any issues held against components...", ts.getProject());
					gl.makeReady();
				} else {
					if (currentlyHeldInMemory != null) {
						//Make sure the Graph Loader is clean if we're loading a different project
						LOGGER.info("{} being wiped to make room for {}", currentlyHeldInMemory.getKey(), ts.getProject());
						gl.reset();
						config.setPopulateReleaseFlag(false);
					}
					//Do we also need a fresh snapshot here so we can have the 'released' flag?
					//If we're loading an edition archive, then that is - by definition - all released.
					if (config.isEnsureSnapshotPlusDeltaLoad() && !config.isPopulateReleaseFlag() && !config.isLoadEditionArchive()) {
						LOGGER.info("Generating fresh snapshot (despite having a non-stale on disk) because 'released' flag must be populated");
						gl.reset();
						generateSnapshot(ts.getProject(), archiveImporter);
						writeSnapshotToCache = true;
						config.setPopulateReleaseFlag(true);
					} else {
						loadSnapshotArchiveIntoMemory(fsnOnly, snapshot, archiveImporter);
					}
				}
			}
			currentlyHeldInMemory = ts.getProject();
			config.setAllowStaleData(originalStateDataFlag);
		} catch (Exception e) {
			String msg = ExceptionUtils.getExceptionCause("Unable to load " + ts.getProject(), e);
			if (e instanceof TermServerScriptException) {
				//No need to include the cause here, it's already in the message
				msg = "Unable to load " + ts.getProject();
			}
			throw new TermServerScriptException (msg, e);
		}
		LOGGER.info("Snapshot loading complete, checking integrity");
		checkIntegrity(fsnOnly);

		if (writeSnapshotToCache ) {
			archiveImporter.writeSnapshotToCache(ts, SCTID_CORE_MODULE);
		}
		
		LOGGER.info("Setting all components to be clean");

		//Make sure to include stated rels in our clean-up, otherwise delta generators
		//will output every axiom!
		gl.getAllConcepts().stream()
			.flatMap(c -> SnomedUtils.getAllComponents(c, true).stream())
			.forEach(Component::setClean);
	}

	private void downloadSnapshot(File snapshot) {
		//If it doesn't exist as a zip file locally either, we can try downloading it from S3
		try {
			String cwd = new File("").getAbsolutePath();
			LOGGER.info("{} not found locally in {}, attempting to download from S3.", snapshot, cwd);
			getArchiveDataLoader().download(snapshot);
		} catch (TermServerScriptException e) {
			LOGGER.info("Could not find {} in S3.", snapshot.getName());
		}
	}

	private void loadSnapshotArchiveIntoMemory(boolean fsnOnly, File snapshot, ArchiveImporter archiveImporter) throws TermServerScriptException {
		LOGGER.info("Loading snapshot archive contents into memory: {}", snapshot);
		try {
			//This archive is 'current state' so we can't know what is released or not
			//Unless it's an edition archive
			config.setPopulateReleaseFlag(config.isLoadEditionArchive());
			//We only know if the components are released when loading an edition archive
			Boolean isReleased = config.isLoadEditionArchive() ? true : null;
			archiveImporter.loadArchive(snapshot, fsnOnly, "Snapshot", isReleased);
		} catch (UnrecoverableTermServerScriptException unrecoverable) {
			throw unrecoverable;
		} catch (Exception e) {
			LOGGER.error("Non-viable snapshot encountered (Exception: " + e.getMessage()  +").", e);
			if (!snapshot.getPath().startsWith("releases/")) {
				LOGGER.info("Deleting {}", snapshot);
				try {
					if (snapshot.isFile()) {
						snapshot.delete();
					} else if (snapshot.isDirectory()) {
						FileUtils.deleteDirectory(snapshot);
					} else {
						throw new TermServerScriptException (snapshot + " is neither file nor directory.");
					}
				} catch (Exception e2) {
					LOGGER.error("Failed to delete snapshot {} due to ", snapshot, e2);
				}
			} else {
				LOGGER.info("Not deleting {} as it's a release, but you should look at it!", snapshot);
			}
			//We were trying to load the archive from disk.  If it's been created from a delta, we can try that again
			//Next time round the snapshot on disk won't be detected, and we'll take a different code path
			if (!config.isLoadEditionArchive()) {
				LOGGER.warn("Attempting to regenerate...");
				loadSnapshot(fsnOnly);
			}
		}
	}

	private void checkIntegrity(boolean fsnOnly) throws TermServerScriptException {
		if (gl.getAllConcepts().size() < 300000) {
			throw new TermServerScriptException("Insufficient number of concepts loaded " + gl.getAllConcepts().size() + " - Snapshot archive damaged?");
		}
		
		if (isRunIntegrityChecks()) {
			//Ensure that every active parent other than root has at least one parent in both views
			LOGGER.debug("Ensuring all concepts have parents and depth if required.");
			StringBuilder integrityFailureMessage = new StringBuilder();
			//We need a separate copy of all concepts because we might modify it in passing if we encounter a phantom concept
			for (Concept c : new ArrayList<>(gl.getAllConcepts())) {
				if (integrityCheckIgnoreList.contains(c.getId())) {
					continue;
				}
				
				if (checkForPhantomConcept(c)) {
					continue;  //In this case we did find a phantom concept, but we'll skip and keep going
				}
				
				if (c.isActiveSafely() && !c.equals(ROOT_CONCEPT)) {
					checkParentalIntegrity(c, CharacteristicType.INFERRED_RELATIONSHIP, integrityFailureMessage);
					if (config.isExpectStatedParents()) {
						checkParentalIntegrity(c, CharacteristicType.STATED_RELATIONSHIP, integrityFailureMessage);
					}
				} else if (!c.isActiveSafely()) {
					if (!c.getParents(CharacteristicType.INFERRED_RELATIONSHIP).isEmpty()) {
						integrityFailureMessage.append(c + " is inactive but has inferred parents.");
					}
					
					if (!c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP).isEmpty()) {
						integrityFailureMessage.append(c + " is inactive but has inferred children.");
					}
				}
				
				if (config.isPopulateHierarchyDepth() && c.isActiveSafely() && c.getDepth() == NOT_SET) {
					if (!integrityFailureMessage.isEmpty()) {
						integrityFailureMessage.append(",\n");
					}
					integrityFailureMessage.append(c).append(" failed to populate depth");
					String ancestorStr = c.getAncestors(NOT_SET).stream().map(Concept::toString).collect(Collectors.joining(","));
					LOGGER.warn("{} ancestors are : {}", c, ancestorStr);
				}
			}
			if (!integrityFailureMessage.isEmpty()) {
				throw new UnrecoverableTermServerScriptException(integrityFailureMessage.toString());
			}
			LOGGER.info("Integrity check passed.  All concepts have at least one stated and one inferred active parent");
		}
		
		if (!fsnOnly) {  
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
			LOGGER.debug("Integrity check complete");
		}
	}

	private boolean checkForPhantomConcept(Concept c) {
		if (c.getActive() == null) {
			//Now SOMETHING had a reference to this concept, so let's try and work out what and
			//report that, rather than talk about a concept that doesn't exist
			String msg = determineSourceofPhantomConcept(c);
			if (ts.getDependencyArchive() != null) {
				msg += ". Check dependency is appropriate - " + ts.getDependencyArchive();
			}
			//Now if we've imported all reference sets and we've got a phantom concept that's coming from an
			//inactive referenceset member, then we're just going to report that as a "final word" rather than
			//bomb out the entire report
			if (config.isLoadOtherReferenceSets() && msg.contains("*RM")) {
				LOGGER.warn("Recording final words rather than throwing exception: {}", msg);
				ts.addFinalWords(msg);
				//And we're going to remove this concept so that we don't trip over it again
				ts.getGraphLoader().removeConcept(c);
				return true;
			} else {
				throw new IllegalStateException(msg);
			}
		}
		return false;
	}

	private String determineSourceofPhantomConcept(Concept c) {
		//Which components referenced this concept?
		Collection<Component> components = SnomedUtils.getAllComponents(c);
		if (components.isEmpty()) {
			return "Integrity concern: concept " + c.getId() + " does not appear in concept file and is not referenced by any components.  Could have come in via WhiteListing?";
		}
		//Reduce the count by 1 because the concept itself gets counted, and that's a phantom.
		int refCount = components.size()-1;

		//If the concept is not referenced by any of it's own components, then we'll see what other concepts reference it.
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
			for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.BOTH)) {
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
		return "No non-concept components found.";
	}

	private boolean checkIsStale(Branch branch, File snapshot) throws IOException {
		Date branchHeadTime = new Date(branch.getHeadTimestamp());
		BasicFileAttributes attr = java.nio.file.Files.readAttributes(snapshot.toPath(), BasicFileAttributes.class);
		LocalDateTime snapshotCreation = LocalDateTime.ofInstant(Instant.ofEpochMilli(attr.creationTime().toMillis()), ZoneId.systemDefault());
		//What timezone is that in?
		TimeZone localZone = TimeZone.getDefault();
		ZonedDateTime snapshotCreationLocal = ZonedDateTime.of(snapshotCreation, localZone.toZoneId());
		ZonedDateTime snapshotCreationUTC = snapshotCreationLocal.withZoneSameInstant(utcZoneID);
		ZonedDateTime branchHeadUTC = ZonedDateTime.ofInstant(branchHeadTime.toInstant(), utcZoneID);
		LOGGER.debug("Comparing branch time: {} to local {} snapshot time: {}", branchHeadUTC,  snapshot.getName(), snapshotCreationUTC);
		return branchHeadUTC.compareTo(snapshotCreationUTC) > 0;
	}

	private void generateSnapshot(Project project, ArchiveImporter archiveImporter) throws TermServerScriptException, IOException {
		File snapshot = getSnapshotPath();
		//Delete the current snapshot if it exists - will be stale
		if (snapshot.isDirectory()) {
			FileUtils.deleteDirectory(snapshot);
		} else {
			java.nio.file.Files.deleteIfExists(snapshot.toPath());
		}
		
		ensureProjectMetadataPopulated(project);
	
		File previous = determinePreviousPackage(project);
		
		//In the case of managed service, we will also have a dependency package
		File dependency = determineDependencyIfRequired(project);
		
		//Now we need a recent delta to add to it
		File delta = generateDelta(project);
		archiveImporter.generateSnapshot(dependency, previous, delta, snapshot);
	}

	private File determineDependencyIfRequired(Project project) throws TermServerScriptException {
		File dependency = null;
		if (project.getMetadata().getDependencyPackage() != null) {
			dependency = new File (dataStoreRoot + "releases/"  + project.getMetadata().getDependencyPackage());
			if (!dependency.exists()) {
				getArchiveDataLoader().download(dependency);
			}
			LOGGER.info("Building Extension snapshot release also based on dependency: {}", dependency);
		}
		return dependency;
	}

	public File determinePreviousPackage(Project project) throws TermServerScriptException {
		File previous = new File (dataStoreRoot + "releases/"  + project.getMetadata().getPreviousPackage());
		if (!previous.exists()) {
			getArchiveDataLoader().download(previous);
		}
		LOGGER.info("Building snapshot release based on previous: {}", previous);
		return previous;
	}

	private DataLoader getArchiveDataLoader() throws TermServerScriptException {
		LOGGER.debug("In getArchiveLoader method, scriptName = {}", ts.getScriptName());
		if (ts.getScriptName().equals("PackageComparisonReport")) {
			return getBuildArchiveDataLoader();
		}
		if (archiveDataLoader == null) {
			if (appContext == null) {
				LOGGER.info("No ArchiveDataLoader configured, creating one locally...");
				archiveDataLoader = ArchiveDataLoader.create();
			} else {
				archiveDataLoader = appContext.getBean(ArchiveDataLoader.class);
			}
		}
		return archiveDataLoader;
	}

	private DataLoader getBuildArchiveDataLoader() throws TermServerScriptException {
		LOGGER.debug("In getBuildArchiveDataLoader method");
		if (buildArchiveDataLoader == null) {
			if (appContext == null) {
				LOGGER.info("No BuildArchiveDataLoader configured, creating one locally...");
				buildArchiveDataLoader = BuildArchiveDataLoader.create();
			} else {
				buildArchiveDataLoader = appContext.getBean(BuildArchiveDataLoader.class);
			}
		}
		return buildArchiveDataLoader;
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
				if (branch == null) {
					throw new TermServerScriptException("Unable to find branch '" + branchPath + "'");
				}
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
		
		if (config.isLoadEditionArchive() ||
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
				LOGGER.info("Release branch determined to be numeric: {}", releaseBranch);
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



	private void checkParentalIntegrity(Concept c, CharacteristicType charType, StringBuilder sb) {
		Set<Concept> parents = c.getParents(charType);
		if (parents.isEmpty()) {
			if (!sb.isEmpty()) {
				sb.append(",\n");
			}
			sb.append(c).append(" has no ").append(charType).append(" parents.");
		}
		
		for (Concept parent : parents) {
			if (checkForPhantomConcept(parent)) {
				continue;
			}
			if (!parent.isActiveSafely()) {
				if (!sb.isEmpty()) {
					sb.append(",\n");
				}
				sb.append(c).append(" has inactive ").append(charType).append(" parent: ").append(parent);
			}
		}
		
		//Check that we've captured those parents correctly
		//Looping through existing objects rather than calling getRelationships so we're 
		//not creating new collections.   getRelationships does all the looping anyway, so no cheaper.
		int parentRelCount = 0;
		for (Relationship r : c.getRelationships()) {
			if (r.isActiveSafely() && r.getCharacteristicType().equals(charType)
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
				if (!sb.isEmpty()) {
					sb.append(",\n");
				}
				sb.append(c + " has internal " + charType + " inconsistency between parents and parental relationship count");
			}
		}
	}

	public boolean isAllowStaleData() {
		return config.isAllowStaleData();
	}

	public void setAllowStaleData(boolean allowStaleData) {
		config.setAllowStaleData(allowStaleData);
	}

	public boolean isLoadDependencyPlusExtensionArchive() {
		return config.isLoadDependencyPlusExtensionArchive();
	}

	public void setLoadDependencyPlusExtensionArchive(boolean loadDependencyPlusExtensionArchive) {
		config.setLoadDependencyPlusExtensionArchive(loadDependencyPlusExtensionArchive);
	}

	public void setLoadEditionArchive(boolean loadEditionArchive) {
		config.setLoadEditionArchive(loadEditionArchive);
	}

	public void setPopulateHierarchyDepth(boolean populateHierarchyDepth) {
		config.setPopulateHierarchyDepth(populateHierarchyDepth);
	}

	public boolean isEnsureSnapshotPlusDeltaLoad() {
		return config.isEnsureSnapshotPlusDeltaLoad();
	}

	public void setEnsureSnapshotPlusDeltaLoad(boolean ensureSnapshotPlusDeltaLoad) {
		config.setEnsureSnapshotPlusDeltaLoad(ensureSnapshotPlusDeltaLoad);
	}

	public void setPopulatePreviousTransitiveClosure(boolean populatePreviousTransitiveClosure) {
		config.setPopulatePreviousTransitiveClosure(populatePreviousTransitiveClosure);
	}

	public void setPopulateReleaseFlag(boolean populateReleaseFlag) {
		config.setPopulateReleaseFlag(populateReleaseFlag);
	}

	public boolean isRunIntegrityChecks() {
		return config.isRunIntegrityChecks();
	}

	public void setExpectStatedParents(boolean expectStatedParents) {
		config.setExpectStatedParents(expectStatedParents);
	}
	
	public void reset() {
		//Don't reset if we're still saving to disk - need that data!
		while (ts.getAsyncSnapshotCacheInProgress()) {
			LOGGER.warn("Snapshot cache still being written to disk.  Waiting for completion. Recheck in 5s.");
			try {
				Thread.sleep(5 * 1000L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				LOGGER.error("Exception encountered",e);
			}
		}

		LOGGER.info("Resetting Archive Manager load flags");
		String stackTrace = ExceptionUtils.getStackTrace(new Exception());
		LOGGER.info("Temporary stack trace so we can see _why_ we're being reset: {}", stackTrace);
		
		gl.reset();
		currentlyHeldInMemory = null;
		config.reset();
	}


	public void setRunIntegrityChecks(boolean runIntegrityChecks) {
		if (!runIntegrityChecks) {
			LOGGER.warn("INTEGRITY CHECK DISABLED - ARE YOU SURE?");
		}
		config.setRunIntegrityChecks(runIntegrityChecks);
		this.gl.setRunIntegrityChecks(runIntegrityChecks);
	}


	public S3Manager getS3Manager() throws TermServerScriptException {
		return ((ArchiveDataLoader)getArchiveDataLoader()).getS3Manager();
	}
}
