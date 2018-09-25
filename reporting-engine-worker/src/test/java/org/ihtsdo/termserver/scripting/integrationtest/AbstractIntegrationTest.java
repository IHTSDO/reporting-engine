package org.ihtsdo.termserver.scripting.integrationtest;

import org.ihtsdo.termserver.scripting.client.SnowOwlClient;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientEventListener;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.ConceptHelper;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.monoid.json.JSONObject;
import us.monoid.web.JSONResource;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractIntegrationTest implements SnowOwlClientEventListener {

	private final String url = "http://localhost:8080/snowowl/snomed-ct/v2";
//	private final String url = "https://dev-term.ihtsdotools.org/snowowl/snomed-ct/v2";
	protected SnowOwlClient client;
	protected String branchName;
	protected String branchPath;
	protected Set<String> branchesToTearDown;
	private Logger logger = LoggerFactory.getLogger(getClass());

	@Before
	public void setup() throws SnowOwlClientException {
		branchesToTearDown = new HashSet<>();
		//TODO Wire in security which can use token override
		client = new SnowOwlClient(url, null);
		client.addEventListener(this);
		branchName = generateTestBranchName();
		branchPath = client.createBranch("MAIN", branchName);
		branchesToTearDown.add(branchPath);
	}

	public JSONResource createConcept(String branchPath, String fsn, String pt, String parentId) throws Exception {
		final JSONObject json = ConceptHelper.newConcept(fsn, pt, parentId);
		return client.createConcept(json, branchPath);
	}

	String generateTestBranchName() {
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
