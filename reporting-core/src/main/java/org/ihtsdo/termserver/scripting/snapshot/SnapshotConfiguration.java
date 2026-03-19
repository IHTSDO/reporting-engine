package org.ihtsdo.termserver.scripting.snapshot;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.TermServerLocation;
import org.snomed.module.storage.CurrentPreviousModuleMetadataPair;
import org.snomed.module.storage.ModuleMetadata;

public class SnapshotConfiguration implements TermServerLocation {

	enum SnapshotSourceType { PROJECT, BRANCH_PATH, PUBLISHED_ARCHIVE, CODE_SYSTEM_VERSION}

	//Deprecated.  Try to remove these once we've moved over to ArchiveManager2
	private boolean loadDependencyPlusExtensionArchive = false;
	private boolean ensureSnapshotPlusDeltaLoad = false;

	private CurrentPreviousModuleMetadataPair currentPreviousModuleMetadataPair;

	private boolean allowStaleData = false;
	private boolean loadEditionArchive = false;
	private boolean populateHierarchyDepth = true;  //Term contains X needs this
	private boolean populatePreviousTransitiveClosure = false;
	private boolean expectStatedParents = true;  //UK Edition doesn't provide these, so don't look for them.
	private boolean populateReleaseFlag = false;
	private boolean runIntegrityChecks = true;
	private boolean loadOtherReferenceSets = false;

	private SnapshotSourceType snapshotSourceType = null;
	private String sourceName;

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

	public boolean isEnsureSnapshotPlusDeltaLoad() {
		return ensureSnapshotPlusDeltaLoad;
	}

	public void setEnsureSnapshotPlusDeltaLoad(boolean ensureSnapshotPlusDeltaLoad) {
		this.ensureSnapshotPlusDeltaLoad = ensureSnapshotPlusDeltaLoad;
	}

	public boolean isPopulatePreviousTransitiveClosure() {
		return populatePreviousTransitiveClosure;
	}

	public void setPopulatePreviousTransitiveClosure(boolean populatePreviousTransitiveClosure) {
		this.populatePreviousTransitiveClosure = populatePreviousTransitiveClosure;
	}

	public boolean isExpectStatedParents() {
		return expectStatedParents;
	}

	public void setExpectStatedParents(boolean expectStatedParents) {
		this.expectStatedParents = expectStatedParents;
	}

	public boolean isPopulateReleaseFlag() {
		return populateReleaseFlag;
	}

	public void setPopulateReleaseFlag(boolean populateReleaseFlag) {
		this.populateReleaseFlag = populateReleaseFlag;
	}

	public boolean isRunIntegrityChecks() {
		return runIntegrityChecks;
	}

	public void setRunIntegrityChecks(boolean runIntegrityChecks) {
		this.runIntegrityChecks = runIntegrityChecks;
	}

	public boolean isLoadOtherReferenceSets() {
		return loadOtherReferenceSets;
	}

	public void setLoadOtherReferenceSets(boolean loadOtherReferenceSets) {
		this.loadOtherReferenceSets = loadOtherReferenceSets;
	}

	public SnapshotSourceType getSnapshotSourceType() {
		return snapshotSourceType;
	}

	public void setSnapshotSourceType(SnapshotSourceType snapshotSource) {
		this.snapshotSourceType = snapshotSource;
	}

	public String getSourceName() {
		return sourceName;
	}

	public void setSourceName(String sourceName) {
		this.sourceName = sourceName;
	}

	public void reset() {
		loadEditionArchive = false;
		populateReleaseFlag = false;
		loadDependencyPlusExtensionArchive = false;
		populatePreviousTransitiveClosure = false;
		ensureSnapshotPlusDeltaLoad = false;
		loadOtherReferenceSets = false;
	}

	public boolean isCompatibleWithExisting(SnapshotConfiguration existing) {
		//If the source type or name is different, we definitely need to reload
		if (existing.getSnapshotSourceType() != getSnapshotSourceType() || !existing.getSourceName().equals(getSourceName())) {
			return false;
		}

		//If we need the release populated and we don't have it, then we need to reload
		if (isPopulateReleaseFlag() && !existing.isPopulateReleaseFlag()) {
			return false;
		}

		//Similarly, if we need a Snapshot+Delta load and we don't have it, then we need to reload
		return !isEnsureSnapshotPlusDeltaLoad() || existing.isEnsureSnapshotPlusDeltaLoad();
	}

	@Override
	public String getBranchPath() {
		//Do we have a branch path?
		if (!snapshotSourceType.equals(SnapshotSourceType.BRANCH_PATH)) {
			throw new IllegalStateException("SnapshotConfiguration is not configured for a branch path");
		}
		return sourceName;
	}

	public ModuleMetadata getPreviousRelease() {
		return currentPreviousModuleMetadataPair.getPreviousRelease();
	}
}
