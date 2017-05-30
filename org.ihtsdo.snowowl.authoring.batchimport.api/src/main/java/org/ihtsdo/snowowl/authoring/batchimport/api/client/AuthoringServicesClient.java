package org.ihtsdo.snowowl.authoring.batchimport.api.client;

import java.io.IOException;

import org.ihtsdo.snowowl.authoring.batchimport.api.pojo.task.AuthoringTask;
import org.ihtsdo.snowowl.authoring.batchimport.api.pojo.task.AuthoringTaskCreateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import us.monoid.json.JSONArray;
import us.monoid.json.JSONObject;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;

public class AuthoringServicesClient {
	
	private final Resty resty;
	private String rootUrl;
	private static final String ALL_CONTENT_TYPE = "*/*";
	private static final String JSON_CONTENT_TYPE = "application/json";
	
	private final ObjectMapper mapper = new ObjectMapper();
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public AuthoringServicesClient(String rootUrl, String authenticatedCookie) {
		resty = new Resty(new RestyOverrideAccept(ALL_CONTENT_TYPE));
		this.rootUrl = rootUrl;
		resty.withHeader("Cookie", authenticatedCookie);
	}

	public AuthoringTask createTask(String projectKey, AuthoringTaskCreateRequest taskCreateRequest) throws AuthoringServicesClientException {
		String endPoint = rootUrl + "projects/" + projectKey + "/tasks";
		JSONObject requestJson = new JSONObject();
		AuthoringTask task = null;
		try {
			requestJson.put("summary", taskCreateRequest.getSummary());
			requestJson.put("description", taskCreateRequest.getDescription());
			JSONResource response = resty.json(endPoint, RestyHelper.content(requestJson, JSON_CONTENT_TYPE));
			String jsonReponse = getJsonResponse(response);
			logger.info("Create task request received: {} - {}", response.getHTTPStatus() , jsonReponse);
			task = mapper.readValue(response.object().toString(1), AuthoringTask.class);
		} catch (Exception e) {
			String errMsg = "Failed to create task in project " + projectKey;
			errMsg += ". With endpoint: " + endPoint + " and request payload: " + requestJson.toString();
			throw new AuthoringServicesClientException(errMsg, e);
		}
		return task;
	}
	
	public String updateTask(String project, String taskKey, String summary, String description, String username) throws Exception {
		String endPoint = rootUrl + "projects/" + project + "/tasks/" + taskKey;
		
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
		if (response.getHTTPStatus() != 200) {
			String jsonReponse = getJsonResponse(response);
			logger.info("Failed to update task {} for user {}.  Received: {} : {}",taskKey, username, response.getHTTPStatus(), jsonReponse);
		} else {
			logger.info("Updated task {} for {}", taskKey, username);
		}
		return response.get("key").toString();
	}
	
	public String putTaskIntoReview(String project, String taskKey, String reviewer) throws Exception {
		String endPoint = rootUrl + "projects/" + project + "/tasks/" + taskKey;
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
		String endPoint = rootUrl + "projects/" + projectKey + "/tasks/" + taskKey;
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
		try {
			String endPoint = rootUrl + "projects/" + projectKey + "/tasks/" + taskKey + "/ui-state/" + panelId;
			JSONResource response;
			if (uiStateStr.startsWith("[")) {
				JSONArray uiState = new JSONArray(uiStateStr);
				response = resty.json(endPoint, RestyHelper.content(uiState, JSON_CONTENT_TYPE));
			} else {
				JSONObject uiState = new JSONObject(uiStateStr);
				response = resty.json(endPoint, RestyHelper.content(uiState, JSON_CONTENT_TYPE));
			}
			if (response.getHTTPStatus() != 200) {
				String jsonReponse = getJsonResponse(response);
				logger.info("Failed to save UI state to {} for {}, received {}:{}", endPoint, user, response.getHTTPStatus(), jsonReponse);
			} else {
				logger.info("Saved UI state to {} for {}", endPoint, user);
			}
		} catch (Exception e) {
			String errStr = "Failed to save '" + panelId + "' ui state - " + taskKey + ": " + uiStateStr;
			throw new AuthoringServicesClientException(errStr, e);
		}
	}

	private String getJsonResponse(JSONResource response) {
		String jsonReponse ="Unparsable Response";
		try {
			jsonReponse = response.object().toString();
		} catch (Exception e) {}
		return jsonReponse;
	}
}
