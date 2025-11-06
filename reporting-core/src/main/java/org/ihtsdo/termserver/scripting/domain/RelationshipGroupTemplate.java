package org.ihtsdo.termserver.scripting.domain;

import java.util.*;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate.Mode;
import org.ihtsdo.otf.exception.TermServerScriptException;

public class RelationshipGroupTemplate {
	Set<RelationshipTemplate> relationships = new HashSet<>();
	
	public RelationshipGroupTemplate () {
		this.relationships = new HashSet<>();
	}

	public Set<RelationshipTemplate> getRelationships() {
		return relationships;
	}
	
	public void setRelationships(Set<RelationshipTemplate> relationships) {
		this.relationships = relationships;
	}
	public void addRelationship (RelationshipTemplate r) {
		relationships.add(r);
	}

	public void removeRelationship(RelationshipTemplate r) {
		relationships.remove(r);
	}

	public boolean isEmpty() {
		return relationships.isEmpty();
	}

	public int size() {
		return relationships.size();
	}

	public static RelationshipGroupTemplate constructGroup(GraphLoader gl, Object[]... templates) throws TermServerScriptException {
		RelationshipGroupTemplate rgt = new RelationshipGroupTemplate();
		for (Object[] template : templates) {
			Concept type = template[0] instanceof Concept ? (Concept)template[0] : gl.getConcept(template[0].toString());
			Concept target = template[1] instanceof Concept ? (Concept)template[1] :gl.getConcept(template[1].toString());
			RelationshipTemplate rt = new RelationshipTemplate(type, target);
			rgt.addRelationship(rt);
		}
		return rgt;
	}

	public void addRelationshipTemplate(Concept type, Concept value) {
		RelationshipTemplate rt = new RelationshipTemplate(type,value);
		addRelationship(rt);
	}
	
	public void addRelationshipTemplate(Concept type, Concept value, RelationshipTemplate.Mode mode) {
		RelationshipTemplate rt = new RelationshipTemplate(type,value);
		rt.setMode(mode);
		addRelationship(rt);
	}

	public void setMode(Mode mode) {
		for (RelationshipTemplate rt : relationships) {
			rt.setMode(mode);
		}
	}

}
