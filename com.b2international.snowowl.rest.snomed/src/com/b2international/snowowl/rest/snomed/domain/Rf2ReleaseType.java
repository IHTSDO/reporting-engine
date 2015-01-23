/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;



/**
 * Enumeration for the available RF2 release format 
 * types for the SNOMED&nbsp;CT ontology.
 * <br>Available types are the followings:
 * <ul>
 * <li>{@link Rf2ReleaseType#DELTA <em>Delta</em>}</li>
 * <li>{@link Rf2ReleaseType#SNAPSHOT <em>Snapshot</em>}</li>
 * <li>{@link Rf2ReleaseType#FULL <em>Full</em>}</li>
 * </ul>
 * @author Akos Kitta
 *
 */
public enum Rf2ReleaseType {

	/**
	 * Delta RF2 publication format.
	 * Contains the most recent changes.
	 * @see Rf2ReleaseType
	 */
	DELTA("Delta"),
	/**
	 * Snapshot RF2 publication format.
	 * Contains all component with their latest state.
	 * @see Rf2ReleaseType
	 */
	SNAPSHOT("Snapshot"),
	/**
	 * Full RF2 publication format.
	 * Contains everything.
	 * @see Rf2ReleaseType
	 */
	FULL("Full");

	private String name;
	
	private Rf2ReleaseType(final String name) {
		this.name = name;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
}
