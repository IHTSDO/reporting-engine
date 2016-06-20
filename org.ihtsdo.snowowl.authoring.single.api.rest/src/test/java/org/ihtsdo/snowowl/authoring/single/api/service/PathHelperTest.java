package org.ihtsdo.snowowl.authoring.single.api.service;

import org.junit.Assert;
import org.junit.Test;

public class PathHelperTest {

	@Test
	public void testGetMainPath() throws Exception {
		Assert.assertEquals("MAIN", PathHelper.getMainPath());
	}

	@Test
	public void testGetProjectPath() throws Exception {
		Assert.assertEquals("MAIN/PROJECTA", PathHelper.getProjectPath(null, "PROJECTA"));
		Assert.assertEquals("MAIN/2016-07-31/PROJECTA", PathHelper.getProjectPath("MAIN/2016-07-31", "PROJECTA"));
	}

	@Test
	public void testGetTaskPath() throws Exception {
		Assert.assertEquals("MAIN/PROJECTA/PROJECTA-5", PathHelper.getTaskPath(null, "PROJECTA", "PROJECTA-5"));
		Assert.assertEquals("MAIN/2016-07-31/PROJECTA/PROJECTA-5", PathHelper.getTaskPath("MAIN/2016-07-31", "PROJECTA", "PROJECTA-5"));

	}

	@Test
	public void testGetParent() throws Exception {
		Assert.assertEquals("MAIN/2016-07-31/PROJECTA", PathHelper.getParentPath("MAIN/2016-07-31/PROJECTA/PROJECTA-5"));
	}

	@Test
	public void testGetName() throws Exception {
		Assert.assertEquals("PROJECTA-5", PathHelper.getName("MAIN/2016-07-31/PROJECTA/PROJECTA-5"));
	}

	@Test
	public void testGetParentName() throws Exception {
		Assert.assertEquals("PROJECTA", PathHelper.getParentName("MAIN/2016-07-31/PROJECTA/PROJECTA-5"));
		Assert.assertEquals("2016-07-31", PathHelper.getParentName("MAIN/2016-07-31/PROJECTA"));
		Assert.assertEquals("MAIN", PathHelper.getParentName("MAIN/PROJECTA"));
	}

}