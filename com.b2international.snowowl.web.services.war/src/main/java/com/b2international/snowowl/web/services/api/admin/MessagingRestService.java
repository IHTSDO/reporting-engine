/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.api.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.b2international.snowowl.rest.service.admin.IMessagingService;
import com.mangofactory.swagger.annotations.ApiIgnore;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * Spring controller for exposing {@link IMessagingService} functionality.
 * 
 * @author apeteri
 */
@RestController
@RequestMapping(value={"/messages"}, consumes={ MediaType.TEXT_PLAIN_VALUE }, produces={ MediaType.TEXT_PLAIN_VALUE })
@Api("Administration")
@ApiIgnore
public class MessagingRestService extends AbstractAdminRestService {

	@Autowired
	protected IMessagingService delegate;

	@RequestMapping(value="send", method=RequestMethod.POST)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@ApiOperation(
			value="Send message to connected users",
			notes="Sends an informational message to all connected users; the message is displayed "
					+ "in the desktop application immediately.")
	@ApiResponses({
		@ApiResponse(code=204, message="Message sent")
	})
	public void sendMessage(
			@RequestBody
			@ApiParam(value="the message to send")
			final String message) {

		delegate.sendMessage(message);
	}
}
