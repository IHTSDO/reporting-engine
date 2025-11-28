package org.ihtsdo.termserver.scripting.domain;

public class ExecutionOptions {

	boolean doSnapshotImport = true;

	public boolean isSnapshotImport() {
		return doSnapshotImport;
	}

	public ExecutionOptions withNoSnapshotImport() {
		doSnapshotImport = false;
		return this;
	}
}
