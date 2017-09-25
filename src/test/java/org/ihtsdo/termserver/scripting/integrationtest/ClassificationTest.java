package org.ihtsdo.termserver.scripting.integrationtest;

import org.junit.After;
import org.junit.Test;

public class ClassificationTest extends AbstractIntegrationTest {

	// This only tests that a classification starts
	@Test
	public void testClassify() throws Exception {
		for (int i = 0; i < 11; i++) {
			branchName = generateTestBranchName();
			branchPath = client.createBranch("MAIN", branchName);
			createConcept(branchPath, "b (finding)", "b", "116680003");
			System.out.println(client.classifyAndWaitForComplete(branchPath));
		}
	}

	@After
	public void tearDown() {
	}

}
