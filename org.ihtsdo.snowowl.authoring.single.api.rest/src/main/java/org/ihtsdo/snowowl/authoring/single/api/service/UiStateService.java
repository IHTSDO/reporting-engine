package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.core.exceptions.NotFoundException;
import org.ihtsdo.snowowl.authoring.single.api.service.dao.ArbitraryJsonService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

public class UiStateService {

	@Autowired
	private ArbitraryJsonService arbitraryJsonService;

	public void persistTaskPanelState(String projectKey, String taskKey, String username, String panelId, String jsonState) throws IOException {
		arbitraryJsonService.write(getTaskUserPanelPath(projectKey, taskKey, username, panelId), jsonState);
	}

	public String retrieveTaskPanelState(String projectKey, String taskKey, String username, String panelId) throws IOException {
		try {
			return arbitraryJsonService.read(getTaskUserPanelPath(projectKey, taskKey, username, panelId));
		} catch (NoSuchFileException e) {
			throw new NotFoundException("ui-state", panelId);
		}
	}

	public void persistPanelState(String username, String panelId, String jsonState) throws IOException {
		arbitraryJsonService.write(getUserPanelPath(username, panelId), jsonState);
	}

	public String retrievePanelState(String username, String panelId) throws IOException {
		try {
			return arbitraryJsonService.read(getUserPanelPath(username, panelId));
		} catch (NoSuchFileException e) {
			throw new NotFoundException("ui-state", panelId);
		}
	}

	private String getTaskUserPanelPath(String projectKey, String taskKey, String username, String panelId) {
		return projectKey + "/" + taskKey + "/user/" + username + "/ui-panel/" + panelId + ".json";
	}

	private String getUserPanelPath(String username, String panelId) {
		return "/user/" + username + "/ui-panel/" + panelId + ".json";
	}


	public void deleteTaskPanelState(String projectKey, String taskKey, String username, String panelId) {
		arbitraryJsonService.delete(getTaskUserPanelPath(projectKey, taskKey, username, panelId));
	}

	public void deletePanelState(String username, String panelId) {
		arbitraryJsonService.delete(getUserPanelPath(username, panelId));
	}
}
