package org.ihtsdo.snowowl.authoring.api.model.lexical;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.ihtsdo.snowowl.authoring.api.model.Model;

public class LexicalModel implements Model {

	@JsonProperty(required = true)
	private String name;

	@JsonProperty(required = true)
	private Term fsn;

	@JsonProperty(required = true)
	private Term preferredTerm;

	private Term synonom;

	public LexicalModel() {
	}

	@JsonProperty(value = "modelName")
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Term getFsn() {
		return fsn;
	}

	public void setFsn(Term fsn) {
		this.fsn = fsn;
	}

	public Term getPreferredTerm() {
		return preferredTerm;
	}

	public void setPreferredTerm(Term preferredTerm) {
		this.preferredTerm = preferredTerm;
	}

	public Term getSynonom() {
		return synonom;
	}

	public void setSynonom(Term synonom) {
		this.synonom = synonom;
	}

	@Override
	public String toString() {
		return "LexicalModel{" +
				"name='" + name + '\'' +
				", fsn=" + fsn +
				", preferredTerm=" + preferredTerm +
				", synonom=" + synonom +
				'}';
	}
}
