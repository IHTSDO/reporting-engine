package org.ihtsdo.snowowl.authoring.single.api.validation.service;

import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.core.domain.CharacteristicType;
import com.b2international.snowowl.snomed.core.domain.ISnomedRelationship;
import com.b2international.snowowl.snomed.core.domain.SnomedRelationships;
import com.b2international.snowowl.snomed.datastore.server.request.SnomedRequests;
import org.ihtsdo.drools.service.RelationshipService;

public class ValidationRelationshipService implements RelationshipService {

	private final IEventBus eventBus;
	private String branchPath;

	public ValidationRelationshipService(IEventBus eventBus, String branchPath) {
		this.eventBus = eventBus;
		this.branchPath = branchPath;
	}

	@Override
	public boolean hasActiveInboundStatedRelationship(String conceptId) {
		return hasActiveInboundStatedRelationship(conceptId, null);
	}

	@Override
	public boolean hasActiveInboundStatedRelationship(String conceptId, String relationshipTypeId) {
		final SnomedRelationships iSnomedRelationships = SnomedRequests
				.prepareSearchRelationship()
				.filterByDestination(conceptId)
				.setLimit(Integer.MAX_VALUE)
				.build(branchPath)
				.executeSync(eventBus);

		for (ISnomedRelationship relationship : iSnomedRelationships.getItems()) {
			if (relationship.isActive() && relationship.getCharacteristicType() != CharacteristicType.INFERRED_RELATIONSHIP
					&& (relationshipTypeId == null || relationshipTypeId.equals(relationship.getTypeId()))) {
				return true;
			}
		}
		return false;
	}

}
