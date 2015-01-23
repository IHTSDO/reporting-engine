/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.rest.snomed.domain;

import com.b2international.snowowl.rest.domain.IReferenceSetMember;

/**
 * TODO: review documentation
 * 
 * Represents a SNOMED CT specific reference set member with a unique SNOMED CT identifier, 
 * a human readable preferred term, its containing reference set, and its referred SNOMED CT component. 
 * Due to its SNOMED CT specific nature it, also has some additional SNOMED CT specific meta-data: 
 * <i>module</i>, <i>effective time</i>, <i>definition status</i> and <i>status</i> .
 * 
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link IIdentifiedElement#getId() <em>Identifier</em>}</li>
 *   <li>{@link INamedElement#getLabel() <em>Label</em>}</li>
 *   <li>{@link IReferencingElement#getReferencedElement() <em>Referenced element</em>}</li>
 *   <li>{@link IReferenceSetMember#getReferenceSetId() <em>Reference set</em>}</li>
 *   <li>{@link ISnomedElement#getModule() <em>Module</em>}</li>
 *   <li>{@link ISnomedElement#getEffectiveTime() <em>Effective Time</em>}</li>
 *   <li>{@link ISnomedElement#isActive() <em>Status</em>}</li>
 * </ul>
 * </p>
 * 
 * @author Akos Kitta
 * @author Andras Peteri
 */
public interface ISnomedReferenceSetMember extends ISnomedComponent, IReferenceSetMember {
	// Empty interface body
}
