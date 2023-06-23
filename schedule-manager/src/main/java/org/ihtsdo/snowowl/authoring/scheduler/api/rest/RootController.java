package org.ihtsdo.snowowl.authoring.scheduler.api.rest;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@ApiIgnore
public class RootController {

	@RequestMapping(path = "/", method = RequestMethod.GET)
	public void getRoot(HttpServletResponse response) throws IOException {
		response.sendRedirect("swagger-ui/");
	}

	@RequestMapping(path = "/swagger-ui.html", method = RequestMethod.GET)
	public void getRootOldSwaggerUrl(HttpServletResponse response) throws IOException {
		response.sendRedirect("swagger-ui/");
	}

}
