package org.ihtsdo.snowowl.authoring.single.api.service.monitor;

import org.ihtsdo.snowowl.authoring.single.api.service.BranchService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.HashSet;

public class MonitorFactory {

	@Autowired
	private BranchService branchService;

	public Collection<Monitor> createMonitors(String focusProjectId, String focusTaskId) {
		final HashSet<Monitor> monitors = new HashSet<>();
		monitors.add(new BranchStateMonitor(focusProjectId, focusTaskId, branchService));
		return monitors;
	}
}
