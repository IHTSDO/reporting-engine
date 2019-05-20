package org.ihtsdo.termserver.scripting.client;

import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class BrowserClient {
	
	protected static Gson gson;
	static {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		gsonBuilder.excludeFieldsWithoutExposeAnnotation();
		gson = gsonBuilder.create();
	}
	
	private final RestTemplate restTemplate;
	private final String serverUrl;
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	public BrowserClient() {
		//String browserURL = "https://dailybuild.ihtsdotools.org/api/snomed/en-edition/v20200131/descriptions?query=pain%20ear&limit=50&searchMode=partialMatching&lang=english&statusFilter=activeOnly&skipTo=0&returnLimit=100&semanticFilter=finding&normalize=true&groupByConcept=1";
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
			String url = serverUrl + "?query=" + searchTerms + 
					"&limit=" + limit +
					"&&searchMode=partialMatching&lang=english&statusFilter=activeOnly&skipTo=0" +
					"&returnLimit=100" +
					(semTagFilter==null ? "" : ("semanticFilter=" + semTagFilter)) +
					"normalize=true&groupByConcept=1";
			logger.debug("Browser search: " + url);
			BrowserMatche matches = restTemplate.getForObject(url, BrowserMatche.class);
			return matches.get();
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to recover search for " + searchTerms ,e);
		}
	}
	
	public class BrowserMatche {
		List<Concept> matches;
		
		public void set(List<Concept> matches) {
			this.matches = matches;
		}
		
		public List<Concept> get() {
			return matches;
		}
	}

}
