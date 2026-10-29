package org.ihtsdo.termserver.scripting.client;

import java.util.Collections;
import java.util.Set;

import org.ihtsdo.otf.rest.client.ExpressiveErrorHandler;
import org.snomed.otf.scheduler.domain.JobType;
import org.snomed.otf.scheduler.domain.WhiteListedConcept;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

/**
 * Client for the Reporting Service, eg to recover the concepts whitelisted for a report
 */
public class ReportingServerClient {

	private static final String API_ROOT = "reporting-service";
	private static final String WHITELIST = "/jobs/{typeName}/{jobName}/{codeSystemShortname}/whitelist";

	private final RestTemplate restTemplate;

	/**
	 * @param serverUrl the environment's base url, eg https://authoring.ihtsdotools.org/
	 */
	public ReportingServerClient(String serverUrl, String cookie) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Cookie", cookie);
		headers.add("Accept", MediaType.APPLICATION_JSON_VALUE);

		restTemplate = new RestTemplateBuilder()
				.uriTemplateHandler(ClientUriFactory.forRootUri(serverUrl + API_ROOT))
				.errorHandler(new ExpressiveErrorHandler())
				.build();

		restTemplate.getInterceptors().add((request, body, execution) -> {
			request.getHeaders().addAll(headers);
			return execution.execute(request, body);
		});
	}

	public Set<WhiteListedConcept> getWhiteListForReport(String reportName, String codeSystemShortname) {
		ResponseEntity<Set<WhiteListedConcept>> response = restTemplate.exchange(
				WHITELIST,
				HttpMethod.GET,
				null,
				new ParameterizedTypeReference<>() {},
				JobType.REPORT, reportName, codeSystemShortname);
		Set<WhiteListedConcept> whiteList = response.getBody();
		return whiteList == null ? Collections.emptySet() : whiteList;
	}
}
