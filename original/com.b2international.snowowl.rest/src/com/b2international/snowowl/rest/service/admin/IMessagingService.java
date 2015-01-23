/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.service.admin;

/**
 * An interface definition for the Messaging Service.
 * <p>
 * The following operations are supported:
 * <ul>
 * <li>{@link #sendMessage(String) <em>Send message to connected users</em>}
 * </ul>
 * 
 * @author Andras Peteri
 */
public interface IMessagingService {

	/**
	 * Sends an informational message to all connected users; the message is displayed in the desktop application
	 * immediately.
	 * 
	 * @param message the message to send (may not be {@code null})
	 */
	void sendMessage(String message);
}
