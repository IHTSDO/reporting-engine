package org.ihtsdo.snowowl.test;

import us.monoid.json.JSONArray;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;

public class ConceptHelper {
	public static JSONObject createBaseConcept() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("definitionStatus", "PRIMITIVE");
		json.put("active", "true");
		json.put("moduleId", "900000000000207008");
		json.put("descriptions", new JSONArray());
		json.put("relationships", new JSONArray());
		return json;
	}

	public static JSONObject createConcept() throws Exception {
		return createConcept("a (finding)", "a", "116680003");
	}

	public static JSONObject createConcept(String pt) throws Exception {
		return createConcept(pt + " (test)", pt, "116680003");
	}

	public static JSONObject createConcept(String fsn, String pt, String parentId) throws Exception {
		final JSONObject json = ConceptHelper.createBaseConcept();
		ConceptHelper.addDescription(fsn, "FSN", json);
		ConceptHelper.addDescription(pt, "SYNONYM", json);
		ConceptHelper.addRelationship("116680003", parentId, json);
		return json;
	}

	public static void addDescription(String term, String type, JSONObject json) throws JSONException {
		final JSONObject desc = new JSONObject();
		desc.put("active", true);
		desc.put("moduleId", "900000000000207008");
		desc.put("type", type);
		desc.put("term", term);
		desc.put("lang", "en");
		desc.put("caseSignificance", "INITIAL_CHARACTER_CASE_INSENSITIVE");
		desc.put("acceptabilityMap", new JSONObject().put("900000000000509007", "PREFERRED").put("900000000000508004", "PREFERRED"));
		json.getJSONArray("descriptions").put(desc);
	}

	public static void addRelationship(String targetId, String typeId, JSONObject concept) throws JSONException {
		JSONObject json = new JSONObject();
		json.put("active", true);
		json.put("characteristicType", "STATED_RELATIONSHIP");
		json.put("groupId", 0);
		json.put("modifier", "EXISTENTIAL");
		json.put("moduleId", "900000000000207008");
		json.put("target", new JSONObject().put("conceptId", targetId));
		json.put("type", new JSONObject().put("conceptId", typeId));
		((JSONArray) concept.get("relationships")).put(json);
	}

}
