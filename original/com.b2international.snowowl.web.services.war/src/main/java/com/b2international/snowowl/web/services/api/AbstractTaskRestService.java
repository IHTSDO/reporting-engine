/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.api;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.security.Principal;

import org.springframework.http.ResponseEntity;

import com.b2international.snowowl.rest.domain.task.ITask;
import com.b2international.snowowl.rest.impl.domain.task.TaskInput;
import com.b2international.snowowl.rest.service.ITaskService;
import com.b2international.snowowl.web.services.domain.CollectionResource;
import com.b2international.snowowl.web.services.util.Responses;

/**
 * @author apeteri
 * @since 1.0
 */
public abstract class AbstractTaskRestService extends AbstractRestService {

	protected ITaskService delegate;

	protected CollectionResource<ITask> getTasks(final String shortName, final String version, final Boolean includePromoted) {
		return CollectionResource.of(delegate.getAllTasks(shortName, version, includePromoted));
	}

	protected ResponseEntity<Void> createTask(final String shortName, final String version, final TaskInput taskInput, final Principal principal) {
		final String taskId = taskInput.getTaskId();
		final ITask newBranchInfo = delegate.createTask(shortName, version, taskId, taskInput, principal.getName());
		checkNotNull(newBranchInfo.getTaskId(), "Task ID must be set on branch info");
		return Responses.created(createTaskLocationUri(shortName, version, newBranchInfo.getTaskId())).build();
	}

	/**
	 * @param shortName
	 * @param version
	 * @param taskId
	 * @return
	 */
	protected abstract URI createTaskLocationUri(String shortName, String version, String taskId);

	protected ITask getTaskByName(final String shortName, final String version, final String taskId) {
		return delegate.getTaskByName(shortName, version, taskId);
	}

	protected final void synchronizeTask(final String shortName, final String version, final String taskId, final Principal principal) {
		delegate.synchronizeTask(shortName, version, taskId, principal.getName());
	}

	protected final void promoteTask(final String shortName, final String version, final String taskId, final Principal principal) {
		delegate.promoteTask(shortName, version, taskId, principal.getName());
	}
}
