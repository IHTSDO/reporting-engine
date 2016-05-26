package org.ihtsdo.termserver.scripting.client;

import java.io.IOException;

import us.monoid.json.JSONObject;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;

public class SCAClient {
	private final Resty resty;
	private final String serverUrl;
	private static final String apiRoot = "snowowl/ihtsdo-sca/";
	private static final String ALL_CONTENT_TYPE = "*/*";
	private static final String JSON_CONTENT_TYPE = "application/json";

	public SCAClient(String serverUrl, String cookie) {
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

	public void setUIState(String project, String taskKey, String quotedList) throws IOException {
		String endPoint = serverUrl + apiRoot + "projects/" + project + "/tasks/" + taskKey + "/ui-state/edit-panel";
		resty.json(endPoint, RestyHelper.content(quotedList, JSON_CONTENT_TYPE));
		//TODO Move to locally maintained Resty so we can easily check for HTTP200 return status
	}
}
