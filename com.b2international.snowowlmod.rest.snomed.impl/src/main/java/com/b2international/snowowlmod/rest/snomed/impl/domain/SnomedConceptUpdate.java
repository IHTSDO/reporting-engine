/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl.domain;

import com.b2international.snowowl.rest.snomed.domain.AssociationType;
import com.b2international.snowowl.rest.snomed.domain.DefinitionStatus;
import com.b2international.snowowl.rest.snomed.domain.ISnomedConceptUpdate;
import com.b2international.snowowl.rest.snomed.domain.InactivationIndicator;
import com.b2international.snowowl.rest.snomed.domain.SubclassDefinitionStatus;
import com.google.common.collect.Multimap;

/**
 * @author apeteri
 */
public class SnomedConceptUpdate extends AbstractSnomedComponentUpdate implements ISnomedConceptUpdate {

	private DefinitionStatus definitionStatus;
	private SubclassDefinitionStatus subclassDefinitionStatus;
	private InactivationIndicator inactivationIndicator;
	private Multimap<AssociationType, String> associationTargets;

	@Override
	public DefinitionStatus getDefinitionStatus() {
		return definitionStatus;
	}

	@Override
	public SubclassDefinitionStatus getSubclassDefinitionStatus() {
		return subclassDefinitionStatus;
	}

	@Override
	public InactivationIndicator getInactivationIndicator() {
		return inactivationIndicator;
	}

	@Override
	public Multimap<AssociationType, String> getAssociationTargets() {
		return associationTargets;
	}

	public void setDefinitionStatus(final DefinitionStatus definitionStatus) {
		this.definitionStatus = definitionStatus;
	}

	public void setSubclassDefinitionStatus(final SubclassDefinitionStatus subclassDefinitionStatus) {
		this.subclassDefinitionStatus = subclassDefinitionStatus;
	}

	public void setInactivationIndicator(final InactivationIndicator inactivationIndicator) {
		this.inactivationIndicator = inactivationIndicator;
	}

	public void setAssociationTargets(final Multimap<AssociationType, String> associationTargets) {
		this.associationTargets = associationTargets;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SnomedConceptUpdate [getModuleId()=");
		builder.append(getModuleId());
		builder.append(", getDefinitionStatus()=");
		builder.append(getDefinitionStatus());
		builder.append(", getSubclassDefinitionStatus()=");
		builder.append(getSubclassDefinitionStatus());
		builder.append(", getInactivationIndicator()=");
		builder.append(getInactivationIndicator());
		builder.append(", getAssociationTargets()=");
		builder.append(getAssociationTargets());
		builder.append("]");
		return builder.toString();
	}
}
