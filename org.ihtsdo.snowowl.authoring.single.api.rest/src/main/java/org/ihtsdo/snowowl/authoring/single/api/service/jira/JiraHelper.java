package org.ihtsdo.snowowl.authoring.single.api.service.jira;

import net.rcarz.jiraclient.*;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public class JiraHelper {

	private static Logger logger = LoggerFactory.getLogger(JiraHelper.class);

	public static String toStringOrNull(Object jsonProperty) {
		if (jsonProperty != null) {
			if (jsonProperty instanceof String) {
				return (String) jsonProperty;
			} else if (jsonProperty instanceof Map) {
				return (String) ((Map)jsonProperty).get("value");
			} else if (jsonProperty instanceof net.sf.json.JSONNull) {
				return null;
			} else {
				logger.info("Unrecognised Jira Field type {}, {}", jsonProperty.getClass(), jsonProperty);
			}
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
