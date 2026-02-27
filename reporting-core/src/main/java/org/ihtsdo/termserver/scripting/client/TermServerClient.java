package org.ihtsdo.termserver.scripting.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.*;

import org.apache.hc.core5.util.Timeout;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.config.RequestConfig;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.rest.client.ExpressiveErrorHandler;
import org.ihtsdo.otf.rest.client.Status;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.RF2Constants.CharacteristicType;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.domain.Branch;
import org.ihtsdo.termserver.scripting.domain.CodeSystem;
import org.ihtsdo.termserver.scripting.domain.CodeSystemVersion;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.*;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class TermServerClient {

	private static final int MAX_LOCK_RETRIES = 3;
	private static final long LOCK_RETRY_SLEEP_MS = 20_000L;

	private static final String BRANCHES = "/branches/";
	private static final String BROWSER = "/browser/";
	private static final String MEMBERS = "/members/";
	private static final String MEMBERS_REFSET = "/members?referenceSet=";
	private static final String MERGE_REVIEWS = "/merge-reviews/";
	private static final String CONCEPTS = "/concepts";
	private static final String RELATIONSHIPS = "/relationships";
	private static final String DESCRIPTIONS = "/descriptions";
	private static final String SEARCH_AFTER = "&searchAfter=";
	private static final String URL_SEPARATOR = "/";

    public enum ExtractType {
		DELTA, SNAPSHOT, FULL
	}
	
	public enum ImportType {
		DELTA, SNAPSHOT, FULL
	}

	public enum ExportType {
		PUBLISHED, UNPUBLISHED, MIXED
	}
	
	public static final int MAX_TRIES = 3;
	public static final int RETRY = 10;

	protected static Gson gson;
	static {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		gsonBuilder.registerTypeAdapter(Relationship.class, new RelationshipSerializer());
		gsonBuilder.excludeFieldsWithoutExposeAnnotation();
		gson = gsonBuilder.create();
	}
	
	private final RestTemplate restTemplate;
	private final HttpHeaders headers;
	private final String serverUrl;
	private static final String JSON_CONTENT_TYPE = "application/json";
	private static final Logger LOGGER = LoggerFactory.getLogger(TermServerClient.class);

	public TermServerClient(String serverUrl, String cookie) {
		this.serverUrl = serverUrl;

		headers = new HttpHeaders();
		headers.add("Cookie", cookie);
		headers.add("Accept", JSON_CONTENT_TYPE);

		RequestConfig requestConfig = RequestConfig.custom()
				.setConnectionRequestTimeout(Timeout.ofSeconds(10))
				.setResponseTimeout(Timeout.ofMinutes(15))
				.build();

		CloseableHttpClient httpClient = HttpClients.custom()
				.setDefaultRequestConfig(requestConfig)
				.build();

		HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

		restTemplate = new RestTemplateBuilder()
				.rootUri(this.serverUrl)
				.additionalMessageConverters(new GsonHttpMessageConverter(gson))
				.additionalMessageConverters(new FormHttpMessageConverter())
				.errorHandler(new ExpressiveErrorHandler())
				.requestFactory(() -> factory)
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
	
	public Branch getBranch(String branchPath) throws TermServerScriptException {
		int retry = 0;
		HttpStatusCode lastHTTPStatus = null;
		while (retry < 3) {
			try {
				String url = getBranchesPath(branchPath);
				LOGGER.debug("Recovering branch information from {}", url);
				return restTemplate.getForObject(url, Branch.class);
			} catch (RestClientResponseException e) {
				lastHTTPStatus = e.getStatusCode();
				//A 4xx error is not going to be helped by retrying.
				if (lastHTTPStatus.is4xxClientError()) {
					retry = 5;
				} else {
					retry++;
					LOGGER.warn("{} problem recovering branch information.   Retrying after a nap...", lastHTTPStatus);
					try {
						Thread.sleep(1000L * 5);
					} catch (InterruptedException e2) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException("Thread interrupted", e2);
					}
				}
			}
		}
		String issue = "reason unknown";
		if (lastHTTPStatus != null) {
			if (lastHTTPStatus.equals(HttpStatus.FORBIDDEN)) {
				issue = "user authentication failure";
			} else if (lastHTTPStatus.equals(HttpStatus.NOT_FOUND)) {
				issue = "branch not found";
			}
		}
		String failureMsg = String.format("Could not recover branch information from %s due to %s. Server returned %s.", branchPath, issue, lastHTTPStatus);
		throw new TermServerScriptException(failureMsg);
	}
	
	public List<CodeSystemVersion> getCodeSystemVersions(String codeSystem) throws TermServerScriptException {
		try {
			String url = this.serverUrl + "/codesystems/" + codeSystem + "/versions?showFutureVersions=true";
			LOGGER.debug("Recovering codesystem versions from {}", url);
			return restTemplate.getForObject(url, CodeSystemVersionCollection.class).getItems();
		} catch (RestClientException e) {
			throw new TermServerScriptException(translateRestClientException(e));
		}
	}

	private Throwable translateRestClientException(RestClientException e) {
		//Can we find some known error in the text returned so that we don't have
		//to return the whole thing?
		String message = e.getMessage();
		String mainContent = StringUtils.substringBetween(message, "<section class=\"mainContent contain\">", "</section>");
		if (mainContent != null) {
			return new TermServerScriptException("Returned from TS: " + mainContent);
		}
		return e;
	}

	public Concept createConcept(Concept c, String branchPath) throws TermServerScriptException {
		try {
			Concept newConcept = restTemplate.postForObject(getConceptBrowserPath(branchPath), new HttpEntity<>(c, headers), Concept.class);
			LOGGER.info("Created concept: {} with {} descriptions.", newConcept, c.getDescriptions().size());
			return newConcept;
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}
	public Concept updateConcept(Concept c, String branchPath) throws TermServerScriptException {
			Preconditions.checkNotNull(c.getConceptId());
			String url = getConceptBrowserPath(branchPath) + URL_SEPARATOR + c.getConceptId();

			ConceptUpdateAction action = new ConceptUpdateAction(c);
			while (!action.updatedOK) {
				attemptUpdateConcept(action, url);
			}

			return action.concept;
	}

	/**
	 * Holds the concept, the success flag, and the current attempt count.
	 */
	private static class ConceptUpdateAction {
		Concept concept;
		boolean updatedOK;
		int tries;

		ConceptUpdateAction(Concept concept) {
			this.concept = concept;
			this.updatedOK = false;
			this.tries = 0;
		}
	}

	private void attemptUpdateConcept(ConceptUpdateAction action, String url) throws TermServerScriptException {
		action.tries++;

		try {
			ResponseEntity<Concept> response = restTemplate.exchange(
					url,
					HttpMethod.PUT,
					new HttpEntity<>(action.concept, headers),
					Concept.class
			);
			Concept updatedConcept = response.getBody();

			if (updatedConcept == null) {
				throw new TermServerScriptException("Could not update concept due to null concept received from " + url);
			}

			// Replace the concept reference in the action object
			action.concept = updatedConcept;
			action.updatedOK = true;

			LOGGER.info("Updated concept {}", updatedConcept.getConceptId());
		} catch (RestClientResponseException e) {
			if (action.tries >= MAX_TRIES || e.getStatusCode().value() == 400) {
				throw new TermServerScriptException(
						"Failed to update concept: " + action.concept + " after " + action.tries + " attempts due to " + e.getMessage(), e
				);
			}

			LOGGER.debug("Update of concept failed, trying again....", e);
			try {
				Thread.sleep(30 * 1000L); // Give the server 30 seconds to recover
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				throw new TermServerScriptException("Interrupted while waiting to retry", ie);
			}
			// updatedOK remains false; the loop will retry
		}
	}


	public Concept getConcept(String sctid, String branchPath) {
		String url = getConceptBrowserPath(branchPath) + URL_SEPARATOR + sctid;
		Concept concept = restTemplate.getForObject(url, Concept.class);
		//RestTemplate is not calling the setter so this is cheaper than writing a custom deserializer
		if (concept != null) {
			concept.setId(concept.getConceptId());
		}
		return concept;
	}

	public Description getDescription(String descriptionId, String branchPath) {
		String url = getDescriptionsPath(descriptionId, branchPath);
		return restTemplate.getForObject(url, Description.class);
	}

	public void deleteConcept(String sctId, String branchPath) throws TermServerScriptException {
		try {
			restTemplate.delete(getConceptsPath(sctId, branchPath));
			LOGGER.info("Deleted concept {} from {}", sctId, branchPath);
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to delete concept: " + sctId, e);
		}
	}

	public void deleteRelationship(String sctId, String branchPath) throws TermServerScriptException {
		try {
			restTemplate.delete(getRelationshipsPath(sctId, branchPath));
			LOGGER.info("Deleted relationship {} from {}", sctId , branchPath);
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to delete relationship: " + sctId, e);
		}
	}

	public void deleteDescription(String sctId, String branchPath) throws TermServerScriptException {
		try {
			restTemplate.delete(getDescriptionsPath(sctId,branchPath));
			LOGGER.info("Deleted description {} from {}", sctId, branchPath);
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to delete description: " + sctId, e);
		}
	}
	
	public ConceptCollection getConcepts(String ecl, String branchPath, String searchAfter, int limit) {
		return getConcepts(ecl, branchPath, CharacteristicType.INFERRED_RELATIONSHIP, searchAfter, limit);
	}
	
	public ConceptCollection getConcepts(String ecl, String branchPath, CharacteristicType charType, String searchAfter, int limit) {
		String eclType = charType.equals(CharacteristicType.INFERRED_RELATIONSHIP)?"ecl":"statedEcl";
		String criteria = SnomedUtils.makeMachineReadable(ecl);
		return getConceptsMatchingCriteria(eclType, criteria, branchPath, searchAfter, limit);
	}
	
	public ConceptCollection getConceptsMatchingCriteria(String eclType, String criteria, String branchPath, String searchAfter, int limit) {
		//RestTemplate will attempt to expand out any curly braces, and we can't URLEncode
		//because RestTemplate does that for us.  So use curly braces to substitute in our criteria
		String url = getConceptsPath(branchPath) + "?active=true&limit=" + limit;
		if (!StringUtils.isEmpty(searchAfter)) {
			url += SEARCH_AFTER + searchAfter;
		}

		url += "&" + eclType + "={criteria}";
		LOGGER.info("Calling: {} with criteria = '{}'", url, criteria);
		return restTemplate.getForObject(url, ConceptCollection.class, criteria);
	}
	
	public int getConceptsCount(String ecl, String branchPath) {
		String url = getConceptsPath(branchPath) + "?active=true&limit=1";
		ecl = SnomedUtils.makeMachineReadable(ecl);
		//RestTemplate will attempt to expand out any curly braces, and we can't URLEncode
		//because RestTemplate does that for us.  So use curly braces to substitute in our ecl
		url += "&ecl={ecl}";
		LOGGER.info("Calling {} with ecl parameter: {}", url, ecl);
		return restTemplate.getForObject(url, ConceptCollection.class, ecl).getTotal();
	}

	private String getBranchesPath(String branchPath) {
		return  serverUrl + BRANCHES + branchPath;
	}
	
	private String getConceptsPath(String sctId, String branchPath) {
		return serverUrl + URL_SEPARATOR + branchPath + CONCEPTS +URL_SEPARATOR + sctId;
	}

	private String getRelationshipsPath(String sctId, String branchPath) {
		return serverUrl + URL_SEPARATOR + branchPath + RELATIONSHIPS +URL_SEPARATOR + sctId;
	}

	private String getConceptsPath(String branchPath) {
		return serverUrl + URL_SEPARATOR + branchPath + CONCEPTS;
	}

	private String getConceptBrowserPath(String branchPath) {
		return serverUrl + BROWSER + branchPath + CONCEPTS;
	}
	
	private String getConceptBrowserValidationPath(String branchPath) {
		return serverUrl + BROWSER + branchPath + "/validate/concept";
	}
	
	private String getDescriptionsPath(String id, String branchPath) {
		return serverUrl + URL_SEPARATOR + branchPath + DESCRIPTIONS + URL_SEPARATOR + id;
	}

	public Branch createBranch(TermServerLocation location) throws TermServerScriptException {
		try {
			String parent = StringUtils.substringBeforeLast(location.getBranchPath(), "/");
			String branchName = StringUtils.substringAfterLast(location.getBranchPath(), "/");

			if (StringUtils.isEmpty(parent) || StringUtils.isEmpty(branchName)) {
				throw new TermServerScriptException("Invalid branch path: " + location.getBranchPath() + ". Must have at least one '/' and a leaf node.");
			}
			Map<String, Object> body = new HashMap<>();
			body.put("parent", parent);
			body.put("name", branchName);

			HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
			Branch branch = restTemplate.postForObject(serverUrl + "/branches", request, Branch.class);
			LOGGER.info("Created branch {}", branch);
			return branch;
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	public String getServerUrl() {
		return serverUrl;
	}

	public File export(String branchPath,
	                   String effectiveDate,
	                   ExportType exportType,
	                   ExtractType extractType,
	                   File saveLocation,
	                   boolean unpromotedChangesOnly)
			throws TermServerScriptException {

		Map<String, Object> exportRequest =
				prepareExportRequest(branchPath, effectiveDate, exportType, extractType, unpromotedChangesOnly);

		if (saveLocation == null) {
			try {
				saveLocation = File.createTempFile("ts-extract", ".zip");
			} catch (IOException e) {
				throw new TermServerScriptException(e);
			}
		}

		int attempt = 0;
		while (true) {
			attempt++;
			try {
				LOGGER.info("Initiating export attempt {}/{} with {}", attempt, MAX_LOCK_RETRIES, exportRequest);
				String exportLocationURL = initiateExport(exportRequest);

				// INFRA-1489 Workaround
				if (!exportLocationURL.startsWith("https://")) {
					exportLocationURL = exportLocationURL.replace("http:/", "https://");
					LOGGER.warn("Malformed export location received, corrected to https://");
				}

				return recoverExportedArchive(exportLocationURL, saveLocation);
			} catch (RestClientException e) {
				if (isLockedBranchError(e) && attempt < MAX_LOCK_RETRIES) {
					LOGGER.warn("Branch locked during export attempt {}/{}. " +
									"Sleeping 20 seconds before restarting export from start...",
							attempt, MAX_LOCK_RETRIES);

					sleepBeforeRetry();
					continue;
				}
				throw new TermServerScriptException("Unable to complete export after " + attempt + " attempt(s)", e);
			}
		}
	}

	private Map<String, Object> prepareExportRequest(String branchPath,
	                                                 String effectiveDate,
	                                                 ExportType exportType,
	                                                 ExtractType extractType,
	                                                 boolean unpromotedChangesOnly)
			throws TermServerScriptException {

		Map<String, Object> exportRequest = new HashMap<>();
		exportRequest.put("type", extractType);
		exportRequest.put("branchPath", branchPath);
		exportRequest.put("unpromotedChangesOnly", unpromotedChangesOnly);

		switch (exportType) {
			case MIXED:
				if (!extractType.equals(ExtractType.SNAPSHOT)) {
					throw new TermServerScriptException("Export type " + exportType + " not recognised");
				}
				exportRequest.put("includeUnpublished", true);
				break;

			case UNPUBLISHED:
				if (effectiveDate != null) {
					exportRequest.put("transientEffectiveTime", effectiveDate);
				}
				break;

			case PUBLISHED:
				if (effectiveDate == null) {
					throw new TermServerScriptException("Cannot export published data without an effective date");
				}
				exportRequest.put("deltaStartEffectiveTime", effectiveDate);
				exportRequest.put("deltaEndEffectiveTime", effectiveDate);
				exportRequest.put("transientEffectiveTime", effectiveDate);
				break;

			default:
				throw new TermServerScriptException("Export type " + exportType + " not recognised");
		}

		return exportRequest;
	}

	private String initiateExport(Map<String, Object> exportRequest)
			throws TermServerScriptException {
		try {
			HttpEntity<?> entity = new HttpEntity<>(exportRequest, headers);
			HttpEntity<String> response =
					restTemplate.exchange(serverUrl + "/exports", HttpMethod.POST, entity, String.class);

			URI location = response.getHeaders().getLocation();
			return Optional.ofNullable(location)
					.map(URI::toString)
					.orElseThrow(() -> new TermServerScriptException(
							"Export initiated but no Location header returned"));
		} catch (RestClientException e) {
			throw new TermServerScriptException("Failed to initiate export", e);
		}
	}

	private File recoverExportedArchive(String exportLocationURL,
	                                    final File saveLocation)
			throws RestClientException {

		LOGGER.info("Recovering exported archive from {}", exportLocationURL);
		LOGGER.info("Saving exported archive to {}", saveLocation);
		RequestCallback requestCallback = request ->
				request.getHeaders().setAccept(Arrays.asList(
						MediaType.APPLICATION_OCTET_STREAM,
						MediaType.ALL));

		restTemplate.execute(
				exportLocationURL + "/archive",
				HttpMethod.GET,
				requestCallback,
				clientHttpResponse -> {
					try (FileOutputStream fos = new FileOutputStream(saveLocation)) {
						StreamUtils.copy(clientHttpResponse.getBody(), fos);
					}
					return null;
				});

		if (saveLocation.length() == 0) {
			throw new RestClientException("Archive recovered as 0 bytes");
		}

		LOGGER.debug("Extract recovery complete");
		return saveLocation;
	}

	private boolean isLockedBranchError(RestClientException e) {
		return e.getMessage() != null &&
				e.getMessage().toLowerCase().contains("locked");
	}

	private void sleepBeforeRetry() throws TermServerScriptException {
		try {
			Thread.sleep(LOCK_RETRY_SLEEP_MS);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new TermServerScriptException(
					"Retry interrupted while waiting for locked branch", ie);
		}
	}

	public void importArchive(String branchPath, ImportType importType, File archive)
			throws TermServerScriptException {
		Map<String, Object> importRequest = new HashMap<>();
		importRequest.put("type", importType);
		importRequest.put("branchPath", branchPath);
		LOGGER.info("Creating import job with {}", importRequest);
		String importJobLocation = initiateImport(importRequest);
		importArchive(importJobLocation, archive);
	}

	private void importArchive(String importJobLocation, File archive) throws TermServerScriptException {
		LOGGER.info("Importing {} in import job {}", archive, importJobLocation);
		String url = importJobLocation + "/archive";
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		
		//No HttpMessageConverter for File directly.
		//FormHttpMessageConverter uses ResourceHttpMessageConverter which can handle this
		form.add("file", new FileSystemResource(archive)); 
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(form, headers);
		restTemplate.postForEntity(url, requestEntity, String.class);
		
		//Now wait for the import to complete
		while(true) {
			try {
				Thread.sleep(5 * 1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			Map<String, String> responseJson = restTemplate.getForObject(importJobLocation, Map.class);
			JsonObject jsonObject = gson.toJsonTree(responseJson).getAsJsonObject();
			String responseJsonStr = gson.toJson(jsonObject); // compact

			//Have we either completed or failed?
			String status = responseJson.get("status");
			if (status == null) {
				throw new IllegalArgumentException("Failed to recover meaningful status from import: " + responseJsonStr); 
			} else if (status.equals("COMPLETED")) {
				return;
			} else if (status.equals("FAILED")) {
				throw new TermServerScriptException("Import " + importJobLocation + " failed: " + responseJsonStr);
			} else if (status.equals("RUNNING")) {
				//Repeat loop with sleep
			} else {
				throw new IllegalStateException("Failed to recover meaningful status from import: " + responseJsonStr); 
			}
		}
	}

	private String initiateImport(Map<String, Object> importRequest) throws TermServerScriptException {
		try {
			HttpEntity<?> entity = new HttpEntity<>(importRequest, headers ); // for request
			HttpEntity<String> response = restTemplate.exchange(serverUrl + "/imports", HttpMethod.POST, entity, String.class);
			return response.getHeaders().get("Location").get(0);
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to initiate import", e);
		}
	}

	public void deleteRefsetMember(String refsetMemberId, String branch, boolean toForce) {
			restTemplate.delete(getRefsetMemberUpdateUrl(refsetMemberId, branch, toForce));
			LOGGER.info("Deleted refset member id: {}", refsetMemberId);
	}

	public void deleteRefsetMembers(List<String> refsetMemberIds, String branch, boolean toForce) throws TermServerScriptException {
		Map<String, List<String>> importRequest = new HashMap<>();
		importRequest.put("memberIds", refsetMemberIds);
		HttpEntity<Map<String, List<String>>> requestEntity = new HttpEntity<>(importRequest, headers);
		ResponseEntity<String> response = null;
		String url = getRefsetMembersUrl(branch) + (toForce?"?force=true":"");
		// Make DELETE request
		try {
			response = restTemplate.exchange(
					url,
					HttpMethod.DELETE,
					requestEntity,
					String.class
			);
		} catch (RestClientException e) {
			LOGGER.error("Failed to delete refset members from {}", url, e);
		}
		if (response != null && !response.getStatusCode().is2xxSuccessful()) {
			throw new TermServerScriptException("Failed to delete refset members from : " + url + " : " + response.getBody());
		}
		LOGGER.info("Deleted {} refset members on branch {}", refsetMemberIds.size(), branch);
	}

	private String getRefsetMembersUrl(String refSetMemberId, String branch) {
		return this.serverUrl + URL_SEPARATOR + branch + MEMBERS + refSetMemberId;
	}

	private String getRelationshipUrl(String relationshipId, String branch) {
		return this.serverUrl + URL_SEPARATOR + branch + "/relationships/" + relationshipId;
	}

	private String getRefsetMembersUrl(String branchPath, String referenceSet, String searchAfter) {
		if (searchAfter == null || searchAfter.isEmpty()) {
			return this.serverUrl + URL_SEPARATOR + branchPath + MEMBERS_REFSET + referenceSet;
		} else {
			return this.serverUrl + URL_SEPARATOR + branchPath + MEMBERS_REFSET + referenceSet + SEARCH_AFTER + searchAfter;
		}
	}

	private String getRefsetMembersUrl(String branch) {
		return this.serverUrl + URL_SEPARATOR + branch + "/members";
	}

	private String getSearchMembersUrl(String branch) {
		return this.serverUrl + URL_SEPARATOR + branch + "/members/search";
	}
	
	private String getRefsetMemberUpdateUrl(String refSetMemberId, String branch, boolean toForce) {
		return getRefsetMembersUrl(refSetMemberId, branch) + "?force=" + toForce;
	}

	public void updateRefsetMember(String branchPath, RefsetMember refsetEntry, boolean forceUpdate) throws TermServerScriptException {
		try {
			ResponseEntity<RefsetMember> response = restTemplate.exchange(
					getRefsetMemberUpdateUrl(refsetEntry.getId(), branchPath, forceUpdate),
					HttpMethod.PUT,
					new HttpEntity<>(refsetEntry, headers),
					RefsetMember.class);
			RefsetMember updatedEntry = response.getBody();
			Preconditions.checkNotNull(updatedEntry);
			LOGGER.info("Updated refset member {}", refsetEntry.getId());
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to update refset entry {}" + refsetEntry + " due to " + e.getMessage(), e);
		}
	}

	public void waitForCompletion(String branchPath, Classification classification) {
		String endPoint = this.serverUrl + URL_SEPARATOR + branchPath + "/classifications/" + classification.getId();
		Status status;
		long sleptSecs = 0;
		do {
			String json = restTemplate.getForObject(endPoint, String.class);
			status = gson.fromJson(json, Status.class);
			if (!status.isFinalState()) {
				try {
					Thread.sleep(RETRY * 1000L);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				sleptSecs += RETRY;
				if (sleptSecs % 60 == 0) {
					LOGGER.info("Waited for {} for classification {} on {}",sleptSecs, classification.getId(), branchPath);
				}
			}
		} while (!status.isFinalState());
	}

	public String getMergeReviewUrl(String mergeReviewId) {
		return this.serverUrl + MERGE_REVIEWS + mergeReviewId;
	}

	public MergeReview waitForMergeReviewToComplete(String mergeReviewId) {
		String endPoint = getMergeReviewUrl(mergeReviewId);
		MergeReview mergeReview;
		long sleptSecs = 0;
		do {
			if (sleptSecs == 0) {
				LOGGER.debug("Waiting for  merge review {} from {}", mergeReviewId, endPoint);
			}
			mergeReview = restTemplate.getForObject(endPoint, MergeReview.class);
			if (mergeReview == null) {
				throw new IllegalStateException("Could not recover merge review from " + endPoint + " null returned from server.");
			}
			if (!mergeReview.isFinalState()) {
				try {
					Thread.sleep(RETRY * 1000L);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				sleptSecs += RETRY;
				if (sleptSecs % 60 == 0) {
					LOGGER.info("Waited for {} for mergeReview {} ",sleptSecs, mergeReviewId);
				}
			}
		} while (!mergeReview.isFinalState());
		return mergeReview;
	}

	public void saveMergeReviewAcceptedConcept (String mergeReviewId, Concept concept) {
		String url = getMergeReviewUrl(mergeReviewId) + URL_SEPARATOR + concept.getId();
		LOGGER.debug("Saving accepted concept {} for review {}", concept.getId(), mergeReviewId);
		HttpEntity<Concept> request = new HttpEntity<>(concept, headers);
		restTemplate.exchange(
				url,
				HttpMethod.POST,
				request,
				Void.class
		);
	}

	public void applyMerge (String mergeReviewId) {
		String url = getMergeReviewUrl(mergeReviewId) + "/apply";
		restTemplate.exchange(
				url,
				HttpMethod.POST,
				new HttpEntity<>(headers),
				Void.class
		);
	}

	public DroolsResponse[] validateConcept(Concept c, String branchPath) {
		String url = getConceptBrowserValidationPath(branchPath);
		HttpEntity<Concept> request = new HttpEntity<>(c, headers); 
		return restTemplate.postForObject(url, request, DroolsResponse[].class);
	}

	public Relationship getRelationship(String relationshipId, String branchPath) {
		try {
			String relationshipUrl = getRelationshipUrl(relationshipId, branchPath);
			return restTemplate.getForObject(relationshipUrl, Relationship.class);
		} catch (Exception e) {
			if (e.getMessage().contains("Relationship not found")) {
				return null;
			}

			throw e;
		}
	}

	public RefsetMember getRefsetMember(String uuid, String branchPath) {
		try {
			String url = getRefsetMembersUrl(uuid, branchPath);
			return restTemplate.getForObject(url, RefsetMember.class);
		} catch (Exception e) {
			if (e.getMessage().contains("Member not found")) {
				return null;
			}
			throw e;
		}
	}

	public List<RefsetMember> getMembersByReferenceSet(String branchPath, String referenceSet) {
		return getMembersByReferenceSet(branchPath, referenceSet, null, new ArrayList<>());
	}

	public List<RefsetMember> getMembersByReferenceSet(String branchPath, String referenceSet, String searchAfter, List<RefsetMember> refsetMembers) {
		try {
			String url = getRefsetMembersUrl(branchPath, referenceSet, searchAfter);
			RefsetMemberCollection response = restTemplate.getForObject(url, RefsetMemberCollection.class);

			boolean responseHasHits = response.getItems() != null && !response.getItems().isEmpty();
			if (responseHasHits) {
				refsetMembers.addAll(response.getItems());
			}

			boolean moreHitsAvailable = response.getTotal() > refsetMembers.size();
			if (moreHitsAvailable) {
				// Recursive
				getMembersByReferenceSet(branchPath, referenceSet, response.getSearchAfter(), refsetMembers);
			}

			return refsetMembers;
		} catch (Exception e) {
			return Collections.emptyList();
		}
	}

	public LangRefsetEntry getLangRefsetMember(String uuid, String branchPath) {
		try {
			String url = getRefsetMembersUrl(uuid, branchPath);
			return restTemplate.getForObject(url, LangRefsetEntry.class);
		} catch (Exception e) {
			if (e.getMessage().contains("Member not found")) {
				return null;
			}
			throw e;
		}
	}

	public Collection<RefsetMember> findRefsetMembers(String branchPath, List<Concept> refCompIds, String refsetFilter) throws TermServerScriptException {
		try {
			String url = getRefsetMembersUrl(branchPath);
			url += "?";
			boolean isFirst = true;
			for (Component c : refCompIds) {
				if (!isFirst) {
					url += "&";
				} else {
					isFirst = false;
				}
				url += "referencedComponentId=" + c.getId();
			}
			if (refsetFilter != null) {
				url += "&referenceSet=" + refsetFilter;
			}
			RefsetMemberCollection members = restTemplate.getForObject(url, RefsetMemberCollection.class);
			return members.getItems();
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	@SuppressWarnings("java:S2259")
	public Collection<RefsetMember> searchMembers(String branchPath, List<String> referencedComponentIds, String referenceSet) throws TermServerScriptException {
		try {
			Collection<RefsetMember> members = new ArrayList<>();
			String url = getSearchMembersUrl(branchPath);

			Map<String, Object> body = new HashMap<>();
			body.put("referenceSet", referenceSet);
			body.put("referencedComponentIds", referencedComponentIds);

			HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

			RefsetMemberCollection memberCollection = restTemplate.postForObject(url, request, RefsetMemberCollection.class);
			members.addAll(memberCollection.getItems());

			int totalExpected = memberCollection.getTotal();
			String searchAfter = memberCollection.getSearchAfter();

			while (members.size() < totalExpected) {
				memberCollection = restTemplate.postForObject(url + SEARCH_AFTER + searchAfter, request, RefsetMemberCollection.class);
				members.addAll(memberCollection.getItems());
				searchAfter = memberCollection.getSearchAfter();
			}

			return members;

		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}


	public Collection<RefsetMember> findRefsetMembers(String branchPath, String refsetId, Boolean isNullEffectiveTime) throws TermServerScriptException {
		try {
			Collection<RefsetMember> members = new ArrayList<>(); 
			String url = getRefsetMembersUrl(branchPath) + "?";
			if (refsetId != null) {
				url += "&referenceSet=" + refsetId;
			}
			if (isNullEffectiveTime != null) {
				url += "&isNullEffectiveTime=" + (isNullEffectiveTime?"true":"false");
			}
			RefsetMemberCollection memberCollection = restTemplate.getForObject(url, RefsetMemberCollection.class);
			int totalExpected = memberCollection.getTotal();
			members.addAll(memberCollection.getItems());
			String searchAfter = memberCollection.getSearchAfter();
			while (members.size() < totalExpected) {
				memberCollection = restTemplate.getForObject(url + SEARCH_AFTER + searchAfter, RefsetMemberCollection.class);
				members.addAll(memberCollection.getItems());
				searchAfter = memberCollection.getSearchAfter();
			}
			return members;
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}
	
	public RefsetMember updateRefsetMember(RefsetMember rm, String branchPath) {
		String url = getRefsetMembersUrl(rm.getId(), branchPath);
		ResponseEntity<RefsetMember> response = restTemplate.exchange(
				url,
				HttpMethod.PUT,
				new HttpEntity<>(rm, headers),
				RefsetMember.class);
		return response.getBody();
	}
	
	public List<CodeSystem> getCodeSystems() throws TermServerScriptException {
		try {
			String url = this.serverUrl + "/codesystems";
			LOGGER.debug("Recovering codesystems from {}", url);
			return restTemplate.getForObject(url, CodeSystemCollection.class).getItems();
		} catch (RestClientException e) {
			throw new TermServerScriptException(translateRestClientException(e));
		}
	}

	public CodeSystem getCodeSystem(String codeSystemName) throws TermServerScriptException {
		try {
			String url = this.serverUrl + "/codesystems/" + codeSystemName;
			LOGGER.debug("Recovering codesystem from {}", url);
			return restTemplate.getForObject(url, CodeSystem.class);
		} catch (RestClientException e) {
			throw new TermServerScriptException(translateRestClientException(e));
		}
	}

	public void setAuthorFlag(String branchPath, String key, String value) throws TermServerScriptException {
		String url = this.serverUrl + BRANCHES + branchPath + "/actions/set-author-flag";
		Map<String, String> requestBody = Map.of("name", key, "value", value);
		try {
			restTemplate.postForObject(url, requestBody, Object.class);
		} catch (RestClientException e) {
			throw new TermServerScriptException(translateRestClientException(e));
		}
	}

	public void updateMetadata(String branchPath, Map<String, Object> metaDataUpdate) throws TermServerScriptException {
		String url = this.serverUrl + BRANCHES + branchPath + "/metadata-upsert";
		try {
			restTemplate.put(url, metaDataUpdate, Object.class);
		} catch (RestClientException e) {
			throw new TermServerScriptException(translateRestClientException(e));
		}
	}

	public Review getReview(String branchPath) throws TermServerScriptException {
		String url = this.serverUrl + "/reviews";
		String targetPath = getParentBranchPath(branchPath);
		Map<String, String> requestBody = Map.of("source", branchPath, "target", targetPath);
		HttpEntity<Map<String,String>> entity = new HttpEntity<>(requestBody);
		try {
			ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.POST, entity, Object.class);
			String location = response.getHeaders().getLocation() == null ? "" : response.getHeaders().getLocation().toString();
			Thread.sleep(3*1000L);
			return restTemplate.getForObject(location, Review.class);
		} catch (RestClientException e) {
			throw new TermServerScriptException(translateRestClientException(e));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new TermServerScriptException(e);
		}
	}
	
	public static String getParentBranchPath(String branchPath) {
		int endIndex = branchPath.lastIndexOf(URL_SEPARATOR);
		return branchPath.substring(0, endIndex);
	}

	public void adminRollbackCommit(Branch b) throws TermServerScriptException {
		String url = this.serverUrl + "/admin/" + b.getPath() + "/actions/rollback-commit?commitHeadTime=" + b.getHeadTimestamp();
		try {
			restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, Object.class);
		} catch (RestClientException e) {
			throw new TermServerScriptException(translateRestClientException(e));
		}
	}

	public <T> Collection<T> getObjectCollection(String url, ParameterizedTypeReference<Collection<T>> typeRef) {
		// Use ParameterizedTypeReference to avoid otf-common needing to know about Concept class
		// which is different in Snowstorm, Reporting and elsewhere
		LOGGER.debug("Recovering collection from {}",  url);
		ResponseEntity<Collection<T>> response = restTemplate.exchange(
				url,
				HttpMethod.GET,
				null,
				typeRef
		);

		Collection<T> collection = response.getBody();
		if (collection == null) {
			throw new IllegalStateException("Failed to recover collection from " + url + " null returned from " + url);
		}
		LOGGER.debug("Recovered {} objects from {}", collection.size(), url);
		return collection;
	}
}