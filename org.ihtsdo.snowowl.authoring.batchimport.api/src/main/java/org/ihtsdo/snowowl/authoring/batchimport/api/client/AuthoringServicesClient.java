package org.ihtsdo.snowowl.authoring.batchimport.api.client;

import java.io.IOException;

import javax.servlet.http.Cookie;

import org.ihtsdo.snowowl.authoring.batchimport.api.pojo.task.AuthoringTask;
import org.ihtsdo.snowowl.authoring.batchimport.api.pojo.task.AuthoringTaskCreateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.monoid.json.JSONArray;
import us.monoid.json.JSONObject;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;
import static us.monoid.web.Resty.put;

public class AuthoringServicesClient {
	
	private final Resty resty;
	private final String serverUrl = "http://localhost/";
	private static final String apiRoot = "authoring-services/";
	private static final String ALL_CONTENT_TYPE = "*/*";
	private static final String JSON_CONTENT_TYPE = "application/json";
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public AuthoringServicesClient(Cookie[] cookies) {
		resty = new Resty(new RestyOverrideAccept(ALL_CONTENT_TYPE));
		resty.authenticate(this.serverUrl, null,null);
		//Set all the cookies that the user originally came in with
		for (Cookie cookie : cookies) {
			resty.withHeader("Cookie", cookie.getValue());
		}
	}

	public AuthoringTask createTask(String projectKey, String summary, AuthoringTaskCreateRequest taskCreateRequest) throws AuthoringServicesClientException {
		String endPoint = serverUrl + apiRoot + "projects/" + projectKey + "/tasks";
		JSONObject requestJson = new JSONObject();
		AuthoringTask task = null;
		try {
			requestJson.put("summary", summary);
			requestJson.put("description", taskCreateRequest);
			JSONResource response = resty.json(endPoint, RestyHelper.content(requestJson, JSON_CONTENT_TYPE));
			logger.info("Create task request received: {} - {}", response.getHTTPStatus() , response.object().toString());
			task = mapper.readValue(response.object().toString(1), AuthoringTask.class);
		} catch (Exception e) {
			String errMsg = "Failed to create task in project " + projectKey;
			throw new AuthoringServicesClientException(errMsg, e);
		}
		return task;
	}

	public void setEditPanelUIState(String project, String taskKey, String quotedList) throws IOException {
		String endPointRoot = serverUrl + apiRoot + "projects/" + project + "/tasks/" + taskKey + "/ui-state/";
		String endPoint = endPointRoot + "edit-panel";
		resty.json(endPoint, RestyHelper.content(quotedList, JSON_CONTENT_TYPE));
	}
	
	public void setSavedListUIState(String project, String taskKey, JSONObject items) throws IOException {
		String endPointRoot = serverUrl + apiRoot + "projects/" + project + "/tasks/" + taskKey + "/ui-state/";
		String endPoint = endPointRoot + "saved-list";
		resty.json(endPoint, RestyHelper.content(items, JSON_CONTENT_TYPE));
	}
	
	public String updateTask(String project, String taskKey, String summary, String description, String username) throws Exception {
		String endPoint = serverUrl + apiRoot + "projects/" + project + "/tasks/" + taskKey;
		
		JSONObject requestJson = new JSONObject();
		if (summary != null) {
			requestJson.put("summary", summary);
		}
		
		if (description != null) {
			requestJson.put("description", description);
		}
		
		if (username != null) {
			JSONObject assigneeJson = new JSONObject();
			assigneeJson.put("username", username);
			requestJson.put("assignee", assigneeJson);
		}
		JSONResource response = resty.json(endPoint, Resty.put(RestyHelper.content(requestJson, JSON_CONTENT_TYPE)));
		return response.get("key").toString();
	}
	
	public String putTaskIntoReview(String project, String taskKey, String reviewer) throws Exception {
		String endPoint = serverUrl + apiRoot + "projects/" + project + "/tasks/" + taskKey;
		JSONObject requestJson = new JSONObject();
		requestJson.put("status", "IN_REVIEW");

		if (reviewer != null) {
			JSONObject assigneeJson = new JSONObject();
			assigneeJson.put("username", reviewer);
			requestJson.put("reviewer", assigneeJson);
		}
		JSONResource response = resty.json(endPoint, Resty.put(RestyHelper.content(requestJson, JSON_CONTENT_TYPE)));
		return response.get("key").toString();
	}

	public void deleteTask(String projectKey, String taskKey, boolean optional) throws AuthoringServicesClientException {
		String endPoint = serverUrl + apiRoot + "projects/" + projectKey + "/tasks/" + taskKey;
		try {
			JSONObject requestJson = new JSONObject();
			requestJson.put("status", "DELETED");
			resty.json(endPoint, Resty.put(RestyHelper.content(requestJson, JSON_CONTENT_TYPE)));
		} catch (Exception e) {
			String errStr = "Failed to delete task - " + taskKey;
			if (optional) {
				System.out.println(errStr + ": " + e.getMessage());
			} else {
				throw new AuthoringServicesClientException (errStr, e);
			}
		}
	}

	public void persistTaskPanelState(String projectKey, String taskKey,
			String user, String panelId, String uiStateStr) throws AuthoringServicesClientException {
		String endPoint = serverUrl + apiRoot + "projects/" + projectKey + "/tasks/" + taskKey + "/ui-state/" + panelId;
		try {
			if (uiStateStr.startsWith("[")) {
				JSONArray  uiState = new JSONArray(uiStateStr);
				resty.json(endPoint, Resty.put(RestyHelper.content(uiState, JSON_CONTENT_TYPE)));
			} else {
				JSONObject uiState = new JSONObject(uiStateStr);
				resty.json(endPoint, Resty.put(RestyHelper.content(uiState, JSON_CONTENT_TYPE)));
			}
		} catch (Exception e) {
			String errStr = "Failed to save '" + panelId + "' ui state - " + taskKey + ": " + uiStateStr;
			throw new AuthoringServicesClientException(errStr, e);
		}
		logger.info("Saved UI stated to {} for {}", endPoint, user);
	}

	public void updateTask(AuthoringTask task) throws AuthoringServicesClientException {
		String endPoint = serverUrl + apiRoot + "projects/" + task.getProjectKey() + "/tasks/" + task.getKey();
		JSONObject requestJson = new JSONObject();
		try {
			resty.json(endPoint, put(RestyHelper.content(requestJson, JSON_CONTENT_TYPE)));
		} catch (IOException e) {
			String errMsg =  "Failed to update task";
			throw new AuthoringServicesClientException(errMsg, e);
		}
	}
}
