package org.ihtsdo.termserver.scripting.domain;

import java.lang.reflect.Type;

import com.google.gson.*;

public class RelationshipSerializer implements JsonSerializer<Relationship>, ScriptConstants {

	@Override
	public JsonElement serialize(Relationship r, Type typeOfSrc,
			JsonSerializationContext context) {
		final JsonObject json = new JsonObject();

		json.addProperty("moduleId",r.getModuleId());
		json.addProperty("active", r.isActive());
		json.addProperty("released", r.isReleased());
		json.addProperty("relationshipId", r.getRelationshipId());
		json.addProperty("sourceId", r.getSourceId());
		if (r.isConcrete()) {
			json.add("concreteValue", createConcreteValueJson(r));
			json.add("target", new JsonObject());
			//json.addProperty("value", r.getConcreteValue().getValueWithPrefix());
		} else {
			json.add("target", createTargetJson(r));
		}
		json.add("type", createTypeJson(r));
		json.addProperty("groupId", r.getGroupId());
		json.addProperty("characteristicType", r.getCharacteristicType().toString());
		if (r.getModifier() == null) {
			//If the source concept is also missing, then we're probably part of an axiom
			//which doesn't specify the existential modifier
			if (r.getSource() != null) {
				throw new IllegalArgumentException("Modifier encountered with no modifier specified: " + r);
			}
		} else {
			json.addProperty("modifier", r.getModifier().toString());
		}
		json.addProperty("effectiveTime", r.getEffectiveTime());
		
		return json;
	}

	private JsonElement createTypeJson(Relationship r) {
		JsonObject typeJson = new JsonObject();
		typeJson.addProperty("conceptId", r.getType().getConceptId());
		typeJson.addProperty("fsn", r.getType().getFsn());
		return typeJson;
	}

	private JsonElement createTargetJson(Relationship r) {
		JsonObject target = new JsonObject();
		if (r.isConcrete()) {
			throw new IllegalArgumentException("Call createConcreteValue() for concrete relationship: " + r);
		} else if (r.getTarget() == null) {
			throw new IllegalArgumentException("Null Target when serializing relationship: " + r);
		} else {
			target.addProperty("effectiveTime", r.getTarget().getEffectiveTime());
			target.addProperty("moduleId", r.getTarget().getModuleId());
			target.addProperty("active", r.getTarget().isActive());
			target.addProperty("conceptId", r.getTarget().getConceptId());
			target.addProperty("fsn", r.getTarget().getFsn());
			target.addProperty("definitionStatus", r.getTarget().getDefinitionStatus().toString());
		}
		return target;
	}

	private JsonElement createConcreteValueJson(Relationship r) {
		JsonObject cvJson = new JsonObject();
		ConcreteValue cv = r.getConcreteValue();
		cvJson.addProperty("dataType", cv.getDataType().toString());
		cvJson.addProperty("value", cv.getValue());
		cvJson.addProperty("valueWithPrefix", cv.getValueWithPrefix());
		return cvJson;
	}

}
