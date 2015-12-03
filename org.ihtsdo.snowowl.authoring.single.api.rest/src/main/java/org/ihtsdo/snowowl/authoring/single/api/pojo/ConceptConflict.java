package org.ihtsdo.snowowl.authoring.single.api.pojo;

import com.b2international.snowowl.core.domain.IComponent;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;

public class ConceptConflict implements IComponent {

	String id;
	Date sourceLastUpdate;
	Date targetLastUpdate;
	String fsn;

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
	public Boolean isReleased() {
		return false;
	}
	
	public String getFsn() {
		return fsn;
	}

	public void setFsn(String fsn) {
		this.fsn = fsn;
	}
}
