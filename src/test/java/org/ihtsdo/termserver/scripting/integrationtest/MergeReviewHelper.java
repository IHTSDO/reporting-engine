package org.ihtsdo.termserver.scripting.integrationtest;

import org.ihtsdo.termserver.scripting.domain.ConceptHelper;
import org.junit.Test;
import us.monoid.json.JSONArray;
import us.monoid.json.JSONObject;

public class MergeReviewHelper extends AbstractIntegrationTest {

	@Test
	public void createConflictingClassificationResultsInParentAndChildBranches() throws Exception {
		final String projectBranch = client.createBranch("MAIN", "WRP-2368");
		final JSONObject concept = client.getConcept("164005006", projectBranch).toObject();
		ConceptHelper.addRelationship("363698007", "12626005", concept);
		client.updateConcept(concept, projectBranch);

		final String taskBranch = client.createBranch(projectBranch, "task1");

		client.classifyAndWaitForComplete(projectBranch);
		client.classifyAndWaitForComplete(taskBranch);
	}

	@Test
	public void acceptAutomergedConcepts() throws Exception {
		final String mergeReviewId = "18ee3f05-f748-41dd-8aaf-194dc486d40c";
		final JSONArray jsonArray = client.getMergeReviewDetails(mergeReviewId);
		for (int i = 0; i < jsonArray.length(); i++) {
			final JSONObject jsonObject = (JSONObject) jsonArray.get(i);
			final JSONObject autoMergedConcept = jsonObject.getJSONObject("autoMergedConcept");
			client.saveConceptMerge(mergeReviewId, autoMergedConcept);
		}
	}

}
