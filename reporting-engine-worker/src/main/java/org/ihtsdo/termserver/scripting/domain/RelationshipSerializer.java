package org.ihtsdo.termserver.scripting.domain;

import java.lang.reflect.Type;

import org.ihtsdo.termserver.scripting.TermServerScript;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

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
		
		JsonObject type = new JsonObject();
		type.addProperty("conceptId", r.getType().getConceptId());
		type.addProperty("fsn", r.getType().getFsn());
		json.add("type", type);
		
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
