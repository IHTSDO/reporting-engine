/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.impl.service;

import com.b2international.commons.ClassUtils;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.rest.domain.ComponentCategory;
import com.b2international.snowowl.rest.domain.IComponentRef;
import com.b2international.snowowl.rest.impl.domain.InternalComponentRef;
import com.b2international.snowowl.rest.snomed.domain.ISnomedRelationship;
import com.b2international.snowowl.rest.snomed.domain.ISnomedRelationshipInput;
import com.b2international.snowowl.rest.snomed.domain.ISnomedRelationshipUpdate;
import com.b2international.snowowl.rest.snomed.service.ISnomedRelationshipService;
import com.b2international.snowowl.snomed.Relationship;
import com.b2international.snowowl.snomed.SnomedFactory;
import com.b2international.snowowl.snomed.datastore.SnomedDatastoreActivator;
import com.b2international.snowowl.snomed.datastore.SnomedEditingContext;
import com.b2international.snowowl.snomed.datastore.SnomedRelationshipIndexEntry;
import com.b2international.snowowl.snomed.datastore.SnomedRelationshipLookupService;

/**
 * @author apeteri
 */
public class SnomedRelationshipServiceImpl 
	extends AbstractSnomedComponentServiceImpl<ISnomedRelationshipInput, ISnomedRelationship, ISnomedRelationshipUpdate, Relationship> 
	implements ISnomedRelationshipService {

	private final SnomedRelationshipLookupService snomedRelationshipLookupService = new SnomedRelationshipLookupService();

	public SnomedRelationshipServiceImpl() {
		super(SnomedDatastoreActivator.REPOSITORY_UUID, ComponentCategory.RELATIONSHIP);
	}

	private SnomedRelationshipConverter getRelationshipConverter(final IBranchPath branchPath) {
		return new SnomedRelationshipConverter(getMembershipLookupService(branchPath));
	}

	@Override
	protected boolean componentExists(final IComponentRef ref) {
		final InternalComponentRef internalRef = ClassUtils.checkAndCast(ref, InternalComponentRef.class);
		return snomedRelationshipLookupService.exists(internalRef.getBranchPath(), internalRef.getComponentId());
	}

	@Override
	protected Relationship convertAndRegister(final ISnomedRelationshipInput input, final SnomedEditingContext editingContext) {
		final Relationship relationship = SnomedFactory.eINSTANCE.createRelationship();

		relationship.setId(input.getIdGenerationStrategy().getId());
		relationship.setActive(true);
		relationship.unsetEffectiveTime();
		relationship.setReleased(false);
		relationship.setModule(getModuleConcept(input, editingContext));
		relationship.setCharacteristicType(getConcept(input.getCharacteristicType().getConceptId(), editingContext));
		relationship.setDestination(getConcept(input.getDestinationId(), editingContext));
		relationship.setDestinationNegated(input.isDestinationNegated());
		relationship.setGroup(input.getGroup());
		relationship.setModifier(getConcept(input.getModifier().getConceptId(), editingContext));
		relationship.setSource(getConcept(input.getSourceId(), editingContext));
		relationship.setType(getConcept(input.getTypeId(), editingContext));
		relationship.setUnionGroup(input.getUnionGroup());

		// TODO: add a refinability refset member here?
		return relationship;
	}

	@Override
	protected ISnomedRelationship doRead(final IComponentRef ref) {
		final InternalComponentRef internalRef = ClassUtils.checkAndCast(ref, InternalComponentRef.class);
		final SnomedRelationshipIndexEntry relationshipIndexEntry = snomedRelationshipLookupService.getComponent(internalRef.getBranchPath(), internalRef.getComponentId());
		return getRelationshipConverter(internalRef.getBranchPath()).apply(relationshipIndexEntry);
	}

	private Relationship getRelationship(final String relationshipId, final SnomedEditingContext editingContext) {
		return snomedRelationshipLookupService.getComponent(relationshipId, editingContext.getTransaction());
	}

	@Override
	protected void doUpdate(final IComponentRef ref, final ISnomedRelationshipUpdate update, final SnomedEditingContext editingContext) {
		final Relationship relationship = getRelationship(ref.getComponentId(), editingContext);

		boolean changed = false;
		changed |= updateStatus(update.isActive(), relationship, editingContext);
		changed |= updateModule(update.getModuleId(), relationship, editingContext);

		if (changed) {
			relationship.unsetEffectiveTime();
		}
	}

	@Override
	protected void doDelete(final IComponentRef ref, final SnomedEditingContext editingContext) {
		final Relationship relationship = getRelationship(ref.getComponentId(), editingContext);
		editingContext.delete(relationship);
	}
}
