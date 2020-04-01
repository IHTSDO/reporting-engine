package org.ihtsdo.termserver.scripting.util;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

public class SnomedUtilsTest {

	@Test
	public void deconstructFSNTest() {
		String testFSN = "foo (bar)";
		String [] parts = SnomedUtils.deconstructFSN(testFSN);
		assertEquals(parts[0], "foo");
		assertEquals(parts[1], "(bar)");
	}
	
	@Test
	public void deconstructFilenameTest() {
		File testFile = new File ("/foo/bar/myfile.txt");
		String[] parts = SnomedUtils.deconstructFilename(testFile);
		assertEquals("/foo/bar",parts[0]);
		assertEquals("myfile",parts[1]);
		assertEquals("txt", parts[2]);
		
		testFile = new File ("/foo/bar/myfile");
		parts = SnomedUtils.deconstructFilename(testFile);
		assertEquals("", parts[2]);
		
		testFile = new File ("/foo/bar/");
		parts = SnomedUtils.deconstructFilename(testFile);
		assertEquals("bar", parts[1]);
		assertEquals("", parts[2]);
	}

}
