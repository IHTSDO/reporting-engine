/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl.service;

import static com.b2international.snowowl.core.ApplicationContext.getServiceForClass;
import static com.b2international.snowowl.rest.snomed.domain.DescriptionInactivationIndicator.getInactivationIndicatorByValueId;

import java.util.Collection;
import java.util.Map;

import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.rest.snomed.domain.Acceptability;
import com.b2international.snowowl.rest.snomed.domain.CaseSignificance;
import com.b2international.snowowl.rest.snomed.domain.DescriptionInactivationIndicator;
import com.b2international.snowowl.rest.snomed.domain.ISnomedDescription;
import com.b2international.snowowlmod.rest.snomed.impl.domain.SnomedDescription;
import com.b2international.snowowl.snomed.datastore.index.SnomedDescriptionIndexEntry;
import com.b2international.snowowl.snomed.datastore.index.refset.SnomedRefSetMemberIndexEntry;
import com.b2international.snowowl.snomed.datastore.services.AbstractSnomedRefSetMembershipLookupService;
import com.b2international.snowowl.snomed.datastore.services.ISnomedComponentService;
import com.google.common.collect.ImmutableMap;

/**
 * @author apeteri
 */
public class SnomedDescriptionConverter extends AbstractSnomedComponentConverter<SnomedDescriptionIndexEntry, ISnomedDescription> {

	private final AbstractSnomedRefSetMembershipLookupService snomedRefSetMembershipLookupService;

	public SnomedDescriptionConverter(final AbstractSnomedRefSetMembershipLookupService snomedRefSetMembershipLookupService) {
		this.snomedRefSetMembershipLookupService = snomedRefSetMembershipLookupService;
	}

	@Override
	public ISnomedDescription apply(final SnomedDescriptionIndexEntry input) {
		final SnomedDescription result = new SnomedDescription();
		result.setAcceptabilityMap(toAcceptabilityMap(input.getId()));
		result.setActive(input.isActive());
		result.setCaseSignificance(toCaseSignificance(input.getCaseSignificance()));
		result.setConceptId(input.getConceptId());
		result.setEffectiveTime(toEffectiveTime(input.getEffectiveTimeAsLong()));
		result.setId(input.getId());
		result.setDescriptionInactivationIndicator(getDescriptionInactivationIndicator(input.getId()));

		// TODO: index language code on SnomedDescriptionIndexEntries -- it's the only property which is not present.
		result.setLanguageCode("en");

		result.setModuleId(input.getModuleId());
		result.setReleased(input.isReleased());
		result.setTerm(input.getLabel());
		result.setTypeId(input.getType());

		return result;
	}

	private DescriptionInactivationIndicator getDescriptionInactivationIndicator(final String descriptionId) {
		final String inactivationId = getServiceForClass(ISnomedComponentService.class).getDescriptionInactivationId(getBranchPath(), descriptionId);
		return getInactivationIndicatorByValueId(inactivationId);
	}

	private IBranchPath getBranchPath() {
		return snomedRefSetMembershipLookupService.getBranchPath();
	}

	private Map<String, Acceptability> toAcceptabilityMap(final String descriptionId) {
		final Collection<SnomedRefSetMemberIndexEntry> languageMembers = snomedRefSetMembershipLookupService.getLanguageMembersForDescription(descriptionId);
		final ImmutableMap.Builder<String, Acceptability> resultsBuilder = ImmutableMap.builder();

		for (final SnomedRefSetMemberIndexEntry languageMember : languageMembers) {
			if (languageMember.isActive()) {
				resultsBuilder.put(languageMember.getRefSetIdentifierId(), toAcceptability(languageMember.getSpecialFieldId()));
			}
		}

		return resultsBuilder.build();
	}

	private Acceptability toAcceptability(final String acceptabilityId) {
		return Acceptability.getByConceptId(acceptabilityId);
	}

	private CaseSignificance toCaseSignificance(final String caseSignificanceId) {
		return CaseSignificance.getByConceptId(caseSignificanceId);
	}
}
