package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.datastore.server.events.BranchReply;
import com.b2international.snowowl.datastore.server.events.CreateBranchEvent;
import com.b2international.snowowl.datastore.server.events.ReadBranchEvent;
import org.springframework.beans.factory.annotation.Autowired;

public class BranchService {

	@Autowired
	private SnowOwlBusHelper snowOwlBusHelper;

	private static final String SNOMED_STORE = "snomedStore";
	private static final String MAIN = "MAIN";

	public void createTaskBranchAndProjectBranchIfNeeded(String projectKey, String taskKey) throws ServiceException {
		createProjectBranchIfNeeded(projectKey);
		snowOwlBusHelper.makeBusRequest(new CreateBranchEvent(SNOMED_STORE, getBranchPath(projectKey), taskKey, null), BranchReply.class, "Failed to create project branch.", this);
	}

	private void createProjectBranchIfNeeded(String projectKey) throws ServiceException {
		try {
			snowOwlBusHelper.makeBusRequest(new ReadBranchEvent(SNOMED_STORE, getBranchPath(projectKey)), BranchReply.class, "Failed to find project branch.", this);
		} catch (ServiceException e) {
			snowOwlBusHelper.makeBusRequest(new CreateBranchEvent(SNOMED_STORE, MAIN, projectKey, null), BranchReply.class, "Failed to create project branch.", this);
		}
	}

	private String getBranchPath(String projectKey) {
		return MAIN + "/" + projectKey;
	}
	
	public String getTaskPath(String projectKey, String taskKey) {
		return getBranchPath(projectKey + "/" + taskKey);
	}

}
