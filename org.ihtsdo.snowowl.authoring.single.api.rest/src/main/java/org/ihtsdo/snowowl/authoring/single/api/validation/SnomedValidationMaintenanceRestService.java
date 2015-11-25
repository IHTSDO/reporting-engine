/*
 * Copyright 2011-2015 B2i Healthcare Pte Ltd, http://b2i.sg
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.ihtsdo.snowowl.authoring.single.api.validation;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Api("Validation Maintenance")
@Controller
@RequestMapping(
		value="/validation-maintenance",
		produces={ MediaType.APPLICATION_JSON_VALUE })
public class SnomedValidationMaintenanceRestService {

	@Autowired
	private SnomedBrowserValidationService validationService;
	
	@ApiOperation(
			value="Reload validation rules",
			notes="Reloads snomed-drools validation rules across all branches.")
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK", response = Void.class)
	})
	@RequestMapping(value="/reload-validation-rules", method=RequestMethod.POST)
	public @ResponseBody ReloadRulesResponse reloadValidationRules() {
		int rulesLoaded = validationService.reloadRules();
		return new ReloadRulesResponse(rulesLoaded);
	}

	public static final class ReloadRulesResponse {

		private int rulesLoaded;

		public ReloadRulesResponse(int rulesLoaded) {
			super();
			this.rulesLoaded = rulesLoaded;
		}

		public int getRulesLoaded() {
			return rulesLoaded;
		}
	}

}
