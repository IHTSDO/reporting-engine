package org.ihtsdo.snowowl.authoring.single.api.service;

import java.io.IOException;

import org.ihtsdo.otf.im.utility.SecurityService;
import org.ihtsdo.otf.rest.client.OrchestrationRestClient;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Validation;
import org.springframework.beans.factory.annotation.Autowired;

import us.monoid.json.JSONException;

public class ValidationService {
	
	@Autowired
	SecurityService ims;
	
	@Autowired
	OrchestrationRestClient orchestrationClient;
	

	public Validation startValidation(String projectKey, String taskKey) throws JSONException, IOException {
		orchestrationClient.validate("MAIN/" + projectKey + "/" + taskKey);
		return new Validation (Validation.STATUS_SCHEDULED, "");
	}


	public Validation getValidation(String projectKey, String taskKey) {
		return new Validation ("NOT YET IMPLEMENTED", "");
	}

	public Validation startValidation(String projectKey) throws JSONException, IOException {
		orchestrationClient.validate("MAIN/" + projectKey);
		return new Validation(Validation.STATUS_SCHEDULED, "");
	}

	public Validation getValidation(String projectKey) {
		return new Validation("NOT YET IMPLEMENTED", "");
	}

}
