/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.rest.snomed.impl.domain;

import com.b2international.snowowl.rest.snomed.domain.AssociationType;
import com.b2international.snowowl.rest.snomed.domain.DefinitionStatus;
import com.b2international.snowowl.rest.snomed.domain.ISnomedConcept;
import com.b2international.snowowl.rest.snomed.domain.InactivationIndicator;
import com.b2international.snowowl.rest.snomed.domain.SubclassDefinitionStatus;
import com.google.common.collect.Multimap;

/**
 * Represents a SNOMED&nbsp;CT concept.
 * 
 * @author akitta
 * @author apeteri
 */
public class SnomedConcept extends AbstractSnomedComponent implements ISnomedConcept {

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
		builder.append("SnomedConcept [isActive()=");
		builder.append(isActive());
		builder.append(", getEffectiveTime()=");
		builder.append(getEffectiveTime());
		builder.append(", getModuleId()=");
		builder.append(getModuleId());
		builder.append(", getId()=");
		builder.append(getId());
		builder.append(", isReleased()=");
		builder.append(isReleased());
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
