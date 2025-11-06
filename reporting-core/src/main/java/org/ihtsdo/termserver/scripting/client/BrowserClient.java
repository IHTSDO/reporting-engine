package org.ihtsdo.termserver.scripting.client;

import java.util.*;

import org.ihtsdo.otf.rest.client.ExpressiveErrorHandler;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class BrowserClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(BrowserClient.class);
	
	protected static Gson gson;
	static {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		gsonBuilder.excludeFieldsWithoutExposeAnnotation();
		gson = gsonBuilder.create();
	}
	
	private final RestTemplate restTemplate;
	private final String serverUrl;

	public BrowserClient() {
		this("https://dailybuild.ihtsdotools.org/api/snomed/en-edition/v20200131/descriptions");
	}

	public BrowserClient(String serverUrl) {
		this.serverUrl = serverUrl;
		restTemplate = new RestTemplateBuilder()
				.rootUri(this.serverUrl)
				.additionalMessageConverters(new GsonHttpMessageConverter())
				.errorHandler(new ExpressiveErrorHandler())
				.build();
	}
	
	public List<Concept> findConcepts(String searchTerms, String semTagFilter, int limit) throws TermServerScriptException {
		try {
			int attempts = 0;
			boolean success = false;
			while (attempts < 3 && !success) {
				try {
					String url = serverUrl + "?query=" + searchTerms + 
							"&limit=" + limit +
							"&&searchMode=partialMatching&lang=english&statusFilter=activeOnly&skipTo=0" +
							"&returnLimit=100" +
							(semTagFilter==null ? "" : ("semanticFilter=" + semTagFilter)) +
							"normalize=true&groupByConcept=1";
					LOGGER.debug("Browser search: {}", url);
					BrowserMatch matches = restTemplate.getForObject(url, BrowserMatch.class);
					return matches.get();
				} catch (Exception e) {
					attempts++;
					if (attempts == 3) {
						throw (e);
					}
					LOGGER.error("Lexical search attempt {} due to ", attempts, e);
					Thread.sleep(5 * 100L);
				}
			}
		} catch (Exception e) {
			Thread.currentThread().interrupt();
			throw new TermServerScriptException("Failed to recover search for " + searchTerms ,e);
		}
		throw new IllegalStateException("Can't reach here");
	}
	
	public class BrowserMatch {
		List<Concept> matches;
		
		public void set(List<Concept> matches) {
			this.matches = matches;
		}
		
		public List<Concept> get() {
			return matches;
		}
	}

}
