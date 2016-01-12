package org.ihtsdo.snowowl.test;

import org.ihtsdo.snowowl.test.domain.ConceptHelper;
import org.ihtsdo.snowowl.test.domain.ConceptIds;
import org.junit.Assert;
import org.junit.Test;
import us.monoid.json.JSONObject;

import java.io.IOException;

public class MergeTest extends AbstractIntegrationTest {

	@Test(expected = IOException.class)
	public void testMergeConceptsWithoutRebase() throws Exception {
		final String childBranchPath = client.createBranch(branchPath, "child");
		createConcept(childBranchPath, "a (finding)", "a", "116680003");
		createConcept(branchPath, "b (finding)", "b", "116680003");
		client.mergeBranch(childBranchPath, branchPath);
	}

	@Test
	public void testMergeConceptsWithoutDuplicatesWithRebase() throws Exception {
		final String childBranchPath = client.createBranch(branchPath, "child");
		createConcept(childBranchPath, "a (finding)", "a", "116680003");
		createConcept(branchPath, "b (finding)", "b", "116680003");
		client.mergeBranch(branchPath, childBranchPath);
		client.mergeBranch(childBranchPath, branchPath);
	}

	@Test
	public void testMergeConceptsWithDuplicateFSN() throws Exception {
		final String childBranchPath = client.createBranch(branchPath, "child");
		final String idFromChild = createConcept(childBranchPath, "a (finding)", "a", "116680003").toObject().getString("conceptId");
		final String idFromParent = createConcept(branchPath, "a (finding)", "b", "116680003").toObject().getString("conceptId");
		// Rebase
		client.mergeBranch(branchPath, childBranchPath);
		// Promote
		client.mergeBranch(childBranchPath, branchPath);
		final JSONObject conceptFromChild = client.getConcept(idFromChild, branchPath).toObject();
		final JSONObject conceptFromParent = client.getConcept(idFromParent, branchPath).toObject();
		Assert.assertNotEquals(conceptFromChild.getString("conceptId"), conceptFromParent.getString("conceptId"));
		Assert.assertEquals(conceptFromChild.getString("fsn"), conceptFromParent.getString("fsn"));
	}

	@Test(expected = IOException.class)
	public void testMergeConceptsWithDuplicateSCTID() throws Exception {
		final String childBranchPath = client.createBranch(branchPath, "child");
		Assert.assertEquals(ConceptIds.exampleExtensionConceptId, client.createConcept(ConceptHelper.newConcept("created on child").put("conceptId", ConceptIds.exampleExtensionConceptId), childBranchPath).toObject().getString("conceptId"));
		Assert.assertEquals(ConceptIds.exampleExtensionConceptId, client.createConcept(ConceptHelper.newConcept("created on parent").put("conceptId", ConceptIds.exampleExtensionConceptId), branchPath).toObject().getString("conceptId"));
		// Rebase
		client.mergeBranch(branchPath, childBranchPath);
	}

	@Test
	public void testCreateConceptsWithDuplicateFSN() throws Exception {
		createConcept(branchPath, "a (finding)", "a", "116680003");
		createConcept(branchPath, "a (finding)", "a", "116680003");
	}

}
