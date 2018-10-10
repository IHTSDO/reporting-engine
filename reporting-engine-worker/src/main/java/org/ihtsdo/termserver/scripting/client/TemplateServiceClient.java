package org.ihtsdo.termserver.scripting.client;

import java.io.IOException;
import java.util.List;

import org.snomed.authoringtemplate.domain.*;
import org.snomed.authoringtemplate.domain.logical.*;
import org.snomed.authoringtemplate.service.LogicalTemplateParserService;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;


/**
 * The intention here is to call the template service on the server we're currently 
 * working with to obtain a template, but for now we'll pick it up as a resource
 * @author Peter
 *
 */
public class TemplateServiceClient {
	
	private final HttpHeaders headers;
	private final RestTemplate restTemplate;
	LogicalTemplateParserService service  = new LogicalTemplateParserService();
	ObjectMapper mapper = new ObjectMapper();
	private static final String CONTENT_TYPE = "application/json";
	
	public LogicalTemplate loadLogicalTemplate (String templateName) throws JsonParseException, JsonMappingException, IOException {
		ClassLoader classLoader = getClass().getClassLoader();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		ConceptTemplate cTemplate = mapper.readValue(classLoader.getResourceAsStream(templateName), ConceptTemplate.class );
		LogicalTemplate lTemplate = parseLogicalTemplate(cTemplate);
		return lTemplate;
	}
	
	public LogicalTemplate parseLogicalTemplate (ConceptTemplate template) throws JsonParseException, JsonMappingException, IOException {
		return service.parseTemplate(template.getLogicalTemplate());
	}
	
	public TemplateServiceClient(String serverUrl, String cookie) {
		headers = new HttpHeaders();
		headers.add("Cookie", cookie);
		headers.add("Accept", CONTENT_TYPE);
		restTemplate = new RestTemplateBuilder()
				.rootUri(serverUrl)
				.additionalMessageConverters(new GsonHttpMessageConverter())
				.errorHandler(new ExpressiveErrorHandler())
				.build();
		
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

}
