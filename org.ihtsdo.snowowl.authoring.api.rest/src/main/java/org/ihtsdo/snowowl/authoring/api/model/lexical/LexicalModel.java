package org.ihtsdo.snowowl.authoring.api.model.lexical;

import org.ihtsdo.snowowl.authoring.api.model.Model;

public class LexicalModel implements Model {

	private String name;
	private Term fsn;
	private Term preferredTerm;
	private Term synonom;

	public LexicalModel() {
	}

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
}
