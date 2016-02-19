package org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo;

public class BatchImportStatus {
	BatchImportState state;
	Integer target;
	Integer loaded;
	Integer processed;
	
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

}
