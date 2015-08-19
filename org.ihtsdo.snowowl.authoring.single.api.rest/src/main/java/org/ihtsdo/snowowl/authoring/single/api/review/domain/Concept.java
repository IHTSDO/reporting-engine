package org.ihtsdo.snowowl.authoring.single.api.review.domain;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Concept {

	@Id
	private String id;

	protected Concept() {
	}

	public Concept(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

}
