package org.ihtsdo.snowowl.authoring.batchimport.api.client;

import us.monoid.json.JSONArray;
import us.monoid.json.JSONObject;
import us.monoid.web.Content;

import java.io.UnsupportedEncodingException;

public class RestyHelper {

	public static final String UTF_8 = "UTF-8";

	public static Content content(JSONObject someJson, String aMimeType) {
		return content(someJson.toString(), aMimeType);
	}
	
	public static Content content(JSONArray someJsonArray, String aMimeType) {
		return content(someJsonArray.toString(), aMimeType);
	}
	
	public static Content content(String content, String aMimeType) {
		try {
			return new Content(aMimeType, content.getBytes(UTF_8));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(UTF_8 + " encoding not supported!", e);
		}
	}

}
