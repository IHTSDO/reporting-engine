package org.ihtsdo.termserver.scripting.elasticsearch;

import java.util.Map;

import org.ihtsdo.otf.rest.client.ExpressiveErrorHandler;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticSearchClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchClient.class);

	private String serverUrl;
	private RestTemplate restTemplate;
	
	private final HttpHeaders headers;
	
	private static final String ALL_DOCUMENTS = "{\"size\" : 100,\"query\":{\"bool\":{\"must\":[{\"term\":{\"conceptId\":{\"value\":\"#CONCEPT_ID#\"}}},{\"term\":{\"path\":{\"value\":\"#PATH#\"}}}]}}}";
	
	public ElasticSearchClient(String serverUrl) {
		this.serverUrl = serverUrl;
		restTemplate = new RestTemplateBuilder()
				.rootUri(this.serverUrl)
				.errorHandler(new ExpressiveErrorHandler())
				.build();
		headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
	}
	
	@SuppressWarnings("unchecked")
	public Map<String,Map<?,?>> getAllDocumentsRelatedToConcept(String sctId, String path) {
		String payload = ALL_DOCUMENTS.replace("#CONCEPT_ID#", sctId)
					.replace("#PATH#", path);
		String url = "/prod_*/_search";
		HttpEntity<String> request = new HttpEntity<String>(payload, headers);
		return restTemplate.postForObject(url, request, Map.class);
	}

}
