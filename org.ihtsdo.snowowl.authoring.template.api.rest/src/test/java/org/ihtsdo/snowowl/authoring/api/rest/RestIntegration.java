package org.ihtsdo.snowowl.authoring.single.api.rest;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// TODO: Get this working
public class RestIntegration extends AbstractRestIntegration {

	@Autowired
	private AuthoringController authoringController;

	@Test
	public void testFlow() throws Exception {
		// Check no logical models there
		MvcResult mvcResult = mockMvc.perform(get("/models/logicals").accept(APPLICATION_SO_V1_JSON_UTF8))
				.andExpect(status().isOk())
				.andReturn();

		String contentAsString = mvcResult.getResponse().getContentAsString();
		assertEquals("[]", contentAsString);

		// The authoring controller is not getting mapped

	}

}
