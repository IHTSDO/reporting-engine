package org.ihtsdo.termserver.scripting.client;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import us.monoid.json.JSONArray;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;
import us.monoid.web.AbstractContent;
import us.monoid.web.BinaryResource;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SnowOwlClient {
	
	public enum ExtractType {
		DELTA, SNAPSHOT, FULL;
	};

	public enum ExportType {
		PUBLISHED, UNPUBLISHED, MIXED;
	}
	
	public static SimpleDateFormat YYYYMMDD = new SimpleDateFormat("yyyyMMdd");
	public static final int MAX_TRIES = 3;
	public static final int retry = 15;
	
	protected static Gson gson;
	static {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		gsonBuilder.excludeFieldsWithoutExposeAnnotation();
		gson = gsonBuilder.create();
	}
	
	private final Resty resty;
	private final RestTemplate restTemplate;
	private final HttpHeaders headers;
	private final String url;
	private static final String ALL_CONTENT_TYPE = "*/*";
	private static final String SNOWOWL_CONTENT_TYPE = "application/json";
	private final Set<SnowOwlClientEventListener> eventListeners;
	private Logger logger = LoggerFactory.getLogger(getClass());
	public static boolean supportsIncludeUnpublished = true;

	public SnowOwlClient(String serverUrl, String cookie) {
		this.url = serverUrl;
		eventListeners = new HashSet<>();
		resty = new Resty(new RestyOverrideAccept(ALL_CONTENT_TYPE));
		resty.withHeader("Cookie", cookie);
		resty.authenticate(this.url, null,null);
		
		headers = new HttpHeaders();
		headers.add("Cookie", cookie);
		headers.add("Accept", SNOWOWL_CONTENT_TYPE);
		
		restTemplate = new RestTemplateBuilder()
				.rootUri(this.url)
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
	
	public Branch getBranch(String branchPath) throws SnowOwlClientException {
		try {
			String url = getBranchesPath(branchPath);
			logger.debug("Recovering branch information from " + url);
			return restTemplate.getForObject(url, Branch.class);
		} catch (RestClientException e) {
			
			throw new SnowOwlClientException(translateRestClientException(e));
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

	public JSONResource createConcept(JSONObject json, String branchPath) throws SnowOwlClientException {
		final JSONResource newConcept;
		try {
			newConcept = resty.json(getConceptBrowserPath(branchPath), RestyHelper.content(json, SNOWOWL_CONTENT_TYPE));
			logger.info("Created concept " + newConcept.get("conceptId") + " |" + newConcept.get("fsn") + "|");
			return newConcept;
		} catch (Exception e) {
			throw new SnowOwlClientException(e);
		}
	}

	public JSONResource updateConcept(JSONObject concept, String branchPath) throws SnowOwlClientException {
		try {
			JSONResource response = null;
			final String id = concept.getString("conceptId");
			Preconditions.checkNotNull(id);
			boolean updatedOK = false;
			int tries = 0;
			while (!updatedOK) {
				try {
					response =  resty.json(getConceptBrowserPath(branchPath) + "/" + id, Resty.put(RestyHelper.content(concept, SNOWOWL_CONTENT_TYPE)));
					updatedOK = true;
					logger.info("Updated concept " + id);
				} catch (Exception e) {
					tries++;
					if (tries >= MAX_TRIES) {
						throw new SnowOwlClientException("Failed to update concept " + id + " after " + tries + " attempts due to " + e.getMessage() + "\nJSON representation: " + concept.toString(), e);
					}
					logger.debug("Update of concept failed, trying again....",e);
					Thread.sleep(30*1000); //Give the server 30 seconds to recover
				}
			}
			return response;
		} catch (Exception e) {
			throw new SnowOwlClientException(e);
		}
	}

	public JSONResource getConcept(String sctid, String branchPath) throws SnowOwlClientException {
		try {
			return resty.json(getConceptBrowserPath(branchPath) + "/" + sctid);
		} catch (IOException e) {
			throw new SnowOwlClientException(e);
		}
	}

	public void deleteConcept(String sctId, String branchPath) throws SnowOwlClientException {
		try {
			resty.json(getConceptsPath(sctId, branchPath), Resty.delete());
			logger.info("Deleted concept " + sctId + " from " + branchPath);
		} catch (Exception e) {
			throw new SnowOwlClientException(e);
		}
	}

	public JSONResource getConcepts(String ecl, String branchPath, int offset, int limit) throws SnowOwlClientException {
		try {
			//I suspect a failure of 0 concepts gets cached somewhere, so I'm adding a random string to force a fresh request
			String random = RandomStringUtils.random(5, true, true);
			String url = getConceptsPath(branchPath) + "?rnd=" + random + "&active=true&limit=" + limit + "&offset=" + offset + "&ecl=" + URLEncoder.encode(ecl, "UTF-8");
			System.out.println("Calling " + url);
			return resty.json(url);
		} catch (IOException e) {
			throw new SnowOwlClientException(e);
		}
	}
	
	private String getBranchesPath(String branchPath) {
		return  url + "/branches/" + branchPath;
	}
	
	private String getConceptsPath(String sctId, String branchPath) {
		return url + "/" + branchPath + "/concepts/" + sctId;
	}

	private String getConceptsPath(String branchPath) {
		return url + "/" + branchPath + "/concepts";
	}

	private String getConceptBrowserPath(String branchPath) {
		return url + "/browser/" + branchPath + "/concepts";
	}
	
	private String getDescriptionsPath(String branchPath, String id) {
		return url  + "/" + branchPath + "/descriptions/" + id;
	}

	public String createBranch(String parent, String branchName) throws SnowOwlClientException {
		try {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("parent", parent);
			jsonObject.put("name", branchName);
			resty.json(url + "/branches", RestyHelper.content(jsonObject, SNOWOWL_CONTENT_TYPE));
			final String branchPath = parent + "/" + branchName;
			logger.info("Created branch {}", branchPath);
			for (SnowOwlClientEventListener eventListener : eventListeners) {
				eventListener.branchCreated(branchPath);
			}
			return branchPath;
		} catch (Exception e) {
			throw new SnowOwlClientException(e);
		}
	}

	public void mergeBranch(String source, String target) throws SnowOwlClientException {
		try {
			final JSONObject json = new JSONObject();
			json.put("source", source);
			json.put("target", target);
			final String message = "Merging " + source + " to " + target;
			json.put("commitComment", message);
			logger.info(message);
			resty.json(url + "/merges", RestyHelper.content(json, SNOWOWL_CONTENT_TYPE));
		} catch (Exception e) {
			throw new SnowOwlClientException(e);
		}
	}

	public void deleteBranch(String branchPath) throws SnowOwlClientException {
		try {
			resty.json(url + "/branches/" + branchPath, Resty.delete());
			logger.info("Deleted branch {}", branchPath);
		} catch (IOException e) {
			throw new SnowOwlClientException(e);
		}
	}

	public JSONResource search(String query, String branchPath) throws SnowOwlClientException {
		try {
			return resty.json(url + "/browser/" + branchPath + "/descriptions?query=" + query);
		} catch (IOException e) {
			throw new SnowOwlClientException(e);
		}
	}

	public JSONResource searchWithPT(String query, String branchPath) throws SnowOwlClientException {
		try {
			return resty.json(url + "/browser/" + branchPath + "/descriptions-pt?query=" + query);
		} catch (IOException e) {
			throw new SnowOwlClientException(e);
		}
	}

	public void addEventListener(SnowOwlClientEventListener eventListener) {
		eventListeners.add(eventListener);
	}

	/**
	 * Returns id of classification
	 * @param branchPath
	 * @return
	 * @throws IOException
	 * @throws JSONException
	 * @throws InterruptedException
	 */
	public String classifyAndWaitForComplete(String branchPath) throws SnowOwlClientException {
		try {
			final JSONObject json = new JSONObject();
			json.put("reasonerId", "org.semanticweb.elk.elk.reasoner.factory");
			String url = this.url + "/" + branchPath + "/classifications";
			System.out.println(url);
			System.out.println(json.toString(3));
			final JSONResource resource = resty.json(url, RestyHelper.content(json, SNOWOWL_CONTENT_TYPE));
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
				throw new SnowOwlClientException("Unexpected classification state " + status);
			}
		} catch (Exception e) {
			throw new SnowOwlClientException(e);
		}
	}

	private boolean sleep(int seconds) throws InterruptedException {
		Thread.sleep(1000 * seconds);
		return true;
	}

	public Resty getResty() {
		return resty;
	}

	public String getUrl() {
		return url;
	}

	public JSONArray getMergeReviewDetails(String mergeReviewId) throws SnowOwlClientException {
		logger.info("Getting merge review {}", mergeReviewId);
		try {
			return resty.json(getMergeReviewUrl(mergeReviewId) + "/details").array();
		} catch (Exception e) {
			throw new SnowOwlClientException(e);
		}
	}

	private String getMergeReviewUrl(String mergeReviewId) {
		return this.url + "/merge-reviews/" + mergeReviewId;
	}

	public void saveConceptMerge(String mergeReviewId, JSONObject mergedConcept) throws SnowOwlClientException {
		try {
			String id = ConceptHelper.getConceptId(mergedConcept);
			logger.info("Saving merged concept {} for merge review {}", id, mergeReviewId);
			resty.json(getMergeReviewUrl(mergeReviewId) + "/" + id, RestyHelper.content(mergedConcept, SNOWOWL_CONTENT_TYPE));
		} catch (JSONException | IOException e) {
			throw new SnowOwlClientException(e);
		}
	}

	public File export(String branchPath, String effectiveDate, ExportType exportType, ExtractType extractType, File saveLocation)
			throws SnowOwlClientException {
		JSONObject jsonObj = prepareExportJSON(branchPath, effectiveDate, exportType, extractType);
		logger.info ("Initiating export with {}",jsonObj.toString());
		String exportLocationURL = initiateExport(jsonObj);
		File recoveredArchive = recoverExportedArchive(exportLocationURL, saveLocation);
		return recoveredArchive;
	}
	
	private JSONObject prepareExportJSON(String branchPath, String effectiveDate, ExportType exportType, ExtractType extractType)
			throws SnowOwlClientException {
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put("type", extractType);
			jsonObj.put("branchPath", branchPath);
			switch (exportType) {
				case MIXED:  //Snapshot allows for both published and unpublished, where unpublished
					//content would get the transient effective Date
					if (!extractType.equals(ExtractType.SNAPSHOT)) {
						throw new SnowOwlClientException("Export type " + exportType + " not recognised");
					}
					if (supportsIncludeUnpublished) {
						jsonObj.put("includeUnpublished", true);
					}
				case UNPUBLISHED:
					//Now leaving effective date blank if not specified
					if (effectiveDate != null) {
						//String tet = (effectiveDate == null) ?YYYYMMDD.format(new Date()) : effectiveDate;
						jsonObj.put("transientEffectiveTime", effectiveDate);
					}
					break;
				case PUBLISHED:
					if (effectiveDate == null) {
						throw new SnowOwlClientException("Cannot export published data without an effective date");
					}
					jsonObj.put("deltaStartEffectiveTime", effectiveDate);
					jsonObj.put("deltaEndEffectiveTime", effectiveDate);
					jsonObj.put("transientEffectiveTime", effectiveDate);
					break;
				
				default:
					throw new SnowOwlClientException("Export type " + exportType + " not recognised");
			}
		} catch (JSONException e) {
			throw new SnowOwlClientException("Failed to prepare JSON for export request.", e);
		}
		return jsonObj;
	}

	private String initiateExport(JSONObject jsonObj) throws SnowOwlClientException {
		try {
			JSONResource jsonResponse = resty.json(url + "/exports", RestyHelper.content(jsonObj, SNOWOWL_CONTENT_TYPE));
			Object exportLocationURLObj = jsonResponse.getUrlConnection().getHeaderField("Location");
			if (exportLocationURLObj == null) {
				throw new SnowOwlClientException("Failed to obtain location of export:");
			} else {
				logger.info ("Recovering export from {}",exportLocationURLObj.toString());
			}
			return exportLocationURLObj.toString() + "/archive";
		} catch (Exception e) {
			// TODO Change this to catch JSONException once Resty no longer throws Exceptions
			throw new SnowOwlClientException("Failed to initiate export", e);
		}
	}

	private File recoverExportedArchive(String exportLocationURL, File saveLocation) throws SnowOwlClientException {
		try {
			logger.info("Recovering exported archive from {}", exportLocationURL);
			resty.withHeader("Accept", ALL_CONTENT_TYPE);
			BinaryResource archiveResource = resty.bytes(exportLocationURL);
			if (saveLocation == null) {
				saveLocation = File.createTempFile("ts-extract", ".zip");
			}
			archiveResource.save(saveLocation);
			logger.debug("Extract saved to {}", saveLocation.getAbsolutePath());
			return saveLocation;
		} catch (IOException e) {
			throw new SnowOwlClientException("Unable to recover exported archive from " + exportLocationURL, e);
		}
	}

	public JSONResource updateDescription(String descId, JSONObject descObj, String branchPath) throws SnowOwlClientException {
		try {
			Preconditions.checkNotNull(descId);
			JSONResource response =  resty.json(getDescriptionsPath(branchPath,descId) + "/updates", RestyHelper.content(descObj, SNOWOWL_CONTENT_TYPE));
			logger.info("Updated description " + descId);
			return response;
		} catch (Exception e) {
			throw new SnowOwlClientException(e);
		}
	}

	public JSONArray getLangRefsetMembers(String descriptionId, String refsetId, String branch) throws SnowOwlClientException {
		final String url = this.url + "/" + branch + "/members?referenceSet=" + refsetId + "&referencedComponentId=" + descriptionId;
		try {
			return (JSONArray) resty.json(url).get("items");
		} catch (Exception e) {
			throw new SnowOwlClientException(e);
		}
	}

	public void deleteRefsetMember(String langRefMemberId, String branch, boolean toForce) throws SnowOwlClientException {
		
		try {
			resty.json(getRefsetMemberUpdateUrl(langRefMemberId, branch, toForce), Resty.delete());
			logger.info("deleted refset member id:" + langRefMemberId);
		} catch (IOException e) {
			throw new SnowOwlClientException(e);
		}
	}

	public JSONResource updateRefsetMember(JSONObject refsetUpdate, String branch, boolean toForce) throws SnowOwlClientException {
		try {
			final String id = refsetUpdate.getString("id");
			Preconditions.checkNotNull(id);
			logger.info("Updating refset member " + id);
			return resty.json(getRefsetMemberUpdateUrl(id, branch, toForce), Resty.put(RestyHelper.content(refsetUpdate, SNOWOWL_CONTENT_TYPE)));
		} catch (Exception e) {
			throw new SnowOwlClientException(e);
		}
	}
	
	private String getRefsetMemberUrl(String refSetMemberId, String branch) {
		return this.url + "/" + branch + "/members/" + refSetMemberId;
	}
	
	
	private String getDescriptionUrl(String descriptionId, String branch) {
		return this.url + "/" + branch + "/descriptions/" + descriptionId;
	}
	
	private String getRefsetMemberUpdateUrl(String refSetMemberId, String branch, boolean toForce) {
		return getRefsetMemberUrl(refSetMemberId, branch) + "?force=" + toForce;
	}

	public JSONResource getRefsetMemberById(String id, String branch) throws SnowOwlClientException {
		try {
			return resty.json(getRefsetMemberUrl(id, branch));
		} catch (IOException e) {
			throw new SnowOwlClientException(e);
		}
	}

	public JSONResource getDescriptionById(String descriptionId, String branch) throws SnowOwlClientException {
		try {
			return resty.json(getDescriptionUrl(descriptionId, branch));
		} catch (IOException e) {
			throw new SnowOwlClientException(e);
		}
	}

	public Refset loadRefsetEntries(String branchPath, String refsetId, String referencedComponentId) throws SnowOwlClientException {
		try {
			String endPoint = this.url + "/" + branchPath + "/members?referenceSet=" + refsetId + "&referencedComponentId=" + referencedComponentId;
			JSONResource response = resty.json(endPoint);
			String json = response.toObject().toString();
			Refset refsetObj = gson.fromJson(json, Refset.class);
			return refsetObj;
		} catch (Exception e) {
			throw new SnowOwlClientException("Unable to recover refset for " + refsetId + " - " + referencedComponentId, e);
		}
	}

	public void updateRefsetMember(String branchPath, RefsetEntry refsetEntry, boolean forceUpdate) throws SnowOwlClientException {
		try {
			String endPoint = this.url + "/" + branchPath + "/members/" + refsetEntry.getId();
			if (forceUpdate) {
				endPoint += "?force=true";
			}
			String json = gson.toJson(refsetEntry);
			AbstractContent content = Resty.put(RestyHelper.content(new JSONObject(json), SNOWOWL_CONTENT_TYPE));
			resty.json(endPoint, content);
		} catch (Exception e) {
			throw new SnowOwlClientException("Unable to update refset entry " + refsetEntry + " due to " + e.getMessage(), e);
		}
	}
	

	public void waitForCompletion(String branchPath, Classification classification) throws SnowOwlClientException {
		try {
			String endPoint = this.url + "/" + branchPath + "/classifications/" + classification.getId();
			Status status = new Status("Unknown");
			long sleptSecs = 0;
			do {
				JSONResource response = resty.json(endPoint);
				String json = response.toObject().toString();
				status = gson.fromJson(json, Status.class);
				if (!status.isFinalState()) {
					Thread.sleep(retry * 1000);
					sleptSecs += retry;
					if (sleptSecs % 60 == 0) {
						System.out.println("Waited for " + sleptSecs + " for classification " + classification.getId() + " on " + branchPath);
					}
				}
			} while (!status.isFinalState());
		} catch (Exception e) {
			throw new SnowOwlClientException("Unable to recover status of classification " + classification.getId() + " due to " + e.getMessage(), e);
		}
	}

}
