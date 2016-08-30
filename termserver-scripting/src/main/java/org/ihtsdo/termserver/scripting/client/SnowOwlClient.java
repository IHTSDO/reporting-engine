package org.ihtsdo.termserver.scripting.client;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.ihtsdo.termserver.scripting.domain.ConceptHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import us.monoid.json.JSONArray;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;
import us.monoid.web.BinaryResource;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;

public class SnowOwlClient {
	
	public enum ExtractType {
		DELTA, SNAPSHOT, FULL;
	};

	public enum ProcessingStatus {
		COMPLETED, SAVED
	}

	public enum ExportType {
		PUBLISHED, UNPUBLISHED, MIXED;
	}
	
	public static SimpleDateFormat YYYYMMDD = new SimpleDateFormat("yyyyMMdd");
	
	public static final int MAX_TRIES = 3;

	private final Resty resty;
	private final String url;
	private static final String ALL_CONTENT_TYPE = "*/*";
	private static final String SNOWOWL_CONTENT_TYPE = "application/json";
	private final Set<SnowOwlClientEventListener> eventListeners;
	private Logger logger = LoggerFactory.getLogger(getClass());

	public SnowOwlClient(String url, String username, String password) {
		this.url = url;
		eventListeners = new HashSet<>();
		resty = new Resty(new RestyOverrideAccept(ALL_CONTENT_TYPE));
		resty.authenticate(url, username, password.toCharArray());
	}
	
	public SnowOwlClient(String serverUrl, String cookie) {
		this.url = serverUrl;
		eventListeners = new HashSet<>();
		resty = new Resty(new RestyOverrideAccept(ALL_CONTENT_TYPE));
		resty.withHeader("Cookie", cookie);
		resty.authenticate(this.url, null,null);
	}

	public JSONResource createConcept(JSONObject json, String branchPath) throws SnowOwlClientException {
		final JSONResource newConcept;
		try {
			newConcept = resty.json(getConceptsPath(branchPath), RestyHelper.content(json, SNOWOWL_CONTENT_TYPE));
			logger.info("Created concept " + newConcept.get("conceptId"));
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
					response =  resty.json(getConceptsPath(branchPath) + "/" + id, Resty.put(RestyHelper.content(concept, SNOWOWL_CONTENT_TYPE)));
					updatedOK = true;
					logger.info("Updated concept " + id);
				} catch (Exception e) {
					tries++;
					if (tries >= MAX_TRIES) {
						throw new SnowOwlClientException("Failed to update concept " + id, e);
					}
					logger.debug("Update of concept failed, trying again....",e);
					Thread.sleep(10*1000); //Give the server 10 seconds to recover
				}
			}
			return response;
		} catch (Exception e) {
			throw new SnowOwlClientException(e);
		}
	}

	public JSONResource getConcept(String sctid, String branchPath) throws SnowOwlClientException {
		try {
			return resty.json(getConceptsPath(branchPath) + "/" + sctid);
		} catch (IOException e) {
			throw new SnowOwlClientException(e);
		}
	}

	private String getConceptsPath(String branchPath) {
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
		} catch (InterruptedException | IOException | JSONException e) {
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
		} catch (IOException | JSONException e) {
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
				case UNPUBLISHED:
					String tet = (effectiveDate == null) ?YYYYMMDD.format(new Date()) : effectiveDate;
					jsonObj.put("transientEffectiveTime", tet);
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
			}
			return exportLocationURLObj.toString() + "/archive";
		} catch (Exception e) {
			// TODO Change this to catch JSONException once Resty no longer throws Exceptions
			throw new SnowOwlClientException("Failed to initiate export", e);
		}
	}

	private File recoverExportedArchive(String exportLocationURL, File saveLocation) throws SnowOwlClientException {
		try {
			logger.debug("Recovering exported archive from {}", exportLocationURL);
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


}
