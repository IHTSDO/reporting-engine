package org.ihtsdo.snowowl.authoring.single.api.service.monitor;

import org.ihtsdo.snowowl.authoring.single.api.service.BranchService;
import org.springframework.beans.factory.annotation.Autowired;

public class MonitorFactory {

	@Autowired
	private BranchService branchService;

	public Monitor createMonitor(String focusProjectId, String focusTaskId) {
		return new BranchStateMonitor(focusProjectId, focusTaskId, branchService);
	}

}
