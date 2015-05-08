package org.ihtsdo.snowowl.authoring.api.model;

public class Template implements Model {

	private String name;
	private String logicalModelName;
	private String lexicalModelName;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLogicalModelName() {
		return logicalModelName;
	}

	public void setLogicalModelName(String logicalModelName) {
		this.logicalModelName = logicalModelName;
	}

	public String getLexicalModelName() {
		return lexicalModelName;
	}

	public void setLexicalModelName(String lexicalModelName) {
		this.lexicalModelName = lexicalModelName;
	}
}
