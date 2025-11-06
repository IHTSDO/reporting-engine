package org.ihtsdo.termserver.scripting.client;

import java.io.IOException;

import org.ihtsdo.otf.rest.client.ExpressiveErrorHandler;
import org.ihtsdo.termserver.scripting.domain.WhitelistItem;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

/**
 * Client can either load a template from the template service, or from a local resource
 */
public class AuthoringAcceptanceGatewayClient {
	private static final String AAG = "/authoring-acceptance-gateway/";
	
	private final HttpHeaders headers;
	private final RestTemplate restTemplate;
	private static final String CONTENT_TYPE = "application/json";
	
	public WhitelistItem createWhitelistItem (WhitelistItem item) throws IOException, TermServerScriptException {
		return restTemplate.postForObject(AAG + "whitelist-items/", new HttpEntity<>(item, headers), WhitelistItem.class);
	}
	
	public AuthoringAcceptanceGatewayClient(String serverUrl, String cookie) {
		headers = new HttpHeaders();
		headers.add("Cookie", cookie);
		headers.add("Accept", CONTENT_TYPE);
		
		if (serverUrl != null) {
			//Have we been passed a full url?
			int cutPoint = serverUrl.indexOf(AAG);
			if (cutPoint != -1) {
				serverUrl = serverUrl.substring(0,cutPoint);
			}
		}
		
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

}
