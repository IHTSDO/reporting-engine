package org.ihtsdo.snowowl.test;

import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;
import us.monoid.web.JSONResource;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractIntegrationTest implements SnowOwlClientEventListener {

	private final String url = "http://localhost:8080/snowowl/snomed-ct/v2";
	protected SnowOwlClient client;
	protected String branchName;
	protected String branchPath;
	private Set<String> branchesToTearDown;
	private Logger logger = LoggerFactory.getLogger(getClass());

	@Before
	public void setup() throws IOException, JSONException {
		branchesToTearDown = new HashSet<>();
		client = new SnowOwlClient(url, "snowowl", "snowowl");
		client.addEventListener(this);
		branchName = generateTestBranchName();
		branchPath = client.createBranch("MAIN", branchName);
		branchesToTearDown.add(branchPath);
	}

	public JSONResource createConcept(String branchPath, String fsn, String pt, String parentId) throws Exception {
		final JSONObject json = ConceptHelper.createConcept(fsn, pt, parentId);
		return client.createConcept(json, branchPath);
	}

	private String generateTestBranchName() {
		return "test_" + new SimpleDateFormat("yyyyMMdd_hhmmss").format(new Date());
	}

	@After
	public void tearDown() {
		for (String branchPath : branchesToTearDown) {
			try {
				client.deleteBranch(branchPath);
			} catch (Exception e) {
				logger.info("Branch deletion failed during teardown: {}", branchPath, e);
			}
		}
	}

	@Override
	public void branchCreated(String branchPath) {
		branchesToTearDown.add(branchPath);
	}
}
