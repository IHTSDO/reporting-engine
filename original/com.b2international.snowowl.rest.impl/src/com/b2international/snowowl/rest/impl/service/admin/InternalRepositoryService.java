/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.service.admin;

import com.b2international.snowowl.rest.service.admin.IRepositoryService;

/**
 * @author apeteri
 */
public interface InternalRepositoryService extends IRepositoryService {

	/**
	 * @param repositoryUuid
	 * @param repositoryVersionId
	 */
	void checkValidRepositoryAndVersionId(final String repositoryUuid, final String repositoryVersionId);
}
