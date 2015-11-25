package org.ihtsdo.snowowl.authoring.single.api.validation.service;

import org.ihtsdo.drools.service.RelationshipService;

import com.b2international.snowowl.api.domain.IComponentList;
import com.b2international.snowowl.api.domain.IComponentRef;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.snomed.api.ISnomedStatementBrowserService;
import com.b2international.snowowl.snomed.api.domain.CharacteristicType;
import com.b2international.snowowl.snomed.api.domain.ISnomedRelationship;
import com.b2international.snowowl.snomed.api.impl.SnomedServiceHelper;

public class ValidationRelationshipService implements RelationshipService {

	private IBranchPath path;
	private ISnomedStatementBrowserService statementBrowserService;

	public ValidationRelationshipService(IBranchPath path) {
		this.path = path;
		this.statementBrowserService = ApplicationContext.getServiceForClass(ISnomedStatementBrowserService.class);
	}

	@Override
	public boolean hasActiveInboundStatedRelationship(String conceptId) {
		return hasActiveInboundStatedRelationship(conceptId, null);
	}

	@Override
	public boolean hasActiveInboundStatedRelationship(String conceptId, String relationshipTypeId) {
		IComponentRef conceptRef = SnomedServiceHelper.createComponentRef(path.getPath(), conceptId);
		IComponentList<ISnomedRelationship> inboundEdges = statementBrowserService.getInboundEdges(conceptRef, 0, Integer.MAX_VALUE);
		for (ISnomedRelationship relationship : inboundEdges.getMembers()) {
			if (relationship.isActive() && relationship.getCharacteristicType() != CharacteristicType.INFERRED_RELATIONSHIP
					&& (relationshipTypeId == null || relationshipTypeId.equals(relationship.getTypeId()))) {
				return true;
			}
		}
		return false;
	}

}
