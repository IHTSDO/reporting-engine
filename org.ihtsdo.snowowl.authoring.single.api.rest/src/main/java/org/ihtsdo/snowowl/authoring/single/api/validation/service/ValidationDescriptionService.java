package org.ihtsdo.snowowl.authoring.single.api.validation.service;

import com.b2international.commons.http.ExtendedLocale;
import com.b2international.snowowl.snomed.SnomedConstants;
import com.b2international.snowowl.snomed.core.domain.ISnomedDescription;
import org.ihtsdo.drools.service.DescriptionService;

import java.util.*;

public class ValidationDescriptionService implements DescriptionService {

	private com.b2international.snowowl.snomed.api.impl.DescriptionService descriptionService;

	public ValidationDescriptionService(com.b2international.snowowl.snomed.api.impl.DescriptionService descriptionService) {
		this.descriptionService = descriptionService;
	}

	@Override
	public Set<String> getFSNs(Set<String> conceptIds, String... languageRefsetIds) {
		List<ExtendedLocale> locales = new ArrayList<>();
		for (String languageRefsetId : languageRefsetIds) {
			String languageCode = SnomedConstants.LanguageCodeReferenceSetIdentifierMapping.getLanguageCode(languageRefsetId);
			locales.add(new ExtendedLocale(languageCode, null, languageRefsetId));
		}
		return getTerms(descriptionService.getFullySpecifiedNames(conceptIds, locales));
	}

	private Set<String> getTerms(Map<String, ISnomedDescription> fullySpecifiedNames) {
		Set<String> terms = new HashSet<>();
		for (ISnomedDescription description : fullySpecifiedNames.values()) {
			terms.add(description.getTerm());
		}
		return terms;
	}

}
