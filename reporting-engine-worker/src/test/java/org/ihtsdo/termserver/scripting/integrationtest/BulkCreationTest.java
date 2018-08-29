package org.ihtsdo.termserver.scripting.integrationtest;

import org.ihtsdo.termserver.scripting.domain.ConceptHelper;
import org.ihtsdo.termserver.scripting.domain.ConceptIds;
import org.ihtsdo.termserver.scripting.util.RandomWords;
import org.ihtsdo.termserver.scripting.util.TimerUtil;
import org.junit.Test;

public class BulkCreationTest extends AbstractIntegrationTest {

	@Test
	public void testCreateBatchOfFive() throws Exception {
		createBatch(5);
	}

	@Test
	public void testCreateBatchOfTen() throws Exception {
		createBatch(10);
	}

	private void createBatch(int batchSize) throws Exception {
		final TimerUtil timer = new TimerUtil("Create batch of " + batchSize);
		for (int i = 0; i < batchSize; i++) {
			final String term = RandomWords.generate();
			client.createConcept(ConceptHelper.newConcept(term + " (finding)", term, ConceptIds.clinicalFinding), branchPath);
		}
		timer.finish();
	}

}
