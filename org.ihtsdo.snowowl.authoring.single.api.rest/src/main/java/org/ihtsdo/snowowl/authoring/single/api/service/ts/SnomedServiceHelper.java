package org.ihtsdo.snowowl.authoring.single.api.service.ts;


import com.b2international.snowowl.core.domain.IComponentRef;
import com.b2international.snowowl.datastore.server.domain.ComponentRef;
import com.b2international.snowowl.datastore.server.domain.StorageRef;

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
