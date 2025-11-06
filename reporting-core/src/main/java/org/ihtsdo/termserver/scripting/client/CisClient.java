package org.ihtsdo.termserver.scripting.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.ExpressiveErrorHandler;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.termserver.scripting.cis.CisBulkRegisterRequest;
import org.ihtsdo.termserver.scripting.cis.CisBulkRequest;
import org.ihtsdo.termserver.scripting.cis.CisRecord;
import org.ihtsdo.termserver.scripting.cis.CisResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;

public class CisClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(CisClient.class);

	private final HttpHeaders headers;
	private final RestTemplate restTemplate;
	private String serverUrl;
	ObjectMapper mapper = new ObjectMapper();
	private static final String CONTENT_TYPE = "application/json";
	private final String token;

	protected static Gson gson;
	static {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		//gsonBuilder.excludeFieldsWithoutExposeAnnotation();
		gson = gsonBuilder.create();
	}

	public CisClient(String serverUrl, String token) {
		this.token = token;
		headers = new HttpHeaders();
		headers.add("Accept", CONTENT_TYPE);

		restTemplate = new RestTemplateBuilder()
				.rootUri(serverUrl)
				.additionalMessageConverters(new GsonHttpMessageConverter())
				.errorHandler(new ExpressiveErrorHandler())
				.build();

		this.serverUrl = serverUrl;

		//Add a ClientHttpRequestInterceptor to the RestTemplate
		restTemplate.getInterceptors().add(new ClientHttpRequestInterceptor() {
			@Override
			public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
				request.getHeaders().addAll(headers);
				return execution.execute(request, body);
			}
		});
	}

	public CisResponse publishSctids(CisBulkRequest cisBulkRequest) {
		return	restTemplate.exchange(
				"/api/sct/bulk/publish?token=" + token,
				HttpMethod.PUT,
				new HttpEntity<>(cisBulkRequest, headers),
				CisResponse.class).getBody();
	}

	public CisResponse registerSctids(CisBulkRegisterRequest cisBulkRegisterRequest) {
		return	restTemplate.exchange(
				"/api/sct/bulk/register?token=" + token,
				HttpMethod.POST,
				new HttpEntity<>(cisBulkRegisterRequest, headers),
				CisResponse.class).getBody();
	}

	public List<CisRecord> getBulkJobBlocking(Long id) throws TermServerScriptException {
		while (true) {
			CisResponse response = restTemplate.getForEntity(
					"/api/bulk/jobs/" + id + "?token=" + token,
					CisResponse.class).getBody();
			if (!response.getStatus().equals("0")) {
				if (response.getStatus().equals("3")) {
					throw new TermServerScriptException("Bulk job " + id + " failed: " + response.getLog());
				}

				ParameterizedTypeReference<List<CisRecord>> type = new ParameterizedTypeReference<>(){};
				String url = "/api/bulk/jobs/" + id + "/records?token=" + token;
				int retry = 0;
				while (retry++ < 5) {
					try {
						return restTemplate.exchange(url, HttpMethod.GET, null, type).getBody();
					} catch (Exception e) {
						LOGGER.info("Failed to get bulk job records due to {} retrying after 30s nap...", e.getMessage());
						try {
							Thread.sleep(30000);
						} catch (InterruptedException ex) {
							throw new RuntimeException(ex);
						}
					}
				}
			}
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public List<CisRecord> getSCTIDs(List<String> sctids) throws TermServerScriptException {
		ParameterizedTypeReference<List<CisRecord>> type = new ParameterizedTypeReference<>(){};
		String url = "/api/sct/bulk/ids?sctids=" + String.join(",", sctids) + "&token=" + token;
		return restTemplate.exchange(url, HttpMethod.GET, null, type).getBody();
	}
}