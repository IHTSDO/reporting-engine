package org.ihtsdo.snowowl.test.client;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private static final String SNOWOWL_CONTENT_TYPE = "application/vnd.com.b2international.snowowl+json";
	private final Set<SnowOwlClientEventListener> eventListeners;
	private Logger logger = LoggerFactory.getLogger(getClass());

	public SnowOwlClient(String url, String username, String password) {
		this.url = url;
		eventListeners = new HashSet<>();
		resty = new Resty();
		resty.authenticate(url, username, password.toCharArray());
	}

	public JSONResource createConcept(JSONObject json, String branchPath) throws Exception {
		final JSONResource newConcept = resty.json(getConceptsPath(branchPath), RestyHelper.content(json, SNOWOWL_CONTENT_TYPE));
		logger.info("Created concept " + newConcept.get("conceptId"));
		return newConcept;
	}

	public JSONResource updateConcept(JSONObject concept, String branchPath) throws IOException, JSONException {
		final String id = concept.getString("conceptId");
		Assert.assertNotNull(id);
		logger.info("Updated concept " + id);
		return resty.json(getConceptsPath(branchPath) + "/" + id, Resty.put(RestyHelper.content(concept, SNOWOWL_CONTENT_TYPE)));
	}

	public JSONResource getConcept(String sctid, String branchPath) throws IOException {
		return resty.json(getConceptsPath(branchPath) + "/" + sctid);
	}

	private String getConceptsPath(String branchPath) {
		return url + "/browser/" + branchPath + "/concepts";
	}

	public String createBranch(String parent, String branchName) throws JSONException, IOException {
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
	}

	public void mergeBranch(String source, String target) throws IOException, JSONException {
		final JSONObject json = new JSONObject();
		json.put("source", source);
		json.put("target", target);
		final String message = "Merging " + source + " to " + target;
		json.put("commitComment", message);
		logger.info(message);
		resty.json(url + "/merges", RestyHelper.content(json, SNOWOWL_CONTENT_TYPE));
	}

	public void deleteBranch(String branchPath) throws IOException {
		resty.json(url + "/branches/" + branchPath, Resty.delete());
		logger.info("Deleted branch {}", branchPath);
	}

	public JSONResource search(String query, String branchPath) throws IOException {
		return resty.json(url + "/browser/" + branchPath + "/descriptions?query=" + query);
	}

	public JSONResource searchWithPT(String query, String branchPath) throws IOException {
		return resty.json(url + "/browser/" + branchPath + "/descriptions-pt?query=" + query);
	}

	public void addEventListener(SnowOwlClientEventListener eventListener) {
		eventListeners.add(eventListener);
	}

	public String classify(String branchPath) throws IOException, JSONException, InterruptedException {
		final JSONObject json = new JSONObject();
		json.put("reasonerId", "org.semanticweb.elk.elk.reasoner.factory");
		final String url = this.url + "/" + branchPath + "/classifications";
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
			throw new IOException("Unexpected classification state " + status);
		}
	}

	private boolean sleep(int seconds) throws InterruptedException {
		Thread.sleep(1000 * seconds);
		return true;
	}

}
