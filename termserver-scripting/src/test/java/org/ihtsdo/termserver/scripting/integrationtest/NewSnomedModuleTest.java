package org.ihtsdo.termserver.scripting.integrationtest;

import org.ihtsdo.termserver.scripting.domain.ConceptHelper;
import org.junit.Assert;
import org.junit.Test;
import us.monoid.json.JSONObject;

public class NewSnomedModuleTest extends AbstractIntegrationTest {

	@Test
	public void testNewModuleCreation() throws Exception {
		// Create module
		final String ihtsdoMaintainedModule = "900000000000445007";
		JSONObject drugsModuleConcept = ConceptHelper.newConcept("Drugs module (core metadata concept)", "Drugs module", ihtsdoMaintainedModule);
		ConceptHelper.setModule("900000000000012004", drugsModuleConcept);
		drugsModuleConcept = client.createConcept(drugsModuleConcept, branchPath).toObject();
		final String drugsModuleId = ConceptHelper.getConceptId(drugsModuleConcept);

		// Create a concept in new module
		JSONObject newDrugA = ConceptHelper.newConcept("New drug A (drugs)", "New drug A", "410942007");
		ConceptHelper.setModule(drugsModuleId, newDrugA);
		newDrugA = client.createConcept(newDrugA, branchPath).toObject();
		Assert.assertEquals(drugsModuleId, newDrugA.getString("moduleId"));

		client.classifyAndWaitForComplete(branchPath);
	}

	@Override
	public void tearDown() {
		// empty teardown does not delete the branch
	}
}
