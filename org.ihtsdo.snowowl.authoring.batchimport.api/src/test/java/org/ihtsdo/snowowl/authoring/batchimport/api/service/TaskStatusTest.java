package org.ihtsdo.snowowl.authoring.batchimport.api.service;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.batchimport.api.service.task.TaskStatus;
import org.junit.Assert;
import org.junit.Test;

public class TaskStatusTest {

	@Test
	public void testValueOfAndEquals() throws BusinessServiceException {
		Assert.assertTrue(TaskStatus.fromLabelOrThrow("In Progress") == TaskStatus.IN_PROGRESS);
	}

	@Test(expected = BusinessServiceException.class)
	public void testThrow() throws BusinessServiceException {
		TaskStatus.fromLabelOrThrow("Something");
	}

}
