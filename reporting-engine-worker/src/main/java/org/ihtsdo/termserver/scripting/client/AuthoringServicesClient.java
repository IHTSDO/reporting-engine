package org.ihtsdo.termserver.scripting.client;

import java.io.IOException;

import org.ihtsdo.termserver.scripting.domain.Project;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import us.monoid.json.JSONObject;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;

public class AuthoringServicesClient {
	RestTemplate restTemplate;
	HttpHeaders headers;
	private final Resty resty;
	private final String serverUrl;
	private final String cookie;
	private static final String apiRoot = "authoring-services/";
	private static final String ALL_CONTENT_TYPE = "*/*";
	private static final String JSON_CONTENT_TYPE = "application/json";
	
	protected static Gson gson;
	static {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		gsonBuilder.disableHtmlEscaping();
		gsonBuilder.excludeFieldsWithoutExposeAnnotation();
		gson = gsonBuilder.create();
	}

	public AuthoringServicesClient(String serverUrl, String cookie) {
		this.serverUrl = serverUrl;
		this.cookie = cookie;
		resty = new Resty(new RestyOverrideAccept(ALL_CONTENT_TYPE));
		resty.withHeader("Cookie", this.cookie);
		resty.withHeader("Connection", "close");
		resty.authenticate(this.serverUrl, null,null);
		
		//sun.util.logging.PlatformLogger.getLogger("sun.net.www.protocol.http.HttpURLConnection").setLevel(PlatformLogger.Level.ALL);
		//sun.util.logging.PlatformLogger.getLogger("sun.net.www.protocol.https.DelegateHttpsURLConnection").setLevel(PlatformLogger.Level.ALL);
		
		headers = new HttpHeaders();
		headers.add("Cookie", this.cookie );
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		restTemplate = new RestTemplateBuilder()
				.rootUri(this.serverUrl)
				.additionalMessageConverters(new GsonHttpMessageConverter())
				.errorHandler(new ExpressiveErrorHandler())
				.build();
	}

	public String createTask(String projectKey, String summary, String description) throws Exception {
		String endPoint = serverUrl + apiRoot + "projects/" + projectKey + "/tasks";
		/*JSONObject requestJson = new JSONObject();
		requestJson.put("summary", summary);
		requestJson.put("description", description);
		JSONResource response = resty.json(endPoint, RestyHelper.content(requestJson, JSON_CONTENT_TYPE));
		*/
		JsonObject requestJson = new JsonObject();
		requestJson.addProperty("summary", summary);
		requestJson.addProperty("description", description);
		HttpEntity<Object> requestEntity = new HttpEntity<Object>(requestJson, headers);
		Task task = restTemplate.postForObject(endPoint, requestEntity, Task.class);
		return task.getKey();
		//return response.get("key").toString();
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
	
	public String updateTask(String project, String taskKey, String summary, String description, String author, String reviewer) throws Exception {
		String endPoint = serverUrl + apiRoot + "projects/" + project + "/tasks/" + taskKey;
		
		//JSONObject requestJson = new JSONObject();
		JsonObject requestJson = new JsonObject();
		if (summary != null) {
			//requestJson.put("summary", summary);
			requestJson.addProperty("summary", summary);
		}
		
		if (description != null) {
			//requestJson.put("description", description);
			requestJson.addProperty("description", description);
		}
		
		if (author != null) {
			//JSONObject assigneeJson = new JSONObject();
			JsonObject assigneeJson = new JsonObject();
			//assigneeJson.put("username", author);
			//requestJson.put("assignee", assigneeJson);
			assigneeJson.addProperty("username", author);
			requestJson.add("assignee", assigneeJson);
		}
		
		if (reviewer != null) {
			//requestJson.put("status", "IN_REVIEW");
			requestJson.addProperty("status", "IN_REVIEW");
			//JSONObject reviewerJson = new JSONObject();
			JsonObject reviewerJson = new JsonObject();
			//reviewerJson.put("username", reviewer);
			//requestJson.put("reviewer", reviewerJson);
			reviewerJson.addProperty("username", reviewer);
			requestJson.add("reviewer", reviewerJson);
		}
		
		HttpEntity<Object> requestEntity = new HttpEntity<Object>(requestJson, headers);
		restTemplate.put(endPoint, requestEntity);
		//   HttpEntity<Shop[]> response = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, Shop[].class, param);

		return taskKey;
		//ResponseEntity<String> response = restTemplate.put(endPoint, entity);
		//return reponse.
		//JSONResource response = resty.json(endPoint, Resty.put(RestyHelper.content(requestJson, JSON_CONTENT_TYPE)));
		//return response.get("key").toString();
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

	public Project getProject(String projectStr) throws SnowOwlClientException {
		JSONResource response = null;
		String json = null;
		try {
			String endPoint = serverUrl + apiRoot + "projects/" + projectStr;
			response = resty.json(endPoint);
			json = response.toObject().toString();
			Project projectObj = gson.fromJson(json, Project.class);
			return projectObj;
		} catch (Exception e) {
			throw new SnowOwlClientException("Unable to recover project " + projectStr +". Received: " + (json==null?"NULL" : json), e);
		}
	}
	
	public Task getTask(String taskKey) throws SnowOwlClientException {
		try {
			String projectStr = taskKey.substring(0, taskKey.indexOf("-"));
			String endPoint = serverUrl + apiRoot + "projects/" + projectStr + "/tasks/" + taskKey;
			JSONResource response = resty.json(endPoint);
			String json = response.toObject().toString();
			Task taskObj = gson.fromJson(json, Task.class);
			return taskObj;
		} catch (Exception e) {
			throw new SnowOwlClientException("Unable to recover task " + taskKey, e);
		}
	}

	public Classification classify(String taskKey) throws SnowOwlClientException {
		try {
			String projectStr = taskKey.substring(0, taskKey.indexOf("-"));
			String endPoint = serverUrl + apiRoot + "projects/" + projectStr + "/tasks/" + taskKey + "/classifications";
			JSONResource response = resty.json(endPoint, Resty.content(""));
			String json = response.toObject().toString();
			Classification classification = gson.fromJson(json, Classification.class);
			return classification;
		} catch (Exception e) {
			throw new SnowOwlClientException("Unable to classify " + taskKey, e);
		}
	}
	
	public Status validate(String taskKey) throws SnowOwlClientException {
		try {
			String projectStr = taskKey.substring(0, taskKey.indexOf("-"));
			String endPoint = serverUrl + apiRoot + "projects/" + projectStr + "/tasks/" + taskKey + "/validation";
			JSONResource response = resty.json(endPoint, Resty.content(""));
			String json = response.toObject().toString();
			Status status = gson.fromJson(json, Status.class);
			return status;
		} catch (Exception e) {
			throw new SnowOwlClientException("Unable to initiate validation on " + taskKey, e);
		}
	}

}
