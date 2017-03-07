package org.ihtsdo.snowowl.authoring.batchimport.api.mapping;

import com.b2international.snowowl.snomed.api.domain.browser.ISnomedBrowserRelationshipTarget;
import com.b2international.snowowl.snomed.api.domain.browser.ISnomedBrowserRelationshipType;
import com.b2international.snowowl.snomed.api.impl.domain.browser.SnomedBrowserRelationshipTarget;
import com.b2international.snowowl.snomed.api.impl.domain.browser.SnomedBrowserRelationshipType;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public interface SnomedBrowserRelationshipMixin {

	@JsonDeserialize(as=SnomedBrowserRelationshipType.class)
	void setType(final ISnomedBrowserRelationshipType type);

	@JsonDeserialize(as=SnomedBrowserRelationshipTarget.class)
	void setTarget(final ISnomedBrowserRelationshipTarget target);
}
