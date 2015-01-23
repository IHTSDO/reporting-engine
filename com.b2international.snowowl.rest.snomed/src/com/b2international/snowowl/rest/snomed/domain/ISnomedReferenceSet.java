/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.rest.snomed.domain;

import com.b2international.snowowl.rest.domain.IReferenceSet;

/**
 * TODO: review documentation 
 * 
 * This interface represents a SNOMED CT specific reference set element with a unique SNOMED CT identifier, 
 * a human readable preferred term, its contained reference set members, and its identifier SNOMED CT concept.
 * 
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link IIdentifiedElement#getId() <em>Identifier</em>}</li>
 *   <li>{@link INamedElement#getLabel() <em>Label</em>}</li>
 *   <li>{@link IContainerElement#getChildCount() <em>Child count</em>}</li>
 *   <li>{@link IParentElement#getChildren() <em>Children</em>}</li>
 *   <li>{@link ISnomedReferenceSet#getIdentifier() <em>Identifier concept</em>}</li>
 *   <li>{@link ISnomedReferenceSet#getMapTargetComponentType() <em>Map target component type</em>}</li>
 *   <li>{@link IReferenceSet#getReferencedComponentType() <em>Referenced component type</em>}</li>
 * </ul>
 * </p>
 * 
 * @author Akos Kitta
 */
public interface ISnomedReferenceSet extends IReferenceSet {
	// Empty interface body
}
