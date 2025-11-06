package org.ihtsdo.termserver.scripting.client;

import us.monoid.json.JSONObject;
import us.monoid.web.Content;

import java.io.UnsupportedEncodingException;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestyHelper {

	private static final Logger LOGGER = LoggerFactory.getLogger(RestyHelper.class);

	public static final String UTF_8 = "UTF-8";

	public static Content content(JSONObject someJson, String aMimeType) {
		return content(someJson.toString(), aMimeType);
	}
	
	public static Content content(String content, String aMimeType) {
		try {
			return new Content(aMimeType, content.getBytes(UTF_8));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(UTF_8 + " encoding not supported!", e);
		}
	}

}
