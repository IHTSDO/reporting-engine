package org.ihtsdo.termserver.scripting.domain;

import java.lang.reflect.Type;

import com.google.gson.*;

public class RelationshipSerializer implements JsonSerializer<Relationship>, RF2Constants {

	@Override
	public JsonElement serialize(Relationship r, Type typeOfSrc,
			JsonSerializationContext context) {
		final JsonObject json = new JsonObject();
		json.addProperty("effectiveTime", r.getEffectiveTime());
		json.addProperty("moduleId",r.getModuleId());
		json.addProperty("active", r.isActive());
		json.addProperty("released", r.isReleased());
		json.addProperty("relationshipId", r.getRelationshipId());
		
		json.add("type", createTypeJson(r));
		json.add("target", createTargetJson(r));
		
		json.addProperty("sourceId", r.getSourceId());
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
		if (r.getTarget() == null) {
			throw new IllegalArgumentException("Null Target when serializing relationship: " + r);
		}
		target.addProperty("effectiveTime", r.getTarget().getEffectiveTime());
		target.addProperty("moduleId", r.getTarget().getModuleId());
		target.addProperty("active", r.getTarget().isActive());
		target.addProperty("conceptId", r.getTarget().getConceptId());
		target.addProperty("fsn", r.getTarget().getFsn());
		target.addProperty("definitionStatus", r.getTarget().getDefinitionStatus().toString());
		return target;
	}

}
