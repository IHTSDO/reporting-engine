package org.ihtsdo.termserver.scripting.fixes;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.ConceptType;
import org.junit.Test;

public class DrugProductFixTest {
	
	DrugProductFix dpf = new DrugProductFix(null);

	@Test
	public void DetermineConceptTypeTest() {
		Concept testConcept = new Concept ("1", "Acetic acid + hydrocortisone 2%vv/1%wv solution (product)");
		dpf.determineConceptType(testConcept);
		assertEquals(ConceptType.PRODUCT_STRENGTH, testConcept.getConceptType());
		
		testConcept = new Concept ("2","Telmisartan 80mg + amlodipine 10mg tablet (product)");
		dpf.determineConceptType(testConcept);
		assertEquals(ConceptType.PRODUCT_STRENGTH, testConcept.getConceptType());
	}
	
	@Test
	public void splitBatchIntoChunksTest() {
		int chunkSize = 5;
		//Check splitting concepts into max 5 with 3, 5, 6, 7, 8, 11 concepts
		ArrayList<Concept> testConcepts = new ArrayList<Concept>();
		testConcepts.addAll(getTestConcepts(1, 3));
		testConcepts.get(2).setConceptType(ConceptType.MEDICINAL_ENTITY);
		List<List<Concept>> chunked = dpf.splitBatchIntoChunks(new ArrayList<Concept>(testConcepts), chunkSize);
		assertEquals(1, chunked.size());
		assertEquals(3, chunked.get(0).size());
		
		testConcepts.addAll(getTestConcepts(4, 5));
		chunked = dpf.splitBatchIntoChunks(new ArrayList<Concept>(testConcepts), chunkSize);
		assertEquals(1, chunked.size());
		assertEquals(5, chunked.get(0).size());
		
		testConcepts.addAll(getTestConcepts(6, 6));
		chunked = dpf.splitBatchIntoChunks(new ArrayList<Concept>(testConcepts), chunkSize);
		assertEquals(2, chunked.size());
		assertEquals(3, chunked.get(0).size());
		assertEquals(3, chunked.get(1).size());
		
		testConcepts.addAll(getTestConcepts(7, 7));
		chunked = dpf.splitBatchIntoChunks(new ArrayList<Concept>(testConcepts), chunkSize);
		assertEquals(2, chunked.size());
		assertEquals(4, chunked.get(0).size());
		assertEquals(3, chunked.get(1).size());
		
		testConcepts.addAll(getTestConcepts(8, 8));
		testConcepts.get(7).setConceptType(ConceptType.MEDICINAL_ENTITY);
		chunked = dpf.splitBatchIntoChunks(new ArrayList<Concept>(testConcepts), chunkSize);
		assertEquals(2, chunked.size());
		assertEquals(4, chunked.get(0).size());
		assertEquals(4, chunked.get(1).size());
		
		testConcepts.addAll(getTestConcepts(9, 11));
		chunked = dpf.splitBatchIntoChunks(new ArrayList<Concept>(testConcepts), chunkSize);
		assertEquals(3, chunked.size());
		assertEquals(4, chunked.get(0).size());
		assertEquals(4, chunked.get(1).size());
		assertEquals(3, chunked.get(2).size());
		
	}
	
	private List<Concept> getTestConcepts(int from, int to) {
		List<Concept> testConcepts = new ArrayList<Concept>();
		for (int x = from; x <= to; x++) {
			Concept c = new Concept ( Integer.toString(x), "Test Concept " + x);
			testConcepts.add(c);
		}
		return testConcepts;
	}

}
