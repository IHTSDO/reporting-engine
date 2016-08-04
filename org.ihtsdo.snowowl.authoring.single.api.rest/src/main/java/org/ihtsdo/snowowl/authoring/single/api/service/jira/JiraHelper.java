package org.ihtsdo.snowowl.authoring.single.api.service.jira;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.rcarz.jiraclient.Attachment;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.RestClient;
import net.rcarz.jiraclient.RestException;
import net.sf.json.JSON;
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
	
	public static JSON getAttachmentAsJSON(Attachment attachment, JiraClient client, Logger logger) {
		
		try {
			
			logger.info("Get attachment " + attachment.toString() + " " + attachment.getContentUrl());
		
			final RestClient restClient = client.getRestClient();
			final JSON result = (JSONArray) restClient.get(attachment.getContentUrl());
			return result;
		} catch (IOException | URISyntaxException | RestException e) {
			logger.info("  Failed " + e.getMessage());
		
			return null;
		}
	}

}
