/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.service.admin;

import static com.google.common.base.Preconditions.checkNotNull;

import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.datastore.server.ICDORepositoryManager;
import com.b2international.snowowl.rest.service.admin.IMessagingService;

/**
 * @author apeteri
 */
public class MessagingServiceImpl implements IMessagingService {

	private static ICDORepositoryManager getRepositoryManager() {
		return ApplicationContext.getServiceForClass(ICDORepositoryManager.class);
	}

	@Override
	public void sendMessage(final String message) {
		checkNotNull(message, "Message to send may not be null.");

		final ICDORepositoryManager repositoryManager = getRepositoryManager();
		repositoryManager.sendMessageToAll(message);
	}
}
