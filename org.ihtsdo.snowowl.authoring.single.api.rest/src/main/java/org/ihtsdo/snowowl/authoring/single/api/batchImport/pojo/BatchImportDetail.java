package org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo;

public class BatchImportDetail {
	
	boolean loaded;
	String failureReason;
	
	public BatchImportDetail (boolean loaded, String failureReason) {
		this.loaded = loaded;
		this.failureReason = failureReason;
	}
	
	public boolean isLoaded() {
		return loaded;
	}

	public String getFailureReason() {
		return failureReason;
	}

}
