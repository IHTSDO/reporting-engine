/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.impl.service;

import java.util.Collection;

import com.b2international.snowowl.rest.snomed.domain.CharacteristicType;
import com.b2international.snowowl.rest.snomed.domain.ISnomedRelationship;
import com.b2international.snowowl.rest.snomed.domain.RelationshipModifier;
import com.b2international.snowowl.rest.snomed.domain.RelationshipRefinability;
import com.b2international.snowowl.rest.snomed.impl.domain.SnomedRelationship;
import com.b2international.snowowl.snomed.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.datastore.SnomedRelationshipIndexEntry;
import com.b2international.snowowl.snomed.datastore.index.refset.SnomedRefSetMemberIndexEntry;
import com.b2international.snowowl.snomed.datastore.services.AbstractSnomedRefSetMembershipLookupService;
import com.google.common.collect.ImmutableSet;

/**
 * @author apeteri
 */
public class SnomedRelationshipConverter extends AbstractSnomedComponentConverter<SnomedRelationshipIndexEntry, ISnomedRelationship> {

	private final AbstractSnomedRefSetMembershipLookupService snomedRefSetMembershipLookupService;

	public SnomedRelationshipConverter(final AbstractSnomedRefSetMembershipLookupService snomedRefSetMembershipLookupService) {
		this.snomedRefSetMembershipLookupService = snomedRefSetMembershipLookupService;
	}

	@Override
	public ISnomedRelationship apply(final SnomedRelationshipIndexEntry input) {
		final SnomedRelationship result = new SnomedRelationship();
		result.setActive(input.isActive());
		result.setCharacteristicType(toCharacteristicType(input.getCharacteristicTypeId()));
		result.setDestinationId(input.getValueId());
		result.setDestinationNegated(input.isDestinationNegated());
		result.setEffectiveTime(toEffectiveTime(input.getEffectiveTimeAsLong()));
		result.setGroup(input.getGroup());
		result.setId(input.getId());
		result.setModifier(toRelationshipModifier(input.isUniversal()));
		result.setModuleId(input.getModuleId());
		result.setRefinability(getRelationshipRefinability(input.getId()));
		result.setReleased(input.isReleased());
		result.setSourceId(input.getObjectId());
		result.setTypeId(input.getAttributeId());
		result.setUnionGroup(input.getUnionGroup());

		return result;
	}

	private CharacteristicType toCharacteristicType(final String characteristicTypeId) {
		return CharacteristicType.getByConceptId(characteristicTypeId);
	}

	private RelationshipModifier toRelationshipModifier(final boolean universal) {
		return universal ? RelationshipModifier.UNIVERSAL : RelationshipModifier.EXISTENTIAL;
	}

	private RelationshipRefinability getRelationshipRefinability(final String relationshipId) {
		final Collection<SnomedRefSetMemberIndexEntry> relationshipMembers = snomedRefSetMembershipLookupService.getRelationshipMembers(
				ImmutableSet.of(Concepts.REFSET_RELATIONSHIP_REFINABILITY), 
				ImmutableSet.of(relationshipId));

		for (final SnomedRefSetMemberIndexEntry relationshipMember : relationshipMembers) {
			if (relationshipMember.isActive()) {
				return RelationshipRefinability.getByConceptId(relationshipMember.getSpecialFieldId());
			}
		}

		// TODO: is this the proper fallback value?
		return RelationshipRefinability.NOT_REFINABLE;
	}
}
