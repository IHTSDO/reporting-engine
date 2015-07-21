package org.ihtsdo.snowowl.authoring.single.api.pojo;

//import org.ihtsdo.otf.rest.client.ClassificationResults;

import org.ihtsdo.otf.rest.client.ClassificationResults;

import com.b2international.snowowl.snomed.api.domain.classification.ClassificationStatus;

public class Classification {

	private String message;
	private ClassificationResults results;
	
	public Classification (ClassificationResults results) {
		this.results = results;
	}
	
	public Classification(String errorMsg) {
		ClassificationResults runObj = new ClassificationResults();
		runObj.setStatus(ClassificationStatus.FAILED.toString());
		results = runObj;
		message = errorMsg;
	}

	public String getStatus() {
		return results.getStatus();

	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getId() {
		return results.getClassificationId();
	}

}
