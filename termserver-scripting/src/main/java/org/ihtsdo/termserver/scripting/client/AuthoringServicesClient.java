package org.ihtsdo.termserver.scripting.client;

import java.io.IOException;

import us.monoid.json.JSONObject;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;

public class AuthoringServicesClient {
	private final Resty resty;
	private final String serverUrl;
	private static final String apiRoot = "snowowl/ihtsdo-sca/";
	//private static final String apiRoot = "authoring-services/";
	private static final String ALL_CONTENT_TYPE = "*/*";
	private static final String JSON_CONTENT_TYPE = "application/json";

	public AuthoringServicesClient(String serverUrl, String cookie) {
		this.serverUrl = serverUrl;
		resty = new Resty(new RestyOverrideAccept(ALL_CONTENT_TYPE));
		resty.withHeader("Cookie", cookie);
		resty.authenticate(this.serverUrl, null,null);
	}

	public String createTask(String project, String summary, String description) throws Exception {
		String endPoint = serverUrl + apiRoot + "projects/" + project + "/tasks";
		JSONObject requestJson = new JSONObject();
		requestJson.put("summary", summary);
		requestJson.put("description", description);
		JSONResource response = resty.json(endPoint, RestyHelper.content(requestJson, JSON_CONTENT_TYPE));
		return response.get("key").toString();
	}

	public void setEditPanelUIState(String project, String taskKey, String quotedList) throws IOException {
		String endPointRoot = serverUrl + apiRoot + "projects/" + project + "/tasks/" + taskKey + "/ui-state/";
		String endPoint = endPointRoot + "edit-panel";
		resty.json(endPoint, RestyHelper.content(quotedList, JSON_CONTENT_TYPE));
		//TODO Move to locally maintained Resty so we can easily check for HTTP200 return status
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

	public void deleteTask(String project, String taskKey, boolean optional) throws SnowOwlClientException {
		String endPoint = serverUrl + apiRoot + "projects/" + project + "/tasks/" + taskKey;
		try {
			JSONObject requestJson = new JSONObject();
			requestJson.put("status", "DELETED");
			resty.json(endPoint, Resty.put(RestyHelper.content(requestJson, JSON_CONTENT_TYPE)));
		} catch (Exception e) {
			String errStr = "Failed to delete task - " + taskKey;
			if (optional) {
				System.out.println(errStr + ": " + e.getMessage());
			} else {
				throw new SnowOwlClientException (errStr, e);
			}
		}
	}
}
