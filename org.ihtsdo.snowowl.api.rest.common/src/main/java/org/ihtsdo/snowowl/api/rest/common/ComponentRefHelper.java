package org.ihtsdo.snowowl.api.rest.common;

import com.b2international.snowowl.api.domain.IComponentRef;
import com.b2international.snowowl.api.impl.domain.ComponentRef;

public class ComponentRefHelper {

	private static final String SNOMEDCT = "SNOMEDCT";

	public IComponentRef createComponentRef(final String branchPath, final String componentId) {
		final ComponentRef conceptRef = new ComponentRef();
		conceptRef.setShortName(SNOMEDCT);
		conceptRef.setBranchPath(branchPath);
		conceptRef.setComponentId(componentId);
		conceptRef.checkStorageExists();
		return conceptRef;
	}

}
