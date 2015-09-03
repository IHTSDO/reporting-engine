package org.ihtsdo.snowowl.authoring.single.api.pojo;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(as = AuthoringTask.class)
public interface AuthoringTaskUpdateRequest {

	String getStatusName();

	void setStatus(String status);

	User getReviewer();

	void setReviewer(User reviewer);
}
