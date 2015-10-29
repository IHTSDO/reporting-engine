package org.ihtsdo.snowowl.test;

public class ClassificationTest extends AbstractIntegrationTest {

	// This only tests that a classification completes
//	@Test TODO: fixme
	public void testClassify() throws Exception {
		createConcept(branchPath, "b (finding)", "b", "116680003");
		final String id = client.classify(branchPath);
		System.out.println(id);
	}

}
