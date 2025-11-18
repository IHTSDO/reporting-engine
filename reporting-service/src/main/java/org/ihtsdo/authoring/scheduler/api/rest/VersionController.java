package org.ihtsdo.authoring.scheduler.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "Version")
@RestController
@RequestMapping(produces = {MediaType.APPLICATION_JSON_VALUE })
public class VersionController {

	@Autowired(required = false)
	private BuildProperties buildProperties;

	@RequestMapping(value = "/version", method = RequestMethod.GET)
	@Operation( summary = "Returns version of current deployment",
		description = "Returns the software-build version from the package manifest." )
	@ResponseBody
	public Map<String, String> getVersion() {
		Map<String, String> versionMap = new HashMap<>();
		String version;
		if (buildProperties != null) {
			version = buildProperties.getVersion();
		} else {
			version = "Build with maven to get package version.";
		}
		versionMap.put("package_version", version);
		return versionMap;
	}

}
