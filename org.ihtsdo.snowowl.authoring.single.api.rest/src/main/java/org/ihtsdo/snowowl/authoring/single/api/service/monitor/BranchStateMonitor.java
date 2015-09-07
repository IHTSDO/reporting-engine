package org.ihtsdo.snowowl.authoring.single.api.service.monitor;

import com.b2international.snowowl.datastore.server.branch.Branch;
import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.ihtsdo.snowowl.authoring.single.api.service.BranchService;
import org.ihtsdo.snowowl.authoring.single.api.service.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BranchStateMonitor implements Monitor {

	private final String projectId;
	private final String taskId;
	private BranchService branchService;
	private Branch.BranchState branchState;
	private Logger logger = LoggerFactory.getLogger(getClass());

	public BranchStateMonitor(String projectId, String taskId, BranchService branchService) {
		this.projectId = projectId;
		this.taskId = taskId;
		this.branchService = branchService;
	}

	@Override
	public Notification runOnce() throws MonitorException {
		try {
			logger.debug("Get branch state");
			final Branch.BranchState branchState = branchService.getBranchState(projectId, taskId);
			if (branchState != this.branchState) {
				logger.debug("Branch {} state {}, changed", taskId, branchState);
				this.branchState = branchState;
				return new Notification(projectId, taskId, EntityType.BranchState, branchState.name());
			} else {
				logger.debug("Branch {} state {}, no change", taskId, branchState);
			}
			return null;
		} catch (ServiceException e) {
			throw new MonitorException("Failed to get branch state", e);
		}
	}

}
