package org.ihtsdo.termserver.scripting.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import us.monoid.json.JSONObject;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;

public class SCAClient {
	private final Resty resty;
	private final String serverUrl;
	private static final String apiRoot = "/snowowl/ihtsdo-sca/";
	private static final String ALL_CONTENT_TYPE = "*/*";
	private static final String SNOWOWL_CONTENT_TYPE = "application/json";
	private Logger logger = LoggerFactory.getLogger(getClass());

	public SCAClient(String serverUrl, String cookie) {
		this.serverUrl = serverUrl;
		resty = new Resty(new RestyOverrideAccept(ALL_CONTENT_TYPE));
		resty.authenticate(this.serverUrl, null,null);
	}

	public String createTask(String project, String summary, String description) throws Exception {
		String endPoint = serverUrl + apiRoot + "/projects/" + project + "/tasks";
		JSONObject requestJson = new JSONObject();
		requestJson.put("summary", summary);
		requestJson.put("description", description);
		JSONResource response = resty.json(endPoint, Resty.put(RestyHelper.content(requestJson, SNOWOWL_CONTENT_TYPE)));
		return response.get("key").toString();
	}
}
