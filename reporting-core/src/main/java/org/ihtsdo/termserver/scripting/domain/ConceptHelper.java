package org.ihtsdo.termserver.scripting.domain;

import us.monoid.json.JSONArray;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;

public class ConceptHelper {

	public static final String PREFERRED = "PREFERRED";
	public static final String ACCEPTABLE = "ACCEPTABLE";

	public static String getConceptId(JSONObject concept) throws JSONException {
		return concept.getString("conceptId");
	}

	public enum DescriptionType {
		FSN, PT, SYNONYM
	}

	public static JSONObject createBaseConcept() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("definitionStatus", "PRIMITIVE");
		json.put("active", "true");
		json.put("moduleId", "900000000000207008");
		json.put("descriptions", new JSONArray());
		json.put("relationships", new JSONArray());
		return json;
	}

	public static JSONObject newConcept() throws Exception {
		return newConcept("a (finding)", "a", "116680003");
	}

	public static JSONObject newConcept(String pt) throws Exception {
		return newConcept(pt + " (test)", pt, "116680003");
	}

	public static JSONObject newConcept(String fsn, String pt, String parentId) throws Exception {
		final JSONObject json = ConceptHelper.createBaseConcept();
		ConceptHelper.addDescription(fsn, DescriptionType.FSN, json);
		ConceptHelper.addDescription(pt, DescriptionType.PT, json);
		ConceptHelper.addRelationship(ConceptIds.isA, parentId, json);
		return json;
	}

	public static JSONObject addDescription(String term, DescriptionType type, JSONObject json) throws JSONException {
		final JSONObject desc = new JSONObject();
		desc.put("active", true);
		desc.put("moduleId", "900000000000207008");
		desc.put("term", term);
		desc.put("lang", "en");
		desc.put("caseSignificance", "INITIAL_CHARACTER_CASE_INSENSITIVE");
		final String acceptability = type != DescriptionType.SYNONYM ? PREFERRED : ACCEPTABLE;
		desc.put("acceptabilityMap", new JSONObject().put("900000000000509007", acceptability).put("900000000000508004", acceptability));
		if (type == DescriptionType.PT) {
			type = DescriptionType.SYNONYM;
		}
		desc.put("type", type.toString());
		json.getJSONArray("descriptions").put(desc);
		return desc;
	}

	public static void addRelationship(String typeId, String targetId, JSONObject concept) throws JSONException {
		JSONObject json = new JSONObject();
		try {
			final String conceptId = concept.getString("conceptId");
			json.put("sourceId", conceptId);
		} catch (JSONException e) {
		}
		json.put("active", true);
		json.put("characteristicType", "STATED_RELATIONSHIP");
		json.put("groupId", 0);
		json.put("modifier", "EXISTENTIAL");
		json.put("moduleId", "900000000000207008");
		json.put("target", new JSONObject().put("conceptId", targetId));
		json.put("type", new JSONObject().put("conceptId", typeId));
		((JSONArray) concept.get("relationships")).put(json);
	}

	public static void setModule(String moduleId, JSONObject concept) throws JSONException {
		concept.put("moduleId", moduleId);
	}

	public static JSONObject findRelationshipById(String relationshipId, JSONObject concept) throws JSONException {
		return findComponentById(relationshipId, concept, "relationships");
	}

	public static JSONObject findDescriptionById(String descriptionId, JSONObject concept) throws JSONException {
		return findComponentById(descriptionId, concept, "descriptions");
	}

	private static JSONObject findComponentById(String componentId, JSONObject concept, String componentType) throws JSONException {
		final JSONArray components = concept.getJSONArray(componentType);
		for (int i = 0; i < components.length(); i++) {
			final JSONObject component = components.getJSONObject(i);
			if (componentId.equals(component.getString("id"))) {
				return component;
			}
		}
		return null;
	}

	public static JSONObject findRelationship(String typeId, JSONObject concept) throws JSONException {
		final JSONArray relationships = concept.getJSONArray("relationships");
		for (int i = 0; i < relationships.length(); i++) {
			final JSONObject relationship = relationships.getJSONObject(i);
			if (relationship.getString("characteristicType").equals("STATED_RELATIONSHIP")) {
				final String relTypeId = relationship.getJSONObject("type").getString("conceptId");
				if (relTypeId.equals(typeId)) {
					return relationship;
				}
			}
		}
		return null;
	}

	public static JSONObject findRelationship(String typeId, String destinationId, JSONObject concept) throws JSONException {
		final JSONArray relationships = concept.getJSONArray("relationships");
		for (int i = 0; i < relationships.length(); i++) {
			final JSONObject relationship = relationships.getJSONObject(i);
			final String relTypeId = relationship.getJSONObject("type").getString("conceptId");
			final String relDestinationId = relationship.getJSONObject("target").getString("conceptId");
			if (relTypeId.equals(typeId) && relDestinationId.equals(destinationId)) {
				return relationship;
			}
		}
		return null;
	}

	public static JSONObject findFSN(JSONObject concept) throws JSONException {
		final JSONArray descriptions = concept.getJSONArray("descriptions");
		for (int a = 0; a < descriptions.length(); a++) {
			final JSONObject desc = descriptions.getJSONObject(a);
			if ("FSN".equals(desc.getString("type"))) {
				return desc;
			}
		}
		return null;
	}

	public static JSONObject findDescription(JSONObject concept, String term) throws JSONException {
		final JSONArray descriptions = concept.getJSONArray("descriptions");
		for (int a = 0; a < descriptions.length(); a++) {
			final JSONObject desc = descriptions.getJSONObject(a);
			if (term.equals(desc.getString("term"))) {
				return desc;
			}
		}
		return null;
	}
}
