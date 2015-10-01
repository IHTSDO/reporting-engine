package org.ihtsdo.snowowl.authoring.single.api.pojo;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.ihtsdo.snowowl.authoring.single.api.service.TaskStatus;

@JsonDeserialize(as = AuthoringTask.class)
public interface AuthoringTaskUpdateRequest extends AuthoringTaskCreateRequest {

	TaskStatus getStatus();

	void setStatus(TaskStatus status);

	User getAssignee();

	void setAssignee(User assignee);

	User getReviewer();

	void setReviewer(User reviewer);
}
