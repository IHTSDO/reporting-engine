package org.ihtsdo.snowowl.authoring.batchimport.api.pojo.task;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = AuthoringTask.class)
public interface AuthoringTaskCreateRequest {

	String getSummary();

	void setSummary(String title);

	String getDescription();

	void setDescription(String description);
}
