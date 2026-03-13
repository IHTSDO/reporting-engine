package org.ihtsdo.termserver.scripting.domain;

public class ExecutionOptions {

	public static final ExecutionOptions DEFAULT = new ExecutionOptions();

	boolean doSnapshotImport = true;
	boolean doIntegrityChecking = true;
	boolean importAllRefsets = false;

	public boolean isSnapshotImport() {
		return doSnapshotImport;
	}

	public ExecutionOptions withNoSnapshotImport() {
		doSnapshotImport = false;
		return this;
	}

	public ExecutionOptions withNoIntegrityChecking() {
		doIntegrityChecking = false;
		return this;
	}

	public boolean isIntegrityChecking() {
		return doIntegrityChecking;
	}

	public boolean isImportAllRefsets() {
		return importAllRefsets;
	}

	public ExecutionOptions withImportAllRefsets() {
		importAllRefsets = true;
		return this;
	}
}
