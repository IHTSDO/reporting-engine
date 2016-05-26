package org.ihtsdo.termserver.scripting.domain;

import java.lang.reflect.Type;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class RelationshipSerializer implements JsonSerializer<Relationship> {

	@Override
	public JsonElement serialize(Relationship r, Type typeOfSrc,
			JsonSerializationContext context) {
		final JsonObject json = new JsonObject();
		json.addProperty("effectiveTime", r.getEffectiveTime());
		json.addProperty("moduleId",r.getModuleId());
		json.addProperty("active", r.isActive());
		json.addProperty("relationshipId", r.getRelationshipId());
		
		JsonObject type = new JsonObject();
		type.addProperty("conceptId", r.getType().getConceptId());
		type.addProperty("fsn", r.getType().getFsn());
		json.add("type", type);
		
		JsonObject target = new JsonObject();
		target.addProperty("effectiveTime", r.getTarget().getEffectiveTime());
		target.addProperty("moduleId", r.getTarget().getModuleId());
		target.addProperty("active", r.getTarget().isActive());
		target.addProperty("conceptId", r.getTarget().getConceptId());
		target.addProperty("fsn", r.getTarget().getFsn());
		target.addProperty("definitionStatus", r.getTarget().getDefinitionStatus());
		json.add("target", target);
		
		json.addProperty("sourceId", r.getSourceId());
		json.addProperty("groupId", r.getGroupId());
		json.addProperty("characteristicType", r.getCharacteristicType().toString());
		json.addProperty("modifier", r.getModifier().toString());
		
		return json;
	}

}
