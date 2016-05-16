package org.ihtsdo.termserver.scripting.domain;

public interface RF2Constants {
	
	
	static int NA = -1;
	static Concept PHARM_BIO_PRODUCT = new Concept ("373873005",NA) ; //Pharmaceutical / biologic product (product)
	static Concept IS_A =  new Concept ("116680003", NA);  // | Is a (attribute) |
	static Concept HAS_ACTIVE_INGRED = new Concept ("127489000", NA);
	
	public enum CHARACTERISTIC_TYPE {	STATED_RELATIONSHIP, INFERRED_RELATIONSHIP, 
										QUALIFYING_RELATIONSHIP, ADDITIONAL_RELATIONSHIP};

	public enum DEFINITION_STATUS { PRIMITIVE, FULLY_DEFINED };
	
	public enum MODIFER { EXISTENTIAL, UNIVERSAL};
}
