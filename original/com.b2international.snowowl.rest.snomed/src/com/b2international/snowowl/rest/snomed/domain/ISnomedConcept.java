/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.rest.snomed.domain;

import com.b2international.snowowl.rest.domain.IComponentNode;
import com.google.common.collect.Multimap;

/**
 * TODO: review documentation
 * 
 * This interface represents a SNOMED CT specific concept with a unique SNOMED CT identifier, 
 * a human readable preferred term, its child and parent concepts. Due to its SNOMED CT specific nature it, also has some additional 
 * SNOMED CT specific meta-data: <i>module</i>, <i>effective time</i>, <i>definition status</i> and <i>status</i> .
 * 
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link IIdentifiedElement#getId() <em>Identifier</em>}</li>
 *   <li>{@link INamedElement#getLabel() <em>Label</em>}</li>
 *   <li>{@link IContainerElement#getChildCount() <em>Child count</em>}</li>
 *   <li>{@link IParentElement#getChildren() <em>Children</em>}</li>
 *   <li>{@link IChildElement#getParents() <em>Parents</em>}</li>
 *   <li>{@link ISnomedElement#getModule() <em>Module</em>}</li>
 *   <li>{@link ISnomedElement#getEffectiveTime() <em>Effective Time</em>}</li>
 *   <li>{@link ISnomedElement#isActive() <em>Status</em>}</li>
 * </ul>
 * </p>
 * 
 * @author Akos Kitta
 * @author Andras Peteri
 */
public interface ISnomedConcept extends ISnomedComponent, IComponentNode {

	/**
	 * TODO document
	 * @return
	 */
	DefinitionStatus getDefinitionStatus();

	/**
	 * TODO document
	 * @return
	 */
	SubclassDefinitionStatus getSubclassDefinitionStatus();

	/**
	 * TODO document
	 * @return
	 */
	InactivationIndicator getInactivationIndicator();

	/**
	 * TODO document
	 * @return
	 */
	Multimap<AssociationType, String> getAssociationTargets();
}
