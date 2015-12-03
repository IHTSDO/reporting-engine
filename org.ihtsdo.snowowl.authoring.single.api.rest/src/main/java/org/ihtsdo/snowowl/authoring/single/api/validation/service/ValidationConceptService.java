package org.ihtsdo.snowowl.authoring.single.api.validation.service;

import org.ihtsdo.drools.service.ConceptService;

import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.snomed.datastore.SnomedTerminologyBrowser;

public class ValidationConceptService implements ConceptService {

	private SnomedTerminologyBrowser terminologyBrowser;
	private IBranchPath branchPath = null;

	public ValidationConceptService(IBranchPath branchPath, SnomedTerminologyBrowser terminologyBrowser) {
		this.branchPath = branchPath;
		this.terminologyBrowser = terminologyBrowser;
	}

	@Override
	public boolean isActive(String conceptId) {
		return terminologyBrowser.getConcept(branchPath, conceptId).isActive();
	}

}
