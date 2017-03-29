package org.ihtsdo.snowowl.authoring.batchimport.api.pojo.batch;

public class BatchImportStatus {
	private BatchImportState state;
	private Integer target;
	private Integer loaded;
	private Integer processed;
	private String message;
	
	public BatchImportStatus(BatchImportState state) {
		this.state = state;
	}
	public BatchImportState getState() {
		return state;
	}
	public void setState(BatchImportState state) {
		this.state = state;
	}
	public Integer getTarget() {
		return target;
	}
	public void setTarget(Integer target) {
		this.target = target;
	}
	public Integer getLoaded() {
		return loaded;
	}
	public void setLoaded(Integer loaded) {
		this.loaded = loaded;
	}
	public Integer getProcessed() {
		return processed;
	}
	public void setProcessed(Integer processed) {
		this.processed = processed;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}

}
