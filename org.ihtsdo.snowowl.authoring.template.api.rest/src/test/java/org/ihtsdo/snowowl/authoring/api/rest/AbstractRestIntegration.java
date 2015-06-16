package org.ihtsdo.snowowl.authoring.single.api.rest;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.Charset;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"/web-test-context.xml"})
@WebAppConfiguration
public class AbstractRestIntegration {

	public static final MediaType APPLICATION_SO_V1_JSON_UTF8 =
			new MediaType("application", "vnd.com.b2international.snowowl-v1+json", Charset.forName("utf8"));

	protected MockMvc mockMvc;

	@Autowired
	protected WebApplicationContext webApplicationContext;

	@Before
	public void setup() throws Exception {
//		webApplicationContext.getServletContext().
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
	}

}
