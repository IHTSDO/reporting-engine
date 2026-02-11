package org.ihtsdo.termserver.scripting.domain;

public class ExecutionOptions {

	public static final ExecutionOptions DEFAULT = new ExecutionOptions();

	boolean doSnapshotImport = true;

	public boolean isSnapshotImport() {
		return doSnapshotImport;
	}

	public ExecutionOptions withNoSnapshotImport() {
		doSnapshotImport = false;
		return this;
	}
}
