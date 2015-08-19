package org.ihtsdo.snowowl.authoring.single.api.review.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class Concept {

	@Id
	@GeneratedValue(strategy= GenerationType.AUTO)
	private long id;
	private String name;

	protected Concept() {
	}

	public Concept(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return "Concept{" +
				"id=" + id +
				", name='" + name + '\'' +
				'}';
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
