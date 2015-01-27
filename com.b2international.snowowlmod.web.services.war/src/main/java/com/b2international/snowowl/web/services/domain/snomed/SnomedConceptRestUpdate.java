/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

import com.b2international.snowowl.rest.snomed.domain.AssociationType;
import com.b2international.snowowl.rest.snomed.domain.DefinitionStatus;
import com.b2international.snowowl.rest.snomed.domain.InactivationIndicator;
import com.b2international.snowowl.rest.snomed.domain.SubclassDefinitionStatus;
import com.b2international.snowowl.rest.snomed.impl.domain.SnomedConceptUpdate;
import com.google.common.collect.Multimap;

/**
 * @author apeteri
 * @since 1.0
 */
public class SnomedConceptRestUpdate extends AbstractSnomedComponentRestUpdate<SnomedConceptUpdate> {

	private DefinitionStatus definitionStatus;
	private SubclassDefinitionStatus subclassDefinitionStatus;
	private Multimap<AssociationType, String> associationTargets;
	private InactivationIndicator inactivationIndicator;

	/**
	 * @return
	 */
	public DefinitionStatus getDefinitionStatus() {
		return definitionStatus;
	}

	/**
	 * @return
	 */
	public SubclassDefinitionStatus getSubclassDefinitionStatus() {
		return subclassDefinitionStatus;
	}

	public void setDefinitionStatus(final DefinitionStatus definitionStatus) {
		this.definitionStatus = definitionStatus;
	}

	public void setSubclassDefinitionStatus(final SubclassDefinitionStatus subclassDefinitionStatus) {
		this.subclassDefinitionStatus = subclassDefinitionStatus;
	}

	@Override
	protected SnomedConceptUpdate createComponentUpdate() {
		return new SnomedConceptUpdate();
	}

	/**
	 * Returns with the associations between the current component and other SNOMED&nbsp;CT concepts.
	 * The associations are represented as a multimap where the keys are the {@link AssociationType association type}s
	 * and the values are the referred associations. 
	 * @return a multimap of associations.
	 */
	public Multimap<AssociationType, String> getAssociationTargets() {
		return associationTargets;
	}

	/**
	 * Sets the associations for the current concept.
	 * <br>Counterpart of {@link #getAssociationTargets()}.
	 * @param associationTargets the multimap of associations.
	 */
	public void setAssociationTargets(final Multimap<AssociationType, String> associationTargets) {
		this.associationTargets = associationTargets;
	}

	/**
	 * Returns with the concept inactivation reason (if any). May return with {@code null}
	 * if the concept is active or no reason was specified during the concept inactivation process.
	 * @return the inactivation process. Can be {@code null} if the concept is not retired or no
	 * reason was specified.
	 */
	public InactivationIndicator getInactivationIndicator() {
		return inactivationIndicator;
	}

	/**
	 * Counterpart of the {@link #getInactivationIndicator()}.
	 * <br>Sets the inactivation reason for the concept update.
	 * @param inactivationIndicator the desired inactivation reason for the concept update.
	 */
	public void setInactivationIndicator(final InactivationIndicator inactivationIndicator) {
		this.inactivationIndicator = inactivationIndicator;
	}

	/**
	 * @return
	 */
	@Override
	public SnomedConceptUpdate toComponentUpdate() {
		final SnomedConceptUpdate result = super.toComponentUpdate();
		result.setDefinitionStatus(getDefinitionStatus());
		result.setSubclassDefinitionStatus(getSubclassDefinitionStatus());
		result.setAssociationTargets(getAssociationTargets());
		result.setInactivationIndicator(getInactivationIndicator());
		return result;
	}
}
