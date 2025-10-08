package org.ihtsdo.termserver.scripting.snapshot;

public class SnapshotConfiguration {

	private boolean allowStaleData = false;
	private boolean loadDependencyPlusExtensionArchive = false;
	private boolean loadEditionArchive = false;
	private boolean populateHierarchyDepth = true;  //Term contains X needs this
	private boolean ensureSnapshotPlusDeltaLoad = false;
	private boolean populatePreviousTransitiveClosure = false;
	private boolean expectStatedParents = true;  //UK Edition doesn't provide these, so don't look for them.
	private boolean populateReleaseFlag = false;
	private boolean runIntegrityChecks = true;
	private boolean loadOtherReferenceSets = false;

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


	public void reset() {
		loadEditionArchive = false;
		populateReleaseFlag = false;
		loadDependencyPlusExtensionArchive = false;
		populatePreviousTransitiveClosure = false;
		ensureSnapshotPlusDeltaLoad = false;
		loadOtherReferenceSets = false;
	}
}
