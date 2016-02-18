package org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo;

public class BatchImportStatus {
	BatchImportState state;
	String progress;
	
	public BatchImportStatus(BatchImportState state) {
		this.state = state;
	}
	public BatchImportState getState() {
		return state;
	}
	public void setState(BatchImportState state) {
		this.state = state;
	}
	public String getProgress() {
		return progress;
	}
	public void setProgress(String progress) {
		this.progress = progress;
	}
}
