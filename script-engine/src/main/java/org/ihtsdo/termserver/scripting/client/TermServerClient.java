package org.ihtsdo.termserver.scripting.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.rest.client.ExpressiveErrorHandler;
import org.ihtsdo.otf.rest.client.Status;
import org.ihtsdo.otf.rest.client.authoringservices.RestyOverrideAccept;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Review;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Classification;
import org.ihtsdo.otf.RF2Constants.CharacteristicType;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
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

import us.monoid.json.*;
import us.monoid.web.*;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TermServerClient {

	private static final String BRANCHES = "/branches/";
	private static final String BROWSER = "/browser/";
	private static final String MEMBERS = "/members/";
	private static final String MEMBERS_REFSET = "/members?referenceSet=";
	private static final String CONCEPTS = "/concepts";
	private static final String DESCRIPTIONS = "/descriptions";
	private static final String SEARCH_AFTER = "&searchAfter=";

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
	
	private final Resty resty;
	private final RestTemplate restTemplate;
	private final HttpHeaders headers;
	private final String serverUrl;
	private static final String ALL_CONTENT_TYPE = "*/*";
	private static final String JSON_CONTENT_TYPE = "application/json";
	private final Set<SnowOwlClientEventListener> eventListeners;
	private static final Logger LOGGER = LoggerFactory.getLogger(TermServerClient.class);
	public static boolean supportsIncludeUnpublished = true;

	public TermServerClient(String serverUrl, String cookie) {
		this.serverUrl = serverUrl;
		eventListeners = new HashSet<>();
		resty = new Resty(new RestyOverrideAccept(ALL_CONTENT_TYPE));
		resty.withHeader("Cookie", cookie);
		resty.authenticate(this.serverUrl, null,null);
		
		headers = new HttpHeaders();
		headers.add("Cookie", cookie);
		headers.add("Accept", JSON_CONTENT_TYPE);
		
		restTemplate = new RestTemplateBuilder()
				.rootUri(this.serverUrl)
				.additionalMessageConverters(new GsonHttpMessageConverter(gson))
				.additionalMessageConverters(new FormHttpMessageConverter())
				.errorHandler(new ExpressiveErrorHandler())
				.setConnectTimeout(Duration.of(10, ChronoUnit.SECONDS))
				.setReadTimeout(Duration.of(5, ChronoUnit.MINUTES))
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
		while (retry < 3) {
			try {
				String url = getBranchesPath(branchPath);
				LOGGER.debug("Recovering branch information from {}", url);
				return restTemplate.getForObject(url, Branch.class);
			} catch (RestClientException e) {
				retry++;
				LOGGER.warn("Problem recovering branch information.   Retrying after a nap...");
				try { Thread.sleep(1000L * 5); } catch (InterruptedException e2) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Thread interrupted", e2);
				}
			}
		}
		return null;
	}
	
	public List<Branch> getBranchChildren(String branchPath) throws TermServerScriptException {
		try {
			String url = getBranchesPath(branchPath) + "/children?immediateChildren=true";
			LOGGER.debug("Recovering branch child information from {}", url);
			ResponseEntity<List<Branch>> response = restTemplate.exchange(
					url,
					HttpMethod.GET,
					null,
					new ParameterizedTypeReference<List<Branch>>(){});
			return response.getBody();
		} catch (RestClientException e) {
			throw new TermServerScriptException(translateRestClientException(e));
		}
	}
	
	public List<CodeSystemVersion> getCodeSystemVersions(String codeSystem) throws TermServerScriptException {
		try {
			String url = this.serverUrl + "/codesystems/" + codeSystem + "/versions?showFutureVersions=true";
			LOGGER.debug("Recovering codesystem versions from " + url);
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
			Concept newConcept =  restTemplate.postForObject(getConceptBrowserPath(branchPath), new HttpEntity<>(c, headers), Concept.class);
			LOGGER.info("Created concept: " + newConcept + " with " + c.getDescriptions().size() + " descriptions.");
			return newConcept;
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	public Concept updateConcept(Concept c, String branchPath) throws TermServerScriptException {
		try {
			Preconditions.checkNotNull(c.getConceptId());
			boolean updatedOK = false;
			int tries = 0;
			while (!updatedOK) {
				try {
					ResponseEntity<Concept> response = restTemplate.exchange(
							getConceptBrowserPath(branchPath) + "/" + c.getConceptId(),
							HttpMethod.PUT,
							new HttpEntity<>(c, headers),
							Concept.class);
					c = response.getBody();
					updatedOK = true;
					LOGGER.info("Updated concept " + c.getConceptId());
				} catch (RestClientResponseException e) {
					tries++;
					if (tries >= MAX_TRIES || e.getRawStatusCode() == 400) {
						throw new TermServerScriptException("Failed to update concept: " + c + " after " + tries + " attempts due to " + e.getMessage(), e);
					}
					LOGGER.debug("Update of concept failed, trying again....",e);
					Thread.sleep(30*1000); //Give the server 30 seconds to recover
				}
			}
			return c;
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	public Concept getConcept(String sctid, String branchPath) throws TermServerScriptException {
		String url = getConceptBrowserPath(branchPath) + "/" + sctid;
		Concept concept = restTemplate.getForObject(url, Concept.class);
		concept.setId(concept.getConceptId());  //RestTemplate is not calling the setter so this is cheaper
		//than writing a custom deserializer
		return concept;
	}

	public Description getDescription(String descriptionId, String branchPath) {
		String url = getDescriptionsPath(descriptionId, branchPath);
		return restTemplate.getForObject(url, Description.class);
	}

	public void deleteConcept(String sctId, String branchPath) throws TermServerScriptException {
		try {
			restTemplate.delete(getConceptsPath(sctId, branchPath));
			LOGGER.info("Deleted concept " + sctId + " from " + branchPath);
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to delete concept: " + sctId, e);
		}
	}

	public void deleteDescription(String sctId, String branchPath) throws TermServerScriptException {
		try {
			restTemplate.delete(getDescriptionsPath(sctId,branchPath));
			LOGGER.info("Deleted description " + sctId + " from " + branchPath);
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to delete description: " + sctId, e);
		}
	}
	
	public ConceptCollection getConcepts(String ecl, String branchPath, String searchAfter, int limit) throws TermServerScriptException {
		return getConcepts(ecl, branchPath, CharacteristicType.INFERRED_RELATIONSHIP, searchAfter, limit);
	}
	
	public ConceptCollection getConcepts(String ecl, String branchPath, CharacteristicType charType, String searchAfter, int limit) throws TermServerScriptException {
		String eclType = charType.equals(CharacteristicType.INFERRED_RELATIONSHIP)?"ecl":"statedEcl";
		String criteria = SnomedUtils.makeMachineReadable(ecl);
		return getConceptsMatchingCriteria(eclType, criteria, branchPath, searchAfter, limit);
	}
	
	public ConceptCollection getConceptsMatchingCriteria(String eclType, String criteria, String branchPath, String searchAfter, int limit) throws TermServerScriptException {
		//RestTemplate will attempt to expand out any curly braces, and we can't URLEncode
		//because RestTemplate does that for us.  So use curly braces to substitute in our criteria
		String url = getConceptsPath(branchPath) + "?active=true&limit=" + limit;
		if (!StringUtils.isEmpty(searchAfter)) {
			url += SEARCH_AFTER + searchAfter;
		}

		url += "&" + eclType + "={criteria}";
		System.out.println("Calling: " + url + " with criteria = '" + criteria + "'");
		return restTemplate.getForObject(url, ConceptCollection.class, criteria);
	}
	
	public int getConceptsCount(String ecl, String branchPath) throws TermServerScriptException {
		String url = getConceptsPath(branchPath) + "?active=true&limit=1";
		ecl = SnomedUtils.makeMachineReadable(ecl);
		//RestTemplate will attempt to expand out any curly braces, and we can't URLEncode
		//because RestTemplate does that for us.  So use curly braces to substitute in our ecl
		url += "&ecl={ecl}";
		System.out.println("Calling " + url + " with ecl parameter: " + ecl);
		return restTemplate.getForObject(url, ConceptCollection.class, ecl).getTotal();
	}

	private String getBranchesPath(String branchPath) {
		return  serverUrl + BRANCHES + branchPath;
	}
	
	private String getConceptsPath(String sctId, String branchPath) {
		return serverUrl + "/" + branchPath + CONCEPTS +"/" + sctId;
	}

	private String getConceptsPath(String branchPath) {
		return serverUrl + "/" + branchPath + CONCEPTS;
	}

	private String getConceptBrowserPath(String branchPath) {
		return serverUrl + BROWSER + branchPath + CONCEPTS;
	}
	
	private String getConceptBrowserValidationPath(String branchPath) {
		return serverUrl + BROWSER + branchPath + "/validate/concept";
	}
	
	private String getDescriptionsPath(String id, String branchPath) {
		return serverUrl + "/" + branchPath + DESCRIPTIONS + "/" + id;
	}

	public String createBranch(String parent, String branchName) throws TermServerScriptException {
		try {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("parent", parent);
			jsonObject.put("name", branchName);
			resty.json(serverUrl + "/branches", RestyHelper.content(jsonObject, JSON_CONTENT_TYPE));
			final String branchPath = parent + "/" + branchName;
			LOGGER.info("Created branch {}", branchPath);
			for (SnowOwlClientEventListener eventListener : eventListeners) {
				eventListener.branchCreated(branchPath);
			}
			return branchPath;
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	public void mergeBranch(String source, String target) throws TermServerScriptException {
		try {
			final JSONObject json = new JSONObject();
			json.put("source", source);
			json.put("target", target);
			final String message = "Merging " + source + " to " + target;
			json.put("commitComment", message);
			LOGGER.info(message);
			resty.json(serverUrl + "/merges", RestyHelper.content(json, JSON_CONTENT_TYPE));
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	public void deleteBranch(String branchPath) throws TermServerScriptException {
		try {
			resty.json(serverUrl + BRANCHES + branchPath, Resty.delete());
			LOGGER.info("Deleted branch {}", branchPath);
		} catch (IOException e) {
			throw new TermServerScriptException(e);
		}
	}

	public JSONResource search(String query, String branchPath) throws TermServerScriptException {
		try {
			return resty.json(serverUrl + BROWSER + branchPath + "/descriptions?query=" + query);
		} catch (IOException e) {
			throw new TermServerScriptException(e);
		}
	}

	public JSONResource searchWithPT(String query, String branchPath) throws TermServerScriptException {
		try {
			return resty.json(serverUrl + BROWSER + branchPath + "/descriptions-pt?query=" + query);
		} catch (IOException e) {
			throw new TermServerScriptException(e);
		}
	}

	public void addEventListener(SnowOwlClientEventListener eventListener) {
		eventListeners.add(eventListener);
	}

	/**
	 * Returns id of classification
	 * @param branchPath
	 * @return
	 */
	public String classifyAndWaitForComplete(String branchPath) throws TermServerScriptException {
		try {
			final JSONObject json = new JSONObject();
			json.put("reasonerId", "org.semanticweb.elk.elk.reasoner.factory");
			String url = this.serverUrl + "/" + branchPath + "/classifications";
			System.out.println(url);
			System.out.println(json.toString(3));
			final JSONResource resource = resty.json(url, RestyHelper.content(json, JSON_CONTENT_TYPE));
			final String location = resource.getUrlConnection().getHeaderField("Location");
			System.out.println("location " + location);

			String status;
			do {
				final JSONObject jsonObject = resty.json(location).toObject();
				status = jsonObject.getString("status");
			} while (("SCHEDULED".equals(status) || "RUNNING".equals(status) && sleep(10)));

			if ("COMPLETED".equals(status)) {
				return location.substring(location.lastIndexOf("/"));
			} else {
				throw new TermServerScriptException("Unexpected classification state " + status);
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	private boolean sleep(int seconds) throws InterruptedException {
		Thread.sleep(1000L * seconds);
		return true;
	}

	public Resty getResty() {
		return resty;
	}

	public String getServerUrl() {
		return serverUrl;
	}

	public JSONArray getMergeReviewDetails(String mergeReviewId) throws TermServerScriptException {
		LOGGER.info("Getting merge review {}", mergeReviewId);
		try {
			return resty.json(getMergeReviewUrl(mergeReviewId) + "/details").array();
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	private String getMergeReviewUrl(String mergeReviewId) {
		return this.serverUrl + "/merge-reviews/" + mergeReviewId;
	}

	public void saveConceptMerge(String mergeReviewId, JSONObject mergedConcept) throws TermServerScriptException {
		try {
			String id = ConceptHelper.getConceptId(mergedConcept);
			LOGGER.info("Saving merged concept {} for merge review {}", id, mergeReviewId);
			resty.json(getMergeReviewUrl(mergeReviewId) + "/" + id, RestyHelper.content(mergedConcept, JSON_CONTENT_TYPE));
		} catch (JSONException | IOException e) {
			throw new TermServerScriptException(e);
		}
	}

	public File export(String branchPath, String effectiveDate, ExportType exportType, ExtractType extractType, File saveLocation, boolean unpromotedChangesOnly)
			throws TermServerScriptException {
		Map<String, Object> exportRequest = prepareExportRequest(branchPath, effectiveDate, exportType, extractType, unpromotedChangesOnly);
		LOGGER.info("Initiating export with {}", exportRequest);
		String exportLocationURL = initiateExport(exportRequest);
		//INFRA-1489 Workaround
		if (!exportLocationURL.startsWith("https://")) {
			exportLocationURL = exportLocationURL.replace("http:/", "https://");
			System.err.println("Malformed export location received, corrected to https://");
		}
		if (saveLocation == null) {
			try {
				saveLocation = File.createTempFile("ts-extract", ".zip");
			} catch (IOException e) {
				throw new TermServerScriptException(e);
			}
		}
		File recoveredArchive = recoverExportedArchive(exportLocationURL, saveLocation);
		return recoveredArchive;
	}
	
	private Map<String, Object> prepareExportRequest(String branchPath, String effectiveDate, ExportType exportType, ExtractType extractType, boolean unpromotedChangesOnly)
			throws TermServerScriptException {
		Map<String, Object> exportRequest = new HashMap<>();
		exportRequest.put("type", extractType);
		exportRequest.put("branchPath", branchPath);
		exportRequest.put("unpromotedChangesOnly", unpromotedChangesOnly);
		switch (exportType) {
			case MIXED:  //Snapshot allows for both published and unpublished, where unpublished
				//content would get the transient effective Date
				if (!extractType.equals(ExtractType.SNAPSHOT)) {
					throw new TermServerScriptException("Export type " + exportType + " not recognised");
				}
				if (supportsIncludeUnpublished) {
					exportRequest.put("includeUnpublished", true);
				}
			case UNPUBLISHED:
				//Now leaving effective date blank if not specified
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

	private String initiateExport(Map<String, Object> exportRequest) throws TermServerScriptException {
		try {
			HttpEntity<?> entity = new HttpEntity<>(exportRequest, headers ); // for request
			HttpEntity<String> response = restTemplate.exchange(serverUrl + "/exports", HttpMethod.POST, entity, String.class);
			return response.getHeaders().get("Location").get(0);
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to initiate export", e);
		}
	}
	
	private File recoverExportedArchive(String exportLocationURL,  final File saveLocation) throws TermServerScriptException {
		try {
			LOGGER.info("Recovering exported archive from {}", exportLocationURL);
			LOGGER.info("Saving exported archive to {}", saveLocation);
			RequestCallback requestCallback = request -> request.getHeaders()
					.setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));
			restTemplate.execute(exportLocationURL + "/archive", HttpMethod.GET, requestCallback, clientHttpResponse -> {
				StreamUtils.copy(clientHttpResponse.getBody(), new FileOutputStream(saveLocation));
				return null;
			});
			if (saveLocation.length() == 0) {
				throw new RestClientException("Archive recovered as 0 bytes");
			}
			LOGGER.debug("Extract recovery complete");
			return saveLocation;
		} catch (RestClientException e) {
			throw new TermServerScriptException("Unable to recover exported archive from " + exportLocationURL, e);
		}
	}
	
	public void importArchive(String branchPath, ImportType importType, File archive)
			throws TermServerScriptException {
		Map<String, Object> importRequest = new HashMap<>();
		importRequest.put("type", importType);
		importRequest.put("branchPath", branchPath);
		LOGGER.info("Creating import job with {}", importRequest);
		String importJobLocation = initiateImport(importRequest);
		try {
			importArchive(importJobLocation, archive);
		} catch (JSONException|InterruptedException e) {
			throw new TermServerScriptException("Failure importing to" + importJobLocation, e);
		}
	}


	private void importArchive(String importJobLocation, File archive) throws JSONException, InterruptedException, TermServerScriptException {
		LOGGER.info("Importing {} in import job {}", archive, importJobLocation);
		String url = importJobLocation + "/archive";
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
		
		//No HttpMessageConverter for File directly.
		//FormHttpMessageConverter uses ResourceHttpMessageConverter which can handle this
		form.add("file", new FileSystemResource(archive)); 
		HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(form, headers);
		ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
		
		//Now wait for the import to complete
		while(true) {
			Thread.sleep(5 * 1000);
			Map<String, String> responseJson = restTemplate.getForObject(importJobLocation, Map.class);
			String responseJsonStr = new JSONObject(responseJson).toString(2);
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

	public JSONResource updateDescription(String descId, JSONObject descObj, String branchPath) throws TermServerScriptException {
		try {
			Preconditions.checkNotNull(descId);
			JSONResource response =  resty.json(getDescriptionsPath(descId, branchPath) + "/updates", RestyHelper.content(descObj, JSON_CONTENT_TYPE));
			LOGGER.info("Updated description {}", descId);
			return response;
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	public JSONArray getLangRefsetMembers(String descriptionId, String refsetId, String branch) throws TermServerScriptException {
		final String url = this.serverUrl + "/" + branch + MEMBERS_REFSET + refsetId + "&referencedComponentId=" + descriptionId;
		try {
			return (JSONArray) resty.json(url).get("items");
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	public void deleteRefsetMember(String refsetMemberId, String branch, boolean toForce) throws TermServerScriptException {
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
		return this.serverUrl + "/" + branch + MEMBERS + refSetMemberId;
	}

	private String getRelationshipUrl(String relationshipId, String branch) {
		return this.serverUrl + "/" + branch + "/relationships/" + relationshipId;
	}

	private String getRefsetMembersUrl(String branchPath, String referenceSet, String searchAfter) {
		if (searchAfter == null || searchAfter.isEmpty()) {
			return this.serverUrl + "/" + branchPath + MEMBERS_REFSET + referenceSet;
		} else {
			return this.serverUrl + "/" + branchPath + MEMBERS_REFSET + referenceSet + SEARCH_AFTER + searchAfter;
		}
	}

	private String getRefsetMembersUrl(String branch) {
		return this.serverUrl + "/" + branch + "/members";
	}
	
	private String getRefsetMemberUpdateUrl(String refSetMemberId, String branch, boolean toForce) {
		return getRefsetMembersUrl(refSetMemberId, branch) + "?force=" + toForce;
	}

	public Refset loadRefsetEntries(String branchPath, String refsetId, String referencedComponentId) throws TermServerScriptException {
		try {
			String endPoint = this.serverUrl + "/" + branchPath + MEMBERS_REFSET + refsetId + "&referencedComponentId=" + referencedComponentId;
			JSONResource response = resty.json(endPoint);
			String json = response.toObject().toString();
			return gson.fromJson(json, Refset.class);
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to recover refset for " + refsetId + " - " + referencedComponentId, e);
		}
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
			LOGGER.info("Updated refset member " + refsetEntry.getId());
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to update refset entry " + refsetEntry + " due to " + e.getMessage(), e);
		}
	}
	

	public void waitForCompletion(String branchPath, Classification classification) throws TermServerScriptException {
		try {
			String endPoint = this.serverUrl + "/" + branchPath + "/classifications/" + classification.getId();
			Status status;
			long sleptSecs = 0;
			do {
				JSONResource response = resty.json(endPoint);
				String json = response.toObject().toString();
				status = gson.fromJson(json, Status.class);
				if (!status.isFinalState()) {
					Thread.sleep(RETRY * 1000L);
					sleptSecs += RETRY;
					if (sleptSecs % 60 == 0) {
						System.out.println("Waited for " + sleptSecs + " for classification " + classification.getId() + " on " + branchPath);
					}
				}
			} while (!status.isFinalState());
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to recover status of classification " + classification.getId() + " due to " + e.getMessage(), e);
		}
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
	
	public Collection<RefsetMember> findRefsetMembers(String branchPath, Concept c, String refsetFilter) throws TermServerScriptException {
		return findRefsetMembers(branchPath, Collections.singletonList(c), refsetFilter);
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
			LOGGER.debug("Recovering codesystems from " + url);
			return restTemplate.getForObject(url, CodeSystemCollection.class).getItems();
		} catch (RestClientException e) {
			throw new TermServerScriptException(translateRestClientException(e));
		}
	}

	public CodeSystem getCodeSystem(String codeSystemName) throws TermServerScriptException {
		try {
			String url = this.serverUrl + "/codesystems/" + codeSystemName;
			LOGGER.debug("Recovering codesystem from " + url);
			return restTemplate.getForObject(url, CodeSystem.class);
		} catch (RestClientException e) {
			throw new TermServerScriptException(translateRestClientException(e));
		}
	}

	//Watch that this POST operation is not available in read-only instances
	public List<Concept> filterConcepts(ConceptSearchRequest searchRequest, String branchPath) throws TermServerScriptException {
		try {
			String url = this.serverUrl + "/" + branchPath + "/concepts/search";
			ConceptCollection collection = restTemplate.postForObject(
								url,
								searchRequest,
								ConceptCollection.class);
			return collection.getItems();
		} catch (RestClientException e) {
			throw new TermServerScriptException(translateRestClientException(e));
		}
	}

	public List<Description> getUnpromotedDescriptions(String branchPath, boolean unpromotedChangesOnly) throws TermServerScriptException {
		try {
			String url = this.serverUrl + "/" + branchPath + "/authoring-stats/new-descriptions?unpromotedChangesOnly=" + (unpromotedChangesOnly?"true":"false");
			Description[] descriptions = restTemplate.getForObject(
								url,
								Description[].class);
			return Arrays.asList(descriptions);
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
		int endIndex = branchPath.lastIndexOf("/");
		return branchPath.substring(0, endIndex);
	}

	public boolean adminRollbackCommit(Branch b) throws TermServerScriptException {
		String url = this.serverUrl + "/admin/" + b.getPath() + "/actions/rollback-commit?commitHeadTime=" + b.getHeadTimestamp();
		HttpEntity<Map<String,Object>> entity = new HttpEntity<>(null);
		try {
			ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.POST, entity, Object.class);
			return response.getStatusCode().is2xxSuccessful();
		} catch (RestClientException e) {
			throw new TermServerScriptException(translateRestClientException(e));
		}
	}

}
