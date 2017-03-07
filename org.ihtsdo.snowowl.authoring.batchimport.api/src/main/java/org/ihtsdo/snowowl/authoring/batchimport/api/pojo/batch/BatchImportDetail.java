package org.ihtsdo.snowowl.authoring.batchimport.api.pojo.batch;

class BatchImportDetail {
	
	private boolean loaded;
	private String failureReason;
	private String sctidCreated;
	
	public BatchImportDetail (boolean loaded, String failureReason, String sctidCreated) {
		this.loaded = loaded;
		this.failureReason = failureReason;
		this.sctidCreated = sctidCreated;
	}
	
	public boolean isLoaded() {
		return loaded;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public Object getSctidCreated() {
		return this.sctidCreated;
	}

}
