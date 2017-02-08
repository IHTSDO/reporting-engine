package org.ihtsdo.termserver.scripting.refset;

import org.ihtsdo.termserver.scripting.domain.Concept;

public class RefsetMember {
	Concept referencedComponentId;
	String[] additionalValues;
	
	public RefsetMember (Concept referencedComponentId, String[] additionalValues) {
		this.referencedComponentId = referencedComponentId;
		this.additionalValues = additionalValues;
	}

	public Concept getReferencedComponentId() {
		return referencedComponentId;
	}

	public void setReferencedComponentId(Concept referencedComponentId) {
		this.referencedComponentId = referencedComponentId;
	}

	public String[] getAdditionalValues() {
		return additionalValues;
	}

	public void setAdditionalValues(String[] additionalValues) {
		this.additionalValues = additionalValues;
	}
}
