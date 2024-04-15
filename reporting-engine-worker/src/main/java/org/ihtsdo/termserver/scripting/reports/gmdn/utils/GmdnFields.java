package org.ihtsdo.termserver.scripting.reports.gmdn.utils;

/**
 * The GmdnFields class represents the fields of the GMDN system.
 *<p>
 * It is an enum that contains the following fields:
 * <ul>
 * <li>TERM_CODE
 * <li>TERM_IS_IVD
 * <li>TERM_NAME
 * <li>TERM_DEFINITION
 * <li>TERM_STATUS
 * <li>CREATED_DATE
 * <li>MODIFIED_DATE
 * </ul>
 *<p>
 * Each field has a name associated with it, which can be retrieved using the {@link #getName()} method.
 *<p>
 * Example usage:
 * <p>
 * <code>
 * String fieldName = GmdnFields.TERM_CODE.getName();
 * </code>
 */
public enum GmdnFields {
	TERM_CODE ("termCode"),
	TERM_IS_IVD ("termIsIVD"),
	TERM_NAME ("termName"),
	TERM_DEFINITION ("termDefinition"),
	TERM_STATUS ("termStatus"),
	CREATED_DATE ("createdDate"),
	MODIFIED_DATE("modifiedDate");

	private final String fieldName;

	GmdnFields (String name) {
		this.fieldName = name;
	}

	public String getName() {
		return this.fieldName;
	}
}
