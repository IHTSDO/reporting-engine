package org.ihtsdo.snowowl.authoring.single.api.service.monitor;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.service.BranchService;
import org.ihtsdo.snowowl.authoring.single.api.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;

public class MonitorFactory {

	@Autowired
	private BranchService branchService;

	@Autowired
	private TaskService taskService;

	public Monitor createMonitor(String focusProjectId, String focusTaskId) throws BusinessServiceException {
		final String branchPath = taskService.getTaskBranchPathUsingCache(focusProjectId, focusTaskId);
		return new BranchStateMonitor(focusProjectId, focusTaskId, branchPath, branchService);
	}

}
