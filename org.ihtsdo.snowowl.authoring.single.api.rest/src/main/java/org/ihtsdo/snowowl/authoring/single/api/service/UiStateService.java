package org.ihtsdo.snowowl.authoring.single.api.service;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.TaskTransferRequest;
import org.ihtsdo.snowowl.authoring.single.api.service.dao.ArbitraryFileService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

public class UiStateService {

	@Autowired
	private ArbitraryFileService arbitraryJsonService;

	public void persistTaskPanelState(String projectKey, String taskKey, String username, String panelId, String jsonState) throws IOException {
		arbitraryJsonService.write(getTaskUserPanelPath(projectKey, taskKey, username, panelId), jsonState);
	}

	private String getTaskUserPath(String projectKey, String taskKey, String username) {
		return projectKey + "/" + taskKey + "/user/" + username + "/ui-panel/";
	}

	private String getTaskUserPanelPath(String projectKey, String taskKey, String username, String panelId) {
		return getTaskUserPath(projectKey, taskKey, username) + panelId + ".json";
	}

	public void transferTask(String projectKey, String taskKey, TaskTransferRequest taskTransferRequest) throws BusinessServiceException {
		String currentUserUIStatePath = getTaskUserPath(projectKey, taskKey, taskTransferRequest.getCurrentUser());
		String newUserUIStatePath = getTaskUserPath(projectKey, taskKey, taskTransferRequest.getNewUser());
		try {
			arbitraryJsonService.moveFiles(currentUserUIStatePath, newUserUIStatePath);
		} catch (IOException e) {
			throw new BusinessServiceException("Unable to move UI State from " + currentUserUIStatePath + " to " + newUserUIStatePath, e);
		}
	}
}
