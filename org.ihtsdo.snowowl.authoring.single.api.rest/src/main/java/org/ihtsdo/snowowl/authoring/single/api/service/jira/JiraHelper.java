package org.ihtsdo.snowowl.authoring.single.api.service.jira;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.rcarz.jiraclient.RestException;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class JiraHelper {

	public static String toStringOrNull(Object jsonProperty) {
		if (jsonProperty != null && jsonProperty instanceof String) {
			return (String) jsonProperty;
		}
		return null;
	}

	public static String fieldIdLookup(String fieldName, JiraClient client) throws JiraException {
		try {
			String fieldId = null;
			final RestClient restClient = client.getRestClient();
			final URI uri = restClient.buildURI("rest/api/latest/field");
			final JSONArray fields = (JSONArray) restClient.get(uri);
			for (int i = 0; i < fields.size(); i++) {
				final JSONObject jsonObject = fields.getJSONObject(i);
				if (fieldName.equals(jsonObject.getString("name"))) {
					fieldId = jsonObject.getString("id");
				}
			}
			return fieldId;
		} catch (IOException | URISyntaxException | RestException e) {
			throw new JiraException("Failed to lookup field ID", e);
		}
	}

}
