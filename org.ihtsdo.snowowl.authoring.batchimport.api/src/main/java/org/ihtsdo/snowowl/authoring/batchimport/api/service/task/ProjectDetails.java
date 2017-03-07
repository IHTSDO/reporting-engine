package org.ihtsdo.snowowl.authoring.batchimport.api.service.task;

public class ProjectDetails {

	private final String baseBranchPath;
	private final String productCode;

	public ProjectDetails(String baseBranchPath, String productCode) {
		this.baseBranchPath = baseBranchPath;
		this.productCode = productCode;
	}

	public String getBaseBranchPath() {
		return baseBranchPath;
	}

	public String getProductCode() {
		return productCode;
	}
}
