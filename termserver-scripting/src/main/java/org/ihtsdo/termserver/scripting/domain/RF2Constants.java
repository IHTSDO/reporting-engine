package org.ihtsdo.termserver.scripting.domain;

public interface RF2Constants {
	
	public enum CHARACTERISTIC_TYPE {	STATED_RELATIONSHIP, INFERRED_RELATIONSHIP, 
										QUALIFYING_RELATIONSHIP, ADDITIONAL_RELATIONSHIP};

	public enum DEFINITION_STATUS { PRIMITIVE, FULLY_DEFINED };
	
	public enum MODIFER { EXISTENTIAL, UNIVERSAL};
}
