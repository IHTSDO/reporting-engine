package org.ihtsdo.termserver.scripting.client;

import us.monoid.json.JSONObject;
import us.monoid.web.Content;

import java.io.UnsupportedEncodingException;

public class RestyHelper {

	public static final String UTF_8 = "UTF-8";

	public static Content content(JSONObject someJson, String aMimeType) {
		try {
			return new Content(aMimeType, someJson.toString().getBytes(UTF_8));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(UTF_8 + " encoding not supported!", e);
		}
	}

}
