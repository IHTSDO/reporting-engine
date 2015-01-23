/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.web.services.api.snomed;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.b2international.snowowl.rest.domain.IComponentRef;
import com.b2international.snowowl.rest.domain.history.IHistoryInfo;
import com.b2international.snowowl.rest.snomed.service.ISnomedReferenceSetHistoryService;
import com.b2international.snowowl.web.services.api.AbstractRestService;
import com.b2international.snowowl.web.services.domain.snomed.SnomedReferenceSetHistory;
import com.mangofactory.swagger.annotations.ApiIgnore;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

/**
 * @author akitta
 * @author bbanfai - ported to Spring
 * @author apeteri
 * @since 1.0
 */
@ApiIgnore
@Api("SNOMED CT History")
@RestController
@RequestMapping(
		value="/snomed-ct/{version}", 
		produces={ AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE })
public class SnomedReferenceSetHistoryRestService extends AbstractSnomedRestService {

	@Autowired
	protected ISnomedReferenceSetHistoryService delegate;

	@RequestMapping(value="/reference-sets/{refSetId}/history", method=RequestMethod.GET)
	@ApiOperation(
			value="Get history for a reference set", 
			notes="Retrieves history for the specified SNOMED CT reference set.")
	public SnomedReferenceSetHistory getHistory(
			@PathVariable(value="version") final String version,
			@PathVariable(value="refSetId") final String refSetId) {

		return getHistoryOnTask(version, null, refSetId);
	}

	@RequestMapping(value="/tasks/{taskId}/reference-sets/{refSetId}/history", method=RequestMethod.GET)
	@ApiOperation(
			value="Get history for a reference set on task", 
			notes="Retrieves history for the specified SNOMED CT reference set.")
	public SnomedReferenceSetHistory getHistoryOnTask(
			@PathVariable(value="version") final String version,
			@PathVariable(value="taskId") final String taskId,
			@PathVariable(value="refSetId") final String refSetId) {

		final IComponentRef refSetRef = createComponentRef(version, taskId, refSetId);
		final List<IHistoryInfo> referenceSetHistory = delegate.getHistory(refSetRef);

		final SnomedReferenceSetHistory result = new SnomedReferenceSetHistory();
		result.setReferenceSetHistory(referenceSetHistory);
		return result;
	}
}
