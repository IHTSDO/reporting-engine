package org.ihtsdo.snowowl.authoring.batchimport.api.mapping;

import com.b2international.snowowl.snomed.api.domain.browser.ISnomedBrowserDescription;
import com.b2international.snowowl.snomed.api.domain.browser.ISnomedBrowserRelationship;
import com.b2international.snowowl.snomed.api.impl.domain.browser.SnomedBrowserDescription;
import com.b2international.snowowl.snomed.api.impl.domain.browser.SnomedBrowserRelationship;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

public interface SnomedBrowserConceptMixin {

	@JsonDeserialize(contentAs=SnomedBrowserDescription.class)
	void setDescriptions(final List<ISnomedBrowserDescription> descriptions);

	@JsonDeserialize(contentAs=SnomedBrowserRelationship.class)
	void setRelationships(final List<ISnomedBrowserRelationship> relationships);

}
