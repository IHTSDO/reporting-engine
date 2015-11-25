package org.ihtsdo.snowowl.authoring.single.api.service.ts;


import com.b2international.snowowl.api.domain.IComponentRef;
import com.b2international.snowowl.api.impl.domain.ComponentRef;
import com.b2international.snowowl.api.impl.domain.StorageRef;

public class SnomedServiceHelper {

	public static final String SNOMEDCT = "SNOMEDCT";

	public static IComponentRef createComponentRef(final String branchPath, final String componentId) {
		final ComponentRef conceptRef = new ComponentRef(SNOMEDCT, branchPath, componentId);
		conceptRef.checkStorageExists();
		return conceptRef;
	}

	public static StorageRef createStorageRef(String branchPath) {
		return new StorageRef(SNOMEDCT, branchPath);
	}
}
