package org.ihtsdo.termserver.scripting.client;

import org.ihtsdo.otf.rest.client.snowowl.pojo.ClassificationResults;

import com.google.gson.annotations.Expose;

public class Classification {
	
	@Expose
	private String id;
	
	@Expose
	private String status;

	@Expose
	private String message;
	
	@Expose
	private ClassificationResults results;
	
	public Classification (ClassificationResults results) {
		this.results = results;
	}
	
	public Classification(String errorMsg) {
		ClassificationResults runObj = new ClassificationResults();
		runObj.setStatus(ClassificationResults.ClassificationStatus.FAILED.toString());
		results = runObj;
		message = errorMsg;
	}

	public String getStatus() {
		return status;

	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getId() {
		return id;
	}

	public ClassificationResults getResults() {
		return results;
	}

	public void setResults(ClassificationResults results) {
		this.results = results;
	}
	
	@Override
	public String toString() {
		String str = "";
		if (id != null ) {
			str += "Classification id: " + id + "\n";
		}
		if (status != null) {
			str += "status : " + status;
		} else {
			str += "No classification data received";
		}
		return str;
	}
	
}
