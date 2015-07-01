package org.ihtsdo.snowowl.authoring.single.api.rest;

import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

@Api("Version")
@RestController
@RequestMapping(produces = { AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE })
public class VersionController extends AbstractSnomedRestService {

	public static final String VERSION_FILE_PATH = "/opt/sca/version.txt";

	private String versionString;

	@RequestMapping(value = "/version", method = RequestMethod.GET)
	@ApiOperation( value = "Returns version of current deployment",
		notes = "Returns the software-build version as captured during installation (deployment using ansible)" )
	@ResponseBody
	public Map<String, String> getVersion(HttpServletRequest request) throws IOException {
		Map<String, String> versionMap = new HashMap<>();
		versionMap.put("package_version", getVersionString());
		return versionMap;
	}

	private String getVersionString() throws IOException {
		if (this.versionString == null) {
			String versionString = "";
			File file = new File(VERSION_FILE_PATH);
			if (file.isFile()) {
				try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
					versionString = bufferedReader.readLine();
				}
			} else {
				versionString = "Version information not found.";
			}
			this.versionString = versionString;
		}
		return versionString;
	}

}
