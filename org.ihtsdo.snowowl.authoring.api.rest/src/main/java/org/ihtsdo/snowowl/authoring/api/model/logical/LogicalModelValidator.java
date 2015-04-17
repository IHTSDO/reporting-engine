package org.ihtsdo.snowowl.authoring.api.model.logical;

import org.ihtsdo.snowowl.api.rest.common.ComponentRefHelper;
import org.ihtsdo.snowowl.authoring.api.Constants;
import org.ihtsdo.snowowl.authoring.api.model.AuthoringContent;
import org.ihtsdo.snowowl.authoring.api.model.AuthoringContentValidationResult;
import org.ihtsdo.snowowl.authoring.api.services.ContentService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LogicalModelValidator {

	@Autowired
	private ContentService contentService;

	@Autowired
	private ComponentRefHelper componentRefHelper;

	public AuthoringContentValidationResult validate(AuthoringContent content, LogicalModel logicalModel) {
		AuthoringContentValidationResult result = new AuthoringContentValidationResult();
		Map<String, Set<String>> descendantsCache = new HashMap<>();

		List<String> isARelationships = content.getIsARelationships();
		List<IsARestriction> isARestrictions = logicalModel.getIsARestrictions();
		boolean tooManyIsARelationships = isARelationships.size() > isARestrictions.size();
		boolean notEnoughIsARelationships = isARelationships.size() < isARestrictions.size();

		for (int i = 0; i < isARelationships.size(); i++) {
			String message = "";
			if (isARestrictions.size() > i) {
				IsARestriction isARestriction = isARestrictions.get(i);
				String isARelationship = isARelationships.get(i);
				message = validate(isARelationship, isARestriction, descendantsCache);
			}
			if ((notEnoughIsARelationships || tooManyIsARelationships) && i == isARelationships.size() - 1) {
				if(!message.isEmpty()) {
					message += "\n";
				}
				message += "There are " + (notEnoughIsARelationships ? "less" : "more") + " isA relationships than in the logical model.";
			}
			result.addIsARelationshipsMessage(message);
		}

		return result;
	}

	private String validate(String isARelationship, IsARestriction isARestriction, Map<String, Set<String>> descendantsCache) {
		String message = "";
		String conceptId = isARestriction.getConceptId();
		switch (isARestriction.getRangeRelationType()) {
			case SELF:
				if (!conceptId.equals(isARelationship)) {
					message = "IsA relation must be '" + conceptId + "'";
				}
				break;
			case DESCENDANTS:
				if (!isDescendant(isARelationship, conceptId, descendantsCache)) {
					message = "IsA relation must be a descendant of '" + conceptId + "'";
				}
				break;
			case DESCENDANTS_AND_SELF:
				if (!conceptId.equals(isARelationship) && !isDescendant(isARelationship, conceptId, descendantsCache)) {
					message = "IsA relation must be a descendant of or equal to '" + conceptId + "'";
				}
				break;
		}

		return message;
	}

	private boolean isDescendant(String isARelationship, String conceptId, Map<String, Set<String>> descendantsCache) {
		if (!descendantsCache.containsKey(conceptId)) {
			descendantsCache.put(conceptId, contentService.getDescendantIds(componentRefHelper.createComponentRef(Constants.MAIN, null, conceptId)));
		}
		Set<String> descendantIds = descendantsCache.get(conceptId);
		return descendantIds.contains(isARelationship);
	}

}
