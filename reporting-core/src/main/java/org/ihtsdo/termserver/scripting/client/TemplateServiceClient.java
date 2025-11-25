package org.ihtsdo.termserver.scripting.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.ihtsdo.otf.rest.client.ExpressiveErrorHandler;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.snomed.authoringtemplate.domain.*;
import org.snomed.authoringtemplate.domain.logical.*;
import org.snomed.authoringtemplate.service.LogicalTemplateParserService;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.*;

/**
 * Client can either load a template from the template service, or from a local resource
 */
public class TemplateServiceClient {

	private static final String TEMPLATES = "/templates?templateName=";
	
	private final HttpHeaders headers;
	private final RestTemplate restTemplate;
	private String serverUrl;
	LogicalTemplateParserService service  = new LogicalTemplateParserService();
	ObjectMapper mapper = new ObjectMapper();
	private static final String CONTENT_TYPE = "application/json";
	
	public ConceptTemplate loadLocalConceptTemplate (String templateName) throws TermServerScriptException {
		if (!templateName.startsWith("/")) {
			templateName = "/" + templateName;
		}
		InputStream templateStream = TemplateServiceClient.class.getResourceAsStream(templateName);
		if (templateStream == null) {
			throw new RuntimeException ("Failed to load template file - not found: " + templateName);
		}
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return loadLocalConceptTemplate(templateName, templateStream);
	}
	
	public ConceptTemplate loadLocalConceptTemplate (String templateName, InputStream templateStream) throws TermServerScriptException {
		try {
			return mapper.readValue(templateStream, ConceptTemplate.class);
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to load template: '" + templateName + "'", e);
		}
	}
	
	public ConceptTemplate loadLogicalTemplate (String templateName) {
		ResponseEntity<List<ConceptTemplate>> response = restTemplate.exchange(
				TEMPLATES + templateName,
				HttpMethod.GET,
				null,
				new ParameterizedTypeReference<List<ConceptTemplate>>(){});
		List<ConceptTemplate> templates = response.getBody();
		return (templates == null || templates.isEmpty()) ? null : templates.get(0);
	}
	
	public LogicalTemplate parseLogicalTemplate (String logicalTemplateStr) throws IOException {
		return service.parseTemplate(logicalTemplateStr);
	}
	
	public TemplateServiceClient(String serverUrl, String cookie) {
		headers = new HttpHeaders();
		headers.add("Cookie", cookie);
		headers.add("Accept", CONTENT_TYPE);
		
		if (serverUrl != null) {
			//Have we been passed a full url?
			int cutPoint = serverUrl.indexOf(TEMPLATES);
			if (cutPoint != -1) {
				serverUrl = serverUrl.substring(0,cutPoint);
			}
		}

		restTemplate = new RestTemplateBuilder()
				.rootUri(serverUrl)
				.additionalMessageConverters(new GsonHttpMessageConverter())
				.errorHandler(new ExpressiveErrorHandler())
				.build();
		
		this.serverUrl = serverUrl;
		
		//Add a ClientHttpRequestInterceptor to the RestTemplate
		restTemplate.getInterceptors().add(new ClientHttpRequestInterceptor(){
			@Override
			public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
				request.getHeaders().addAll(headers);
				return execution.execute(request, body);
			}
		}); 
	}
	
	public List<ConceptTemplate> getAllTemplates() {
		ResponseEntity<List<ConceptTemplate>> response = restTemplate.exchange(
				"/templates",
				HttpMethod.GET,
				null,
				new ParameterizedTypeReference<List<ConceptTemplate>>(){});
		return response.getBody();
	}
	
	public String getServerUrl() {
		return serverUrl;
	}

}
