package org.ihtsdo.snowowl.authoring.single.api.validation.domain;

import com.b2international.snowowl.snomed.api.domain.browser.ISnomedBrowserConcept;
import com.b2international.snowowl.snomed.api.domain.browser.ISnomedBrowserDescription;
import com.b2international.snowowl.snomed.api.domain.browser.ISnomedBrowserRelationship;
import org.ihtsdo.drools.domain.Description;
import org.ihtsdo.drools.domain.Relationship;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ValidationConcept implements org.ihtsdo.drools.domain.Concept {

	private ISnomedBrowserConcept browserConcept;
	private List<Description> descriptions;
	private List<Relationship> relationships;

	public ValidationConcept(ISnomedBrowserConcept browserConcept) {
		this.browserConcept = browserConcept;
		String conceptId = browserConcept.getConceptId();
		descriptions = new ArrayList<>();
		for (ISnomedBrowserDescription browserDescription : browserConcept.getDescriptions()) {
			descriptions.add(new ValidationDescription(browserDescription, conceptId));
		}
		relationships = new ArrayList<>();
		for (ISnomedBrowserRelationship browserRelationship : browserConcept.getRelationships()) {
			relationships.add(new ValidationRelationship(browserRelationship, conceptId));
		}
	}
	
	@Override
	public String getId() {
		return browserConcept.getConceptId();
	}
	
	@Override
	public boolean isActive() {
		return browserConcept.isActive();
	}
	
	@Override
	public boolean isPublished() {
		return browserConcept.getEffectiveTime() != null;
	}

	@Override
	public String getDefinitionStatusId() {
		return browserConcept.getDefinitionStatus().getConceptId();
	}

	@Override
	public Collection<Description> getDescriptions() {
		return descriptions;
	}

	@Override
	public Collection<Relationship> getRelationships() {
		return relationships;
	}

}
