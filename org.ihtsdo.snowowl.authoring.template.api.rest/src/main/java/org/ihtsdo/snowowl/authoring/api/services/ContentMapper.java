package org.ihtsdo.snowowl.authoring.single.api.services;

import com.b2international.snowowl.snomed.SnomedConstants;
import com.b2international.snowowl.snomed.api.domain.Acceptability;
import com.b2international.snowowl.snomed.api.domain.CharacteristicType;
import com.b2international.snowowl.snomed.api.rest.domain.SnomedConceptRestInput;
import com.b2international.snowowl.snomed.api.rest.domain.SnomedDescriptionRestInput;
import com.b2international.snowowl.snomed.api.rest.domain.SnomedRelationshipRestInput;
import org.ihtsdo.snowowl.authoring.single.api.model.lexical.LexicalModel;
import org.ihtsdo.snowowl.authoring.single.api.model.work.WorkingConcept;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContentMapper {

	public SnomedConceptRestInput getConceptInput(LexicalModel lexicalModel, WorkingConcept concept) {
		SnomedConceptRestInput change = new SnomedConceptRestInput();

		List<String> isARelationships = concept.getParents();
		Assert.isTrue(!isARelationships.isEmpty(), "At least one parent concept is required.");
		String isAId = isARelationships.get(0);
		change.setIsAId(isAId);
		change.setParentId(isAId);
		change.setModuleId(SnomedConstants.Concepts.MODULE_SCT_CORE);

		List<SnomedDescriptionRestInput> descriptions = new ArrayList<>();
		String term = concept.getTerm();
		descriptions.add(getFsn(lexicalModel, term));
		descriptions.add(getPreferredTerm(lexicalModel, term));
		descriptions.add(getSynonym(lexicalModel, term));

		change.setDescriptions(descriptions);

		return change;
	}

	public List<SnomedRelationshipRestInput> getRelationshipInputs(WorkingConcept concept) {
		List<SnomedRelationshipRestInput> relationshipInputs = new ArrayList<>();

		// Parents other than the first
		List<String> isARelationships = concept.getParents();
		for (int i = 1; i < isARelationships.size(); i++) {
			String isARelationship = isARelationships.get(i);
			SnomedRelationshipRestInput input = new SnomedRelationshipRestInput();
			input.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
			input.setTypeId(SnomedConstants.Concepts.IS_A);
			input.setSourceId(concept.getId());
			input.setDestinationId(isARelationship);
			input.setModuleId(SnomedConstants.Concepts.MODULE_SCT_CORE);
			relationshipInputs.add(input);
		}

		// Attributes
		for (int group = 0; group < concept.getAttributeGroups().size(); group++) {
			Map<String, String> attributes = concept.getAttributeGroups().get(group);
			for (String attributeType : attributes.keySet()) {
				String attributeValue = attributes.get(attributeType);
				SnomedRelationshipRestInput input = new SnomedRelationshipRestInput();
				input.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
				input.setTypeId(attributeType);
				input.setSourceId(concept.getId());
				input.setDestinationId(attributeValue);
				input.setGroup(group);
				input.setModuleId(SnomedConstants.Concepts.MODULE_SCT_CORE);
				relationshipInputs.add(input);
			}
		}

		return relationshipInputs;
	}

	private SnomedDescriptionRestInput getFsn(LexicalModel lexicalModel, String term) {
		String builtTerm = lexicalModel.getFsn().buildTerm(term);
		return newTerm(Acceptability.PREFERRED, SnomedConstants.Concepts.FULLY_SPECIFIED_NAME, builtTerm);
	}

	private SnomedDescriptionRestInput getPreferredTerm(LexicalModel lexicalModel, String term) {
		String builtTerm = lexicalModel.getPreferredTerm().buildTerm(term);
		return newTerm(Acceptability.PREFERRED, SnomedConstants.Concepts.SYNONYM, builtTerm);
	}

	private SnomedDescriptionRestInput getSynonym(LexicalModel lexicalModel, String term) {
		String builtTerm = lexicalModel.getSynonom().buildTerm(term);
		return newTerm(Acceptability.ACCEPTABLE, SnomedConstants.Concepts.SYNONYM, builtTerm);
	}

	private SnomedDescriptionRestInput newTerm(Acceptability acceptability, String typeId, String builtTerm) {
		SnomedDescriptionRestInput fsn = new SnomedDescriptionRestInput();

		fsn.setLanguageCode(SnomedConstants.LanguageCodeReferenceSetIdentifierMapping.EN_LANGUAGE_CODE);

		Map<String, Acceptability> acceptabilityMap = new HashMap<>();
		acceptabilityMap.put(SnomedConstants.Concepts.REFSET_LANGUAGE_TYPE_UK, acceptability);
		acceptabilityMap.put(SnomedConstants.Concepts.REFSET_LANGUAGE_TYPE_US, acceptability);
		fsn.setAcceptability(acceptabilityMap);

		fsn.setTypeId(typeId);
		fsn.setTerm(builtTerm);
		return fsn;
	}
}
