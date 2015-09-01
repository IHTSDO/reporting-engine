package org.ihtsdo.snowowl.authoring.single.api.pojo;

import java.util.Date;

public class ConceptConflict {

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
}
