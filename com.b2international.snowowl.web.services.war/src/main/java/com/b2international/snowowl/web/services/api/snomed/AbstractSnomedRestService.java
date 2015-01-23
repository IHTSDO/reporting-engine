/**
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.web.services.api.snomed;

import com.b2international.snowowl.rest.domain.IComponentRef;
import com.b2international.snowowl.rest.impl.domain.ComponentRef;
import com.b2international.snowowl.web.services.api.AbstractRestService;

/**
 * Abstract SNOMED CT REST service base class.
 * 
 * @author apeteri
 * @since 1.0
 */
public class AbstractSnomedRestService extends AbstractRestService {

	protected IComponentRef createComponentRef(final String version, final String taskId, final String componentId) {
		final ComponentRef conceptRef = new ComponentRef();
		conceptRef.setShortName("SNOMEDCT");
		conceptRef.setVersion(version);
		conceptRef.setTaskId(taskId);
		conceptRef.setComponentId(componentId);
		conceptRef.checkStorageExists();
		return conceptRef;
	}
}
