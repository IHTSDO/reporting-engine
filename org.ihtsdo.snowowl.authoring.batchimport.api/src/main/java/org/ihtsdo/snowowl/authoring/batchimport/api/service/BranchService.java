package org.ihtsdo.snowowl.authoring.batchimport.api.service;

import com.b2international.snowowl.core.branch.Branch;
import com.b2international.snowowl.core.exceptions.NotFoundException;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;
import org.springframework.beans.factory.annotation.Autowired;

public class BranchService {
	
	@Autowired
	private IEventBus eventBus;

	public void createTaskBranchAndProjectBranchIfNeeded(String branchPath) {
		createProjectBranchIfNeeded(PathHelper.getParentPath(branchPath));
		createBranch(branchPath);
	}

	public Branch getBranch(String branchPath) {
		return SnomedRequests.branching().prepareGet(branchPath).executeSync(eventBus);
	}

	private void createBranch(String branchPath) {
		SnomedRequests
				.branching()
				.prepareCreate()
				.setParent(PathHelper.getParentPath(branchPath))
				.setName(PathHelper.getName(branchPath))
				.build()
				.executeSync(eventBus);
	}

	public void createProjectBranchIfNeeded(String branchPath) {
		try {
			getBranch(branchPath);
		} catch (NotFoundException e) {
			createBranch(branchPath);
		}
	}

}
