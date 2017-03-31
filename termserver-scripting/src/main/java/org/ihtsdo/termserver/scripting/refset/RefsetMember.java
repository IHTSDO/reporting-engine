package org.ihtsdo.termserver.scripting.refset;

import org.ihtsdo.termserver.scripting.domain.Concept;

public class RefsetMember {
	
	String refsetId;
	Concept referencedComponentId;
	String[] additionalValues;
	
	public RefsetMember (String refsetId, Concept referencedComponentId, String[] additionalValues) {
		this.refsetId = refsetId;
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

	public String getRefsetId() {
		return refsetId;
	}

	public void setRefsetId(String refsetId) {
		this.refsetId = refsetId;
	}
}
