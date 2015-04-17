package org.ihtsdo.snowowl.api.rest.common;

import com.b2international.snowowl.api.domain.IComponentRef;
import com.b2international.snowowl.api.impl.domain.ComponentRef;

public class ComponentRefHelper {

	public IComponentRef createComponentRef(final String version, final String taskId, final String componentId) {
		final ComponentRef conceptRef = new ComponentRef();
		conceptRef.setShortName("SNOMEDCT");
		conceptRef.setVersion(version);
		conceptRef.setTaskId(taskId);
		conceptRef.setComponentId(componentId);
		return conceptRef;
	}

}
