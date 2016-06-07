package org.ihtsdo.termserver.scripting.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class SnomedUtilsTest {

	@Test
	public void deconstructFSNTest() {
		String testFSN = "foo (bar)";
		String [] parts = SnomedUtils.deconstructFSN(testFSN);
		assertEquals(parts[0], "foo");
		assertEquals(parts[1], "(bar)");
	}

}
