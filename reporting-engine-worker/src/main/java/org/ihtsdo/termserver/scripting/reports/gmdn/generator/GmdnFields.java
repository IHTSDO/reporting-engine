package org.ihtsdo.termserver.scripting.reports.gmdn.generator;

public enum GmdnFields {
	TERM_CODE ("termCode"),
	TERM_IS_IVD ("termIsIVD"),
	TERM_NAME ("termName"),
	TERM_DEFINITION ("termDefinition"),
	TERM_STATUS ("termStatus"),
	CREATED_DATE ("createdDate"),
	MODIFIED_DATE("modifiedDate");

	private String fieldName;

	GmdnFields (String name) {
		this.fieldName = name;
	}

	public String getName() {
		return this.fieldName;
	}
}
