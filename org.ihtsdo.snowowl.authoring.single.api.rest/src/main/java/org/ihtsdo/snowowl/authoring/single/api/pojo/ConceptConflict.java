package org.ihtsdo.snowowl.authoring.single.api.pojo;

import java.util.Date;

import com.b2international.snowowl.api.domain.IComponent;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class ConceptConflict implements IComponent {

	String id;
	Date sourceLastUpdate;
	Date targetLastUpdate;
	
	public ConceptConflict(String id) {
		this.id = id;
	}
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public Date getSourceLastUpdate() {
		return sourceLastUpdate;
	}
	public void setSourceLastUpdate(Date sourceLastUpdate) {
		this.sourceLastUpdate = sourceLastUpdate;
	}
	public Date getTargetLastUpdate() {
		return targetLastUpdate;
	}
	public void setTargetLastUpdate(Date targetLastUpdate) {
		this.targetLastUpdate = targetLastUpdate;
	}

	@Override
	@JsonIgnore
	public boolean isReleased() {
		return false;
	}
}
