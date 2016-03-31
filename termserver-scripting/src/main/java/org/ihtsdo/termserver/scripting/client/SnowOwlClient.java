package org.ihtsdo.termserver.scripting.client;

import com.google.common.base.Preconditions;
import org.ihtsdo.termserver.scripting.domain.ConceptHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.monoid.json.JSONArray;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class SnowOwlClient {

	private final Resty resty;
	private final String url;
	private static final String SNOWOWL_CONTENT_TYPE = "application/json";
//	private static final String SNOWOWL_CONTENT_TYPE = "application/vnd.com.b2international.snowowl+json";
	private final Set<SnowOwlClientEventListener> eventListeners;
	private Logger logger = LoggerFactory.getLogger(getClass());

	public SnowOwlClient(String url, String username, String password) {
		this.url = url;
		eventListeners = new HashSet<>();
		resty = new Resty();
		resty.authenticate(url, username, password.toCharArray());
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
			final String id = concept.getString("conceptId");
			Preconditions.checkNotNull(id);
			logger.info("Updated concept " + id);
			return resty.json(getConceptsPath(branchPath) + "/" + id, Resty.put(RestyHelper.content(concept, SNOWOWL_CONTENT_TYPE)));
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
//		resty.withHeader("Accept", "application/vnd.com.b2international.snowowl+json");
//		url = "http://requestb.in/rky7oqrk";
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
}
