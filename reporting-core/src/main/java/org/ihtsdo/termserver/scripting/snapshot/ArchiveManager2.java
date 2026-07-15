package org.ihtsdo.termserver.scripting.snapshot;

import org.ihtsdo.otf.exception.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.termserver.scripting.dao.ArchiveDataLoader;
import org.ihtsdo.termserver.scripting.dao.BuildArchiveDataLoader2;
import org.ihtsdo.termserver.scripting.domain.Branch;
import org.ihtsdo.termserver.scripting.domain.CodeSystemVersion;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.FileType;
import org.snomed.module.storage.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.util.Comparator;
import java.util.List;

/**
 * Here's the plan.  When ArchiveManager2 is asked to load a snapshot, the caller will pass a snapshot configuration
 * that explains what is needed.  AM2 will compare this to what it has in memory, and if it's compatible, then no work
 * is needed.  But if different data is needed, then we'll go to the MSC to obtain the packages we need.
 */
@Service
public class ArchiveManager2 {

	private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveManager2.class);
	private static ArchiveManager2 singleton;

	@Autowired(required = false)
	private ArchiveDataLoader archiveDataLoader;

	@Autowired(required = false)
	private BuildArchiveDataLoader2 buildArchiveDataLoader;

	private ApplicationContext appContext;
	private ModuleStorageCoordinator msc;
	private TBCHelper fileHelper;
	private SnapshotConfiguration currentlyHeldInMemory;

	private ArchiveManager2() {
		//Private usage.  Obtain a singleton object via create()
	}

	public static ArchiveManager2 create() {
		if (singleton == null) {
			singleton = new ArchiveManager2();
		}
		return singleton;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void init(ApplicationReadyEvent event) {
		LOGGER.info("ArchiveManager2.init: assigning to singleton");
		ArchiveManager2.singleton = this;
		appContext = event.getApplicationContext();
	}

	private ArchiveDataLoader getArchiveDataLoader() throws TermServerScriptException {
		if (archiveDataLoader == null) {
			if (appContext == null) {
				LOGGER.info("No ArchiveDataLoader configured, creating one locally...");
				archiveDataLoader = ArchiveDataLoader.create(true);
			} else {
				archiveDataLoader = appContext.getBean(ArchiveDataLoader.class);
			}
		}
		return archiveDataLoader;
	}

	private BuildArchiveDataLoader2 getBuildArchiveDataLoader() throws TermServerScriptException {
		if (buildArchiveDataLoader == null) {
			if (appContext == null) {
				LOGGER.info("No BuildArchiveDataLoader configured, creating one locally...");
				buildArchiveDataLoader = BuildArchiveDataLoader2.create();
			} else {
				buildArchiveDataLoader = appContext.getBean(BuildArchiveDataLoader2.class);
			}
		}
		return buildArchiveDataLoader;
	}

	private ModuleStorageCoordinator getModuleStorageCoordinator(TermServerScript ts) throws TermServerScriptException {
		if (msc == null) {
			ResourceManager resourceManager = getArchiveDataLoader().getS3Manager().getResourceManager();
			msc = ModuleStorageCoordinator.create(ts.getEnv(), resourceManager);
		}
		return msc;
	}

	public void loadSnapshot(TermServerScript ts, SnapshotConfiguration config) throws TermServerScriptException {
		fileHelper = new TBCHelper(ts);
		//Is what I've been asked to load compatible with what I've currently got in memory?
		if (currentlyHeldInMemory != null && config.isCompatibleWithExisting(currentlyHeldInMemory)) {
			LOGGER.info("Snapshot currently in memory is compatible with requested snapshot.  No need to load.");
		} else {
			switch (config.getSnapshotSourceType()) {
				case PROJECT, BRANCH_PATH -> loadFromBranchPath(ts, config);
				case PUBLISHED_ARCHIVE -> loadFromPublishedArchive(ts, config);
				case BUILD_ARCHIVE -> loadFromBuildArchive(ts, config);
				case CODE_SYSTEM_VERSION -> loadFromCodeSystemVersion(ts, config);
			}
		}
	}

	private void loadFromBranchPath(TermServerScript ts, SnapshotConfiguration config) throws TermServerScriptException {
		//Obtain a delta, pass that to Module Storage Coordinator so it can tell us what we need to load
		try {
			File delta = fileHelper.getExportedDelta(config);
			CurrentPreviousModuleMetadataPair moduleMetadataPair = getModuleStorageCoordinator(ts).getCurrentAndPreviousMetadata(delta, true);
			constructSnapshotInMemory(ts, moduleMetadataPair);
		} catch (ModuleStorageCoordinatorException e) {
			throw new TermServerScriptException("Unable to obtain delta for " + ts.getSnapshotConfiguration(), e);
		}
	}

	private void loadFromPublishedArchive(TermServerScript ts, SnapshotConfiguration config) throws TermServerScriptException {
		//In this situation, the MSC call tells us if we also need to load one or more dependencies
		if (msc == null) {
			getModuleStorageCoordinator(ts);
		}

		try {
			ModuleMetadata moduleMetadata = null;

			try {
				//Do we already have this file locally?
				File archive = fileHelper.getPublishedArchive(config);
				moduleMetadata = msc.getMetadata(archive);
			} catch (TermServerScriptException e) {}

			if (moduleMetadata == null) {
				moduleMetadata = msc.findPackageOrThrow(config.getSource(), true);
			}

			constructSnapshotInMemory(ts,  moduleMetadata);
		} catch (ModuleStorageCoordinatorException e) {
			throw new TermServerScriptException("Unable to obtain published archive for " + ts.getSnapshotConfiguration(), e);
		}
	}

	private void loadFromBuildArchive(TermServerScript ts, SnapshotConfiguration config) throws TermServerScriptException {
		try {
			//In this situation, the MSC call tells us if we also need to load one or more dependencies
			if (msc == null) {
				getModuleStorageCoordinator(ts);
			}

			File archive;

			try {
				//Do we already have this file locally?
				archive = fileHelper.getPublishedArchive(config);
			} catch (TermServerScriptException e) {
				getBuildArchiveDataLoader().download(new File(config.getSource()));
				archive = fileHelper.getPublishedArchive(config);
			}

			ModuleMetadata moduleMetadata = msc.getMetadata(archive, true);

			constructSnapshotInMemory(ts, moduleMetadata);
		} catch (TermServerScriptException | ModuleStorageCoordinatorException e) {
			throw new TermServerScriptException("Unable to obtain build archive for " + ts.getSnapshotConfiguration(), e);
		}
	}

	private void loadFromCodeSystemVersion(TermServerScript ts, SnapshotConfiguration config) throws TermServerScriptException {
		try {
			URI codeSystemVersionURI = URI.create(config.getSource());
			ModuleMetadata moduleMetadata = getModuleStorageCoordinator(ts).getMetadata(codeSystemVersionURI);
			//constructSnapshotInMemory(ts, null, moduleMetadata);
			throw new NotImplementedException();
		} catch (ModuleStorageCoordinatorException e) {
			throw new TermServerScriptException("Unable to obtain code system version for " + ts.getSnapshotConfiguration(), e);
		}
	}

	private void constructSnapshotInMemory(TermServerScript ts, CurrentPreviousModuleMetadataPair moduleMetadataPair) throws TermServerScriptException {
		ArchiveImporter archiveImporter = new ArchiveImporter(ts.getGraphLoader(), ts.getSnapshotConfiguration());
		//First, load the dependencies (perhaps none for an Edition package)
		for (ModuleMetadata dependency : moduleMetadataPair.getCurrentRelease().getDependencies()) {
			archiveImporter.loadArchive(dependency.getFile(), FileType.SNAPSHOT, true);
		}
		//Now the previous package
		archiveImporter.loadArchive(moduleMetadataPair.getPreviousRelease().getFile(), FileType.SNAPSHOT, true);

		//And finally the delta, if provided
		archiveImporter.loadArchive(moduleMetadataPair.getCurrentRelease().getFile(), FileType.DELTA, false);

		//Now whatever we've loaded, store that in memory
		currentlyHeldInMemory = ts.getSnapshotConfiguration();
	}

	//Now for a published archive, we don't need the previous release, just the dependencies
	private void constructSnapshotInMemory(TermServerScript ts, ModuleMetadata moduleMetadata) throws TermServerScriptException, ModuleStorageCoordinatorException {
		ArchiveImporter archiveImporter = new ArchiveImporter(ts.getGraphLoader(), ts.getSnapshotConfiguration());
		//First, load the dependencies (perhaps none for an Edition package)
		for (ModuleMetadata dependency : moduleMetadata.getDependencies()) {
			if (dependency.getFile() == null) {
				getModuleStorageCoordinator(ts).addFileLocally(dependency);
			}
			archiveImporter.loadArchive(dependency.getFile(), FileType.SNAPSHOT, true);
		}
		//Now the previous package
		archiveImporter.loadArchive(moduleMetadata.getFile(), FileType.SNAPSHOT, true);
	}

	public String getPreviousBranch() {
		throw new NotImplementedException();
	}

	public String getPreviousPreviousBranch(TermServerScript ts, Project project) throws TermServerScriptException {
		Branch branch = loadBranch(ts, project);
		String previousRelease = branch.getMetadata().getPreviousRelease();
		String codeSystem = extractCodeSystemFromBranch(branch);
		try {
			List<CodeSystemVersion> codeSystems = ts.getTSClient().getCodeSystemVersions(codeSystem);
			List<CodeSystemVersion> releases = codeSystems.stream()
					.sorted(Comparator.comparing(CodeSystemVersion::getEffectiveDate).reversed())
					.toList();
			if (releases.size() < 2) {
				throw new TermServerScriptException("Less than 2 previous releases detected");
			}
			if (!releases.get(0).getEffectiveDate().toString().equals(previousRelease)) {
				LOGGER.warn("Check here - unexpected previous release: {} expected {}", releases.get(0).getEffectiveDate(), previousRelease);
			}
			return releases.get(1).getBranchPath();
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to recover child branches due to " + e.getMessage(), e);
		}
	}

	private Branch loadBranch(TermServerScript ts, Project project) throws TermServerScriptException {
		String branchPath = project.getBranchPath();
		try {
			Branch branch = ts.getTSClient().getBranch(branchPath);
			if (branch == null) {
				throw new TermServerScriptException("Unable to find branch: '" + branchPath + "'");
			}
			while (branch.getMetadata() == null || branch.getMetadata().getPreviousRelease() == null) {
				if (branchPath.equals("MAIN")) {
					throw new TermServerScriptException("Metadata missing in MAIN");
				}
				int lastSlash = branchPath.lastIndexOf("/");
				branchPath = (lastSlash == -1) ? branchPath : branchPath.substring(0, lastSlash);
				Branch parent = loadBranch(ts, new Project().withBranchPath(branchPath));
				branch.setMetadata(parent.getMetadata());
			}
			return branch;
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to recover branch " + branchPath + " due to " + e.getMessage(), e);
		}
	}

	private String extractCodeSystemFromBranch(Branch branch) {
		String codeSystem = "SNOMEDCT";
		if (branch.getPath().contains(codeSystem)) {
			String[] branchParts = branch.getPath().split("/");
			if (branchParts.length > 1 && branchParts[1].startsWith(codeSystem)) {
				codeSystem = branchParts[1];
			}
		}
		return codeSystem;
	}

	public void reset(TermServerScript ts) {
		LOGGER.info("Resetting ArchiveManager2");
		currentlyHeldInMemory = null;
		msc = null;
		ts.getSnapshotConfiguration().reset();
		ts.getGraphLoader().reset();
		ts.getGraphLoader().setRecordPreviousState(false);
	}

	public SnapshotConfiguration getCurrentConfiguration() {
		return currentlyHeldInMemory;
	}

}
